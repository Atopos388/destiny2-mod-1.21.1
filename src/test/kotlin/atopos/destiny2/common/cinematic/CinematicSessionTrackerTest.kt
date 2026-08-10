package atopos.destiny2.common.cinematic

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class CinematicSessionTrackerTest {
    @Test
    fun `late completion cannot finish replacement session`() {
        val tracker = CinematicSessionTracker()
        val player = UUID.randomUUID()
        val oldSession = UUID.randomUUID()
        val newSession = UUID.randomUUID()

        tracker.start(player, oldSession, 20)
        tracker.start(player, newSession, 20)

        assertFalse(tracker.finish(player, oldSession))
        assertTrue(tracker.isActive(player))
        assertTrue(tracker.finish(player, newSession))
        assertFalse(tracker.isActive(player))
    }

    @Test
    fun `completion and timeout are idempotent`() {
        val tracker = CinematicSessionTracker()
        val player = UUID.randomUUID()
        val session = UUID.randomUUID()

        tracker.start(player, session, 2)
        assertTrue(tracker.tick().isEmpty())
        assertTrue(player in tracker.tick())
        assertFalse(tracker.isActive(player))
        assertFalse(tracker.finish(player, session))
        tracker.clear(player)
        assertFalse(tracker.isActive(player))
    }

    @Test
    fun `phase requests only belong to the current session`() {
        val tracker = CinematicSessionTracker()
        val player = UUID.randomUUID()
        val current = UUID.randomUUID()

        tracker.start(player, current, 20)

        assertTrue(tracker.owns(player, current))
        assertFalse(tracker.owns(player, UUID.randomUUID()))
    }
}
