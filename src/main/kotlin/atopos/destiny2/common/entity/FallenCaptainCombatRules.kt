// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.entity

import net.minecraft.util.Mth
import kotlin.math.cos
import kotlin.math.sqrt

object FallenCaptainCombatRules {
    const val MAX_HEALTH = 200.0f
    const val LEFT_MELEE_DAMAGE = 6.0f
    const val RIGHT_MELEE_DAMAGE = 7.0f
    const val PELLET_COUNT = 5
    const val CLOSE_PELLET_DAMAGE = 4.0f
    const val RETREAT_HEALTH_RATIO = 0.35f
    const val MELEE_START_EDGE_RANGE = 2.25
    const val MELEE_SWEEP_EDGE_RANGE = 2.75
    const val MELEE_MAX_VERTICAL_GAP = 1.5
    const val MELEE_ARC_DEGREES = 110.0
    private const val REFERENCE_WALK_SPEED = 0.18

    fun shouldRetreat(health: Float, maxHealth: Float): Boolean {
        if (maxHealth <= 0.0f) return false
        return health / maxHealth <= RETREAT_HEALTH_RATIO
    }

    fun pelletDamage(distance: Double): Float {
        return when {
            distance <= 4.0 -> CLOSE_PELLET_DAMAGE
            distance <= 10.0 -> Mth.lerp(((distance - 4.0) / 6.0).toFloat(), 4.0f, 2.5f)
            distance <= 18.0 -> Mth.lerp(((distance - 10.0) / 8.0).toFloat(), 2.5f, 1.0f)
            else -> 0.5f
        }
    }

    fun walkAnimationSpeed(horizontalBlocksPerTick: Double): Double {
        if (horizontalBlocksPerTick <= 1.0E-4) return 1.0
        return (horizontalBlocksPerTick / REFERENCE_WALK_SPEED).coerceIn(0.2, 1.85)
    }

    fun canStartMelee(horizontalEdgeGap: Double, verticalGap: Double, hasLineOfSight: Boolean): Boolean {
        return hasLineOfSight &&
            horizontalEdgeGap <= MELEE_START_EDGE_RANGE &&
            verticalGap <= MELEE_MAX_VERTICAL_GAP
    }

    fun isInsideMeleeSector(
        forwardX: Double,
        forwardZ: Double,
        offsetX: Double,
        offsetZ: Double,
        distance: Double
    ): Boolean {
        if (distance > MELEE_SWEEP_EDGE_RANGE) return false
        val forwardLength = sqrt(forwardX * forwardX + forwardZ * forwardZ)
        val offsetLength = sqrt(offsetX * offsetX + offsetZ * offsetZ)
        if (forwardLength <= 1.0E-6 || offsetLength <= 1.0E-6) return false
        val normalizedDot =
            (forwardX * offsetX + forwardZ * offsetZ) / (forwardLength * offsetLength)
        val minimumDot = cos(Math.toRadians(MELEE_ARC_DEGREES * 0.5))
        return normalizedDot >= minimumDot
    }
}
