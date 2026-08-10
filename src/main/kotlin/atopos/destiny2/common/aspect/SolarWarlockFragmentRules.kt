package atopos.destiny2.common.aspect

import atopos.destiny2.common.player.DestinyStats

/** Damage/event classifications supplied by the future server runtime. */
enum class SolarFragmentCombatSource {
    SOLAR_WEAPON,
    SOLAR_GRENADE,
    SOLAR_POWERED_MELEE,
    SOLAR_SUPER,
    SOLAR_SUPER_PROJECTILE,
    SOLAR_ABILITY,
    UNCHARGED_MELEE,
    OTHER
}

enum class SolarFragmentBuff {
    RADIANT,
    RESTORATION,
    CURE,
    OTHER
}

data class SolarSuperTrackingProfile(
    val acquisitionRange: Double,
    val minimumForwardDot: Double,
    val steeringStrength: Double
)

enum class SolarCombatantTier {
    MINOR,
    MAJOR,
    BOSS
}

data class SolarScorchProfile(
    val baseStacks: Int,
    /** Bungie tunes the Ember of Ashes bonus per source instead of using one global multiplier. */
    val ashesBonusStacks: Int
)

data class SolarIgnitionMultikillState(
    val killCount: Int = 0,
    val windowStartedAtTick: Long? = null,
    val nextOrbEligibleTick: Long = 0L
)

data class SolarIgnitionMultikillUpdate(
    val state: SolarIgnitionMultikillState,
    val shouldGenerateOrb: Boolean
)

/**
 * Pure, deterministic rules for all sixteen Solar Fragments in Bungie's current manifest.
 *
 * Constants prefixed with MINECRAFT_CALIBRATION are explicit platform adaptations where
 * Bungie's public Manifest and patch notes do not publish the live scalar, refund, radius,
 * internal cooldown, or multikill window. They must remain centralized and be adjusted from
 * Destiny/Minecraft A-B testing instead of being copied into server event handlers.
 */
object SolarWarlockFragmentRules {
    private const val MOD_ID = "destiny2-mod"

    const val EMBER_OF_ASHES = "$MOD_ID:fragment_ember_of_ashes"
    const val EMBER_OF_BEAMS = "$MOD_ID:fragment_ember_of_beams"
    const val EMBER_OF_BENEVOLENCE = "$MOD_ID:fragment_ember_of_benevolence"
    const val EMBER_OF_BLISTERING = "$MOD_ID:fragment_ember_of_blistering"
    const val EMBER_OF_CHAR = "$MOD_ID:fragment_ember_of_char"
    const val EMBER_OF_COMBUSTION = "$MOD_ID:fragment_ember_of_combustion"
    const val EMBER_OF_EMPYREAN = "$MOD_ID:fragment_ember_of_empyrean"
    const val EMBER_OF_ERUPTION = "$MOD_ID:fragment_ember_of_eruption"
    const val EMBER_OF_MERCY = "$MOD_ID:fragment_ember_of_mercy"
    const val EMBER_OF_RESOLVE = "$MOD_ID:fragment_ember_of_resolve"
    const val EMBER_OF_SEARING = "$MOD_ID:fragment_ember_of_searing"
    const val EMBER_OF_SINGEING = "$MOD_ID:fragment_ember_of_singeing"
    const val EMBER_OF_SOLACE = "$MOD_ID:fragment_ember_of_solace"
    const val EMBER_OF_TEMPERING = "$MOD_ID:fragment_ember_of_tempering"
    const val EMBER_OF_TORCHES = "$MOD_ID:fragment_ember_of_torches"
    const val EMBER_OF_WONDER = "$MOD_ID:fragment_ember_of_wonder"

    val ALL_FRAGMENT_IDS: Set<String> = linkedSetOf(
        EMBER_OF_ASHES,
        EMBER_OF_BEAMS,
        EMBER_OF_BENEVOLENCE,
        EMBER_OF_BLISTERING,
        EMBER_OF_CHAR,
        EMBER_OF_COMBUSTION,
        EMBER_OF_EMPYREAN,
        EMBER_OF_ERUPTION,
        EMBER_OF_MERCY,
        EMBER_OF_RESOLVE,
        EMBER_OF_SEARING,
        EMBER_OF_SINGEING,
        EMBER_OF_SOLACE,
        EMBER_OF_TEMPERING,
        EMBER_OF_TORCHES,
        EMBER_OF_WONDER
    )

    private val ZERO_STATS = DestinyStats(0, 0, 0, 0, 0, 0)

    /** Armor 3.0 stat modifiers from the current Simplified Chinese Bungie Manifest. */
    val OFFICIAL_STATIC_STAT_BONUSES: Map<String, DestinyStats> = linkedMapOf(
        EMBER_OF_ASHES to ZERO_STATS,
        EMBER_OF_BEAMS to stats(superStat = 10),
        EMBER_OF_BENEVOLENCE to stats(grenade = -10),
        EMBER_OF_BLISTERING to ZERO_STATS,
        EMBER_OF_CHAR to stats(grenade = 10),
        EMBER_OF_COMBUSTION to stats(melee = 10),
        EMBER_OF_EMPYREAN to stats(health = -10),
        EMBER_OF_ERUPTION to stats(melee = 10),
        EMBER_OF_MERCY to stats(health = 10),
        EMBER_OF_RESOLVE to ZERO_STATS,
        EMBER_OF_SEARING to stats(classAbility = 10),
        EMBER_OF_SINGEING to ZERO_STATS,
        EMBER_OF_SOLACE to ZERO_STATS,
        EMBER_OF_TEMPERING to stats(classAbility = -10),
        EMBER_OF_TORCHES to stats(grenade = -10),
        EMBER_OF_WONDER to stats(health = 10)
    )

    const val TORCHES_BASE_DURATION_TICKS = 8 * 20
    const val SOLACE_DURATION_MULTIPLIER = 1.5f
    const val BENEVOLENCE_BASE_DURATION_TICKS = 2 * 20
    const val EMPYREAN_MAX_DURATION_TICKS = 15 * 20
    const val TEMPERING_MAX_STACKS = 3

    const val MINECRAFT_CALIBRATION_ERUPTION_RADIUS_MULTIPLIER = 1.5
    const val MINECRAFT_CALIBRATION_BASE_SUPER_RANGE_BLOCKS = 10.0
    const val MINECRAFT_CALIBRATION_BASE_SUPER_MINIMUM_DOT = 0.92
    const val MINECRAFT_CALIBRATION_BASE_SUPER_STEERING_STRENGTH = 0.06
    const val MINECRAFT_CALIBRATION_BEAMS_RANGE_BLOCKS = 18.0
    const val MINECRAFT_CALIBRATION_BEAMS_MINIMUM_DOT = 0.65
    const val MINECRAFT_CALIBRATION_BEAMS_STEERING_STRENGTH = 0.22
    const val MINECRAFT_CALIBRATION_CHAR_SCORCH_STACKS = 40
    const val MINECRAFT_CALIBRATION_CHAR_ASHES_BONUS_STACKS = 20
    const val MINECRAFT_CALIBRATION_SINGEING_DURATION_TICKS = 3 * 20
    const val MINECRAFT_CALIBRATION_SINGEING_RECHARGE_MULTIPLIER = 4.0f
    const val MINECRAFT_CALIBRATION_BENEVOLENCE_RECHARGE_MULTIPLIER = 4.0f
    const val MINECRAFT_CALIBRATION_BLISTERING_GRENADE_REFUND_TICKS = 40
    const val MINECRAFT_CALIBRATION_SEARING_MELEE_REFUND_TICKS = 40
    const val MINECRAFT_CALIBRATION_FIRESPRITE_GRENADE_REFUND_TICKS = 60
    const val MINECRAFT_CALIBRATION_MERCY_RESTORATION_DURATION_TICKS = 3 * 20
    const val MINECRAFT_CALIBRATION_RESOLVE_CURE_HEALTH = 6.0f
    const val MINECRAFT_CALIBRATION_EMPYREAN_MINOR_EXTENSION_TICKS = 20
    const val MINECRAFT_CALIBRATION_EMPYREAN_MAJOR_EXTENSION_TICKS = 40
    const val MINECRAFT_CALIBRATION_EMPYREAN_BOSS_EXTENSION_TICKS = 60
    const val MINECRAFT_CALIBRATION_TEMPERING_DURATION_TICKS = 8 * 20
    const val MINECRAFT_CALIBRATION_TEMPERING_HEALTH_PER_STACK = 5
    const val MINECRAFT_CALIBRATION_TEMPERING_AIRBORNE_SPREAD_MULTIPLIER = 0.85f
    const val MINECRAFT_CALIBRATION_WONDER_REQUIRED_KILLS = 2
    const val MINECRAFT_CALIBRATION_WONDER_MULTIKILL_WINDOW_TICKS = 50L
    const val MINECRAFT_CALIBRATION_WONDER_ORB_COOLDOWN_TICKS = 10 * 20L

    fun staticStatBonuses(equippedFragmentIds: Set<String>): DestinyStats {
        var weapons = 0
        var health = 0
        var classAbility = 0
        var grenade = 0
        var superStat = 0
        var melee = 0
        equippedFragmentIds.forEach { id ->
            val bonus = OFFICIAL_STATIC_STAT_BONUSES[id] ?: return@forEach
            weapons += bonus.weapons
            health += bonus.health
            classAbility += bonus.classAbility
            grenade += bonus.grenade
            superStat += bonus.superStat
            melee += bonus.melee
        }
        return DestinyStats(weapons, health, classAbility, grenade, superStat, melee)
    }

    fun scorchStacks(profile: SolarScorchProfile, hasAshes: Boolean): Int {
        val base = profile.baseStacks.coerceAtLeast(0)
        return base + if (hasAshes) profile.ashesBonusStacks.coerceAtLeast(0) else 0
    }

    fun ignitionRadius(baseRadius: Double, hasEruption: Boolean): Double {
        val safeRadius = baseRadius.coerceAtLeast(0.0)
        return if (hasEruption) safeRadius * MINECRAFT_CALIBRATION_ERUPTION_RADIUS_MULTIPLIER else safeRadius
    }

    fun solarBuffDuration(baseDurationTicks: Int, buff: SolarFragmentBuff, hasSolace: Boolean): Int {
        val safeDuration = baseDurationTicks.coerceAtLeast(0)
        if (!hasSolace || buff !in setOf(SolarFragmentBuff.RADIANT, SolarFragmentBuff.RESTORATION)) {
            return safeDuration
        }
        return (safeDuration * SOLACE_DURATION_MULTIPLIER).toInt()
    }

    fun torchesDuration(hasSolace: Boolean): Int =
        solarBuffDuration(TORCHES_BASE_DURATION_TICKS, SolarFragmentBuff.RADIANT, hasSolace)

    fun torchesTriggers(
        hasTorches: Boolean,
        source: SolarFragmentCombatSource,
        targetIsCombatant: Boolean
    ): Boolean = hasTorches && source == SolarFragmentCombatSource.SOLAR_POWERED_MELEE && targetIsCombatant

    fun singeingTriggers(hasSingeing: Boolean, appliedScorchToCombatant: Boolean): Boolean =
        hasSingeing && appliedScorchToCombatant

    fun beamsAffectsProjectile(hasBeams: Boolean, source: SolarFragmentCombatSource): Boolean =
        hasBeams && source == SolarFragmentCombatSource.SOLAR_SUPER_PROJECTILE

    fun beamsTrackingProfile(hasBeams: Boolean): SolarSuperTrackingProfile =
        if (hasBeams) {
            SolarSuperTrackingProfile(
                acquisitionRange = MINECRAFT_CALIBRATION_BEAMS_RANGE_BLOCKS,
                minimumForwardDot = MINECRAFT_CALIBRATION_BEAMS_MINIMUM_DOT,
                steeringStrength = MINECRAFT_CALIBRATION_BEAMS_STEERING_STRENGTH
            )
        } else {
            SolarSuperTrackingProfile(
                acquisitionRange = MINECRAFT_CALIBRATION_BASE_SUPER_RANGE_BLOCKS,
                minimumForwardDot = MINECRAFT_CALIBRATION_BASE_SUPER_MINIMUM_DOT,
                steeringStrength = MINECRAFT_CALIBRATION_BASE_SUPER_STEERING_STRENGTH
            )
        }

    fun benevolenceTriggers(
        hasBenevolence: Boolean,
        buff: SolarFragmentBuff,
        recipientIsAlly: Boolean,
        recipientIsSelf: Boolean
    ): Boolean = hasBenevolence && recipientIsAlly && !recipientIsSelf &&
        buff in setOf(SolarFragmentBuff.RADIANT, SolarFragmentBuff.RESTORATION, SolarFragmentBuff.CURE)

    fun benevolenceDuration(hasSolace: Boolean): Int =
        solarBuffDuration(BENEVOLENCE_BASE_DURATION_TICKS, SolarFragmentBuff.RADIANT, hasSolace)

    fun blisteringTriggers(hasBlistering: Boolean, ignitionFinalBlow: Boolean): Boolean =
        hasBlistering && ignitionFinalBlow

    fun charSpreadScorchStacks(hasChar: Boolean, hasAshes: Boolean): Int? {
        if (!hasChar) return null
        return scorchStacks(
            SolarScorchProfile(
                MINECRAFT_CALIBRATION_CHAR_SCORCH_STACKS,
                MINECRAFT_CALIBRATION_CHAR_ASHES_BONUS_STACKS
            ),
            hasAshes
        )
    }

    fun combustionTriggers(
        hasCombustion: Boolean,
        source: SolarFragmentCombatSource,
        finalBlow: Boolean
    ): Boolean = hasCombustion && finalBlow && source.isSolarSuper()

    fun empyreanTriggers(
        hasEmpyrean: Boolean,
        source: SolarFragmentCombatSource,
        finalBlow: Boolean,
        hasRadiantOrRestoration: Boolean
    ): Boolean = hasEmpyrean && finalBlow && hasRadiantOrRestoration && source.isSolarWeaponOrAbility()

    fun empyreanExtendedDuration(currentDurationTicks: Int, tier: SolarCombatantTier): Int {
        val extension = when (tier) {
            SolarCombatantTier.MINOR -> MINECRAFT_CALIBRATION_EMPYREAN_MINOR_EXTENSION_TICKS
            SolarCombatantTier.MAJOR -> MINECRAFT_CALIBRATION_EMPYREAN_MAJOR_EXTENSION_TICKS
            SolarCombatantTier.BOSS -> MINECRAFT_CALIBRATION_EMPYREAN_BOSS_EXTENSION_TICKS
        }
        return (currentDurationTicks.coerceAtLeast(0) + extension).coerceAtMost(EMPYREAN_MAX_DURATION_TICKS)
    }

    fun mercyTriggersOnRevive(hasMercy: Boolean, revivedAlly: Boolean): Boolean =
        hasMercy && revivedAlly

    fun mercyTriggersOnFirespritePickup(hasMercy: Boolean, pickedUpFiresprite: Boolean): Boolean =
        hasMercy && pickedUpFiresprite

    fun resolveTriggers(
        hasResolve: Boolean,
        source: SolarFragmentCombatSource,
        finalBlow: Boolean
    ): Boolean = hasResolve && finalBlow && source == SolarFragmentCombatSource.SOLAR_GRENADE

    fun searingTriggers(hasSearing: Boolean, targetWasScorched: Boolean, finalBlow: Boolean): Boolean =
        hasSearing && targetWasScorched && finalBlow

    fun temperingTriggers(
        hasTempering: Boolean,
        source: SolarFragmentCombatSource,
        finalBlow: Boolean
    ): Boolean = hasTempering && finalBlow && source == SolarFragmentCombatSource.SOLAR_WEAPON

    fun temperingNextStacks(currentStacks: Int, triggered: Boolean): Int {
        val safeCurrent = currentStacks.coerceIn(0, TEMPERING_MAX_STACKS)
        return if (triggered) (safeCurrent + 1).coerceAtMost(TEMPERING_MAX_STACKS) else safeCurrent
    }

    fun temperingHealthBonus(stacks: Int): Int =
        stacks.coerceIn(0, TEMPERING_MAX_STACKS) * MINECRAFT_CALIBRATION_TEMPERING_HEALTH_PER_STACK

    fun wonderTracksFinalBlow(hasWonder: Boolean, ignitionFinalBlow: Boolean): Boolean =
        hasWonder && ignitionFinalBlow

    fun recordWonderIgnitionFinalBlow(
        state: SolarIgnitionMultikillState,
        currentTick: Long,
        hasWonder: Boolean,
        ignitionFinalBlow: Boolean = true
    ): SolarIgnitionMultikillUpdate {
        if (!hasWonder) {
            return SolarIgnitionMultikillUpdate(SolarIgnitionMultikillState(), shouldGenerateOrb = false)
        }
        if (!ignitionFinalBlow) {
            return SolarIgnitionMultikillUpdate(state, shouldGenerateOrb = false)
        }
        if (currentTick < state.nextOrbEligibleTick) {
            return SolarIgnitionMultikillUpdate(
                state.copy(killCount = 0, windowStartedAtTick = null),
                shouldGenerateOrb = false
            )
        }

        val start = state.windowStartedAtTick
        val insideWindow = start != null && currentTick >= start &&
            currentTick - start <= MINECRAFT_CALIBRATION_WONDER_MULTIKILL_WINDOW_TICKS
        val nextCount = if (insideWindow) state.killCount + 1 else 1
        val nextStart = if (insideWindow) start else currentTick
        if (nextCount >= MINECRAFT_CALIBRATION_WONDER_REQUIRED_KILLS) {
            return SolarIgnitionMultikillUpdate(
                SolarIgnitionMultikillState(
                    killCount = 0,
                    windowStartedAtTick = null,
                    nextOrbEligibleTick = currentTick + MINECRAFT_CALIBRATION_WONDER_ORB_COOLDOWN_TICKS
                ),
                shouldGenerateOrb = true
            )
        }
        return SolarIgnitionMultikillUpdate(
            state.copy(killCount = nextCount, windowStartedAtTick = nextStart),
            shouldGenerateOrb = false
        )
    }

    private fun SolarFragmentCombatSource.isSolarSuper(): Boolean =
        this == SolarFragmentCombatSource.SOLAR_SUPER || this == SolarFragmentCombatSource.SOLAR_SUPER_PROJECTILE

    private fun SolarFragmentCombatSource.isSolarWeaponOrAbility(): Boolean = when (this) {
        SolarFragmentCombatSource.SOLAR_WEAPON,
        SolarFragmentCombatSource.SOLAR_GRENADE,
        SolarFragmentCombatSource.SOLAR_POWERED_MELEE,
        SolarFragmentCombatSource.SOLAR_SUPER,
        SolarFragmentCombatSource.SOLAR_SUPER_PROJECTILE,
        SolarFragmentCombatSource.SOLAR_ABILITY -> true
        SolarFragmentCombatSource.UNCHARGED_MELEE,
        SolarFragmentCombatSource.OTHER -> false
    }

    private fun stats(
        weapons: Int = 0,
        health: Int = 0,
        classAbility: Int = 0,
        grenade: Int = 0,
        superStat: Int = 0,
        melee: Int = 0
    ): DestinyStats = DestinyStats(weapons, health, classAbility, grenade, superStat, melee)
}
