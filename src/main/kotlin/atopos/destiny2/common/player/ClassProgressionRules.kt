package atopos.destiny2.common.player

object ClassProgressionRules {
    const val COMBAT_SWITCH_LOCK_TICKS = 10 * 20L

    enum class ResonanceResult {
        ALLOWED,
        NOT_AWAKENED,
        MISSING_GHOST_CORE,
        ALREADY_UNLOCKED
    }

    fun resonanceResult(
        stage: GuardianJourneyStage,
        hasGhostCore: Boolean,
        unlockedClasses: Set<DestinyClassType>,
        requestedClass: DestinyClassType
    ): ResonanceResult = when {
        stage == GuardianJourneyStage.MORTAL -> ResonanceResult.NOT_AWAKENED
        !hasGhostCore -> ResonanceResult.MISSING_GHOST_CORE
        requestedClass in unlockedClasses -> ResonanceResult.ALREADY_UNLOCKED
        else -> ResonanceResult.ALLOWED
    }

    fun canSwitchClass(
        requestedClass: DestinyClassType,
        unlockedClasses: Set<DestinyClassType>,
        lastDamageGameTime: Long,
        currentGameTime: Long
    ): Boolean {
        if (requestedClass !in unlockedClasses) return false
        if (lastDamageGameTime == Long.MIN_VALUE) return true
        return currentGameTime - lastDamageGameTime >= COMBAT_SWITCH_LOCK_TICKS
    }
}
