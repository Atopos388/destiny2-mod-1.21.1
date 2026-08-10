package atopos.destiny2.common.aspect

import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.common.weapon.DestinyAmmoType

/**
 * Pure, server-oriented rules for the sixteen current Arc Fragments.
 *
 * Names, effects and static stat modifiers follow Bungie's current Simplified
 * Chinese Destiny manifest. Values called out as calibration values below are
 * intentionally Minecraft-specific because Bungie does not publish the live
 * sandbox constants needed to reproduce them exactly.
 *
 * This object does not own player state or touch a Minecraft world. Runtime
 * code is expected to keep the returned timers and counters server-side.
 */
object ArcTitanFragmentRules {
    private const val MOD_ID = "destiny2-mod"

    const val SPARK_OF_BEACONS = "$MOD_ID:fragment_spark_of_beacons"
    const val SPARK_OF_RESISTANCE = "$MOD_ID:fragment_spark_of_resistance"
    const val SPARK_OF_MOMENTUM = "$MOD_ID:fragment_spark_of_momentum"
    const val SPARK_OF_SHOCK = "$MOD_ID:fragment_spark_of_shock"
    const val SPARK_OF_IONS = "$MOD_ID:fragment_spark_of_ions"
    const val SPARK_OF_DISCHARGE = "$MOD_ID:fragment_spark_of_discharge"
    const val SPARK_OF_FREQUENCY = "$MOD_ID:fragment_spark_of_frequency"
    const val SPARK_OF_FOCUS = "$MOD_ID:fragment_spark_of_focus"
    const val SPARK_OF_RECHARGE = "$MOD_ID:fragment_spark_of_recharge"
    const val SPARK_OF_MAGNITUDE = "$MOD_ID:fragment_spark_of_magnitude"
    const val SPARK_OF_AMPLITUDE = "$MOD_ID:fragment_spark_of_amplitude"
    const val SPARK_OF_FEEDBACK = "$MOD_ID:fragment_spark_of_feedback"
    const val SPARK_OF_VOLTS = "$MOD_ID:fragment_spark_of_volts"
    const val SPARK_OF_BRILLIANCE = "$MOD_ID:fragment_spark_of_brilliance"
    const val SPARK_OF_HASTE = "$MOD_ID:fragment_spark_of_haste"
    const val SPARK_OF_INSTINCT = "$MOD_ID:fragment_spark_of_instinct"

    val ALL_FRAGMENT_IDS = listOf(
        SPARK_OF_BEACONS,
        SPARK_OF_RESISTANCE,
        SPARK_OF_MOMENTUM,
        SPARK_OF_SHOCK,
        SPARK_OF_IONS,
        SPARK_OF_DISCHARGE,
        SPARK_OF_FREQUENCY,
        SPARK_OF_FOCUS,
        SPARK_OF_RECHARGE,
        SPARK_OF_MAGNITUDE,
        SPARK_OF_AMPLITUDE,
        SPARK_OF_FEEDBACK,
        SPARK_OF_VOLTS,
        SPARK_OF_BRILLIANCE,
        SPARK_OF_HASTE,
        SPARK_OF_INSTINCT
    )

    // Minecraft calibration values. Static +10/-10 stat modifiers are official.
    const val SURROUNDED_HOSTILE_COUNT = 3
    const val RESISTANCE_INCOMING_DAMAGE_MULTIPLIER = 0.75f
    const val BLIND_EXPLOSION_RADIUS = 5.0
    const val BLIND_DURATION_TICKS = 3 * 20
    const val JOLT_DURATION_TICKS = 10 * 20
    const val IONIC_TRACE_COOLDOWN_TICKS = 10 * 20L
    const val DISCHARGE_TRACE_CHANCE = 0.20f
    const val FREQUENCY_DURATION_TICKS = 6 * 20L
    const val FREQUENCY_RELOAD_MULTIPLIER = 0.70f
    const val AMPLIFIED_FREQUENCY_RELOAD_MULTIPLIER = 0.60f
    const val FREQUENCY_STABILITY_MULTIPLIER = 0.75f
    const val AMPLIFIED_FREQUENCY_STABILITY_MULTIPLIER = 0.65f
    const val FOCUS_SPRINT_TRIGGER_TICKS = 20
    const val FOCUS_DURATION_TICKS = 4 * 20L
    const val PASSIVE_RECHARGE_EXTRA_TICKS = 1
    const val BASE_PULSE_GRENADE_PULSES = 7
    const val MAGNITUDE_PULSE_GRENADE_PULSES = 10
    const val AMPLITUDE_REQUIRED_FINAL_BLOWS = 2
    const val AMPLITUDE_WINDOW_TICKS = 4 * 20L
    const val AMPLITUDE_ORB_COOLDOWN_TICKS = 10 * 20L
    const val FEEDBACK_DURATION_TICKS = 4 * 20L
    const val FEEDBACK_MELEE_DAMAGE_MULTIPLIER = 1.75f
    const val VOLTS_AMPLIFIED_DURATION_TICKS = 15 * 20
    const val HASTE_HEALTH_STAT_BONUS = 30
    const val INSTINCT_COOLDOWN_TICKS = 15 * 20L
    const val INSTINCT_RADIUS = 5.0
    const val INSTINCT_DAMAGE = 6.0f

    /** Official static Armor 3.0 modifiers from the current manifest. */
    fun staticStatBonuses(equippedFragmentIds: Collection<String>): DestinyStats {
        val equipped = equippedFragmentIds.toSet()
        return DestinyStats(
            weapons = if (SPARK_OF_FOCUS in equipped) -10 else 0,
            health = (if (SPARK_OF_FOCUS in equipped) -10 else 0) +
                (if (SPARK_OF_FEEDBACK in equipped) 10 else 0),
            classAbility = (if (SPARK_OF_FOCUS in equipped) -10 else 0) +
                (if (SPARK_OF_VOLTS in equipped) 10 else 0),
            grenade = if (SPARK_OF_SHOCK in equipped) -10 else 0,
            superStat = if (SPARK_OF_BRILLIANCE in equipped) 10 else 0,
            melee = (if (SPARK_OF_RESISTANCE in equipped) 10 else 0) +
                (if (SPARK_OF_DISCHARGE in equipped) -10 else 0)
        )
    }

    fun shouldTriggerBeacons(
        hasFragment: Boolean,
        isAmplified: Boolean,
        isArcWeaponFinalBlow: Boolean,
        ammoType: DestinyAmmoType
    ): Boolean = hasFragment && isAmplified && isArcWeaponFinalBlow &&
        (ammoType == DestinyAmmoType.SPECIAL || ammoType == DestinyAmmoType.HEAVY)

    fun isSurrounded(hostileCount: Int): Boolean = hostileCount >= SURROUNDED_HOSTILE_COUNT

    fun resistanceIncomingDamageMultiplier(hostileCount: Int): Float =
        if (isSurrounded(hostileCount)) RESISTANCE_INCOMING_DAMAGE_MULTIPLIER else 1.0f

    fun shouldTriggerMomentum(
        hasFragment: Boolean,
        isSliding: Boolean,
        isSpecialOrHeavyAmmoPickup: Boolean
    ): Boolean = hasFragment && isSliding && isSpecialOrHeavyAmmoPickup

    fun momentumBoltChargeGain(triggered: Boolean): Int = if (triggered) 1 else 0

    fun shouldApplyShock(
        hasFragment: Boolean,
        isArcGrenadeDamage: Boolean,
        damageAccepted: Boolean
    ): Boolean = hasFragment && isArcGrenadeDamage && damageAccepted

    fun shouldSpawnIonicTraceFromIons(
        hasFragment: Boolean,
        targetWasJolted: Boolean,
        targetHadBoltCharge: Boolean,
        currentTick: Long,
        nextEligibleTick: Long
    ): Boolean = hasFragment && (targetWasJolted || targetHadBoltCharge) && currentTick >= nextEligibleTick

    fun shouldSpawnIonicTraceFromDischarge(
        hasFragment: Boolean,
        isArcWeaponFinalBlow: Boolean,
        randomRoll: Float
    ): Boolean = hasFragment && isArcWeaponFinalBlow &&
        randomRoll >= 0.0f && randomRoll < DISCHARGE_TRACE_CHANCE

    fun shouldTriggerFrequency(
        hasFragment: Boolean,
        isDirectMeleeHit: Boolean,
        damageAccepted: Boolean
    ): Boolean = hasFragment && isDirectMeleeHit && damageAccepted

    fun frequencyReloadMultiplier(isActive: Boolean, isAmplified: Boolean): Float = when {
        !isActive -> 1.0f
        isAmplified -> AMPLIFIED_FREQUENCY_RELOAD_MULTIPLIER
        else -> FREQUENCY_RELOAD_MULTIPLIER
    }

    fun frequencyStabilityMultiplier(isActive: Boolean, isAmplified: Boolean): Float = when {
        !isActive -> 1.0f
        isAmplified -> AMPLIFIED_FREQUENCY_STABILITY_MULTIPLIER
        else -> FREQUENCY_STABILITY_MULTIPLIER
    }

    /** Frequency grants more Bolt Charge from every source while Amplified. */
    fun modifiedBoltChargeGain(baseGain: Int, hasFrequency: Boolean, isAmplified: Boolean): Int {
        val safeBase = baseGain.coerceAtLeast(0)
        return if (hasFrequency && isAmplified) safeBase * 2 else safeBase
    }

    fun canPrimeFocus(continuousSprintTicks: Int): Boolean =
        continuousSprintTicks >= FOCUS_SPRINT_TRIGGER_TICKS

    fun focusWindowExpiresAt(currentTick: Long, continuousSprintTicks: Int): Long? =
        if (canPrimeFocus(continuousSprintTicks)) currentTick + FOCUS_DURATION_TICKS else null

    fun focusExtraClassCooldownTicks(isActive: Boolean): Int =
        if (isActive) PASSIVE_RECHARGE_EXTRA_TICKS else 0

    /**
     * Recharge first latches when the normal health shield breaks, then remains
     * active until that shield is completely full. This matches Bungie's later
     * correction that recharge must not stop merely because shield recovery began.
     */
    fun nextRechargeLatched(previouslyLatched: Boolean, currentShield: Float, fullShield: Float): Boolean {
        if (fullShield <= 0.0f) return false
        return when {
            currentShield <= 0.0f -> true
            currentShield >= fullShield -> false
            else -> previouslyLatched
        }
    }

    fun rechargeExtraCooldownTicks(isLatched: Boolean): Int =
        if (isLatched) PASSIVE_RECHARGE_EXTRA_TICKS else 0

    fun pulseGrenadePulses(hasMagnitude: Boolean): Int =
        if (hasMagnitude) MAGNITUDE_PULSE_GRENADE_PULSES else BASE_PULSE_GRENADE_PULSES

    data class AmplitudeState(
        val finalBlows: Int = 0,
        val windowExpiresAt: Long = Long.MIN_VALUE,
        val nextOrbEligibleTick: Long = Long.MIN_VALUE
    )

    data class AmplitudeResult(
        val state: AmplitudeState,
        val shouldSpawnOrb: Boolean
    )

    /** Called once for each server-confirmed final blow. */
    fun recordAmplitudeFinalBlow(
        hasFragment: Boolean,
        isAmplified: Boolean,
        currentTick: Long,
        previous: AmplitudeState
    ): AmplitudeResult {
        if (!hasFragment || !isAmplified) {
            return AmplitudeResult(previous.copy(finalBlows = 0, windowExpiresAt = Long.MIN_VALUE), false)
        }
        if (currentTick < previous.nextOrbEligibleTick) {
            return AmplitudeResult(previous.copy(finalBlows = 0, windowExpiresAt = Long.MIN_VALUE), false)
        }

        val count = if (currentTick <= previous.windowExpiresAt) previous.finalBlows + 1 else 1
        if (count >= AMPLITUDE_REQUIRED_FINAL_BLOWS) {
            return AmplitudeResult(
                AmplitudeState(nextOrbEligibleTick = currentTick + AMPLITUDE_ORB_COOLDOWN_TICKS),
                true
            )
        }
        return AmplitudeResult(
            previous.copy(finalBlows = count, windowExpiresAt = currentTick + AMPLITUDE_WINDOW_TICKS),
            false
        )
    }

    fun shouldTriggerFeedback(
        hasFragment: Boolean,
        isHostileDirectMeleeDamage: Boolean,
        actualDamage: Float
    ): Boolean = hasFragment && isHostileDirectMeleeDamage && actualDamage > 0.0f

    fun feedbackMeleeDamageMultiplier(isActive: Boolean): Float =
        if (isActive) FEEDBACK_MELEE_DAMAGE_MULTIPLIER else 1.0f

    fun shouldTriggerVolts(hasFragment: Boolean, isFinisherFinalBlow: Boolean): Boolean =
        hasFragment && isFinisherFinalBlow

    fun voltsBoltChargeGain(triggered: Boolean): Int = if (triggered) 1 else 0

    fun shouldTriggerBrilliance(
        hasFragment: Boolean,
        isPrecisionFinalBlow: Boolean,
        targetWasBlinded: Boolean
    ): Boolean = hasFragment && isPrecisionFinalBlow && targetWasBlinded

    fun hasteHealthStatBonus(hasFragment: Boolean, isSprinting: Boolean): Int =
        if (hasFragment && isSprinting) HASTE_HEALTH_STAT_BONUS else 0

    fun shouldTriggerInstinct(
        hasFragment: Boolean,
        isCriticallyWounded: Boolean,
        isHostileDamage: Boolean,
        actualDamage: Float,
        currentTick: Long,
        nextEligibleTick: Long
    ): Boolean = hasFragment && isCriticallyWounded && isHostileDamage && actualDamage > 0.0f &&
        currentTick >= nextEligibleTick
}
