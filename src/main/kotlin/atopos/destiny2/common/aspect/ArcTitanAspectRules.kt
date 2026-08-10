package atopos.destiny2.common.aspect

import kotlin.math.min

/**
 * Pure, server-oriented rules for the four current Striker (Arc Titan) Aspects.
 *
 * IDs and effect semantics follow Bungie's current Simplified Chinese manifest,
 * the Arc 3.0 article, and subsequent official ability update notes. Constants
 * prefixed with [MINECRAFT_CALIBRATION_] are intentionally local tuning values:
 * Bungie either does not publish the live value or the Destiny value does not
 * translate directly to Minecraft's tick, distance, health, or damage scales.
 *
 * Runtime code owns world queries and persistent player state. It should feed
 * server-confirmed facts into these functions and apply the returned decisions.
 */
object ArcTitanAspectRules {
    private const val MOD_ID = "destiny2-mod"

    const val TOUCH_OF_THUNDER = "$MOD_ID:aspect_touch_of_thunder"
    const val JUGGERNAUT = "$MOD_ID:aspect_juggernaut"
    const val KNOCKOUT = "$MOD_ID:aspect_knockout"
    const val STORMS_KEEP = "$MOD_ID:aspect_storms_keep"

    val ALL_ASPECT_IDS = listOf(
        TOUCH_OF_THUNDER,
        JUGGERNAUT,
        KNOCKOUT,
        STORMS_KEEP
    )

    enum class ArcGrenadeKind {
        FLASHBANG,
        PULSE,
        LIGHTNING,
        STORM,
        OTHER
    }

    enum class TouchOfThunderStrategy {
        FLASHBANG_FIRST_BOUNCE_BLIND,
        PULSE_RAMPING_DAMAGE_AND_IONIC_TRACES,
        LIGHTNING_EXTRA_CHARGE_AND_FIRST_DAMAGE_JOLT,
        STORM_ROAMING_TRACKING_THUNDERCLOUD
    }

    const val MINECRAFT_CALIBRATION_TOUCH_FLASHBANG_BLIND_RADIUS = 5.0
    const val MINECRAFT_CALIBRATION_TOUCH_FLASHBANG_BLIND_DURATION_TICKS = 3 * 20
    const val MINECRAFT_CALIBRATION_TOUCH_PULSE_TRACE_EVERY_DAMAGE_EVENTS = 3
    const val MINECRAFT_CALIBRATION_TOUCH_PULSE_DAMAGE_RAMP_PER_EVENT = 0.10f
    const val MINECRAFT_CALIBRATION_TOUCH_PULSE_MAX_DAMAGE_MULTIPLIER = 1.60f
    const val TOUCH_LIGHTNING_ADDITIONAL_GRENADE_CHARGES = 1
    const val MINECRAFT_CALIBRATION_TOUCH_STORM_CLOUD_DURATION_TICKS = 6 * 20
    const val MINECRAFT_CALIBRATION_TOUCH_STORM_TRACKING_RADIUS = 8.0
    const val MINECRAFT_CALIBRATION_TOUCH_STORM_BOLT_INTERVAL_TICKS = 20

    fun touchOfThunderStrategy(
        hasAspect: Boolean,
        grenadeKind: ArcGrenadeKind
    ): TouchOfThunderStrategy? {
        if (!hasAspect) return null
        return when (grenadeKind) {
            ArcGrenadeKind.FLASHBANG -> TouchOfThunderStrategy.FLASHBANG_FIRST_BOUNCE_BLIND
            ArcGrenadeKind.PULSE -> TouchOfThunderStrategy.PULSE_RAMPING_DAMAGE_AND_IONIC_TRACES
            ArcGrenadeKind.LIGHTNING -> TouchOfThunderStrategy.LIGHTNING_EXTRA_CHARGE_AND_FIRST_DAMAGE_JOLT
            ArcGrenadeKind.STORM -> TouchOfThunderStrategy.STORM_ROAMING_TRACKING_THUNDERCLOUD
            ArcGrenadeKind.OTHER -> null
        }
    }

    fun shouldEmitTouchFlashbangBlind(
        hasAspect: Boolean,
        grenadeKind: ArcGrenadeKind,
        bounceIndex: Int
    ): Boolean = hasAspect && grenadeKind == ArcGrenadeKind.FLASHBANG && bounceIndex == 1

    /** The Ionic Trace cadence is local calibration; the periodic behavior is official. */
    fun shouldCreateTouchPulseIonicTrace(
        hasAspect: Boolean,
        grenadeKind: ArcGrenadeKind,
        damageAccepted: Boolean,
        acceptedDamageEventIndex: Int
    ): Boolean = hasAspect && grenadeKind == ArcGrenadeKind.PULSE && damageAccepted &&
        acceptedDamageEventIndex > 0 &&
        (acceptedDamageEventIndex - 1) % MINECRAFT_CALIBRATION_TOUCH_PULSE_TRACE_EVERY_DAMAGE_EVENTS == 0

    fun touchPulseDamageMultiplier(
        hasAspect: Boolean,
        grenadeKind: ArcGrenadeKind,
        acceptedDamageEventIndex: Int
    ): Float {
        if (!hasAspect || grenadeKind != ArcGrenadeKind.PULSE) return 1.0f
        val completedRamps = (acceptedDamageEventIndex - 1).coerceAtLeast(0)
        return min(
            1.0f + completedRamps * MINECRAFT_CALIBRATION_TOUCH_PULSE_DAMAGE_RAMP_PER_EVENT,
            MINECRAFT_CALIBRATION_TOUCH_PULSE_MAX_DAMAGE_MULTIPLIER
        )
    }

    fun touchLightningMaximumCharges(
        baseMaximumCharges: Int,
        hasAspect: Boolean,
        grenadeKind: ArcGrenadeKind
    ): Int {
        val safeBase = baseMaximumCharges.coerceAtLeast(0)
        return if (hasAspect && grenadeKind == ArcGrenadeKind.LIGHTNING) {
            safeBase + TOUCH_LIGHTNING_ADDITIONAL_GRENADE_CHARGES
        } else {
            safeBase
        }
    }

    /** Update 8.0.0.1 moved Jolt application to after the first accepted damage event. */
    fun shouldApplyTouchLightningJolt(
        hasAspect: Boolean,
        grenadeKind: ArcGrenadeKind,
        damageAccepted: Boolean,
        acceptedDamageEventIndex: Int
    ): Boolean = hasAspect && grenadeKind == ArcGrenadeKind.LIGHTNING && damageAccepted &&
        acceptedDamageEventIndex == 1

    fun shouldCreateTouchStormCloud(
        hasAspect: Boolean,
        grenadeKind: ArcGrenadeKind,
        grenadeDetonated: Boolean
    ): Boolean = hasAspect && grenadeKind == ArcGrenadeKind.STORM && grenadeDetonated

    const val MINECRAFT_CALIBRATION_JUGGERNAUT_SPRINT_TRIGGER_TICKS = 20
    const val MINECRAFT_CALIBRATION_JUGGERNAUT_FRONTAL_DOT_THRESHOLD = 0.50
    const val MINECRAFT_CALIBRATION_JUGGERNAUT_BASE_SHIELD_CAPACITY = 20.0f
    const val MINECRAFT_CALIBRATION_JUGGERNAUT_AMPLIFIED_SHIELD_CAPACITY = 30.0f
    const val MINECRAFT_CALIBRATION_JUGGERNAUT_BOLT_CHARGE_PER_ABSORBED_HIT = 1

    fun canActivateJuggernaut(
        hasAspect: Boolean,
        continuousSprintTicks: Int,
        classAbilityEnergy: Float,
        maximumClassAbilityEnergy: Float
    ): Boolean = hasAspect &&
        continuousSprintTicks >= MINECRAFT_CALIBRATION_JUGGERNAUT_SPRINT_TRIGGER_TICKS &&
        maximumClassAbilityEnergy > 0.0f &&
        classAbilityEnergy >= maximumClassAbilityEnergy

    /** Input is the dot product of normalized look-forward and player-to-attacker vectors. */
    fun isJuggernautFrontalHit(forwardDotToAttacker: Double): Boolean =
        forwardDotToAttacker >= MINECRAFT_CALIBRATION_JUGGERNAUT_FRONTAL_DOT_THRESHOLD

    fun juggernautShieldCapacity(isAmplified: Boolean): Float =
        if (isAmplified) {
            MINECRAFT_CALIBRATION_JUGGERNAUT_AMPLIFIED_SHIELD_CAPACITY
        } else {
            MINECRAFT_CALIBRATION_JUGGERNAUT_BASE_SHIELD_CAPACITY
        }

    data class JuggernautShieldState(
        val remainingCapacity: Float = 0.0f,
        val breakCostConsumed: Boolean = false
    )

    data class JuggernautDamageResult(
        val state: JuggernautShieldState,
        val absorbedDamage: Float,
        val passedThroughDamage: Float,
        val shieldBroken: Boolean,
        val shouldConsumeClassAbility: Boolean,
        val boltChargeGained: Int
    )

    fun newJuggernautShield(isAmplified: Boolean): JuggernautShieldState =
        JuggernautShieldState(remainingCapacity = juggernautShieldCapacity(isAmplified))

    /**
     * Absorbs only frontal damage. Crossing zero emits the class-ability cost
     * once; repeated calls on a broken state cannot emit a second cost.
     */
    fun absorbJuggernautDamage(
        previous: JuggernautShieldState,
        incomingDamage: Float,
        forwardDotToAttacker: Double
    ): JuggernautDamageResult {
        val safeDamage = incomingDamage.coerceAtLeast(0.0f)
        if (safeDamage == 0.0f || previous.remainingCapacity <= 0.0f ||
            !isJuggernautFrontalHit(forwardDotToAttacker)
        ) {
            return JuggernautDamageResult(
                previous,
                absorbedDamage = 0.0f,
                passedThroughDamage = safeDamage,
                shieldBroken = false,
                shouldConsumeClassAbility = false,
                boltChargeGained = 0
            )
        }

        val absorbed = min(previous.remainingCapacity, safeDamage)
        val remaining = (previous.remainingCapacity - absorbed).coerceAtLeast(0.0f)
        val brokenNow = remaining == 0.0f
        val consumeNow = brokenNow && !previous.breakCostConsumed
        return JuggernautDamageResult(
            state = previous.copy(
                remainingCapacity = remaining,
                breakCostConsumed = previous.breakCostConsumed || consumeNow
            ),
            absorbedDamage = absorbed,
            passedThroughDamage = safeDamage - absorbed,
            shieldBroken = brokenNow,
            shouldConsumeClassAbility = consumeNow,
            boltChargeGained = MINECRAFT_CALIBRATION_JUGGERNAUT_BOLT_CHARGE_PER_ABSORBED_HIT
        )
    }

    const val MINECRAFT_CALIBRATION_KNOCKOUT_DURATION_TICKS = 6 * 20L
    const val MINECRAFT_CALIBRATION_KNOCKOUT_UNPOWERED_MELEE_DAMAGE_MULTIPLIER = 2.0f
    const val MINECRAFT_CALIBRATION_KNOCKOUT_POWERED_MELEE_DAMAGE_MULTIPLIER = 1.5f
    const val MINECRAFT_CALIBRATION_KNOCKOUT_MELEE_REACH_BLOCKS = 5.5
    const val MINECRAFT_CALIBRATION_KNOCKOUT_PLAYER_HEAL = 3.0f
    const val MINECRAFT_CALIBRATION_KNOCKOUT_MINOR_HEAL = 4.0f
    const val MINECRAFT_CALIBRATION_KNOCKOUT_MAJOR_HEAL = 6.0f
    const val MINECRAFT_CALIBRATION_KNOCKOUT_BOSS_HEAL = 8.0f
    const val MINECRAFT_CALIBRATION_KNOCKOUT_AMPLIFIED_DURATION_TICKS = 15 * 20

    data class KnockoutTriggerResult(
        /** Whether Knockout was already active and may modify this damage event. */
        val enhanceCurrentHit: Boolean,
        /** A trigger from this damage event applies only to later melee hits. */
        val triggeredForSubsequentHits: Boolean,
        val refreshedExpiresAt: Long?
    )

    fun resolveKnockoutDamageTrigger(
        hasAspect: Boolean,
        wasActiveBeforeDamage: Boolean,
        damageAccepted: Boolean,
        currentDamageIsMelee: Boolean,
        targetBecameCriticallyWounded: Boolean,
        targetShieldBroken: Boolean,
        currentTick: Long
    ): KnockoutTriggerResult {
        val triggeredNow = hasAspect && damageAccepted &&
            (targetBecameCriticallyWounded || targetShieldBroken)
        return KnockoutTriggerResult(
            enhanceCurrentHit = hasAspect && wasActiveBeforeDamage && damageAccepted && currentDamageIsMelee,
            triggeredForSubsequentHits = triggeredNow,
            refreshedExpiresAt = if (triggeredNow) {
                currentTick + MINECRAFT_CALIBRATION_KNOCKOUT_DURATION_TICKS
            } else {
                null
            }
        )
    }

    fun knockoutMeleeDamageMultiplier(
        hasAspect: Boolean,
        wasActiveBeforeHit: Boolean,
        isMeleeDamage: Boolean,
        isPoweredMelee: Boolean
    ): Float = when {
        !hasAspect || !wasActiveBeforeHit || !isMeleeDamage -> 1.0f
        isPoweredMelee -> MINECRAFT_CALIBRATION_KNOCKOUT_POWERED_MELEE_DAMAGE_MULTIPLIER
        else -> MINECRAFT_CALIBRATION_KNOCKOUT_UNPOWERED_MELEE_DAMAGE_MULTIPLIER
    }

    fun knockoutMeleeIsArc(
        hasAspect: Boolean,
        isKnockoutActive: Boolean,
        isMeleeDamage: Boolean
    ): Boolean = hasAspect && isKnockoutActive && isMeleeDamage

    fun knockoutMeleeReach(
        baseReach: Double,
        hasAspect: Boolean,
        isKnockoutActive: Boolean
    ): Double = if (hasAspect && isKnockoutActive) {
        maxOf(baseReach.coerceAtLeast(0.0), MINECRAFT_CALIBRATION_KNOCKOUT_MELEE_REACH_BLOCKS)
    } else {
        baseReach.coerceAtLeast(0.0)
    }

    enum class KnockoutTargetRank {
        PLAYER,
        MINOR,
        MAJOR,
        BOSS_OR_CHAMPION
    }

    data class KnockoutFinalBlowResult(
        val healAmount: Float,
        val shouldGrantAmplified: Boolean,
        val amplifiedDurationTicks: Int
    )

    fun resolveKnockoutMeleeFinalBlow(
        hasAspect: Boolean,
        isMeleeFinalBlow: Boolean,
        targetRank: KnockoutTargetRank
    ): KnockoutFinalBlowResult {
        if (!hasAspect || !isMeleeFinalBlow) {
            return KnockoutFinalBlowResult(0.0f, false, 0)
        }
        val healing = when (targetRank) {
            KnockoutTargetRank.PLAYER -> MINECRAFT_CALIBRATION_KNOCKOUT_PLAYER_HEAL
            KnockoutTargetRank.MINOR -> MINECRAFT_CALIBRATION_KNOCKOUT_MINOR_HEAL
            KnockoutTargetRank.MAJOR -> MINECRAFT_CALIBRATION_KNOCKOUT_MAJOR_HEAL
            KnockoutTargetRank.BOSS_OR_CHAMPION -> MINECRAFT_CALIBRATION_KNOCKOUT_BOSS_HEAL
        }
        return KnockoutFinalBlowResult(
            healAmount = healing,
            shouldGrantAmplified = true,
            amplifiedDurationTicks = MINECRAFT_CALIBRATION_KNOCKOUT_AMPLIFIED_DURATION_TICKS
        )
    }

    /** Bungie's Bolt Charge article publicly defines maximum charge as ten stacks. */
    const val MAX_BOLT_CHARGE_STACKS = 10
    const val MINECRAFT_CALIBRATION_STORMS_KEEP_CAST_STACKS = 3
    const val MINECRAFT_CALIBRATION_STORMS_KEEP_TEAM_RADIUS = 8.0
    const val MINECRAFT_CALIBRATION_STORMS_KEEP_BARRICADE_STACK_INTERVAL_TICKS = 20L
    const val MINECRAFT_CALIBRATION_STORMS_KEEP_BARRICADE_STACK_GAIN = 1

    fun stormsKeepCastBoltChargeGain(
        hasAspect: Boolean,
        classAbilityCast: Boolean,
        isCasterOrAlly: Boolean,
        distanceSquaredFromCaster: Double
    ): Int {
        val radiusSquared = MINECRAFT_CALIBRATION_STORMS_KEEP_TEAM_RADIUS *
            MINECRAFT_CALIBRATION_STORMS_KEEP_TEAM_RADIUS
        return if (hasAspect && classAbilityCast && isCasterOrAlly &&
            distanceSquaredFromCaster >= 0.0 && distanceSquaredFromCaster <= radiusSquared
        ) {
            MINECRAFT_CALIBRATION_STORMS_KEEP_CAST_STACKS
        } else {
            0
        }
    }

    /** Multiple overlapping Storm's Keep Barricades are one logical source. */
    fun normalizedStormsKeepBarricadeSources(qualifyingBarricadeCount: Int): Int =
        if (qualifyingBarricadeCount > 0) 1 else 0

    fun stormsKeepBarricadeBoltChargeGain(
        hasAspect: Boolean,
        isOwnerOrAlly: Boolean,
        qualifyingBarricadeCount: Int,
        currentStacks: Int,
        currentTick: Long,
        nextEligibleTick: Long
    ): Int {
        val room = MAX_BOLT_CHARGE_STACKS - currentStacks.coerceIn(0, MAX_BOLT_CHARGE_STACKS)
        return if (hasAspect && isOwnerOrAlly &&
            normalizedStormsKeepBarricadeSources(qualifyingBarricadeCount) == 1 &&
            room > 0 && currentTick >= nextEligibleTick
        ) {
            min(MINECRAFT_CALIBRATION_STORMS_KEEP_BARRICADE_STACK_GAIN, room)
        } else {
            0
        }
    }

    fun nextStormsKeepBarricadeStackTick(currentTick: Long, gainedStacks: Int): Long? =
        if (gainedStacks > 0) {
            currentTick + MINECRAFT_CALIBRATION_STORMS_KEEP_BARRICADE_STACK_INTERVAL_TICKS
        } else {
            null
        }

    fun addBoltChargeStacks(currentStacks: Int, gainedStacks: Int): Int =
        (currentStacks.coerceAtLeast(0) + gainedStacks.coerceAtLeast(0))
            .coerceAtMost(MAX_BOLT_CHARGE_STACKS)

    data class StormsKeepWeaponResult(
        val shouldDischargeBoltCharge: Boolean,
        val remainingBoltChargeStacks: Int
    )

    /**
     * Storm's Keep uniquely permits weapon damage to release full Bolt Charge
     * behind a qualifying Barricade. Allies and immune objects are invalid
     * targets, matching the later official bug fix.
     */
    fun resolveStormsKeepWeaponDamage(
        hasAspect: Boolean,
        isOwnerOrAlly: Boolean,
        qualifyingBarricadeCount: Int,
        currentBoltChargeStacks: Int,
        isWeaponDamage: Boolean,
        damageAccepted: Boolean,
        targetIsHostile: Boolean,
        targetIsImmune: Boolean
    ): StormsKeepWeaponResult {
        val safeStacks = currentBoltChargeStacks.coerceIn(0, MAX_BOLT_CHARGE_STACKS)
        val shouldDischarge = hasAspect && isOwnerOrAlly &&
            normalizedStormsKeepBarricadeSources(qualifyingBarricadeCount) == 1 &&
            safeStacks == MAX_BOLT_CHARGE_STACKS && isWeaponDamage && damageAccepted &&
            targetIsHostile && !targetIsImmune
        return StormsKeepWeaponResult(
            shouldDischargeBoltCharge = shouldDischarge,
            remainingBoltChargeStacks = if (shouldDischarge) 0 else safeStacks
        )
    }
}
