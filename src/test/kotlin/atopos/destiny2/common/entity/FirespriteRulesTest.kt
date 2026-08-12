package atopos.destiny2.common.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FirespriteRulesTest {
    @Test
    fun `pickup remains delayed and close range`() {
        assertEquals(10, FirespriteRules.PICKUP_DELAY_TICKS)
        assertTrue(FirespriteRules.PICKUP_RADIUS in 0.5..1.0)
    }

    @Test
    fun `world pickup does not expire earlier than old item entity`() {
        assertEquals(5 * 60 * 20, FirespriteRules.LIFETIME_TICKS)
    }
}
