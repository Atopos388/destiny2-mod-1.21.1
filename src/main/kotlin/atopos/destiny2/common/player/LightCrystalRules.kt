package atopos.destiny2.common.player

import kotlin.math.max
import kotlin.math.min

object LightCrystalRules {
    enum class CreationResult {
        NOT_AWAKENED,
        WRONG_BLOCK,
        NO_SUNLIGHT,
        ALLOWED
    }

    fun creationResult(
        awakened: Boolean,
        ordinaryAmethystBlock: Boolean,
        daytime: Boolean,
        skyVisible: Boolean
    ): CreationResult = when {
        !ordinaryAmethystBlock -> CreationResult.WRONG_BLOCK
        !awakened -> CreationResult.NOT_AWAKENED
        !daytime || !skyVisible -> CreationResult.NO_SUNLIGHT
        else -> CreationResult.ALLOWED
    }
}

object LightCrystalAnimation {
    private const val OUTER_RADIUS = 1.45
    private const val INNER_RADIUS = 0.12

    fun ringRadius(elapsedTicks: Int, totalTicks: Int): Double {
        if (totalTicks <= 0) return INNER_RADIUS
        val linear = min(1.0, max(0.0, elapsedTicks.toDouble() / totalTicks.toDouble()))
        val eased = linear * linear * (3.0 - 2.0 * linear)
        return OUTER_RADIUS + (INNER_RADIUS - OUTER_RADIUS) * eased
    }
}
