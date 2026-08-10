package atopos.destiny2.common.ability

import atopos.destiny2.common.entity.ArcAbilityDamageEntity
import atopos.destiny2.common.entity.ArcPulseGrenadeEntity
import atopos.destiny2.common.entity.ArcFlashbangGrenadeEntity
import atopos.destiny2.common.entity.ArcLightningGrenadeEntity
import atopos.destiny2.common.entity.ArcStormGrenadeEntity
import atopos.destiny2.common.aspect.ArcTitanAspectRuntime
import atopos.destiny2.common.action.DestinyActionRegistry
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Arc Titan V1 active loadout.
 *
 * Gameplay is server-authoritative. Thunderclap starts on the normal melee cast
 * request and is released by a separate C2S message; the client never supplies
 * the charge duration.
 */
object ArcTitanAbilities {
    private const val MOD_ID = "destiny2-mod"
    private const val THUNDERCLAP_MAX_CHARGE_TICKS = 40
    private const val THUNDERCLAP_MIN_RELEASE_TICKS = 4
    private const val THUNDERCLAP_RELEASE_ANIMATION_TICKS = 36
    private const val THUNDERCRASH_MAX_FLIGHT_TICKS = 42
    private const val THUNDERCRASH_COLLISION_GRACE_TICKS = 5
    private const val THUNDERCRASH_SPEED = 1.55

    private data class ThunderclapCharge(
        val playerId: UUID,
        val anchor: Vec3,
        var chargeTicks: Int = 0,
        var releaseRequested: Boolean = false
    )

    private data class ThunderclapRecovery(
        val playerId: UUID,
        val anchor: Vec3,
        var remainingTicks: Int = THUNDERCLAP_RELEASE_ANIMATION_TICKS
    )

    private data class ThundercrashFlight(
        val playerId: UUID,
        var ageTicks: Int = 0
    )

    private val thunderclapCharges = ConcurrentHashMap<UUID, ThunderclapCharge>()
    private val thunderclapRecoveries = ConcurrentHashMap<UUID, ThunderclapRecovery>()
    private val thundercrashFlights = ConcurrentHashMap<UUID, ThundercrashFlight>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            thunderclapCharges.values.toList().forEach { charge ->
                val player = server.playerList.getPlayer(charge.playerId)
                if (player == null || !player.isAlive) {
                    thunderclapCharges.remove(charge.playerId)
                    return@forEach
                }

                charge.chargeTicks++
                holdThunderclap(player, charge)
                if ((charge.releaseRequested && charge.chargeTicks >= THUNDERCLAP_MIN_RELEASE_TICKS) ||
                    charge.chargeTicks >= THUNDERCLAP_MAX_CHARGE_TICKS
                ) {
                    thunderclapCharges.remove(charge.playerId)
                    releaseThunderclap(player, charge)
                }
            }

            thunderclapRecoveries.values.toList().forEach { recovery ->
                val player = server.playerList.getPlayer(recovery.playerId)
                if (player == null || !player.isAlive) {
                    thunderclapRecoveries.remove(recovery.playerId)
                    return@forEach
                }
                lockThunderclapPosition(player, recovery.anchor)
                recovery.remainingTicks--
                if (recovery.remainingTicks <= 0) {
                    thunderclapRecoveries.remove(recovery.playerId)
                }
            }

            thundercrashFlights.values.toList().forEach { flight ->
                val player = server.playerList.getPlayer(flight.playerId)
                if (player == null || !player.isAlive) {
                    player?.setNoGravity(false)
                    thundercrashFlights.remove(flight.playerId)
                    return@forEach
                }

                flight.ageTicks++
                tickThundercrash(player, flight)
            }
        }
    }

    val PULSE_GRENADE = object : AbstractExecutableAbility(
        id = id("arc_titan_pulse_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "脉冲手雷",
        baseCooldownTicks = 105 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            val grenade = ArcPulseGrenadeEntity(player.serverLevel(), player)
            grenade.shootFromRotation(player, player.xRot, player.yRot, 0.0f, 1.35f, 0.8f)
            player.serverLevel().addFreshEntity(grenade)
            player.serverLevel().playSound(
                null,
                player.blockPosition(),
                SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS,
                0.65f,
                1.35f
            )
            return true
        }
    }

    val FLASHBANG_GRENADE = object : AbstractExecutableAbility(
        id = id("arc_titan_flashbang_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "闪光手雷",
        baseCooldownTicks = 91 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val grenade = ArcFlashbangGrenadeEntity(context.player.serverLevel(), context.player)
            grenade.shootFromRotation(context.player, context.player.xRot, context.player.yRot, 0.0f, 1.35f, 0.8f)
            context.player.serverLevel().addFreshEntity(grenade)
            return true
        }
    }

    val LIGHTNING_GRENADE = object : AbstractExecutableAbility(
        id = id("arc_titan_lightning_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "闪电手雷",
        baseCooldownTicks = 121 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val grenade = ArcLightningGrenadeEntity(context.player.serverLevel(), context.player)
            grenade.shootFromRotation(context.player, context.player.xRot, context.player.yRot, 0.0f, 1.35f, 0.75f)
            context.player.serverLevel().addFreshEntity(grenade)
            ArcTitanAspectRuntime.noteLightningGrenadeCast(context.player)
            return true
        }
    }

    val STORM_GRENADE = object : AbstractExecutableAbility(
        id = id("arc_titan_storm_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "风暴手雷",
        baseCooldownTicks = 121 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val grenade = ArcStormGrenadeEntity(context.player.serverLevel(), context.player)
            grenade.shootFromRotation(context.player, context.player.xRot, context.player.yRot, 0.0f, 1.25f, 0.75f)
            context.player.serverLevel().addFreshEntity(grenade)
            return true
        }
    }

    val THUNDERCLAP = object : AbstractExecutableAbility(
        id = id("arc_titan_thunderclap"),
        slot = AbilitySlot.MELEE,
        displayName = "雷霆一击",
        baseCooldownTicks = 90 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            if (!player.onGround() ||
                thunderclapCharges.containsKey(player.uuid) ||
                thunderclapRecoveries.containsKey(player.uuid) ||
                thundercrashFlights.containsKey(player.uuid)
            ) {
                return false
            }
            thunderclapCharges[player.uuid] = ThunderclapCharge(player.uuid, player.position())
            player.addEffect(
                MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    THUNDERCLAP_MAX_CHARGE_TICKS + 8,
                    5,
                    false,
                    false,
                    false
                )
            )
            player.addEffect(
                MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    THUNDERCLAP_MAX_CHARGE_TICKS + 8,
                    0,
                    false,
                    false,
                    false
                )
            )
            player.serverLevel().playSound(
                null,
                player.blockPosition(),
                SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS,
                0.55f,
                1.65f
            )
            return true
        }
    }

    val THRUSTER = object : AbstractExecutableAbility(
        id = id("arc_titan_thruster"),
        slot = AbilitySlot.CLASS_ABILITY,
        displayName = "推进器",
        baseCooldownTicks = 36 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            if (thunderclapCharges.containsKey(player.uuid) ||
                thunderclapRecoveries.containsKey(player.uuid) ||
                thundercrashFlights.containsKey(player.uuid)
            ) {
                return false
            }

            val direction = ArcTitanRules.directionForInput(horizontalFacing(player), context.extraData)
            val current = player.deltaMovement
            player.deltaMovement = Vec3(
                direction.x * 1.42,
                max(current.y, if (player.onGround()) 0.12 else 0.03),
                direction.z * 1.42
            )
            player.hasImpulse = true
            player.hurtMarked = true
            player.fallDistance = 0.0f
            player.serverLevel().sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                player.x,
                player.y + 0.75,
                player.z,
                34,
                0.35,
                0.35,
                0.35,
                0.18
            )
            player.serverLevel().playSound(
                null,
                player.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS,
                0.45f,
                1.7f
            )
            return true
        }
    }

    val BARRICADE = object : AbstractExecutableAbility(
        id = id("arc_titan_barricade"),
        slot = AbilitySlot.CLASS_ABILITY,
        displayName = "屏障",
        baseCooldownTicks = 48 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean =
            ArcTitanAspectRuntime.deployBarricade(context.player)
    }

    val THUNDERCRASH = object : AbstractExecutableAbility(
        id = id("arc_titan_thundercrash"),
        slot = AbilitySlot.SUPER,
        displayName = "雷霆冲击",
        baseCooldownTicks = 455 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            if (thundercrashFlights.containsKey(player.uuid) ||
                thunderclapCharges.containsKey(player.uuid) ||
                thunderclapRecoveries.containsKey(player.uuid)
            ) {
                return false
            }

            thundercrashFlights[player.uuid] = ThundercrashFlight(player.uuid)
            player.setNoGravity(true)
            player.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, THUNDERCRASH_MAX_FLIGHT_TICKS + 10, 2))
            player.deltaMovement = player.lookAngle.normalize().scale(THUNDERCRASH_SPEED).add(0.0, 0.18, 0.0)
            player.hasImpulse = true
            player.hurtMarked = true
            player.serverLevel().playSound(
                null,
                player.blockPosition(),
                SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS,
                0.9f,
                1.35f
            )
            return true
        }
    }

    val ALL = listOf(
        PULSE_GRENADE,
        FLASHBANG_GRENADE,
        LIGHTNING_GRENADE,
        STORM_GRENADE,
        THUNDERCLAP,
        THRUSTER,
        BARRICADE,
        THUNDERCRASH
    )

    fun requestThunderclapRelease(player: ServerPlayer) {
        thunderclapCharges[player.uuid]?.releaseRequested = true
    }

    private fun holdThunderclap(player: ServerPlayer, charge: ThunderclapCharge) {
        lockThunderclapPosition(player, charge.anchor)
        val chargeTicks = charge.chargeTicks
        if (chargeTicks == 1 || chargeTicks % 4 == 0) {
            val spread = 0.18 + chargeTicks.toDouble() / THUNDERCLAP_MAX_CHARGE_TICKS * 0.42
            player.serverLevel().sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                player.x,
                player.eyeY - 0.35,
                player.z,
                5,
                spread,
                0.22,
                spread,
                0.04
            )
        }
    }

    private fun releaseThunderclap(player: ServerPlayer, charge: ThunderclapCharge) {
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN)
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE)
        thunderclapRecoveries[player.uuid] = ThunderclapRecovery(player.uuid, charge.anchor)
        DestinyNetworking.broadcastDestinyAction(player, DestinyActionRegistry.ARC_TITAN_THUNDERCLAP_RELEASE)
        val chargeTicks = charge.chargeTicks
        val ratio = ArcTitanRules.chargeRatio(chargeTicks, THUNDERCLAP_MAX_CHARGE_TICKS)
        val range = 4.0 + ratio * 3.0
        val damage = (9.0 + ratio * 18.0).toFloat()
        val forward = horizontalFacing(player)
        val origin = player.eyePosition.add(0.0, -0.55, 0.0)
        DestinyNetworking.broadcastWorldVfx(
            player = player,
            effectId = id("vfx/thunderclap_ground_lift"),
            origin = player.position(),
            yaw = player.yRot,
            pitch = 0.0f
        )
        val proxy = ArcAbilityDamageEntity(player.serverLevel(), origin, player, AbilitySlot.MELEE)
        player.serverLevel().addFreshEntity(proxy)
        val source = player.damageSources().indirectMagic(proxy, player)
        player.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(origin, range * 2.0, 4.5, range * 2.0)
        ) { target -> isEnemy(player, target) && ArcTitanRules.insideThunderclapCone(origin, forward, target.position(), range) }
            .forEach { target ->
                if (target.hurt(source, damage)) {
                    target.push(forward.x * (0.55 + ratio * 0.45), 0.18 + ratio * 0.16, forward.z * (0.55 + ratio * 0.45))
                    target.hurtMarked = true
                }
            }

        player.serverLevel().sendParticles(
            ParticleTypes.ELECTRIC_SPARK,
            origin.x + forward.x * range * 0.55,
            origin.y,
            origin.z + forward.z * range * 0.55,
            // The procedural cone renderer owns the dense electrical body. Keep only
            // a sparse set of vanilla sparks so the release does not read as a cloud
            // of large four-point magic stars.
            10,
            range * 0.42,
            1.1,
            range * 0.42,
            0.22
        )
        player.serverLevel().sendParticles(
            ParticleTypes.FLASH,
            origin.x + forward.x * 1.2,
            origin.y,
            origin.z + forward.z * 1.2,
            1,
            0.0,
            0.0,
            0.0,
            0.0
        )
        player.serverLevel().playSound(
            null,
            player.blockPosition(),
            SoundEvents.LIGHTNING_BOLT_IMPACT,
            SoundSource.PLAYERS,
            0.95f,
            (1.35f - ratio.toFloat() * 0.35f)
        )
    }

    private fun lockThunderclapPosition(player: ServerPlayer, anchor: Vec3) {
        if (player.position().distanceToSqr(anchor) > 0.0001) {
            player.teleportTo(anchor.x, anchor.y, anchor.z)
        }
        player.deltaMovement = Vec3.ZERO
        player.hasImpulse = true
        player.hurtMarked = true
        player.fallDistance = 0.0f
    }

    private fun tickThundercrash(player: ServerPlayer, flight: ThundercrashFlight) {
        val level = player.serverLevel()
        val direction = player.lookAngle.normalize()
        player.deltaMovement = direction.scale(THUNDERCRASH_SPEED)
        player.hasImpulse = true
        player.hurtMarked = true
        player.fallDistance = 0.0f
        level.sendParticles(
            ParticleTypes.ELECTRIC_SPARK,
            player.x,
            player.y + 0.9,
            player.z,
            12,
            0.42,
            0.42,
            0.42,
            0.16
        )

        val hitTarget = level.getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(1.15)
        ) { target -> isEnemy(player, target) }.isNotEmpty()
        val collided = flight.ageTicks > THUNDERCRASH_COLLISION_GRACE_TICKS &&
            (player.horizontalCollision || player.verticalCollision || player.onGround() || hitTarget)
        if (collided || flight.ageTicks >= THUNDERCRASH_MAX_FLIGHT_TICKS) {
            detonateThundercrash(player)
        }
    }

    private fun detonateThundercrash(player: ServerPlayer) {
        if (thundercrashFlights.remove(player.uuid) == null) return
        player.setNoGravity(false)
        player.deltaMovement = player.deltaMovement.scale(0.18).add(0.0, 0.22, 0.0)
        player.hasImpulse = true
        player.hurtMarked = true

        val level = player.serverLevel()
        val center = player.position().add(0.0, 0.7, 0.0)
        val proxy = ArcAbilityDamageEntity(level, center, player, AbilitySlot.SUPER)
        level.addFreshEntity(proxy)
        val source = player.damageSources().indirectMagic(proxy, player)
        val radius = 6.5
        level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0)
        ) { target -> isEnemy(player, target) && target.position().distanceToSqr(center) <= radius * radius }
            .forEach { target ->
                val distance = target.position().distanceTo(center)
                val damage = ArcTitanRules.thundercrashDamage(distance, radius)
                if (target.hurt(source, damage)) {
                    val push = target.position().subtract(center).let {
                        if (it.lengthSqr() > 1.0e-6) it.normalize() else Vec3(0.0, 1.0, 0.0)
                    }.scale(1.25)
                    target.push(push.x, 0.52, push.z)
                    target.hurtMarked = true
                }
            }

        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 2, 0.0, 0.0, 0.0, 0.0)
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 180, 2.2, 1.2, 2.2, 0.32)
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 65, 1.6, 0.8, 1.6, 0.22)
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.35f, 0.75f)
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f, 0.85f)
    }

    private fun horizontalFacing(player: ServerPlayer): Vec3 {
        val look = player.lookAngle
        val horizontal = Vec3(look.x, 0.0, look.z)
        return if (horizontal.lengthSqr() > 1.0e-6) horizontal.normalize() else Vec3(0.0, 0.0, 1.0)
    }

    private fun isEnemy(owner: LivingEntity, target: LivingEntity): Boolean {
        if (!target.isAlive || target === owner) return false
        return target !is Player || owner !is Player || !owner.isAlliedTo(target)
    }

    private fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
}

object ArcTitanRules {
    fun chargeRatio(chargeTicks: Int, maxChargeTicks: Int): Double {
        if (maxChargeTicks <= 0) return 1.0
        return chargeTicks.coerceIn(0, maxChargeTicks).toDouble() / maxChargeTicks.toDouble()
    }

    fun directionForInput(forward: Vec3, input: Int): Vec3 {
        val normalizedForward = Vec3(forward.x, 0.0, forward.z).let {
            if (it.lengthSqr() > 1.0e-6) it.normalize() else Vec3(0.0, 0.0, 1.0)
        }
        val right = Vec3(-normalizedForward.z, 0.0, normalizedForward.x)
        return when (input) {
            1 -> normalizedForward.scale(-1.0)
            2 -> right.scale(-1.0)
            3 -> right
            else -> normalizedForward
        }
    }

    fun insideThunderclapCone(origin: Vec3, forward: Vec3, target: Vec3, range: Double): Boolean {
        val delta = target.subtract(origin)
        val horizontal = Vec3(delta.x, 0.0, delta.z)
        if (horizontal.lengthSqr() > range * range || kotlin.math.abs(delta.y) > 2.25) return false
        if (horizontal.lengthSqr() < 0.04) return true
        return horizontal.normalize().dot(forward) >= 0.62
    }

    fun thundercrashDamage(distance: Double, radius: Double): Float {
        val falloff = (1.0 - distance.coerceIn(0.0, radius) / radius).coerceIn(0.0, 1.0)
        return (18.0 + 34.0 * falloff).toFloat()
    }
}
