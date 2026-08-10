package atopos.destiny2.common.aspect

/** Server-confirmed final-blow sources that can contribute to Icarus Dash's Cure trigger. */
enum class IcarusDashFinalBlowSource {
    WEAPON,
    SUPER,
    OTHER
}

/** Solar grenade families explicitly enhanced by Touch of Flame. */
enum class TouchOfFlameGrenadeType {
    HEALING,
    SOLAR,
    FIREBOLT,
    FUSION,
    OTHER
}

/**
 * Semantic effects from Bungie's current Touch of Flame definition.
 *
 * Scalars such as Cure strength, acquisition radius, and duration belong in
 * calibration constants once the runtime implements the corresponding grenade.
 */
enum class TouchOfFlameEffect {
    STRONGER_CURE_AND_RESTORATION,
    HEAT_RISES_GRANTS_ALLY_RESTORATION,
    EXTENDED_DURATION,
    PERIODIC_LAVA_BLOBS,
    INCREASED_TARGET_ACQUISITION_RANGE,
    INCREASED_MAX_TARGETS,
    SECOND_EXPLOSION
}

data class HeatRisesHoldState(val startedAtTick: Long? = null)

data class HeatRisesHoldUpdate(
    val state: HeatRisesHoldState,
    val shouldConsumeGrenade: Boolean,
    val shouldActivateHeatRises: Boolean
)

data class IcarusDashChargeState(
    val chargesUsed: Int = 0,
    val windowExpiresAtTick: Long = Long.MIN_VALUE
)

data class IcarusDashAttempt(
    val state: IcarusDashChargeState,
    val accepted: Boolean
)

data class IcarusDashMultikillState(
    val qualifyingFinalBlows: Int = 0,
    val windowExpiresAtTick: Long = Long.MIN_VALUE
)

data class IcarusDashMultikillUpdate(
    val state: IcarusDashMultikillState,
    val shouldApplyCure: Boolean
)

data class HellionState(
    val summonedAtTick: Long,
    val expiresAtTick: Long,
    val nextShotTick: Long
)

data class TouchOfFlameStrategy(
    val grenadeType: TouchOfFlameGrenadeType,
    val effects: Set<TouchOfFlameEffect>
)

data class HealingGrenadeProfile(
    val impactRadius: Double,
    val cureHealth: Float,
    val restorationDurationTicks: Int,
    val restorationLevel: Int,
    val orbPickupRadius: Double,
    val orbLifetimeTicks: Int
)

data class FireboltGrenadeProfile(
    val targetSearchRadius: Double,
    val maximumTargets: Int,
    val damage: Float,
    val scorchStacks: Int,
    val scorchDurationTicks: Int
)

data class FusionGrenadeProfile(
    val fuseTicks: Int,
    val explosionRadius: Double,
    val explosionDamage: Float,
    val scorchStacks: Int,
    val scorchDurationTicks: Int,
    val explosionCount: Int,
    val repeatExplosionDelayTicks: Int
)

/**
 * Pure, deterministic server rules for the four current Solar Warlock Aspects.
 *
 * Aspect IDs and semantic behavior follow Bungie's current Destiny manifest and
 * first-party patch notes. Every gameplay scalar not published by Bungie is
 * deliberately prefixed with `MINECRAFT_CALIBRATION_`; runtime code should not
 * duplicate those values in event handlers.
 */
object SolarWarlockAspectRules {
    private const val MOD_ID = "destiny2-mod"

    const val HEAT_RISES = "$MOD_ID:aspect_heat_rises"
    const val TOUCH_OF_FLAME = "$MOD_ID:aspect_touch_of_flame"
    const val ICARUS_DASH = "$MOD_ID:aspect_icarus_dash"
    const val HELLION = "$MOD_ID:aspect_hellion"

    val ALL_ASPECT_IDS: Set<String> = linkedSetOf(
        HEAT_RISES,
        TOUCH_OF_FLAME,
        ICARUS_DASH,
        HELLION
    )

    const val MINECRAFT_CALIBRATION_HEAT_RISES_HOLD_TICKS = 20L
    const val MINECRAFT_CALIBRATION_HEAT_RISES_AIRBORNE_FINAL_BLOW_EXTENSION_TICKS = 40L
    const val MINECRAFT_CALIBRATION_HEAT_RISES_MAX_REMAINING_TICKS = 30L * 20L
    const val MINECRAFT_CALIBRATION_HEAT_RISES_MELEE_REFUND_TICKS = 40

    const val MINECRAFT_CALIBRATION_ICARUS_DASH_COOLDOWN_TICKS = 4L * 20L
    const val MINECRAFT_CALIBRATION_ICARUS_DASH_HORIZONTAL_SPEED = 1.45
    const val MINECRAFT_CALIBRATION_ICARUS_DASH_VERTICAL_SPEED = 0.10
    const val MINECRAFT_CALIBRATION_ICARUS_MULTIKILL_REQUIRED_FINAL_BLOWS = 2
    const val MINECRAFT_CALIBRATION_ICARUS_MULTIKILL_WINDOW_TICKS = 5L * 20L
    const val MINECRAFT_CALIBRATION_ICARUS_CURE_HEALTH = 6.0f

    /** 32 metres is public Destiny tuning; treating one block as one metre remains a Minecraft calibration. */
    const val MINECRAFT_CALIBRATION_HELLION_TARGET_RANGE_BLOCKS = 32.0
    const val MINECRAFT_CALIBRATION_HELLION_DURATION_TICKS = 20L * 20L
    const val MINECRAFT_CALIBRATION_HELLION_INITIAL_SHOT_DELAY_TICKS = 10L
    const val MINECRAFT_CALIBRATION_HELLION_SHOT_INTERVAL_TICKS = 30L
    const val MINECRAFT_CALIBRATION_HELLION_BOLT_SPEED = 1.10
    const val MINECRAFT_CALIBRATION_HELLION_BOLT_LIFETIME_TICKS = 40
    const val MINECRAFT_CALIBRATION_HELLION_DAMAGE = 4.0f
    const val MINECRAFT_CALIBRATION_HELLION_SCORCH_STACKS = 30

    const val MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_SPEED = 1.5f
    const val MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_INACCURACY = 1.0f
    const val MINECRAFT_CALIBRATION_GRENADE_MAX_FLIGHT_TICKS = 10 * 20
    const val MINECRAFT_CALIBRATION_GRENADE_ENTITY_SIZE = 0.25f
    const val MINECRAFT_CALIBRATION_HEALING_ORB_ENTITY_SIZE = 0.5f
    const val MINECRAFT_CALIBRATION_GRENADE_TRACK_RANGE_CHUNKS = 8
    const val MINECRAFT_CALIBRATION_GRENADE_TRACKED_UPDATE_RATE = 1
    const val MINECRAFT_CALIBRATION_HEALING_ORB_TRACKED_UPDATE_RATE = 2

    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_COOLDOWN_TICKS = 91 * 20
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_IMPACT_RADIUS = 4.0
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_CURE_HEALTH = 8.0f
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_TOUCH_OF_FLAME_CURE_HEALTH = 12.0f
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_RESTORATION_TICKS = 5 * 20
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_TOUCH_OF_FLAME_RESTORATION_TICKS = 7 * 20
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_RESTORATION_LEVEL = 1
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_TOUCH_OF_FLAME_RESTORATION_LEVEL = 2
    const val MINECRAFT_CALIBRATION_HEALING_ORB_PICKUP_RADIUS = 1.25
    const val MINECRAFT_CALIBRATION_HEALING_ORB_LIFETIME_TICKS = 10 * 20
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_HEAT_RISES_ALLY_RADIUS = 8.0

    const val MINECRAFT_CALIBRATION_FIREBOLT_GRENADE_COOLDOWN_TICKS = 64 * 20
    const val MINECRAFT_CALIBRATION_FIREBOLT_TARGET_RADIUS = 5.0
    const val MINECRAFT_CALIBRATION_FIREBOLT_TOUCH_OF_FLAME_TARGET_RADIUS = 8.0
    const val MINECRAFT_CALIBRATION_FIREBOLT_MAX_TARGETS = 3
    const val MINECRAFT_CALIBRATION_FIREBOLT_TOUCH_OF_FLAME_MAX_TARGETS = 5
    const val MINECRAFT_CALIBRATION_FIREBOLT_DAMAGE = 5.0f
    const val MINECRAFT_CALIBRATION_FIREBOLT_SCORCH_STACKS = 15
    const val MINECRAFT_CALIBRATION_FIREBOLT_SCORCH_DURATION_TICKS = 5 * 20

    const val MINECRAFT_CALIBRATION_FUSION_GRENADE_COOLDOWN_TICKS = 73 * 20
    const val MINECRAFT_CALIBRATION_FUSION_GRENADE_FUSE_TICKS = 20
    const val MINECRAFT_CALIBRATION_FUSION_GRENADE_EXPLOSION_RADIUS = 3.0
    const val MINECRAFT_CALIBRATION_FUSION_GRENADE_EXPLOSION_DAMAGE = 9.0f
    const val MINECRAFT_CALIBRATION_FUSION_GRENADE_SCORCH_STACKS = 40
    const val MINECRAFT_CALIBRATION_FUSION_GRENADE_SCORCH_DURATION_TICKS = 7 * 20
    const val MINECRAFT_CALIBRATION_FUSION_GRENADE_REPEAT_EXPLOSION_DELAY_TICKS = 12

    /**
     * Advances the hold-to-consume handshake from server-observed input.
     * Releasing, losing the Aspect, or becoming unable to consume clears the hold
     * without spending the grenade. Only the completion tick requests consumption.
     */
    fun updateHeatRisesHold(
        hasAspect: Boolean,
        isHoldingGrenadeInput: Boolean,
        canConsumeGrenade: Boolean,
        currentTick: Long,
        previous: HeatRisesHoldState
    ): HeatRisesHoldUpdate {
        if (!hasAspect || !isHoldingGrenadeInput || !canConsumeGrenade) {
            return HeatRisesHoldUpdate(HeatRisesHoldState(), false, false)
        }

        val startedAt = previous.startedAtTick
        if (startedAt == null || currentTick < startedAt) {
            return HeatRisesHoldUpdate(HeatRisesHoldState(currentTick), false, false)
        }

        val completed = currentTick - startedAt >= MINECRAFT_CALIBRATION_HEAT_RISES_HOLD_TICKS
        return if (completed) {
            HeatRisesHoldUpdate(HeatRisesHoldState(), true, true)
        } else {
            HeatRisesHoldUpdate(previous, false, false)
        }
    }

    /** Explicit cancellation hook for input-stop, death, disconnect, or loadout changes. */
    fun cancelHeatRisesHold(): HeatRisesHoldState = HeatRisesHoldState()

    /**
     * Extends active Heat Rises only for a server-confirmed airborne final blow.
     * The returned expiry is capped relative to the current server tick.
     */
    fun heatRisesExpiryAfterFinalBlow(
        hasAspect: Boolean,
        isHeatRisesActive: Boolean,
        isAirborne: Boolean,
        isServerConfirmedFinalBlow: Boolean,
        currentTick: Long,
        previousExpiresAtTick: Long
    ): Long {
        if (!hasAspect || !isHeatRisesActive || !isAirborne || !isServerConfirmedFinalBlow) {
            return previousExpiresAtTick
        }

        val cap = saturatedAdd(currentTick, MINECRAFT_CALIBRATION_HEAT_RISES_MAX_REMAINING_TICKS)
        val extended = saturatedAdd(
            maxOf(currentTick, previousExpiresAtTick),
            MINECRAFT_CALIBRATION_HEAT_RISES_AIRBORNE_FINAL_BLOW_EXTENSION_TICKS
        )
        return extended.coerceAtMost(cap)
    }

    fun heatRisesMeleeEnergyRefundTicks(
        hasAspect: Boolean,
        isHeatRisesActive: Boolean,
        isAirborne: Boolean,
        isServerConfirmedFinalBlow: Boolean
    ): Int = if (hasAspect && isHeatRisesActive && isAirborne && isServerConfirmedFinalBlow) {
        MINECRAFT_CALIBRATION_HEAT_RISES_MELEE_REFUND_TICKS
    } else {
        0
    }

    /** Heat Rises changes the official allowance from one air dodge to two. */
    fun icarusDashChargeLimit(isHeatRisesActive: Boolean): Int = if (isHeatRisesActive) 2 else 1

    /**
     * Attempts a server-authoritative air dodge and advances its charge window.
     * Direction and velocity validation remain runtime responsibilities.
     */
    fun attemptIcarusDash(
        hasAspect: Boolean,
        isAirborne: Boolean,
        canControlMovement: Boolean,
        isHeatRisesActive: Boolean,
        currentTick: Long,
        previous: IcarusDashChargeState
    ): IcarusDashAttempt {
        val normalized = if (currentTick >= previous.windowExpiresAtTick || currentTick < 0L) {
            IcarusDashChargeState()
        } else {
            previous.copy(chargesUsed = previous.chargesUsed.coerceAtLeast(0))
        }

        if (!hasAspect || !isAirborne || !canControlMovement) {
            return IcarusDashAttempt(normalized, false)
        }

        if (normalized.chargesUsed >= icarusDashChargeLimit(isHeatRisesActive)) {
            return IcarusDashAttempt(normalized, false)
        }

        val windowExpiry = if (normalized.chargesUsed == 0) {
            saturatedAdd(currentTick, MINECRAFT_CALIBRATION_ICARUS_DASH_COOLDOWN_TICKS)
        } else {
            normalized.windowExpiresAtTick
        }
        return IcarusDashAttempt(
            IcarusDashChargeState(normalized.chargesUsed + 1, windowExpiry),
            true
        )
    }

    /**
     * Records a qualifying airborne weapon/Super final blow for Icarus Dash.
     * Non-qualifying events do not erase a still-valid sequence; expiry or Aspect
     * removal does. Reaching the calibrated threshold consumes the sequence.
     */
    fun recordIcarusAirborneFinalBlow(
        hasAspect: Boolean,
        isAirborne: Boolean,
        isServerConfirmedFinalBlow: Boolean,
        source: IcarusDashFinalBlowSource,
        currentTick: Long,
        previous: IcarusDashMultikillState
    ): IcarusDashMultikillUpdate {
        if (!hasAspect) return IcarusDashMultikillUpdate(IcarusDashMultikillState(), false)

        val unexpired = currentTick < previous.windowExpiresAtTick
        val normalized = if (unexpired) previous else IcarusDashMultikillState()
        val qualifies = isAirborne && isServerConfirmedFinalBlow &&
            source in setOf(IcarusDashFinalBlowSource.WEAPON, IcarusDashFinalBlowSource.SUPER)
        if (!qualifies) return IcarusDashMultikillUpdate(normalized, false)

        val count = normalized.qualifyingFinalBlows + 1
        if (count >= MINECRAFT_CALIBRATION_ICARUS_MULTIKILL_REQUIRED_FINAL_BLOWS) {
            return IcarusDashMultikillUpdate(IcarusDashMultikillState(), true)
        }

        return IcarusDashMultikillUpdate(
            IcarusDashMultikillState(
                qualifyingFinalBlows = count,
                windowExpiresAtTick = saturatedAdd(
                    currentTick,
                    MINECRAFT_CALIBRATION_ICARUS_MULTIKILL_WINDOW_TICKS
                )
            ),
            false
        )
    }

    /** A Hellion is summoned only after the server accepts the class-ability cast. */
    fun summonHellion(
        hasAspect: Boolean,
        classAbilityCastSucceeded: Boolean,
        ownerCanAct: Boolean,
        currentTick: Long
    ): HellionState? {
        if (!hasAspect || !classAbilityCastSucceeded || !ownerCanAct) return null
        return HellionState(
            summonedAtTick = currentTick,
            expiresAtTick = saturatedAdd(currentTick, MINECRAFT_CALIBRATION_HELLION_DURATION_TICKS),
            nextShotTick = saturatedAdd(currentTick, MINECRAFT_CALIBRATION_HELLION_INITIAL_SHOT_DELAY_TICKS)
        )
    }

    fun isHellionActive(state: HellionState, currentTick: Long): Boolean =
        currentTick >= state.summonedAtTick && currentTick < state.expiresAtTick

    fun isValidHellionTarget(
        isHostile: Boolean,
        isAlive: Boolean,
        isSpectator: Boolean,
        isSameDimension: Boolean,
        hasLineOfSight: Boolean,
        distanceSquared: Double
    ): Boolean {
        val rangeSquared = MINECRAFT_CALIBRATION_HELLION_TARGET_RANGE_BLOCKS *
            MINECRAFT_CALIBRATION_HELLION_TARGET_RANGE_BLOCKS
        return isHostile && isAlive && !isSpectator && isSameDimension && hasLineOfSight &&
            distanceSquared.isFinite() && distanceSquared >= 0.0 && distanceSquared <= rangeSquared
    }

    fun shouldHellionFire(state: HellionState, currentTick: Long, hasValidTarget: Boolean): Boolean =
        hasValidTarget && isHellionActive(state, currentTick) && currentTick >= state.nextShotTick

    /** Advances cadence only after the server actually creates a Hellion projectile. */
    fun hellionAfterShot(state: HellionState, currentTick: Long): HellionState = state.copy(
        nextShotTick = saturatedAdd(
            maxOf(currentTick, state.nextShotTick),
            MINECRAFT_CALIBRATION_HELLION_SHOT_INTERVAL_TICKS
        )
    )

    fun touchOfFlameStrategy(
        hasAspect: Boolean,
        grenadeType: TouchOfFlameGrenadeType
    ): TouchOfFlameStrategy {
        if (!hasAspect) return TouchOfFlameStrategy(grenadeType, emptySet())
        val effects = when (grenadeType) {
            TouchOfFlameGrenadeType.HEALING -> setOf(
                TouchOfFlameEffect.STRONGER_CURE_AND_RESTORATION,
                TouchOfFlameEffect.HEAT_RISES_GRANTS_ALLY_RESTORATION
            )
            TouchOfFlameGrenadeType.SOLAR -> setOf(
                TouchOfFlameEffect.EXTENDED_DURATION,
                TouchOfFlameEffect.PERIODIC_LAVA_BLOBS
            )
            TouchOfFlameGrenadeType.FIREBOLT -> setOf(
                TouchOfFlameEffect.INCREASED_TARGET_ACQUISITION_RANGE,
                TouchOfFlameEffect.INCREASED_MAX_TARGETS
            )
            TouchOfFlameGrenadeType.FUSION -> setOf(TouchOfFlameEffect.SECOND_EXPLOSION)
            TouchOfFlameGrenadeType.OTHER -> emptySet()
        }
        return TouchOfFlameStrategy(grenadeType, effects)
    }

    fun healingGrenadeProfile(hasTouchOfFlame: Boolean): HealingGrenadeProfile =
        HealingGrenadeProfile(
            impactRadius = MINECRAFT_CALIBRATION_HEALING_GRENADE_IMPACT_RADIUS,
            cureHealth = if (hasTouchOfFlame) {
                MINECRAFT_CALIBRATION_HEALING_GRENADE_TOUCH_OF_FLAME_CURE_HEALTH
            } else {
                MINECRAFT_CALIBRATION_HEALING_GRENADE_CURE_HEALTH
            },
            restorationDurationTicks = if (hasTouchOfFlame) {
                MINECRAFT_CALIBRATION_HEALING_GRENADE_TOUCH_OF_FLAME_RESTORATION_TICKS
            } else {
                MINECRAFT_CALIBRATION_HEALING_GRENADE_RESTORATION_TICKS
            },
            restorationLevel = if (hasTouchOfFlame) {
                MINECRAFT_CALIBRATION_HEALING_GRENADE_TOUCH_OF_FLAME_RESTORATION_LEVEL
            } else {
                MINECRAFT_CALIBRATION_HEALING_GRENADE_RESTORATION_LEVEL
            },
            orbPickupRadius = MINECRAFT_CALIBRATION_HEALING_ORB_PICKUP_RADIUS,
            orbLifetimeTicks = MINECRAFT_CALIBRATION_HEALING_ORB_LIFETIME_TICKS
        )

    fun fireboltGrenadeProfile(hasTouchOfFlame: Boolean): FireboltGrenadeProfile =
        FireboltGrenadeProfile(
            targetSearchRadius = if (hasTouchOfFlame) {
                MINECRAFT_CALIBRATION_FIREBOLT_TOUCH_OF_FLAME_TARGET_RADIUS
            } else {
                MINECRAFT_CALIBRATION_FIREBOLT_TARGET_RADIUS
            },
            maximumTargets = if (hasTouchOfFlame) {
                MINECRAFT_CALIBRATION_FIREBOLT_TOUCH_OF_FLAME_MAX_TARGETS
            } else {
                MINECRAFT_CALIBRATION_FIREBOLT_MAX_TARGETS
            },
            damage = MINECRAFT_CALIBRATION_FIREBOLT_DAMAGE,
            scorchStacks = MINECRAFT_CALIBRATION_FIREBOLT_SCORCH_STACKS,
            scorchDurationTicks = MINECRAFT_CALIBRATION_FIREBOLT_SCORCH_DURATION_TICKS
        )

    fun fusionGrenadeProfile(hasTouchOfFlame: Boolean): FusionGrenadeProfile =
        FusionGrenadeProfile(
            fuseTicks = MINECRAFT_CALIBRATION_FUSION_GRENADE_FUSE_TICKS,
            explosionRadius = MINECRAFT_CALIBRATION_FUSION_GRENADE_EXPLOSION_RADIUS,
            explosionDamage = MINECRAFT_CALIBRATION_FUSION_GRENADE_EXPLOSION_DAMAGE,
            scorchStacks = MINECRAFT_CALIBRATION_FUSION_GRENADE_SCORCH_STACKS,
            scorchDurationTicks = MINECRAFT_CALIBRATION_FUSION_GRENADE_SCORCH_DURATION_TICKS,
            explosionCount = if (hasTouchOfFlame) 2 else 1,
            repeatExplosionDelayTicks = MINECRAFT_CALIBRATION_FUSION_GRENADE_REPEAT_EXPLOSION_DELAY_TICKS
        )

    private fun saturatedAdd(value: Long, positiveDelta: Long): Long {
        require(positiveDelta >= 0L)
        return if (value > Long.MAX_VALUE - positiveDelta) Long.MAX_VALUE else value + positiveDelta
    }
}
