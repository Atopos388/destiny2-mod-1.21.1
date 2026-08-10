package atopos.destiny2.common.combat

import kotlin.math.cos

/**
 * Pure tuning rules for the uncharged melee fallback.
 *
 * The search cone tightens with distance. Nearby targets are caught
 * generously, while targets at the edge of lunge range still require intent.
 */
object QuickMeleeRules {
    const val BASE_DAMAGE = 6.0f
    const val COOLDOWN_TICKS = 12L
    const val HUNTER_SOUND_VOLUME = 0.2f
    const val ASSIST_RANGE = 3.75
    const val NEAR_ASSIST_HALF_ANGLE_DEGREES = 40.0
    const val FAR_ASSIST_HALF_ANGLE_DEGREES = 22.0
    const val MAX_LUNGE_SPEED = 0.46
    private const val LUNGE_START_DISTANCE = 1.25

    fun candidateScore(distance: Double, lookDot: Double): Double? {
        if (distance <= 0.01 || distance > ASSIST_RANGE) {
            return null
        }
        val halfAngle = assistHalfAngle(distance)
        if (lookDot < cos(Math.toRadians(halfAngle))) return null

        val distanceRatio = (distance / ASSIST_RANGE).coerceIn(0.0, 1.0)
        return lookDot * 1.8 - distanceRatio * 0.4
    }

    fun assistHalfAngle(distance: Double): Double {
        val t = ((distance - LUNGE_START_DISTANCE) /
            (ASSIST_RANGE - LUNGE_START_DISTANCE)).coerceIn(0.0, 1.0)
        return NEAR_ASSIST_HALF_ANGLE_DEGREES +
            (FAR_ASSIST_HALF_ANGLE_DEGREES - NEAR_ASSIST_HALF_ANGLE_DEGREES) * t
    }

    fun lungeSpeed(distance: Double): Double =
        ((distance - LUNGE_START_DISTANCE) * 0.18).coerceIn(0.0, MAX_LUNGE_SPEED)
}
