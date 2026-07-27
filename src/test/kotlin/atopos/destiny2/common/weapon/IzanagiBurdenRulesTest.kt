package atopos.destiny2.common.weapon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IzanagiBurdenRulesTest {
    @Test
    fun `base shot uses requested damage and precision multiplier`() {
        assertEquals(20.0f, IzanagiBurdenRules.BASE_DAMAGE)
        assertEquals(1.5f, IzanagiBurdenRules.PRECISION_MULTIPLIER)
        assertEquals(30.0f, IzanagiBurdenRules.BASE_DAMAGE * IzanagiBurdenRules.PRECISION_MULTIPLIER)
    }

    @Test
    fun `honed edge compresses two to four loaded rounds`() {
        assertFalse(IzanagiBurdenRules.canLoadHonedEdge(1))
        assertTrue(IzanagiBurdenRules.canLoadHonedEdge(4))
        assertEquals(4.0f, IzanagiBurdenRules.damageMultiplier(4))
        assertEquals(1.6, IzanagiBurdenRules.rangeMultiplier(4), 0.0001)
    }
}
