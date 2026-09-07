package atopos.destiny2.client.renderer

import atopos.destiny2.client.gui.HudTemplateRefreshGate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HudTemplateRefreshGateTest {
    @Test fun `checks at most twice per second regardless of frame rate`() {
        for (fps in listOf(30, 60, 120, 240)) {
            val gate = HudTemplateRefreshGate()
            val checks = (0 until fps * 10).count { gate.shouldCheck(it * 1_000_000_000L / fps) }
            assertEquals(20, checks, "fps=$fps")
        }
    }
    @Test fun `editor invalidation checks immediately`() {
        val gate = HudTemplateRefreshGate()
        assertTrue(gate.shouldCheck(100L))
        assertFalse(gate.shouldCheck(101L))
        gate.reset()
        assertTrue(gate.shouldCheck(102L))
    }
    @Test fun `half second boundary uses monotonic elapsed time`() {
        val gate = HudTemplateRefreshGate()
        assertTrue(gate.shouldCheck(-1_000_000_000L))
        assertFalse(gate.shouldCheck(-500_000_001L))
        assertTrue(gate.shouldCheck(-500_000_000L))
    }
}
