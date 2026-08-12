package atopos.destiny2.common.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrbOfPowerRulesTest {
    @Test
    fun `pickup grants the configured amount of super energy`() {
        assertEquals(42.5f, OrbOfPowerRules.addSuperEnergy(40.0f))
    }

    @Test
    fun `super energy is capped and invalid negative rewards cannot drain it`() {
        assertEquals(100.0f, OrbOfPowerRules.addSuperEnergy(99.0f))
        assertEquals(40.0f, OrbOfPowerRules.addSuperEnergy(40.0f, -5.0f))
    }
}
