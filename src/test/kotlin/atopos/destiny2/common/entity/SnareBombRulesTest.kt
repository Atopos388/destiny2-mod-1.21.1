package atopos.destiny2.common.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SnareBombRulesTest {
    @Test
    fun `smoke damage increases once per second and reaches a cap`() {
        assertEquals(0.5f, SnareBombRules.damageForExposure(1), 0.0001f)
        assertEquals(0.6f, SnareBombRules.damageForExposure(20), 0.0001f)
        assertEquals(1.2f, SnareBombRules.damageForExposure(140), 0.0001f)
        assertEquals(1.2f, SnareBombRules.damageForExposure(200), 0.0001f)
    }
}
