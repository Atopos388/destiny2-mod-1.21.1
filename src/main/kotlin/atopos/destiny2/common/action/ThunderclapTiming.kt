// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.action

/** Shared release timing so animation, camera, gameplay contact, and recovery cannot drift apart. */
object ThunderclapTiming {
    const val RELEASE_PLAYBACK_SPEED = 1.5
    const val RELEASE_DURATION_TICKS = 24
    const val RELEASE_CONTACT_TICK = 4

    // The rare hand-drawn branch is an authored impact sequence, not a short
    // grayscale flash. The pose stops only for the opening contact; animation
    // and energy resume while the remaining graphic plates continue.
    const val IMPACT_HIT_STOP_MS = 500L
    const val IMPACT_ABSTRACT_END_MS = 520L
    const val IMPACT_LINE_ART_END_MS = 1_050L
    const val IMPACT_INK_CLOSE_END_MS = 1_210L
    const val IMPACT_SEQUENCE_END_MS = 1_320L

    enum class ImpactPhase {
        ABSTRACT,
        LINE_ART,
        INK_CLOSE,
        RELEASE,
        COMPLETE
    }

    fun impactPhase(elapsedMillis: Double): ImpactPhase = when {
        elapsedMillis < 0.0 -> ImpactPhase.COMPLETE
        elapsedMillis < IMPACT_ABSTRACT_END_MS -> ImpactPhase.ABSTRACT
        elapsedMillis < IMPACT_LINE_ART_END_MS -> ImpactPhase.LINE_ART
        elapsedMillis < IMPACT_INK_CLOSE_END_MS -> ImpactPhase.INK_CLOSE
        elapsedMillis <= IMPACT_SEQUENCE_END_MS -> ImpactPhase.RELEASE
        else -> ImpactPhase.COMPLETE
    }

    fun impactPhaseProgress(elapsedMillis: Double): Float {
        val (start, end) = when (impactPhase(elapsedMillis)) {
            ImpactPhase.ABSTRACT -> 0L to IMPACT_ABSTRACT_END_MS
            ImpactPhase.LINE_ART -> IMPACT_ABSTRACT_END_MS to IMPACT_LINE_ART_END_MS
            ImpactPhase.INK_CLOSE -> IMPACT_LINE_ART_END_MS to IMPACT_INK_CLOSE_END_MS
            ImpactPhase.RELEASE -> IMPACT_INK_CLOSE_END_MS to IMPACT_SEQUENCE_END_MS
            ImpactPhase.COMPLETE -> return 1.0f
        }
        return ((elapsedMillis - start) / (end - start).toDouble()).toFloat().coerceIn(0.0f, 1.0f)
    }
}
