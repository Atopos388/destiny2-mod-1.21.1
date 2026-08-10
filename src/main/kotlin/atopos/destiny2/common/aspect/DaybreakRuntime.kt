package atopos.destiny2.common.aspect

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.effect.SolarIgnitionRuntime
import atopos.destiny2.common.effect.SolarScorchContext
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Server-authoritative roaming Daybreak super and its Beams-aware sword projectiles. */
object DaybreakRuntime {
    const val BUFF_ID = "destiny2-mod:super/daybreak"
    private const val MINECRAFT_CALIBRATION_DURATION_TICKS = 12 * 20
    private const val MINECRAFT_CALIBRATION_MAX_BLADES = 8
    private const val MINECRAFT_CALIBRATION_FIRE_INTERVAL_TICKS = 10L
    private const val MINECRAFT_CALIBRATION_BLADE_SPEED = 1.65
    private const val MINECRAFT_CALIBRATION_BLADE_LIFETIME_TICKS = 45
    private const val MINECRAFT_CALIBRATION_IMPACT_RADIUS = 3.0
    private const val MINECRAFT_CALIBRATION_DIRECT_DAMAGE = 14.0f
    private const val MINECRAFT_CALIBRATION_SPLASH_DAMAGE = 9.0f
    private const val MINECRAFT_CALIBRATION_SCORCH_STACKS = 30

    private data class ActiveDaybreak(
        val expiresAt: Long,
        var nextFireAt: Long,
        var remainingBlades: Int
    )

    private data class DaybreakBlade(
        val ownerId: UUID,
        val castId: UUID,
        val tracking: SolarSuperTrackingProfile,
        val targetId: UUID?,
        var position: Vec3,
        var velocity: Vec3,
        var remainingTicks: Int
    )

    private val active = mutableMapOf<UUID, ActiveDaybreak>()
    private val blades = mutableListOf<DaybreakBlade>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            val activeIterator = active.iterator()
            while (activeIterator.hasNext()) {
                val (playerId, state) = activeIterator.next()
                val player = server.playerList.getPlayer(playerId)
                val now = player?.serverLevel()?.gameTime ?: Long.MAX_VALUE
                if (player == null || !player.isAlive || now >= state.expiresAt) {
                    activeIterator.remove()
                    continue
                }
                tickActivePlayer(player, state)
            }
            tickBlades(server)
        }
    }

    fun activate(player: ServerPlayer): Boolean {
        val now = player.serverLevel().gameTime
        active[player.uuid] = ActiveDaybreak(
            expiresAt = now + MINECRAFT_CALIBRATION_DURATION_TICKS,
            nextFireAt = now,
            remainingBlades = MINECRAFT_CALIBRATION_MAX_BLADES
        )
        DestinyStatusRules.syncTemporaryBuff(player, BUFF_ID, "破晓", MINECRAFT_CALIBRATION_DURATION_TICKS)
        player.serverLevel().sendParticles(ParticleTypes.FLAME, player.x, player.eyeY, player.z, 48, 0.65, 0.85, 0.65, 0.08)
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.75f)
        return true
    }

    fun tryFire(player: ServerPlayer, requestedDirection: Vec3): Boolean {
        val state = active[player.uuid] ?: return false
        val now = player.serverLevel().gameTime
        if (now >= state.expiresAt || now < state.nextFireAt || state.remainingBlades <= 0) return false
        if (!requestedDirection.x.isFinite() || !requestedDirection.y.isFinite() || !requestedDirection.z.isFinite()) return false
        if (requestedDirection.lengthSqr() < 1.0e-6) return false

        val direction = requestedDirection.normalize()
        val hasBeams = DestinyAspectRuntime.hasFragment(player, SolarWarlockFragmentRules.EMBER_OF_BEAMS)
        val tracking = SolarWarlockFragmentRules.beamsTrackingProfile(hasBeams)
        val target = acquireTarget(player, direction, tracking)
        blades += DaybreakBlade(
            ownerId = player.uuid,
            castId = UUID.randomUUID(),
            tracking = tracking,
            targetId = target?.uuid,
            position = player.eyePosition.add(direction.scale(0.8)),
            velocity = direction.scale(MINECRAFT_CALIBRATION_BLADE_SPEED),
            remainingTicks = MINECRAFT_CALIBRATION_BLADE_LIFETIME_TICKS
        )
        state.nextFireAt = now + MINECRAFT_CALIBRATION_FIRE_INTERVAL_TICKS
        state.remainingBlades--
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9f, 0.72f)
        return true
    }

    fun clear(playerId: UUID) {
        active.remove(playerId)
        blades.removeIf { it.ownerId == playerId }
    }

    private fun tickActivePlayer(player: ServerPlayer, state: ActiveDaybreak) {
        player.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 6, 0, false, false, false))
        if (player.serverLevel().gameTime % 4L == 0L) {
            player.serverLevel().sendParticles(ParticleTypes.FLAME, player.x, player.y + 1.0, player.z, 7, 0.40, 0.70, 0.40, 0.02)
        }
        if (player.serverLevel().gameTime % 20L == 0L) {
            DestinyStatusRules.syncTemporaryBuff(
                player,
                BUFF_ID,
                "破晓",
                (state.expiresAt - player.serverLevel().gameTime).toInt().coerceAtLeast(1),
                state.remainingBlades
            )
        }
    }

    private fun acquireTarget(
        player: ServerPlayer,
        direction: Vec3,
        tracking: SolarSuperTrackingProfile
    ): LivingEntity? {
        val range = tracking.acquisitionRange
        return player.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(range)
        ) { candidate ->
            if (!isEnemy(player, candidate) || !player.hasLineOfSight(candidate)) return@getEntitiesOfClass false
            val toTarget = candidate.eyePosition.subtract(player.eyePosition)
            val distanceSquared = toTarget.lengthSqr()
            distanceSquared > 1.0e-6 && distanceSquared <= range * range &&
                direction.dot(toTarget.normalize()) >= tracking.minimumForwardDot
        }.maxByOrNull { candidate ->
            val toTarget = candidate.eyePosition.subtract(player.eyePosition)
            direction.dot(toTarget.normalize()) - toTarget.length() / (range * 10.0)
        }
    }

    private fun tickBlades(server: net.minecraft.server.MinecraftServer) {
        val iterator = blades.iterator()
        while (iterator.hasNext()) {
            val blade = iterator.next()
            val owner = server.playerList.getPlayer(blade.ownerId)
            if (owner == null || !owner.isAlive || blade.remainingTicks-- <= 0) {
                iterator.remove()
                continue
            }
            val level = owner.serverLevel()
            val target = blade.targetId?.let { level.getEntity(it) as? LivingEntity }
                ?.takeIf { it.isAlive && isEnemy(owner, it) }
            if (target != null) {
                val desired = target.eyePosition.subtract(blade.position).normalize()
                    .scale(MINECRAFT_CALIBRATION_BLADE_SPEED)
                blade.velocity = blade.velocity.scale(1.0 - blade.tracking.steeringStrength)
                    .add(desired.scale(blade.tracking.steeringStrength))
                    .normalize().scale(MINECRAFT_CALIBRATION_BLADE_SPEED)
            }
            val previous = blade.position
            val next = previous.add(blade.velocity)
            val blockHit = level.clip(
                ClipContext(previous, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)
            )
            val entityHit = findBladeVictim(level, owner, previous, next)
            blade.position = if (blockHit.type != HitResult.Type.MISS) blockHit.location else next
            level.sendParticles(ParticleTypes.FLAME, blade.position.x, blade.position.y, blade.position.z, 8, 0.09, 0.09, 0.09, 0.02)
            level.sendParticles(ParticleTypes.END_ROD, blade.position.x, blade.position.y, blade.position.z, 2, 0.04, 0.04, 0.04, 0.005)
            if (entityHit != null || blockHit.type != HitResult.Type.MISS) {
                impact(level, owner, blade, entityHit)
                iterator.remove()
            }
        }
    }

    private fun findBladeVictim(level: ServerLevel, owner: ServerPlayer, start: Vec3, end: Vec3): LivingEntity? {
        val bounds = AABB(
            minOf(start.x, end.x), minOf(start.y, end.y), minOf(start.z, end.z),
            maxOf(start.x, end.x), maxOf(start.y, end.y), maxOf(start.z, end.z)
        ).inflate(0.65)
        return level.getEntitiesOfClass(LivingEntity::class.java, bounds) { isEnemy(owner, it) }
            .minByOrNull { it.distanceToSqr(start) }
    }

    private fun impact(
        level: ServerLevel,
        owner: ServerPlayer,
        blade: DaybreakBlade,
        directTarget: LivingEntity?
    ) {
        val center = blade.position
        val context = SolarScorchContext(owner.uuid, SolarDamageKind.SUPER, blade.castId)
        val source = owner.damageSources().indirectMagic(owner, owner)
        val radiusSquared = MINECRAFT_CALIBRATION_IMPACT_RADIUS * MINECRAFT_CALIBRATION_IMPACT_RADIUS
        val victims = level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(center, MINECRAFT_CALIBRATION_IMPACT_RADIUS * 2.0, MINECRAFT_CALIBRATION_IMPACT_RADIUS * 2.0, MINECRAFT_CALIBRATION_IMPACT_RADIUS * 2.0)
        ) { candidate -> isEnemy(owner, candidate) && candidate.position().distanceToSqr(center) <= radiusSquared }
        victims.forEach { victim ->
            val wasAlive = victim.isAlive
            val damage = if (victim === directTarget) MINECRAFT_CALIBRATION_DIRECT_DAMAGE else MINECRAFT_CALIBRATION_SPLASH_DAMAGE
            SolarIgnitionRuntime.withAttributedSolarDamage(victim, context, ignition = false) {
                victim.hurt(source, damage)
            }
            if (wasAlive && !victim.isAlive) {
                SolarWarlockFragmentRuntime.onAttributedFinalBlow(owner, victim, context, wasScorched = false)
            } else if (victim.isAlive) {
                DestinyStatusRules.applyScorch(
                    victim,
                    MINECRAFT_CALIBRATION_SCORCH_STACKS,
                    DestinyStatusRules.MEDIUM_DURATION,
                    owner,
                    SolarDamageKind.SUPER,
                    blade.castId
                )
            }
        }
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 52, 0.80, 0.65, 0.80, 0.10)
        level.sendParticles(ParticleTypes.LAVA, center.x, center.y, center.z, 14, 0.55, 0.45, 0.55, 0.08)
        level.playSound(
            null,
            net.minecraft.core.BlockPos.containing(center),
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS,
            0.9f,
            1.15f
        )
    }

    private fun isEnemy(owner: ServerPlayer, candidate: LivingEntity): Boolean =
        candidate !== owner && candidate.isAlive && !candidate.isAlliedTo(owner) &&
            (candidate !is Player || !candidate.isSpectator)
}
