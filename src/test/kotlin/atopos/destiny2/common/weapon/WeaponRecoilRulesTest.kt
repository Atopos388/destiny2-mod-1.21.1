package atopos.destiny2.common.weapon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeaponRecoilRulesTest {
    @Test
    fun `curve reaches its peak and recovers without overshoot`() {
        assertEquals(0.0f, WeaponRecoilMath.offsetFactor(0, 50, 250))
        assertEquals(1.0f, WeaponRecoilMath.offsetFactor(50, 50, 250), 0.0001f)

        var previous = 1.0f
        for (elapsed in 60L..300L step 10) {
            val current = WeaponRecoilMath.offsetFactor(elapsed, 50, 250)
            assertTrue(current in 0.0f..1.0f)
            assertTrue(current <= previous + 0.0001f)
            previous = current
        }
        assertEquals(0.0f, WeaponRecoilMath.offsetFactor(300, 50, 250), 0.0001f)
    }

    @Test
    fun `stability and recoil direction reduce the intended axes`() {
        val loose = WeaponRecoilProfile(
            pitchMin = 1.0f,
            pitchMax = 1.0f,
            yawMin = -1.0f,
            yawMax = 1.0f,
            stability = 0.0f,
            recoilDirection = 0.0f
        )
        val stable = loose.copy(stability = 100.0f, recoilDirection = 100.0f)
        val looseShot = WeaponRecoilMath.sampleShot(loose, 0.5f, 1.0f)
        val stableShot = WeaponRecoilMath.sampleShot(stable, 0.5f, 1.0f)

        assertTrue(stableShot.pitch < looseShot.pitch)
        assertTrue(kotlin.math.abs(stableShot.yaw) < kotlin.math.abs(looseShot.yaw))
        assertTrue(stableShot.recoverDurationMs < looseShot.recoverDurationMs)
    }

    @Test
    fun `configured pattern is repeatable with only small per shot deviation`() {
        val profile = WeaponRecoilProfile(
            pitchMin = 1.0f,
            pitchMax = 1.0f,
            yawMin = -1.0f,
            yawMax = 1.0f,
            yawPattern = listOf(1.0f, 0.5f, -0.5f, -1.0f),
            stability = 0.0f,
            recoilDirection = 0.0f
        )

        val first = WeaponRecoilMath.sampleShot(profile, 0.5f, 0.5f, sequenceIndex = 0)
        val second = WeaponRecoilMath.sampleShot(profile, 0.5f, 0.5f, sequenceIndex = 1)
        val repeated = WeaponRecoilMath.sampleShot(profile, 0.5f, 0.5f, sequenceIndex = 4)
        val wrappedNegative = WeaponRecoilMath.sampleShot(profile, 0.5f, 0.5f, sequenceIndex = -1)
        val jittered = WeaponRecoilMath.sampleShot(profile, 0.5f, 1.0f, sequenceIndex = 0)

        assertEquals(first.yaw, repeated.yaw, 0.0001f)
        assertTrue(first.yaw > second.yaw)
        assertTrue(wrappedNegative.yaw < 0.0f)
        assertTrue(jittered.yaw > first.yaw)
        assertTrue(jittered.yaw - first.yaw < first.yaw)
    }
}
