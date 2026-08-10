package atopos.destiny2.common.effect

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Top-down footprint for the Thunderclap ground-lift presentation. */
object ThunderclapGroundLiftShape {
    const val FORWARD_LENGTH = 6.0
    const val HALF_WIDTH_AT_FRONT = 2.7
    const val NECK_DISTANCE = 0.55
    const val STABLE_CENTER_HALF_WIDTH = 0.62

    /**
     * Uses the rear half of an ellipse whose widest point is six blocks ahead.
     * This produces the narrow neck and smooth outward flare from the reference.
     */
    fun halfWidth(forwardDistance: Double): Double {
        if (forwardDistance < NECK_DISTANCE || forwardDistance > FORWARD_LENGTH) return 0.0
        val longitudinalRadius = FORWARD_LENGTH - NECK_DISTANCE
        val normalized = (forwardDistance - FORWARD_LENGTH) / longitudinalRadius
        return HALF_WIDTH_AT_FRONT * sqrt((1.0 - normalized * normalized).coerceAtLeast(0.0))
    }

    fun contains(forwardDistance: Double, lateralDistance: Double, cellMargin: Double = 0.24): Boolean {
        val width = halfWidth(forwardDistance)
        return width > 0.0 && abs(lateralDistance) <= width + cellMargin
    }

    /** The centre strip remains real, unchanged terrain; only the two wings tilt. */
    fun shouldTilt(lateralDistance: Double): Boolean =
        abs(lateralDistance) > STABLE_CENTER_HALF_WIDTH

    /** Quiet near the centre, then rapidly exaggerates toward the outer rim. */
    fun sideIntensity(lateralDistance: Double): Double {
        if (!shouldTilt(lateralDistance)) return 0.0
        val linear = ((abs(lateralDistance) - STABLE_CENTER_HALF_WIDTH) /
            (HALF_WIDTH_AT_FRONT - STABLE_CENTER_HALF_WIDTH)).coerceIn(0.0, 1.0)
        return linear.pow(1.45)
    }
}
