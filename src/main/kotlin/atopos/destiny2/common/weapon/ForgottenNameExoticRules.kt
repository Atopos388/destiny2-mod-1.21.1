package atopos.destiny2.common.weapon

import kotlin.math.ceil

object ForgottenNameExoticRules {
    const val MAX_ECHO_STACKS = 6
    const val ECHO_DURATION_TICKS = 25
    const val MARK_DURATION_TICKS = 8 * 20
    const val MAX_MARKED_TARGETS = 5
    const val REMNANT_RADIUS = 8.0
    const val PRECISION_BONUS_PER_STACK = 0.25f / MAX_ECHO_STACKS
    const val MARKED_DAMAGE_BONUS = 0.15f
    const val AIM_ASSIST_VALUE_PER_STACK = 5
    const val MAX_AIM_ASSIST_VALUE = 25

    fun clampStacks(stacks: Int): Int = stacks.coerceIn(0, MAX_ECHO_STACKS)

    fun precisionBonusMultiplier(stacks: Int): Float =
        1.0f + clampStacks(stacks) * PRECISION_BONUS_PER_STACK

    fun reloadTicks(baseTicks: Int, stacks: Int): Int {
        val multiplier = if (clampStacks(stacks) >= MAX_ECHO_STACKS) 0.85f else 1.0f
        return ceil(baseTicks.coerceAtLeast(1) * multiplier).toInt().coerceAtLeast(1)
    }

    fun aimAssistValue(stacks: Int): Int =
        (clampStacks(stacks) * AIM_ASSIST_VALUE_PER_STACK).coerceAtMost(MAX_AIM_ASSIST_VALUE)

    fun aimAssistConeDegrees(stacks: Int): Double =
        1.5 * (1.0 - clampStacks(stacks).toDouble() / MAX_ECHO_STACKS * 0.10)

    fun aimCorrectionBlend(stacks: Int): Double =
        clampStacks(stacks).toDouble() / MAX_ECHO_STACKS * 0.25

    fun projectileInaccuracy(baseInaccuracy: Float, stacks: Int): Float =
        baseInaccuracy * (1.0f - clampStacks(stacks).toFloat() / MAX_ECHO_STACKS * 0.10f)

    fun markedDamageMultiplier(marked: Boolean): Float = if (marked) 1.0f + MARKED_DAMAGE_BONUS else 1.0f

    fun shouldTriggerNameless(stacksBeforeHit: Int, precisionHit: Boolean, killed: Boolean): Boolean =
        precisionHit && killed && clampStacks(stacksBeforeHit) >= MAX_ECHO_STACKS

    fun remainingMarkSlots(activeMarks: Int): Int =
        (MAX_MARKED_TARGETS - activeMarks.coerceAtLeast(0)).coerceAtLeast(0)
}
