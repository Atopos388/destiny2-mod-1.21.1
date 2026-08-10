package atopos.destiny2.common.aspect

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.effect.SolarScorchContext
import atopos.destiny2.common.ability.DestinyAbilityRegistry
import atopos.destiny2.common.ability.SolarWarlockAbilities
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.sound.DestinySounds
import atopos.destiny2.common.weapon.DestinyElementalDamageCarrier
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Server-authoritative Aspect and Fragment rules. */
object DestinyAspectRuntime {
    const val HEAT_RISES = "destiny2-mod:aspect_heat_rises"
    const val TOUCH_OF_FLAME = "destiny2-mod:aspect_touch_of_flame"
    const val EMBER_OF_TORCHES = SolarWarlockFragmentRules.EMBER_OF_TORCHES
    const val EMBER_OF_SINGEING = SolarWarlockFragmentRules.EMBER_OF_SINGEING
    const val EMBER_OF_SOLACE = SolarWarlockFragmentRules.EMBER_OF_SOLACE
    const val EMBER_OF_ASHES = SolarWarlockFragmentRules.EMBER_OF_ASHES
    private const val HEAT_RISES_DURATION_TICKS = 15 * 20
    private const val HEAT_RISES_CURE_HEALTH = 6.0f
    private const val HEAT_RISES_AIRBORNE_KILL_EXTENSION_TICKS = 5 * 20
    private const val HEAT_RISES_MAX_DURATION_TICKS = 30 * 20
    private const val HEAT_RISES_MELEE_REFUND_TICKS = 20
    private const val HEAT_RISES_CHARGE_RADIUS = 8.0
    private const val HEAT_RISES_ASCENT_DISTANCE = 6.0
    private const val HEAT_RISES_ASCENT_VELOCITY = 0.24
    private const val HEAT_RISES_HOVER_VELOCITY = 0.08
    private const val HEAT_RISES_HOLD_GRACE_TICKS = 3L
    private val heatRisesExtraJumpUsed = mutableSetOf<UUID>()
    private val heatRisesActiveUntil = mutableMapOf<UUID, Long>()
    private val heatRisesFlights = mutableMapOf<UUID, HeatRisesFlight>()
    private val heatRisesHoldStates = mutableMapOf<UUID, HeatRisesHoldState>()
    private val icarusDashStates = mutableMapOf<UUID, IcarusDashChargeState>()
    private val icarusMultikillStates = mutableMapOf<UUID, IcarusDashMultikillState>()

    private data class HeatRisesFlight(
        val targetY: Double,
        var holdUntil: Long
    )

    fun tickPlayer(player: ServerPlayer) {
        SolarWarlockFragmentRuntime.tickPlayer(player)
        SolarWarlockAspectRuntime.tickPlayer(player)
        ArcBoltChargeRuntime.tickPlayer(player)
        ArcTitanAspectRuntime.tickPlayer(player)
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
            nearby.isAlive && !nearby.isSpectator &&
                (nearby === player || nearby.isAlliedTo(player))
        }.forEach { nearby ->
            DestinyStatusRules.applyCure(nearby, HEAT_RISES_CURE_HEALTH, player)
        }
        SolarWarlockAbilities.applyHealingGrenadeHeatRisesTouchOfFlameBonus(player)

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

    /** Server-authoritative hold handshake; one forged completion packet cannot spend the grenade. */
    fun handleHeatRisesGrenadeHold(player: ServerPlayer, action: Int) {
        if (action == DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.RELEASE) {
            heatRisesHoldStates.remove(player.uuid)
            return
        }
        val now = player.serverLevel().gameTime
        val canConsume = canBeginHeatRisesConsume(player, now)
        val previous = heatRisesHoldStates[player.uuid] ?: HeatRisesHoldState()
        val update = when (action) {
            DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.START ->
                SolarWarlockAspectRules.updateHeatRisesHold(true, true, canConsume, now, HeatRisesHoldState())
            DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.COMPLETE ->
                SolarWarlockAspectRules.updateHeatRisesHold(hasAspect(player, HEAT_RISES), true, canConsume, now, previous)
            else -> return
        }
        if (update.state.startedAtTick == null) heatRisesHoldStates.remove(player.uuid)
        else heatRisesHoldStates[player.uuid] = update.state
        if (update.shouldConsumeGrenade) tryConsumeGrenadeForHeatRises(player)
    }

    fun onFinalBlow(
        attacker: ServerPlayer,
        damageSource: DamageSource? = null,
        solarContext: SolarScorchContext? = null
    ) {
        val now = attacker.serverLevel().gameTime
        val currentEnd = heatRisesActiveUntil[attacker.uuid] ?: Long.MIN_VALUE
        val extendedEnd = SolarWarlockAspectRules.heatRisesExpiryAfterFinalBlow(
            hasAspect(attacker, HEAT_RISES),
            isHeatRisesActive(attacker, now),
            isAirborne = !attacker.onGround(),
            isServerConfirmedFinalBlow = true,
            currentTick = now,
            previousExpiresAtTick = currentEnd
        )
        if (extendedEnd != currentEnd) {
            heatRisesActiveUntil[attacker.uuid] = extendedEnd
            val refund = SolarWarlockAspectRules.heatRisesMeleeEnergyRefundTicks(
                hasAspect(attacker, HEAT_RISES),
                isHeatRisesActive(attacker, now),
                isAirborne = !attacker.onGround(),
                isServerConfirmedFinalBlow = true
            )
            PlayerDestinyDataApi.get(attacker).cooldowns.reduce(AbilitySlot.MELEE, now, refund)
            DestinyNetworking.syncCooldowns(attacker)
            DestinyStatusRules.syncTemporaryBuff(
                attacker,
                "destiny2-mod:aspect/heat_rises",
                "炽热升腾",
                (extendedEnd - now).toInt().coerceAtLeast(1)
            )
        }

        val icarusUpdate = SolarWarlockAspectRules.recordIcarusAirborneFinalBlow(
            hasAspect(attacker, SolarWarlockAspectRules.ICARUS_DASH),
            isAirborne = !attacker.onGround(),
            isServerConfirmedFinalBlow = true,
            source = classifyIcarusFinalBlow(attacker, damageSource, solarContext),
            currentTick = now,
            previous = icarusMultikillStates[attacker.uuid] ?: IcarusDashMultikillState()
        )
        if (icarusUpdate.state.qualifyingFinalBlows == 0) icarusMultikillStates.remove(attacker.uuid)
        else icarusMultikillStates[attacker.uuid] = icarusUpdate.state
        if (icarusUpdate.shouldApplyCure) {
            DestinyStatusRules.applyCure(
                attacker,
                SolarWarlockAspectRules.MINECRAFT_CALIBRATION_ICARUS_CURE_HEALTH,
                attacker
            )
        }
    }

    fun tryIcarusDash(player: ServerPlayer, inputDirection: Int): Boolean {
        if (inputDirection !in 0..3) return false
        val now = player.serverLevel().gameTime
        val attempt = SolarWarlockAspectRules.attemptIcarusDash(
            hasAspect(player, SolarWarlockAspectRules.ICARUS_DASH),
            isAirborne = !player.onGround(),
            canControlMovement = canUseIcarusDash(player),
            isHeatRisesActive = isHeatRisesActive(player, now),
            currentTick = now,
            previous = icarusDashStates[player.uuid] ?: IcarusDashChargeState()
        )
        if (!attempt.accepted) return false
        icarusDashStates[player.uuid] = attempt.state

        val forward = horizontalFacing(player)
        val direction = when (inputDirection) {
            1 -> forward.scale(-1.0)
            2 -> Vec3(forward.z, 0.0, -forward.x)
            3 -> Vec3(-forward.z, 0.0, forward.x)
            else -> forward
        }.normalize()
        player.deltaMovement = Vec3(
            direction.x * SolarWarlockAspectRules.MINECRAFT_CALIBRATION_ICARUS_DASH_HORIZONTAL_SPEED,
            SolarWarlockAspectRules.MINECRAFT_CALIBRATION_ICARUS_DASH_VERTICAL_SPEED,
            direction.z * SolarWarlockAspectRules.MINECRAFT_CALIBRATION_ICARUS_DASH_HORIZONTAL_SPEED
        )
        player.fallDistance = 0.0f
        player.hasImpulse = true
        player.hurtMarked = true
        player.serverLevel().sendParticles(ParticleTypes.CLOUD, player.x, player.y + 0.8, player.z, 14, 0.25, 0.35, 0.25, 0.04)
        player.serverLevel().sendParticles(ParticleTypes.FLAME, player.x, player.y + 0.8, player.z, 9, 0.20, 0.25, 0.20, 0.02)
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 1.25f)
        return true
    }

    fun clearPlayer(playerId: UUID) {
        heatRisesActiveUntil.remove(playerId)
        heatRisesExtraJumpUsed.remove(playerId)
        heatRisesFlights.remove(playerId)
        heatRisesHoldStates.remove(playerId)
        icarusDashStates.remove(playerId)
        icarusMultikillStates.remove(playerId)
        SolarWarlockAspectRuntime.clear(playerId)
    }

    private fun isHeatRisesActive(player: ServerPlayer, now: Long): Boolean {
        return (heatRisesActiveUntil[player.uuid] ?: Long.MIN_VALUE) > now && hasAspect(player, HEAT_RISES)
    }

    private fun canBeginHeatRisesConsume(player: ServerPlayer, now: Long): Boolean {
        if (!hasAspect(player, HEAT_RISES) || !canUseHeatRises(player) || player.hasEffect(DestinyEffects.SUPPRESSION)) return false
        val data = PlayerDestinyDataApi.get(player)
        return data.cooldowns.isReady(AbilitySlot.GRENADE, now) &&
            DestinyAbilityRegistry.abilityFor(data, AbilitySlot.GRENADE) != null
    }

    private fun canUseIcarusDash(player: ServerPlayer): Boolean =
        !player.isSpectator && !player.hasEffect(DestinyEffects.SUPPRESSION) &&
            !player.abilities.flying && !player.isInWater && !player.isInLava &&
            !player.isFallFlying && !player.isPassenger

    private fun horizontalFacing(player: ServerPlayer): Vec3 {
        val look = player.lookAngle
        val horizontal = Vec3(look.x, 0.0, look.z)
        if (horizontal.lengthSqr() > 1.0e-6) return horizontal.normalize()
        val yaw = Math.toRadians(player.yRot.toDouble())
        return Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize()
    }

    private fun classifyIcarusFinalBlow(
        attacker: ServerPlayer,
        source: DamageSource?,
        context: SolarScorchContext?
    ): IcarusDashFinalBlowSource {
        when (context?.sourceKind) {
            SolarDamageKind.SUPER -> return IcarusDashFinalBlowSource.SUPER
            SolarDamageKind.WEAPON -> return IcarusDashFinalBlowSource.WEAPON
            else -> Unit
        }
        val direct = source?.directEntity
        val ability = direct as? DestinyAbilityDamageCarrier
        if (ability?.destinyAbilitySlot == AbilitySlot.SUPER) return IcarusDashFinalBlowSource.SUPER
        if (ability != null) return IcarusDashFinalBlowSource.OTHER
        if (direct is DestinyElementalDamageCarrier || source?.`is`(DamageTypeTags.IS_PROJECTILE) == true) {
            return IcarusDashFinalBlowSource.WEAPON
        }
        if (direct === attacker && attacker.mainHandItem.item is DestinyRangedWeapon) {
            return IcarusDashFinalBlowSource.WEAPON
        }
        return IcarusDashFinalBlowSource.OTHER
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
        VoidHunterAspectRuntime.onMeleeHit(attacker, target, source)
    }

    fun onPoweredMeleeHit(attacker: ServerPlayer, target: LivingEntity, castId: UUID = UUID.randomUUID()) {
        SolarWarlockFragmentRuntime.onPoweredMeleeHit(attacker, target, castId)
    }

    fun onScorchApplied(source: ServerPlayer?, target: LivingEntity) =
        SolarWarlockFragmentRuntime.onScorchApplied(source, target)

    fun modifyScorchStacks(source: ServerPlayer?, stacks: Int, sourceKind: atopos.destiny2.common.effect.SolarDamageKind) =
        SolarWarlockFragmentRuntime.modifyScorchStacks(source, stacks, sourceKind)

    fun modifySolarBuffDuration(target: LivingEntity, durationTicks: Int): Int =
        SolarWarlockFragmentRuntime.modifySolarBuffDuration(target, durationTicks)

    fun hasTouchOfFlame(owner: LivingEntity): Boolean =
        (owner as? ServerPlayer)?.let { hasAspect(it, TOUCH_OF_FLAME) } == true

    fun hasAspect(player: ServerPlayer, id: String): Boolean =
        PlayerDestinyDataApi.get(player).subclassConfig.selectedAspects.contains(id)

    fun hasFragment(player: ServerPlayer, id: String): Boolean =
        PlayerDestinyDataApi.get(player).subclassConfig.selectedFragments.contains(id)

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
