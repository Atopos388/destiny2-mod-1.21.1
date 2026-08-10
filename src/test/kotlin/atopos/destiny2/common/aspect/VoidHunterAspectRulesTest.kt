package atopos.destiny2.common.aspect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoidHunterAspectRulesTest {
    @Test
    fun `remnants extends vortex from four to six seconds`() {
        assertEquals(80, VoidHunterAspectRules.vortexDuration(hasRemnants = false))
        assertEquals(120, VoidHunterAspectRules.vortexDuration(hasRemnants = true))
    }

    @Test
    fun `undermining exclusively controls grenade weaken`() {
        assertFalse(VoidHunterAspectRules.shouldApplyGrenadeWeaken(hasUndermining = false))
        assertTrue(VoidHunterAspectRules.shouldApplyGrenadeWeaken(hasUndermining = true))
    }

    @Test
    fun `provision only triggers for equipped grenade damage after throttle`() {
        assertFalse(
            VoidHunterAspectRules.canTriggerProvision(
                VoidAbilitySource.GRENADE,
                hasProvision = false,
                currentTick = 100,
                nextEligibleTick = 0
            )
        )
        assertFalse(
            VoidHunterAspectRules.canTriggerProvision(
                VoidAbilitySource.SUPER,
                hasProvision = true,
                currentTick = 100,
                nextEligibleTick = 0
            )
        )
        assertFalse(
            VoidHunterAspectRules.canTriggerProvision(
                VoidAbilitySource.GRENADE,
                hasProvision = true,
                currentTick = 99,
                nextEligibleTick = 100
            )
        )
        assertTrue(
            VoidHunterAspectRules.canTriggerProvision(
                VoidAbilitySource.GRENADE,
                hasProvision = true,
                currentTick = 100,
                nextEligibleTick = 100
            )
        )
    }

    @Test
    fun `successful hunt caps at three stacks and improves handling`() {
        assertEquals(1.0f, VoidHunterAspectRules.successfulHuntReloadMultiplier(0))
        assertEquals(0.90f, VoidHunterAspectRules.successfulHuntReloadMultiplier(1))
        assertEquals(0.75f, VoidHunterAspectRules.successfulHuntReloadMultiplier(3))
        assertEquals(0.75f, VoidHunterAspectRules.successfulHuntReloadMultiplier(99))

        assertEquals(1.0f, VoidHunterAspectRules.successfulHuntStabilityMultiplier(0))
        assertEquals(0.94f, VoidHunterAspectRules.successfulHuntStabilityMultiplier(1))
        assertEquals(0.82f, VoidHunterAspectRules.successfulHuntStabilityMultiplier(3))
        assertEquals(0.82f, VoidHunterAspectRules.successfulHuntStabilityMultiplier(99))
    }

    @Test
    fun `trappers ambush scales with void buff and class stat`() {
        val base = VoidHunterAspectRules.trappersAmbushDamage(classStat = 0, hasVoidBuff = false)
        val buffed = VoidHunterAspectRules.trappersAmbushDamage(classStat = 0, hasVoidBuff = true)
        val specialized = VoidHunterAspectRules.trappersAmbushDamage(classStat = 200, hasVoidBuff = true)

        assertEquals(11.0f, base)
        assertTrue(buffed > base)
        assertTrue(specialized > buffed)
        assertEquals(4.25, VoidHunterAspectRules.trappersAmbushRadius(hasVoidBuff = false))
        assertEquals(6.0, VoidHunterAspectRules.trappersAmbushRadius(hasVoidBuff = true))
    }
}
