package atopos.destiny2.common.weapon

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.acos

class WeaponAccuracyRulesTest {
    private val profile = WeaponAccuracyProfile(
        hipBaseDegrees = 0.6f,
        aimedBaseDegrees = 0.05f,
        movingPenaltyDegrees = 0.2f,
        airbornePenaltyDegrees = 1.0f,
        bloomPerShotDegrees = 0.1f,
        maxBloomDegrees = 0.3f,
        settleDelayTicks = 2,
        bloomDecayPerTick = 0.05f
    )

    @Test
    fun `ads tightens the resting cone while movement and air widen it`() {
        val hip = WeaponAccuracyRuntime.advance(profile, 0.0f, Long.MAX_VALUE, 0.0f, 0.0f, false)
        val aimed = WeaponAccuracyRuntime.advance(profile, 0.0f, Long.MAX_VALUE, 1.0f, 0.0f, false)
        val airborne = WeaponAccuracyRuntime.advance(profile, 0.0f, Long.MAX_VALUE, 1.0f, 1.0f, true)

        assertEquals(0.6f, hip.degrees, 0.0001f)
        assertEquals(0.05f, aimed.degrees, 0.0001f)
        assertTrue(airborne.degrees > hip.degrees)
    }

    @Test
    fun `bloom caps and settles only after its delay`() {
        var bloom = 0.0f
        repeat(10) {
            bloom = WeaponAccuracyRuntime.advance(profile, bloom, 0, 1.0f, 0.0f, false).bloomAfterShot
        }
        assertEquals(0.3f, bloom, 0.0001f)
        assertEquals(0.3f, WeaponAccuracyRuntime.recoveredBloom(profile, bloom, 2), 0.0001f)
        assertEquals(0.2f, WeaponAccuracyRuntime.recoveredBloom(profile, bloom, 4), 0.0001f)
        assertEquals(0.0f, WeaponAccuracyRuntime.recoveredBloom(profile, bloom, 100), 0.0001f)
    }

    @Test
    fun `sampled direction is normalized and stays inside the cone`() {
        val forward = Vec3(0.0, 0.0, 1.0)
        val cone = 1.25f
        val spread = WeaponAccuracyRuntime.spread(forward, cone, 1.0f, 0.37f)
        val angle = Math.toDegrees(acos(spread.dot(forward).coerceIn(-1.0, 1.0)))

        assertEquals(1.0, spread.length(), 0.000001)
        assertTrue(angle <= cone + 0.0001)
    }
}
