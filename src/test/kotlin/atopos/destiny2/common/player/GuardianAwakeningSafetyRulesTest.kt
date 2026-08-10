package atopos.destiny2.common.player

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuardianAwakeningSafetyRulesTest {
    @Test
    fun `only unnamed non-elite monsters are removed`() {
        assertTrue(GuardianAwakeningSafetyRules.shouldRemoveOrdinaryMonster(false, false))
        assertFalse(GuardianAwakeningSafetyRules.shouldRemoveOrdinaryMonster(true, false))
        assertFalse(GuardianAwakeningSafetyRules.shouldRemoveOrdinaryMonster(false, true))
        assertFalse(GuardianAwakeningSafetyRules.shouldRemoveOrdinaryMonster(true, true))
    }

    @Test
    fun `safe search never checks the elite encounter origin`() {
        val offsets = GuardianAwakeningSafetyRules.safeSearchOffsets()

        assertTrue(offsets.isNotEmpty())
        assertTrue(offsets.none { it.x == 0 && it.z == 0 })
        assertTrue(offsets.all { maxOf(kotlin.math.abs(it.x), kotlin.math.abs(it.z)) >= 24 })
    }

    @Test
    fun `fluid evacuation can search nearby dry shore before distant fallback`() {
        val offsets = GuardianAwakeningSafetyRules.safeSearchOffsets(4, 72, 4)

        assertTrue(offsets.none { it.x == 0 && it.z == 0 })
        assertTrue(offsets.any { maxOf(kotlin.math.abs(it.x), kotlin.math.abs(it.z)) == 4 })
        assertTrue(offsets.any { maxOf(kotlin.math.abs(it.x), kotlin.math.abs(it.z)) == 72 })
    }
}
