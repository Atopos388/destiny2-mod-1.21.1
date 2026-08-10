package atopos.destiny2.common.aspect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArcTitanFragmentRuntimeTest {
    @Test
    fun `focus activates exactly at sprint threshold and reduces class cooldown`() {
        var sprintTicks = 0
        var activeUntil = Long.MIN_VALUE
        repeat(19) { index ->
            val update = ArcTitanFragmentRuntimeRules.tickFocus(
                true,
                true,
                index.toLong(),
                sprintTicks,
                activeUntil
            )
            sprintTicks = update.sprintTicks
            activeUntil = update.activeUntil
            assertEquals(0, update.classCooldownReductionTicks)
        }

        val triggered = ArcTitanFragmentRuntimeRules.tickFocus(true, true, 19, sprintTicks, activeUntil)
        assertEquals(20, triggered.sprintTicks)
        assertEquals(99L, triggered.activeUntil)
        assertEquals(1, triggered.classCooldownReductionTicks)
    }

    @Test
    fun `focus persists after sprint stops then expires and unequip clears it`() {
        val stopped = ArcTitanFragmentRuntimeRules.tickFocus(true, false, 50, 20, 100)
        assertEquals(0, stopped.sprintTicks)
        assertEquals(100L, stopped.activeUntil)
        assertEquals(1, stopped.classCooldownReductionTicks)

        val expired = ArcTitanFragmentRuntimeRules.tickFocus(true, false, 100, 0, 100)
        assertEquals(Long.MIN_VALUE, expired.activeUntil)
        assertEquals(0, expired.classCooldownReductionTicks)

        val unequipped = ArcTitanFragmentRuntimeRules.tickFocus(false, true, 50, 20, 100)
        assertEquals(0, unequipped.sprintTicks)
        assertEquals(Long.MIN_VALUE, unequipped.activeUntil)
    }

    @Test
    fun `recharge latches on health shield break until shield is fully restored`() {
        val broken = ArcTitanFragmentRuntimeRules.tickRecharge(true, false, 0.0f, 10.0f)
        assertTrue(broken.latched)
        assertEquals(1, broken.grenadeCooldownReductionTicks)
        assertEquals(1, broken.meleeCooldownReductionTicks)

        val recovering = ArcTitanFragmentRuntimeRules.tickRecharge(true, true, 9.99f, 10.0f)
        assertTrue(recovering.latched)

        val full = ArcTitanFragmentRuntimeRules.tickRecharge(true, true, 10.0f, 10.0f)
        assertFalse(full.latched)
        assertEquals(0, full.grenadeCooldownReductionTicks)

        val unequipped = ArcTitanFragmentRuntimeRules.tickRecharge(false, true, 0.0f, 10.0f)
        assertFalse(unequipped.latched)
    }

    @Test
    fun `jolt requires later real damage inside duration and cooldown`() {
        assertTrue(ArcTitanFragmentRuntimeRules.shouldTriggerJoltChain(100, 200, 100, 1.0f, false))
        assertFalse(ArcTitanFragmentRuntimeRules.shouldTriggerJoltChain(100, 200, 101, 1.0f, false))
        assertFalse(ArcTitanFragmentRuntimeRules.shouldTriggerJoltChain(200, 200, 100, 1.0f, false))
        assertFalse(ArcTitanFragmentRuntimeRules.shouldTriggerJoltChain(100, 200, 100, 0.0f, false))
        assertFalse(ArcTitanFragmentRuntimeRules.shouldTriggerJoltChain(100, 200, 100, 1.0f, true))
    }

    @Test
    fun `all fragments have concrete public Arc foundations`() {
        ArcTitanFragmentRules.ALL_FRAGMENT_IDS.forEach { fragmentId ->
            assertTrue(ArcTitanFragmentRuntimeRules.pendingFoundationDependencies(fragmentId).isEmpty())
        }
    }
}
