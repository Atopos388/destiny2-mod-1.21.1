package atopos.destiny2.common.cinematic

import java.util.UUID

/**
 * Server-side ownership for one active cinematic per player.
 *
 * Session ids make completion idempotent: a late acknowledgement from an old
 * cinematic can never finish a newer one. The timeout is only a safety valve;
 * normal completion is acknowledged by the client.
 */
class CinematicSessionTracker {
    private data class Entry(val sessionId: UUID, var remainingTicks: Int)

    private val active = mutableMapOf<UUID, Entry>()

    fun start(playerId: UUID, sessionId: UUID, timeoutTicks: Int) {
        active[playerId] = Entry(sessionId, timeoutTicks.coerceAtLeast(1))
    }

    fun finish(playerId: UUID, sessionId: UUID): Boolean {
        val entry = active[playerId] ?: return false
        if (entry.sessionId != sessionId) return false
        active.remove(playerId)
        return true
    }

    fun isActive(playerId: UUID): Boolean = playerId in active

    fun clear(playerId: UUID) {
        active.remove(playerId)
    }

    fun tick(): Set<UUID> {
        val expired = linkedSetOf<UUID>()
        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val (playerId, entry) = iterator.next()
            entry.remainingTicks--
            if (entry.remainingTicks <= 0) {
                iterator.remove()
                expired += playerId
            }
        }
        return expired
    }
}
