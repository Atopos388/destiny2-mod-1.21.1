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
}
