package atopos.destiny2.common.weapon

import net.minecraft.util.Mth
import kotlin.math.pow

/** Frame-rate-independent recoil sampling and Destiny-style stat shaping. */
object WeaponRecoilMath {
    data class Shot(
        val pitch: Float,
        val yaw: Float,
        val kickDurationMs: Int,
        val recoverDurationMs: Int
    )

    fun sampleShot(
        profile: WeaponRecoilProfile,
        pitchRoll: Float,
        yawRoll: Float,
        sequenceIndex: Int = 0
    ): Shot {
        val stability = (profile.stability / 100.0f).coerceIn(0.0f, 1.0f)
        val direction = (profile.recoilDirection / 100.0f).coerceIn(0.0f, 1.0f)
        val stabilityScale = Mth.lerp(stability.pow(0.7f), 1.12f, 0.72f)
        val pitch = Mth.lerp(
            pitchRoll.coerceIn(0.0f, 1.0f),
            minOf(profile.pitchMin, profile.pitchMax),
            maxOf(profile.pitchMin, profile.pitchMax)
        ) * stabilityScale

        val yawCenter = (profile.yawMin + profile.yawMax) * 0.5f
        val yawHalfRange = kotlin.math.abs(profile.yawMax - profile.yawMin) * 0.5f
        val randomYaw = yawRoll.coerceIn(0.0f, 1.0f) * 2.0f - 1.0f
        val yaw = if (profile.yawPattern.isEmpty()) {
            val directionScale = Mth.lerp(direction, 1.0f, 0.25f)
            (
                yawCenter +
                    randomYaw * yawHalfRange * directionScale
                ) * stabilityScale
        } else {
            // Destiny's deterministic recoil keeps the sustained path recognizable while
            // recoil direction compresses horizontal spacing. Accuracy adds only a small
            // per-shot deviation instead of replacing the path with a random walk.
            val patternIndex = Math.floorMod(sequenceIndex, profile.yawPattern.size)
            val patternYaw = profile.yawPattern[patternIndex]
            val directionScale = Mth.lerp(direction, 1.10f, 0.65f)
            (
                yawCenter +
                    (patternYaw + randomYaw * PATTERN_RANDOMNESS) * yawHalfRange * directionScale
                ) * stabilityScale
        }
        val recoveryScale = Mth.lerp(stability, 1.12f, 0.78f)
        return Shot(
            pitch = pitch,
            yaw = yaw,
            kickDurationMs = profile.kickDurationMs.coerceAtLeast(1),
            recoverDurationMs = (profile.recoverDurationMs * recoveryScale).toInt().coerceAtLeast(1)
        )
    }

    private const val PATTERN_RANDOMNESS = 0.12f

    /**
     * A fast ease-out kick followed by a smootherstep recovery. Sampling the
     * same elapsed time always returns the same value at 30, 60, or 144 FPS.
     */
    fun offsetFactor(elapsedMs: Long, kickDurationMs: Int, recoverDurationMs: Int): Float {
        if (elapsedMs <= 0L) return 0.0f
        val kick = kickDurationMs.coerceAtLeast(1)
        val recover = recoverDurationMs.coerceAtLeast(1)
        if (elapsedMs <= kick) {
            val t = (elapsedMs.toFloat() / kick).coerceIn(0.0f, 1.0f)
            return 1.0f - (1.0f - t).pow(3)
        }
        val recoveryT = ((elapsedMs - kick).toFloat() / recover).coerceIn(0.0f, 1.0f)
        val smooth = recoveryT * recoveryT * recoveryT *
            (recoveryT * (recoveryT * 6.0f - 15.0f) + 10.0f)
        return 1.0f - smooth
    }
}
