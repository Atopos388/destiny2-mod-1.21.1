package atopos.destiny2.common.aspect

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.effect.SolarIgnitionRuntime
import atopos.destiny2.common.effect.SolarScorchContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Server-owned runtime for Solar Warlock Aspects that require persistent world state. */
object SolarWarlockAspectRuntime {
    private data class HellionBolt(
        val ownerId: UUID,
        val targetId: UUID,
        val castId: UUID,
        var position: Vec3,
        var velocity: Vec3,
        var remainingTicks: Int
    )

    private val hellionStates = mutableMapOf<UUID, HellionState>()
    private val hellionBolts = mutableMapOf<UUID, MutableList<HellionBolt>>()

    fun onClassAbilityCast(player: ServerPlayer) {
        val now = player.serverLevel().gameTime
        val state = SolarWarlockAspectRules.summonHellion(
            DestinyAspectRuntime.hasAspect(player, SolarWarlockAspectRules.HELLION),
            classAbilityCastSucceeded = true,
            ownerCanAct = canOwnerAct(player),
            currentTick = now
        ) ?: return
        hellionStates[player.uuid] = state
        hellionBolts.remove(player.uuid)
        val anchor = hellionAnchor(player)
        player.serverLevel().sendParticles(ParticleTypes.FLAME, anchor.x, anchor.y, anchor.z, 24, 0.28, 0.28, 0.28, 0.03)
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.65f, 1.35f)
    }

    fun tickPlayer(player: ServerPlayer) {
        val now = player.serverLevel().gameTime
        val state = hellionStates[player.uuid]
        if (state != null) {
            if (!SolarWarlockAspectRules.isHellionActive(state, now) ||
                !DestinyAspectRuntime.hasAspect(player, SolarWarlockAspectRules.HELLION) ||
                !canOwnerAct(player)
            ) {
                hellionStates.remove(player.uuid)
                hellionBolts.remove(player.uuid)
            } else {
                renderHellion(player, now)
                val target = findHellionTarget(player)
                if (SolarWarlockAspectRules.shouldHellionFire(state, now, target != null) && target != null) {
                    fireHellionBolt(player, target)
                    hellionStates[player.uuid] = SolarWarlockAspectRules.hellionAfterShot(state, now)
                }
            }
        }
        tickHellionBolts(player)
    }

    fun clear(playerId: UUID) {
        hellionStates.remove(playerId)
        hellionBolts.remove(playerId)
    }

    private fun renderHellion(player: ServerPlayer, now: Long) {
        if (now % 2L != 0L) return
        val anchor = hellionAnchor(player)
        player.serverLevel().sendParticles(ParticleTypes.SMALL_FLAME, anchor.x, anchor.y, anchor.z, 3, 0.09, 0.09, 0.09, 0.005)
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, anchor.x, anchor.y, anchor.z, 1, 0.03, 0.03, 0.03, 0.002)
    }

    private fun findHellionTarget(player: ServerPlayer): LivingEntity? {
        val range = SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HELLION_TARGET_RANGE_BLOCKS
        return player.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(range)
        ) { candidate ->
            val hostile = candidate !== player && !candidate.isAlliedTo(player) &&
                (candidate !is Player || !candidate.isSpectator)
            SolarWarlockAspectRules.isValidHellionTarget(
                isHostile = hostile,
                isAlive = candidate.isAlive,
                isSpectator = candidate is Player && candidate.isSpectator,
                isSameDimension = candidate.level() === player.level(),
                hasLineOfSight = player.hasLineOfSight(candidate),
                distanceSquared = candidate.distanceToSqr(player)
            )
        }.minByOrNull { it.distanceToSqr(player) }
    }

    private fun fireHellionBolt(player: ServerPlayer, target: LivingEntity) {
        val start = hellionAnchor(player)
        val direction = target.eyePosition.subtract(start).normalize()
        val castId = UUID.randomUUID()
        hellionBolts.getOrPut(player.uuid) { mutableListOf() }.add(
            HellionBolt(
                ownerId = player.uuid,
                targetId = target.uuid,
                castId = castId,
                position = start,
                velocity = direction.scale(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HELLION_BOLT_SPEED),
                remainingTicks = SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HELLION_BOLT_LIFETIME_TICKS
            )
        )
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.55f, 1.45f)
    }

    private fun tickHellionBolts(player: ServerPlayer) {
        val bolts = hellionBolts[player.uuid] ?: return
        val iterator = bolts.iterator()
        while (iterator.hasNext()) {
            val bolt = iterator.next()
            val target = player.serverLevel().getEntity(bolt.targetId) as? LivingEntity
            if (target == null || !target.isAlive || target.isAlliedTo(player) || bolt.remainingTicks-- <= 0) {
                iterator.remove()
                continue
            }
            val desired = target.eyePosition.subtract(bolt.position).normalize()
                .scale(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HELLION_BOLT_SPEED)
            bolt.velocity = bolt.velocity.scale(0.45).add(desired.scale(0.55))
            bolt.position = bolt.position.add(bolt.velocity)
            player.serverLevel().sendParticles(ParticleTypes.FLAME, bolt.position.x, bolt.position.y, bolt.position.z, 3, 0.04, 0.04, 0.04, 0.01)
            player.serverLevel().sendParticles(ParticleTypes.SMOKE, bolt.position.x, bolt.position.y, bolt.position.z, 1, 0.02, 0.02, 0.02, 0.0)
            if (target.boundingBox.inflate(0.55).contains(bolt.position)) {
                hitHellionTarget(player, target, bolt.castId)
                iterator.remove()
            }
        }
        if (bolts.isEmpty()) hellionBolts.remove(player.uuid)
    }

    private fun hitHellionTarget(owner: ServerPlayer, target: LivingEntity, castId: UUID) {
        val context = SolarScorchContext(owner.uuid, SolarDamageKind.GENERIC, castId)
        val source = owner.damageSources().indirectMagic(owner, owner)
        val wasAlive = target.isAlive
        SolarIgnitionRuntime.withAttributedSolarDamage(target, context, ignition = false) {
            target.hurt(source, SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HELLION_DAMAGE)
        }
        if (wasAlive && !target.isAlive) {
            SolarWarlockFragmentRuntime.onAttributedFinalBlow(owner, target, context, wasScorched = false)
        } else if (target.isAlive) {
            DestinyStatusRules.applyScorch(
                target,
                SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HELLION_SCORCH_STACKS,
                DestinyStatusRules.MEDIUM_DURATION,
                owner,
                SolarDamageKind.GENERIC,
                castId
            )
        }
        owner.serverLevel().sendParticles(ParticleTypes.FLAME, target.x, target.eyeY, target.z, 16, 0.25, 0.30, 0.25, 0.04)
    }

    private fun hellionAnchor(player: ServerPlayer): Vec3 {
        val look = player.lookAngle
        val horizontal = Vec3(look.x, 0.0, look.z).let { if (it.lengthSqr() > 1.0e-6) it.normalize() else Vec3(0.0, 0.0, 1.0) }
        val right = Vec3(-horizontal.z, 0.0, horizontal.x)
        return player.position().add(right.scale(0.65)).add(0.0, 1.75, 0.0)
    }

    private fun canOwnerAct(player: ServerPlayer): Boolean =
        player.isAlive && !player.isSpectator && !player.isDeadOrDying
}
