package atopos.destiny2.common.player

/** Pure progression rules for the universal grenade memory and its legacy variants. */
object GrenadeMemoryRules {
    const val VOID_HUNTER_GRENADE_ID = "destiny2-mod:void_hunter_void_grenade"
    const val ARC_TITAN_GRENADE_ID = "destiny2-mod:arc_titan_pulse_grenade"
    const val SOLAR_WARLOCK_GRENADE_ID = "destiny2-mod:solar_warlock_solar_grenade"

    data class Target(val subclass: DestinySubclassType, val grenadeOptionId: String)

    fun targetFor(subclass: DestinySubclassType): Target = when (subclass) {
        DestinySubclassType.VOID_HUNTER -> Target(subclass, VOID_HUNTER_GRENADE_ID)
        DestinySubclassType.ARC_TITAN -> Target(subclass, ARC_TITAN_GRENADE_ID)
        DestinySubclassType.SOLAR_WARLOCK -> Target(subclass, SOLAR_WARLOCK_GRENADE_ID)
    }

    enum class UseResult {
        ALLOWED,
        NOT_AWAKENED,
        CLASS_LOCKED,
        ALREADY_UNLOCKED
    }

    fun useResult(
        stage: GuardianJourneyStage,
        unlockedClasses: Set<DestinyClassType>,
        unlockedOptions: Set<String>,
        requiredSubclass: DestinySubclassType,
        grenadeOptionId: String
    ): UseResult = when {
        stage == GuardianJourneyStage.MORTAL -> UseResult.NOT_AWAKENED
        requiredSubclass.requiredClass !in unlockedClasses -> UseResult.CLASS_LOCKED
        grenadeOptionId in unlockedOptions -> UseResult.ALREADY_UNLOCKED
        else -> UseResult.ALLOWED
    }

    fun isRegisteredGrenade(requiredSubclass: DestinySubclassType, grenadeOptionId: String): Boolean {
        return DestinySubclassConfigRegistry.definitionFor(requiredSubclass)
            .abilityOptions[AbilitySlot.GRENADE]
            .orEmpty()
            .any { it.id == grenadeOptionId }
    }

    /** Applies the permanent unlock and equips it only when its subclass is active. */
    fun unlock(data: PlayerDestinyData, requiredSubclass: DestinySubclassType, grenadeOptionId: String): Boolean {
        if (!isRegisteredGrenade(requiredSubclass, grenadeOptionId)) return false
        if (
            useResult(
                data.journeyStage,
                data.unlockedClasses,
                data.unlockedSubclassOptions,
                requiredSubclass,
                grenadeOptionId
            ) != UseResult.ALLOWED
        ) return false

        data.unlockedSubclassOptions += grenadeOptionId
        if (data.subclass == requiredSubclass) {
            data.subclassConfig.selectedAbilities[AbilitySlot.GRENADE] = grenadeOptionId
        }
        data.restrictCurrentSubclassConfig()
        return true
    }
}
