package atopos.destiny2.common.aspect

import atopos.destiny2.common.weapon.DestinyAmmoType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArcTitanFragmentRulesTest {
    @Test
    fun `catalog contains all sixteen unique official fragment ids`() {
        assertEquals(
            listOf(
                "destiny2-mod:fragment_spark_of_beacons",
                "destiny2-mod:fragment_spark_of_resistance",
                "destiny2-mod:fragment_spark_of_momentum",
                "destiny2-mod:fragment_spark_of_shock",
                "destiny2-mod:fragment_spark_of_ions",
                "destiny2-mod:fragment_spark_of_discharge",
                "destiny2-mod:fragment_spark_of_frequency",
                "destiny2-mod:fragment_spark_of_focus",
                "destiny2-mod:fragment_spark_of_recharge",
                "destiny2-mod:fragment_spark_of_magnitude",
                "destiny2-mod:fragment_spark_of_amplitude",
                "destiny2-mod:fragment_spark_of_feedback",
                "destiny2-mod:fragment_spark_of_volts",
                "destiny2-mod:fragment_spark_of_brilliance",
                "destiny2-mod:fragment_spark_of_haste",
                "destiny2-mod:fragment_spark_of_instinct"
            ),
            ArcTitanFragmentRules.ALL_FRAGMENT_IDS
        )
        assertEquals(16, ArcTitanFragmentRules.ALL_FRAGMENT_IDS.toSet().size)
        assertTrue(ArcTitanFragmentRules.ALL_FRAGMENT_IDS.all { it.startsWith("destiny2-mod:fragment_spark_of_") })
    }

    @Test
    fun `beacons requires amplified Arc special or heavy final blow`() {
        assertTrue(ArcTitanFragmentRules.shouldTriggerBeacons(true, true, true, DestinyAmmoType.SPECIAL))
        assertTrue(ArcTitanFragmentRules.shouldTriggerBeacons(true, true, true, DestinyAmmoType.HEAVY))
        assertFalse(ArcTitanFragmentRules.shouldTriggerBeacons(true, true, true, DestinyAmmoType.PRIMARY))
        assertFalse(ArcTitanFragmentRules.shouldTriggerBeacons(true, false, true, DestinyAmmoType.SPECIAL))
        assertFalse(ArcTitanFragmentRules.shouldTriggerBeacons(false, true, true, DestinyAmmoType.SPECIAL))
    }

    @Test
    fun `resistance starts at three hostiles and grants official melee stat`() {
        assertFalse(ArcTitanFragmentRules.isSurrounded(2))
        assertTrue(ArcTitanFragmentRules.isSurrounded(3))
        assertEquals(1.0f, ArcTitanFragmentRules.resistanceIncomingDamageMultiplier(2))
        assertEquals(0.75f, ArcTitanFragmentRules.resistanceIncomingDamageMultiplier(3))
        assertEquals(10, ArcTitanFragmentRules.staticStatBonuses(setOf(ArcTitanFragmentRules.SPARK_OF_RESISTANCE)).melee)
    }

    @Test
    fun `momentum requires sliding over special or heavy ammo`() {
        assertTrue(ArcTitanFragmentRules.shouldTriggerMomentum(true, true, true))
        assertFalse(ArcTitanFragmentRules.shouldTriggerMomentum(true, false, true))
        assertFalse(ArcTitanFragmentRules.shouldTriggerMomentum(true, true, false))
        assertEquals(1, ArcTitanFragmentRules.momentumBoltChargeGain(true))
        assertEquals(0, ArcTitanFragmentRules.momentumBoltChargeGain(false))
    }

    @Test
    fun `shock only jolts after accepted Arc grenade damage and penalizes grenade stat`() {
        assertTrue(ArcTitanFragmentRules.shouldApplyShock(true, true, true))
        assertFalse(ArcTitanFragmentRules.shouldApplyShock(true, false, true))
        assertFalse(ArcTitanFragmentRules.shouldApplyShock(true, true, false))
        assertEquals(-10, ArcTitanFragmentRules.staticStatBonuses(setOf(ArcTitanFragmentRules.SPARK_OF_SHOCK)).grenade)
    }

    @Test
    fun `ions accepts jolted or Bolt Charged defeats after cooldown`() {
        assertTrue(ArcTitanFragmentRules.shouldSpawnIonicTraceFromIons(true, true, false, 100, 100))
        assertTrue(ArcTitanFragmentRules.shouldSpawnIonicTraceFromIons(true, false, true, 100, 100))
        assertFalse(ArcTitanFragmentRules.shouldSpawnIonicTraceFromIons(true, false, false, 100, 0))
        assertFalse(ArcTitanFragmentRules.shouldSpawnIonicTraceFromIons(true, true, false, 99, 100))
    }

    @Test
    fun `discharge uses server supplied roll and official melee penalty`() {
        assertTrue(ArcTitanFragmentRules.shouldSpawnIonicTraceFromDischarge(true, true, 0.0f))
        assertTrue(ArcTitanFragmentRules.shouldSpawnIonicTraceFromDischarge(true, true, 0.1999f))
        assertFalse(ArcTitanFragmentRules.shouldSpawnIonicTraceFromDischarge(true, true, 0.20f))
        assertFalse(ArcTitanFragmentRules.shouldSpawnIonicTraceFromDischarge(true, true, -0.01f))
        assertFalse(ArcTitanFragmentRules.shouldSpawnIonicTraceFromDischarge(true, false, 0.0f))
        assertEquals(-10, ArcTitanFragmentRules.staticStatBonuses(setOf(ArcTitanFragmentRules.SPARK_OF_DISCHARGE)).melee)
    }

    @Test
    fun `frequency requires accepted direct melee and Amplified improves handling`() {
        assertTrue(ArcTitanFragmentRules.shouldTriggerFrequency(true, true, true))
        assertFalse(ArcTitanFragmentRules.shouldTriggerFrequency(true, false, true))
        assertFalse(ArcTitanFragmentRules.shouldTriggerFrequency(true, true, false))
        assertEquals(1.0f, ArcTitanFragmentRules.frequencyReloadMultiplier(false, true))
        assertEquals(0.70f, ArcTitanFragmentRules.frequencyReloadMultiplier(true, false))
        assertEquals(0.60f, ArcTitanFragmentRules.frequencyReloadMultiplier(true, true))
        assertEquals(0.75f, ArcTitanFragmentRules.frequencyStabilityMultiplier(true, false))
        assertEquals(0.65f, ArcTitanFragmentRules.frequencyStabilityMultiplier(true, true))
        assertEquals(4, ArcTitanFragmentRules.modifiedBoltChargeGain(2, true, true))
        assertEquals(2, ArcTitanFragmentRules.modifiedBoltChargeGain(2, true, false))
    }

    @Test
    fun `focus primes after one second and carries all three official penalties`() {
        assertFalse(ArcTitanFragmentRules.canPrimeFocus(19))
        assertTrue(ArcTitanFragmentRules.canPrimeFocus(20))
        assertNull(ArcTitanFragmentRules.focusWindowExpiresAt(100, 19))
        assertEquals(180L, ArcTitanFragmentRules.focusWindowExpiresAt(100, 20))
        assertEquals(1, ArcTitanFragmentRules.focusExtraClassCooldownTicks(true))
        val stats = ArcTitanFragmentRules.staticStatBonuses(setOf(ArcTitanFragmentRules.SPARK_OF_FOCUS))
        assertEquals(-10, stats.weapons)
        assertEquals(-10, stats.health)
        assertEquals(-10, stats.classAbility)
    }

    @Test
    fun `recharge latches on shield break and stays until shield is full`() {
        assertFalse(ArcTitanFragmentRules.nextRechargeLatched(false, 5.0f, 10.0f))
        assertTrue(ArcTitanFragmentRules.nextRechargeLatched(false, 0.0f, 10.0f))
        assertTrue(ArcTitanFragmentRules.nextRechargeLatched(true, 4.0f, 10.0f))
        assertFalse(ArcTitanFragmentRules.nextRechargeLatched(true, 10.0f, 10.0f))
        assertFalse(ArcTitanFragmentRules.nextRechargeLatched(true, 0.0f, 0.0f))
        assertEquals(1, ArcTitanFragmentRules.rechargeExtraCooldownTicks(true))
    }

    @Test
    fun `magnitude extends pulse grenade from seven to ten pulses`() {
        assertEquals(7, ArcTitanFragmentRules.pulseGrenadePulses(false))
        assertEquals(10, ArcTitanFragmentRules.pulseGrenadePulses(true))
    }

    @Test
    fun `amplitude generates an orb on two rapid amplified final blows and enforces cooldown`() {
        val first = ArcTitanFragmentRules.recordAmplitudeFinalBlow(
            hasFragment = true,
            isAmplified = true,
            currentTick = 100,
            previous = ArcTitanFragmentRules.AmplitudeState()
        )
        assertFalse(first.shouldSpawnOrb)
        assertEquals(1, first.state.finalBlows)

        val second = ArcTitanFragmentRules.recordAmplitudeFinalBlow(true, true, 180, first.state)
        assertTrue(second.shouldSpawnOrb)
        assertEquals(380L, second.state.nextOrbEligibleTick)

        val blocked = ArcTitanFragmentRules.recordAmplitudeFinalBlow(true, true, 200, second.state)
        assertFalse(blocked.shouldSpawnOrb)
        assertEquals(0, blocked.state.finalBlows)

        val expiredWindow = ArcTitanFragmentRules.recordAmplitudeFinalBlow(
            true,
            true,
            500,
            ArcTitanFragmentRules.AmplitudeState(finalBlows = 1, windowExpiresAt = 499)
        )
        assertFalse(expiredWindow.shouldSpawnOrb)
        assertEquals(1, expiredWindow.state.finalBlows)
    }

    @Test
    fun `feedback requires real hostile melee damage and grants official health stat`() {
        assertTrue(ArcTitanFragmentRules.shouldTriggerFeedback(true, true, 1.0f))
        assertFalse(ArcTitanFragmentRules.shouldTriggerFeedback(true, true, 0.0f))
        assertFalse(ArcTitanFragmentRules.shouldTriggerFeedback(true, false, 1.0f))
        assertEquals(1.75f, ArcTitanFragmentRules.feedbackMeleeDamageMultiplier(true))
        assertEquals(1.0f, ArcTitanFragmentRules.feedbackMeleeDamageMultiplier(false))
        assertEquals(10, ArcTitanFragmentRules.staticStatBonuses(setOf(ArcTitanFragmentRules.SPARK_OF_FEEDBACK)).health)
    }

    @Test
    fun `volts requires a finisher and grants class stat and Bolt Charge`() {
        assertTrue(ArcTitanFragmentRules.shouldTriggerVolts(true, true))
        assertFalse(ArcTitanFragmentRules.shouldTriggerVolts(true, false))
        assertEquals(1, ArcTitanFragmentRules.voltsBoltChargeGain(true))
        assertEquals(0, ArcTitanFragmentRules.voltsBoltChargeGain(false))
        assertEquals(10, ArcTitanFragmentRules.staticStatBonuses(setOf(ArcTitanFragmentRules.SPARK_OF_VOLTS)).classAbility)
    }

    @Test
    fun `brilliance requires precision final blow against blinded target and grants super stat`() {
        assertTrue(ArcTitanFragmentRules.shouldTriggerBrilliance(true, true, true))
        assertFalse(ArcTitanFragmentRules.shouldTriggerBrilliance(true, false, true))
        assertFalse(ArcTitanFragmentRules.shouldTriggerBrilliance(true, true, false))
        assertEquals(10, ArcTitanFragmentRules.staticStatBonuses(setOf(ArcTitanFragmentRules.SPARK_OF_BRILLIANCE)).superStat)
    }

    @Test
    fun `haste only adds calibrated health while sprinting`() {
        assertEquals(30, ArcTitanFragmentRules.hasteHealthStatBonus(true, true))
        assertEquals(0, ArcTitanFragmentRules.hasteHealthStatBonus(true, false))
        assertEquals(0, ArcTitanFragmentRules.hasteHealthStatBonus(false, true))
    }

    @Test
    fun `instinct requires hostile damage while critically wounded and respects cooldown`() {
        assertTrue(ArcTitanFragmentRules.shouldTriggerInstinct(true, true, true, 1.0f, 100, 100))
        assertFalse(ArcTitanFragmentRules.shouldTriggerInstinct(true, false, true, 1.0f, 100, 0))
        assertFalse(ArcTitanFragmentRules.shouldTriggerInstinct(true, true, false, 1.0f, 100, 0))
        assertFalse(ArcTitanFragmentRules.shouldTriggerInstinct(true, true, true, 0.0f, 100, 0))
        assertFalse(ArcTitanFragmentRules.shouldTriggerInstinct(true, true, true, 1.0f, 99, 100))
        assertEquals(300L, ArcTitanFragmentRules.INSTINCT_COOLDOWN_TICKS)
        assertEquals(5.0, ArcTitanFragmentRules.INSTINCT_RADIUS)
        assertEquals(6.0f, ArcTitanFragmentRules.INSTINCT_DAMAGE)
    }

    @Test
    fun `all official static Arc modifiers combine without losing signs`() {
        val stats = ArcTitanFragmentRules.staticStatBonuses(ArcTitanFragmentRules.ALL_FRAGMENT_IDS)
        assertEquals(-10, stats.weapons)
        assertEquals(0, stats.health)
        assertEquals(0, stats.classAbility)
        assertEquals(-10, stats.grenade)
        assertEquals(10, stats.superStat)
        assertEquals(0, stats.melee)
        assertNotNull(stats)
    }
}
