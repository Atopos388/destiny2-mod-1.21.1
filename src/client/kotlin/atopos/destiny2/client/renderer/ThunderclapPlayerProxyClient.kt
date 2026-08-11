// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.DestinyEntities
import atopos.destiny2.common.entity.ThunderclapPlayerProxyEntity
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import java.util.UUID

object ThunderclapPlayerProxyClient {
    private val proxies = mutableMapOf<UUID, ThunderclapPlayerProxyEntity>()
    private var nextClientEntityId = -2_000_000
    private var releaseHitStopUntilNanos = 0L

    fun register() {
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear() }
    }

    fun play(
        player: AbstractClientPlayer,
        phase: ThunderclapPlayerProxyEntity.Phase,
        durationTicks: Int
    ): Boolean {
        val level = player.level() as? ClientLevel ?: return false
        proxies.remove(player.uuid)?.discard()
        val proxy = ThunderclapPlayerProxyEntity(DestinyEntities.THUNDERCLAP_PLAYER_PROXY, level)
        proxy.id = nextClientEntityId--
        // Capture once per phase. Camera orbit during charge must not rotate the authored pose;
        // the release action creates a new proxy after the server samples the live view direction.
        proxy.configure(player.uuid, phase, durationTicks, player.yRot)
        if (phase == ThunderclapPlayerProxyEntity.Phase.RELEASE) {
            proxy.beginVisualHitStop(releaseHitStopUntilNanos)
        }
        proxy.setPos(player.x, player.y, player.z)
        level.addEntity(proxy)
        proxies[player.uuid] = proxy
        return true
    }

    fun beginReleaseHitStop(durationMillis: Long) {
        releaseHitStopUntilNanos = System.nanoTime() + durationMillis.coerceAtLeast(1L) * 1_000_000L
        proxies.values
            .filter { it.phase == ThunderclapPlayerProxyEntity.Phase.RELEASE }
            .forEach { it.beginVisualHitStop(releaseHitStopUntilNanos) }
    }

    fun isReplacing(playerId: UUID): Boolean {
        val proxy = proxies[playerId] ?: return false
        if (proxy.isRemoved) {
            proxies.remove(playerId)
            return false
        }
        return true
    }

    /** Removes the visual stand-in before a local camera can return inside it. */
    fun stop(playerId: UUID) {
        proxies.remove(playerId)?.discard()
    }

    private fun clear() {
        proxies.values.forEach { it.discard() }
        proxies.clear()
        releaseHitStopUntilNanos = 0L
    }
}
