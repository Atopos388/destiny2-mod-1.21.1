package atopos.destiny2.common.aspect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolarWarlockAspectRulesTest {
    @Test
    fun `catalog exposes exactly the four official aspect ids`() {
        assertEquals(
            linkedSetOf(
                "destiny2-mod:aspect_heat_rises",
                "destiny2-mod:aspect_touch_of_flame",
                "destiny2-mod:aspect_icarus_dash",
                "destiny2-mod:aspect_hellion"
            ),
            SolarWarlockAspectRules.ALL_ASPECT_IDS
        )
    }

    @Test
    fun `heat rises hold starts completes and consumes exactly at threshold`() {
        val started = SolarWarlockAspectRules.updateHeatRisesHold(true, true, true, 100, HeatRisesHoldState())
        assertEquals(100L, started.state.startedAtTick)
        assertFalse(started.shouldConsumeGrenade)

        val early = SolarWarlockAspectRules.updateHeatRisesHold(
            true,
            true,
            true,
            119,
            started.state
        )
        assertFalse(early.shouldConsumeGrenade)

        val completed = SolarWarlockAspectRules.updateHeatRisesHold(
            true,
            true,
            true,
            120,
            started.state
        )
        assertTrue(completed.shouldConsumeGrenade)
        assertTrue(completed.shouldActivateHeatRises)
        assertNull(completed.state.startedAtTick)
    }

    @Test
    fun `heat rises release invalid state and clock rollback cancel without consumption`() {
        val held = HeatRisesHoldState(100)
        listOf(
            SolarWarlockAspectRules.updateHeatRisesHold(true, false, true, 120, held),
            SolarWarlockAspectRules.updateHeatRisesHold(false, true, true, 120, held),
            SolarWarlockAspectRules.updateHeatRisesHold(true, true, false, 120, held)
        ).forEach {
            assertNull(it.state.startedAtTick)
            assertFalse(it.shouldConsumeGrenade)
            assertFalse(it.shouldActivateHeatRises)
        }

        val rollback = SolarWarlockAspectRules.updateHeatRisesHold(true, true, true, 99, held)
        assertEquals(99L, rollback.state.startedAtTick)
        assertFalse(rollback.shouldConsumeGrenade)
        assertNull(SolarWarlockAspectRules.cancelHeatRisesHold().startedAtTick)
    }

    @Test
    fun `heat rises only extends for airborne final blows and respects cap`() {
        assertEquals(
            240L,
            SolarWarlockAspectRules.heatRisesExpiryAfterFinalBlow(true, true, true, true, 100, 200)
        )
        assertEquals(
            200L,
            SolarWarlockAspectRules.heatRisesExpiryAfterFinalBlow(true, true, false, true, 100, 200)
        )
        assertEquals(
            700L,
            SolarWarlockAspectRules.heatRisesExpiryAfterFinalBlow(true, true, true, true, 100, 699)
        )
        assertEquals(
            SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEAT_RISES_MELEE_REFUND_TICKS,
            SolarWarlockAspectRules.heatRisesMeleeEnergyRefundTicks(true, true, true, true)
        )
        assertEquals(0, SolarWarlockAspectRules.heatRisesMeleeEnergyRefundTicks(true, true, false, true))
    }

    @Test
    fun `icarus dash requires airborne control and allows second charge only during heat rises`() {
        val grounded = SolarWarlockAspectRules.attemptIcarusDash(
            true,
            false,
            true,
            false,
            100,
            IcarusDashChargeState()
        )
        assertFalse(grounded.accepted)
        assertFalse(
            SolarWarlockAspectRules.attemptIcarusDash(
                hasAspect = false,
                isAirborne = true,
                canControlMovement = true,
                isHeatRisesActive = true,
                currentTick = 100,
                previous = IcarusDashChargeState()
            ).accepted
        )
        assertFalse(
            SolarWarlockAspectRules.attemptIcarusDash(
                hasAspect = true,
                isAirborne = true,
                canControlMovement = false,
                isHeatRisesActive = true,
                currentTick = 100,
                previous = IcarusDashChargeState()
            ).accepted
        )

        val first = SolarWarlockAspectRules.attemptIcarusDash(
            true,
            true,
            true,
            false,
            100,
            IcarusDashChargeState()
        )
        assertTrue(first.accepted)
        assertEquals(1, first.state.chargesUsed)

        assertFalse(
            SolarWarlockAspectRules.attemptIcarusDash(true, true, true, false, 101, first.state).accepted
        )
        val heatRisesSecond = SolarWarlockAspectRules.attemptIcarusDash(true, true, true, true, 101, first.state)
        assertTrue(heatRisesSecond.accepted)
        assertEquals(2, heatRisesSecond.state.chargesUsed)
        assertFalse(
            SolarWarlockAspectRules.attemptIcarusDash(true, true, true, true, 102, heatRisesSecond.state).accepted
        )
    }

    @Test
    fun `icarus dash charge window resets at cooldown boundary`() {
        val exhausted = IcarusDashChargeState(
            chargesUsed = 2,
            windowExpiresAtTick = 180
        )
        assertFalse(
            SolarWarlockAspectRules.attemptIcarusDash(true, true, true, true, 179, exhausted).accepted
        )
        val reset = SolarWarlockAspectRules.attemptIcarusDash(true, true, true, false, 180, exhausted)
        assertTrue(reset.accepted)
        assertEquals(1, reset.state.chargesUsed)
        assertEquals(260L, reset.state.windowExpiresAtTick)
    }

    @Test
    fun `icarus cure requires rapid airborne weapon or super final blows`() {
        val first = SolarWarlockAspectRules.recordIcarusAirborneFinalBlow(
            true,
            true,
            true,
            IcarusDashFinalBlowSource.WEAPON,
            100,
            IcarusDashMultikillState()
        )
        assertEquals(1, first.state.qualifyingFinalBlows)
        assertFalse(first.shouldApplyCure)

        val invalid = SolarWarlockAspectRules.recordIcarusAirborneFinalBlow(
            true,
            false,
            true,
            IcarusDashFinalBlowSource.SUPER,
            101,
            first.state
        )
        assertEquals(first.state, invalid.state)
        assertFalse(invalid.shouldApplyCure)

        val second = SolarWarlockAspectRules.recordIcarusAirborneFinalBlow(
            true,
            true,
            true,
            IcarusDashFinalBlowSource.SUPER,
            102,
            invalid.state
        )
        assertTrue(second.shouldApplyCure)
        assertEquals(IcarusDashMultikillState(), second.state)

        val expired = SolarWarlockAspectRules.recordIcarusAirborneFinalBlow(
            true,
            true,
            true,
            IcarusDashFinalBlowSource.WEAPON,
            200,
            first.state
        )
        assertEquals(1, expired.state.qualifyingFinalBlows)
        assertFalse(expired.shouldApplyCure)
        assertEquals(
            IcarusDashMultikillState(),
            SolarWarlockAspectRules.recordIcarusAirborneFinalBlow(
                false,
                true,
                true,
                IcarusDashFinalBlowSource.WEAPON,
                102,
                first.state
            ).state
        )
    }

    @Test
    fun `hellion requires successful class ability and valid owner`() {
        assertNull(SolarWarlockAspectRules.summonHellion(false, true, true, 100))
        assertNull(SolarWarlockAspectRules.summonHellion(true, false, true, 100))
        assertNull(SolarWarlockAspectRules.summonHellion(true, true, false, 100))

        val state = SolarWarlockAspectRules.summonHellion(true, true, true, 100)!!
        assertEquals(100L, state.summonedAtTick)
        assertEquals(500L, state.expiresAtTick)
        assertEquals(110L, state.nextShotTick)
        assertTrue(SolarWarlockAspectRules.isHellionActive(state, 100))
        assertFalse(SolarWarlockAspectRules.isHellionActive(state, 500))
    }

    @Test
    fun `hellion target validation enforces hostility visibility dimension and inclusive range`() {
        fun valid(distanceSquared: Double) = SolarWarlockAspectRules.isValidHellionTarget(
            isHostile = true,
            isAlive = true,
            isSpectator = false,
            isSameDimension = true,
            hasLineOfSight = true,
            distanceSquared = distanceSquared
        )

        assertTrue(valid(32.0 * 32.0))
        assertFalse(valid(32.0 * 32.0 + 0.001))
        assertFalse(valid(-1.0))
        assertFalse(valid(Double.NaN))
        assertFalse(
            SolarWarlockAspectRules.isValidHellionTarget(false, true, false, true, true, 1.0)
        )
        assertFalse(
            SolarWarlockAspectRules.isValidHellionTarget(true, true, false, true, false, 1.0)
        )
        assertFalse(
            SolarWarlockAspectRules.isValidHellionTarget(true, true, true, true, true, 1.0)
        )
        assertFalse(
            SolarWarlockAspectRules.isValidHellionTarget(true, true, false, false, true, 1.0)
        )
    }

    @Test
    fun `hellion firing honors initial delay cadence target and expiry`() {
        val state = SolarWarlockAspectRules.summonHellion(true, true, true, 100)!!
        assertFalse(SolarWarlockAspectRules.shouldHellionFire(state, 109, true))
        assertFalse(SolarWarlockAspectRules.shouldHellionFire(state, 110, false))
        assertTrue(SolarWarlockAspectRules.shouldHellionFire(state, 110, true))

        val afterShot = SolarWarlockAspectRules.hellionAfterShot(state, 110)
        assertEquals(140L, afterShot.nextShotTick)
        assertFalse(SolarWarlockAspectRules.shouldHellionFire(afterShot, 139, true))
        assertTrue(SolarWarlockAspectRules.shouldHellionFire(afterShot, 140, true))
        assertFalse(SolarWarlockAspectRules.shouldHellionFire(afterShot, 500, true))
    }

    @Test
    fun `touch of flame maps all four grenade strategies and rejects unsupported grenades`() {
        assertEquals(
            setOf(
                TouchOfFlameEffect.STRONGER_CURE_AND_RESTORATION,
                TouchOfFlameEffect.HEAT_RISES_GRANTS_ALLY_RESTORATION
            ),
            SolarWarlockAspectRules.touchOfFlameStrategy(true, TouchOfFlameGrenadeType.HEALING).effects
        )
        assertEquals(
            setOf(TouchOfFlameEffect.EXTENDED_DURATION, TouchOfFlameEffect.PERIODIC_LAVA_BLOBS),
            SolarWarlockAspectRules.touchOfFlameStrategy(true, TouchOfFlameGrenadeType.SOLAR).effects
        )
        assertEquals(
            setOf(
                TouchOfFlameEffect.INCREASED_TARGET_ACQUISITION_RANGE,
                TouchOfFlameEffect.INCREASED_MAX_TARGETS
            ),
            SolarWarlockAspectRules.touchOfFlameStrategy(true, TouchOfFlameGrenadeType.FIREBOLT).effects
        )
        assertEquals(
            setOf(TouchOfFlameEffect.SECOND_EXPLOSION),
            SolarWarlockAspectRules.touchOfFlameStrategy(true, TouchOfFlameGrenadeType.FUSION).effects
        )
        assertTrue(
            SolarWarlockAspectRules.touchOfFlameStrategy(true, TouchOfFlameGrenadeType.OTHER).effects.isEmpty()
        )
        assertTrue(
            SolarWarlockAspectRules.touchOfFlameStrategy(false, TouchOfFlameGrenadeType.SOLAR).effects.isEmpty()
        )
    }
}
