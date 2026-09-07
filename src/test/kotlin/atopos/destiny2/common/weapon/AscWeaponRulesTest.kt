package atopos.destiny2.common.weapon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AscWeaponRulesTest {
    @Test
    fun `siva damage grows per attached nanite and caps`() {
        assertEquals(1.0f, AscWeaponRules.sivaDamageMultiplier(0))
        assertEquals(1.24f, AscWeaponRules.sivaDamageMultiplier(3))
        assertEquals(1.48f, AscWeaponRules.sivaDamageMultiplier(6))
        assertEquals(1.48f, AscWeaponRules.sivaDamageMultiplier(99))
    }

    @Test
    fun `signature conversions remain explicit`() {
        assertEquals(6, AscWeaponRules.MEMENTO_ROUNDS)
        assertEquals(1.30f, AscWeaponRules.MEMENTO_DAMAGE_MULTIPLIER)
        assertEquals(3, AscWeaponRules.WHISPER_PRECISION_HITS)
        assertEquals(100, AscWeaponRules.ARC_CONDUCTOR_TICKS)
    }
}
