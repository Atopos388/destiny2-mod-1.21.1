package atopos.destiny2.common.aspect

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.ability.DestinyAbilityRegistry
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.sound.DestinySounds
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Server-authoritative Aspect and Fragment rules. */
object DestinyAspectRuntime {
    const val HEAT_RISES = "destiny2-mod:aspect_heat_rises"
    const val TOUCH_OF_FLAME = "destiny2-mod:aspect_touch_of_flame"
    const val EMBER_OF_TORCHES = "destiny2-mod:fragment_ember_of_torches"
    const val EMBER_OF_SINGEING = "destiny2-mod:fragment_ember_of_singeing"
    const val EMBER_OF_SOLACE = "destiny2-mod:fragment_ember_of_solace"
    const val EMBER_OF_ASHES = "destiny2-mod:fragment_ember_of_ashes"

    private const val TORCHES_DURATION = 160
    private const val SINGEING_COOLDOWN_REDUCTION = 20
    private const val HEAT_RISES_DURATION_TICKS = 15 * 20
    private const val HEAT_RISES_RESTORATION_TICKS = 3 * 20
    private const val HEAT_RISES_CHARGE_RADIUS = 8.0
    private const val HEAT_RISES_ASCENT_DISTANCE = 6.0
    private const val HEAT_RISES_ASCENT_VELOCITY = 0.24
    private const val HEAT_RISES_HOVER_VELOCITY = 0.08
    private const val HEAT_RISES_HOLD_GRACE_TICKS = 3L
    private val heatRisesExtraJumpUsed = mutableSetOf<UUID>()
    private val heatRisesActiveUntil = mutableMapOf<UUID, Long>()
    private val heatRisesFlights = mutableMapOf<UUID, HeatRisesFlight>()

    private data class HeatRisesFlight(
        val targetY: Double,
        var holdUntil: Long
    )

    fun tickPlayer(player: ServerPlayer) {
        val now = player.serverLevel().gameTime
        if (!isHeatRisesActive(player, now) || !canUseHeatRises(player)) {
            heatRisesActiveUntil.remove(player.uuid)
            heatRisesExtraJumpUsed.remove(player.uuid)
            stopHeatRisesFlight(player)
            return
        }

        if (player.onGround()) {
            heatRisesExtraJumpUsed.remove(player.uuid)
            heatRisesFlights.remove(player.uuid)
            return
        }

        val flight = heatRisesFlights[player.uuid] ?: return
        if (flight.holdUntil < now) {
            stopHeatRisesFlight(player)
            return
        }

        applyHeatRisesFlightVelocity(player, flight)
    }

    fun handleHeatRisesMovement(player: ServerPlayer, action: Int) {
        val now = player.serverLevel().gameTime
        if (!isHeatRisesActive(player, now) || !canUseHeatRises(player)) {
            stopHeatRisesFlight(player)
            return
        }

        when (action) {
            DestinyNetworking.HeatRisesMovementPayload.DOUBLE_JUMP -> {
                if (player.onGround()) return
                if (!heatRisesExtraJumpUsed.add(player.uuid)) return
                val flight = HeatRisesFlight(
                    targetY = player.y + HEAT_RISES_ASCENT_DISTANCE,
                    holdUntil = now + HEAT_RISES_HOLD_GRACE_TICKS
                )
                heatRisesFlights[player.uuid] = flight
                applyHeatRisesFlightVelocity(player, flight)
            }
            DestinyNetworking.HeatRisesMovementPayload.HOLD -> {
                val flight = heatRisesFlights[player.uuid] ?: return
                flight.holdUntil = now + HEAT_RISES_HOLD_GRACE_TICKS
                applyHeatRisesFlightVelocity(player, flight)
            }
            DestinyNetworking.HeatRisesMovementPayload.RELEASE -> {
                stopHeatRisesFlight(player)
                return
            }
            else -> return
        }
    }

    fun tryConsumeGrenadeForHeatRises(player: ServerPlayer): Boolean {
        if (!hasAspect(player, HEAT_RISES) || !canUseHeatRises(player) || player.hasEffect(DestinyEffects.SUPPRESSION)) {
            return false
        }

        val level = player.serverLevel()
        val now = level.gameTime
        val data = PlayerDestinyDataApi.get(player)
        if (!data.cooldowns.isReady(AbilitySlot.GRENADE, now)) {
            val remaining = (data.cooldowns.nextAvailableTick(AbilitySlot.GRENADE) - now).coerceAtLeast(0L)
            if (remaining > 0) {
                ServerPlayNetworking.send(
                    player,
                    DestinyNetworking.SyncCooldownPayload(
                        AbilitySlot.GRENADE.legacyNetworkId,
                        remaining.toInt(),
                        data.cooldowns.totalDurationTicks(AbilitySlot.GRENADE).coerceAtLeast(remaining.toInt())
                    )
                )
            }
            return false
        }

        val grenade = DestinyAbilityRegistry.abilityFor(data, AbilitySlot.GRENADE) ?: return false
        val cooldownTicks = DestinyStatFormulas.cooldownTicks(grenade.baseCooldownTicks, AbilitySlot.GRENADE, DestinyStatsResolver.resolve(player))
        data.cooldowns.setCooldown(AbilitySlot.GRENADE, now, cooldownTicks)
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.SyncCooldownPayload(AbilitySlot.GRENADE.legacyNetworkId, cooldownTicks, cooldownTicks)
        )

        level.getEntitiesOfClass(ServerPlayer::class.java, player.boundingBox.inflate(HEAT_RISES_CHARGE_RADIUS)) { nearby ->
            nearby.isAlive && !nearby.isSpectator
        }.forEach { nearby ->
            nearby.heal(nearby.maxHealth * 0.5f)
        }
        DestinyStatusRules.applyRestoration(player, HEAT_RISES_RESTORATION_TICKS)

        heatRisesActiveUntil[player.uuid] = now + HEAT_RISES_DURATION_TICKS
        heatRisesExtraJumpUsed.remove(player.uuid)
        heatRisesFlights.remove(player.uuid)
        DestinyStatusRules.syncTemporaryBuff(
            player,
            "destiny2-mod:aspect/heat_rises",
            "炙热升腾",
            HEAT_RISES_DURATION_TICKS
        )
        level.sendParticles(ParticleTypes.FLAME, player.x, player.eyeY, player.z, 28, 1.1, 0.65, 1.1, 0.03)
        level.playSound(null, player.blockPosition(), DestinySounds.HEAT_RISES_ACTIVATE, SoundSource.PLAYERS, 0.75f, 1.0f)
        return true
    }

    private fun isHeatRisesActive(player: ServerPlayer, now: Long): Boolean {
        return (heatRisesActiveUntil[player.uuid] ?: Long.MIN_VALUE) > now && hasAspect(player, HEAT_RISES)
    }

    private fun applyHeatRisesFlightVelocity(player: ServerPlayer, flight: HeatRisesFlight) {
        val motion = player.deltaMovement
        val verticalVelocity = if (player.y < flight.targetY) {
            HEAT_RISES_ASCENT_VELOCITY
        } else {
            HEAT_RISES_HOVER_VELOCITY
        }
        player.deltaMovement = Vec3(motion.x, verticalVelocity, motion.z)
        player.fallDistance = 0.0f
        player.hasImpulse = true
        player.hurtMarked = true
    }

    private fun stopHeatRisesFlight(player: ServerPlayer) {
        if (heatRisesFlights.remove(player.uuid) == null) return
        val motion = player.deltaMovement
        player.deltaMovement = Vec3(motion.x, motion.y.coerceAtMost(0.0), motion.z)
        player.hasImpulse = true
        player.hurtMarked = true
    }

    fun onMeleeHit(attacker: ServerPlayer, target: LivingEntity, source: DamageSource) {
        if (source.directEntity !== attacker) return
        applyEmberOfTorches(attacker)
    }

    fun onPoweredMeleeHit(attacker: ServerPlayer) {
        applyEmberOfTorches(attacker)
    }

    fun onScorchApplied(source: ServerPlayer?) {
        source ?: return
        if (!hasFragment(source, EMBER_OF_SINGEING)) return

        val data = PlayerDestinyDataApi.get(source)
        val reduced = data.cooldowns.reduce(AbilitySlot.CLASS_ABILITY, source.serverLevel().gameTime, SINGEING_COOLDOWN_REDUCTION)
        if (reduced > 0) {
            val remaining = (data.cooldowns.nextAvailableTick(AbilitySlot.CLASS_ABILITY) - source.serverLevel().gameTime)
                .toInt()
                .coerceAtLeast(0)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                source,
                DestinyNetworking.SyncCooldownPayload(
                    AbilitySlot.CLASS_ABILITY.legacyNetworkId,
                    remaining,
                    data.cooldowns.totalDurationTicks(AbilitySlot.CLASS_ABILITY).coerceAtLeast(remaining)
                )
            )
            DestinyStatusRules.syncTemporaryBuff(
                source,
                "destiny2-mod:fragment/ember_of_singeing",
                "焦燃余烬",
                20,
                reduced
            )
        }
    }

    fun modifyScorchStacks(source: ServerPlayer?, stacks: Int): Int {
        return if (source != null && hasFragment(source, EMBER_OF_ASHES)) {
            (stacks * 1.5f).toInt().coerceAtLeast(stacks + 1)
        } else {
            stacks
        }
    }

    fun modifySolarBuffDuration(target: LivingEntity, durationTicks: Int): Int {
        val player = target as? ServerPlayer ?: return durationTicks
        return if (hasFragment(player, EMBER_OF_SOLACE)) {
            (durationTicks * 1.5f).toInt()
        } else {
            durationTicks
        }
    }

    fun hasTouchOfFlame(owner: LivingEntity): Boolean =
        (owner as? ServerPlayer)?.let { hasAspect(it, TOUCH_OF_FLAME) } == true

    fun hasAspect(player: ServerPlayer, id: String): Boolean =
        PlayerDestinyDataApi.get(player).subclassConfig.selectedAspects.contains(id)

    fun hasFragment(player: ServerPlayer, id: String): Boolean =
        PlayerDestinyDataApi.get(player).subclassConfig.selectedFragments.contains(id)

    private fun applyEmberOfTorches(attacker: ServerPlayer) {
        if (hasFragment(attacker, EMBER_OF_TORCHES)) {
            DestinyStatusRules.applyRadiant(attacker, TORCHES_DURATION)
        }
    }

    private fun canUseHeatRises(player: ServerPlayer): Boolean {
        return hasAspect(player, HEAT_RISES) &&
            !player.isSpectator &&
            !player.abilities.flying &&
            !player.isInWater &&
            !player.isInLava &&
            !player.isFallFlying &&
            !player.isPassenger
    }
}
