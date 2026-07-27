package atopos.destiny2.common.weapon

import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.registries.BuiltInRegistries
import java.util.UUID

object WeaponHudSync {
    private val lastSent = mutableMapOf<UUID, WeaponHudStatus>()
    private var ticks = 0

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            ticks++
            if (ticks % 2 != 0) return@register
            server.playerList.players.forEach { sync(it) }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> lastSent.remove(handler.player.uuid) }
    }

    fun syncNow(player: net.minecraft.server.level.ServerPlayer) {
        sync(player, force = true)
    }

    private fun sync(player: net.minecraft.server.level.ServerPlayer, force: Boolean = false) {
        val stack = player.mainHandItem
        val weapon = stack.item as? DestinyRangedWeapon
        val status = weapon?.weaponHudStatus(player, stack)?.copy(
            weaponId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        ) ?: WeaponHudStatus.INACTIVE
        if (!force && lastSent[player.uuid] == status) return
        lastSent[player.uuid] = status
        ServerPlayNetworking.send(player, DestinyNetworking.SyncWeaponStatePayload.from(status))
    }
}
