package atopos.destiny2.common.player

enum class GuardianJourneyStage(
    val id: String,
    val displayName: String,
    val dropFloor: Int,
    val rewardCap: Int
) {
    MORTAL("mortal", "凡人", 0, 0),
    AWAKENED("awakened", "机灵唤醒", 100, 120),
    LIGHT_OUTPOST("light_outpost", "光能据点", 110, 140),
    REARMED("rearmed", "重新武装", 125, 160),
    ELEMENTAL_RESONANCE("elemental_resonance", "元素共鸣", 145, 175),
    DIMENSION_BREAKTHROUGH("dimension_breakthrough", "维度突破", 165, 190),
    ENDGAME("endgame", "终局世界", 180, 200);

    companion object {
        fun fromId(id: String): GuardianJourneyStage =
            entries.firstOrNull { it.id == id } ?: MORTAL
    }
}

object GuardianAwakeningRules {
    fun canAwaken(stage: GuardianJourneyStage, creative: Boolean, spectator: Boolean): Boolean =
        stage == GuardianJourneyStage.MORTAL && !creative && !spectator
}
