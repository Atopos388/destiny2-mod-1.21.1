package atopos.destiny2.common.weapon

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Server-side ADS timing used by accuracy; client presentation remains independently smooth. */
object WeaponAimRuntime {
    private data class AimState(
        val itemId: String,
        val slot: Int,
        val startedAtTick: Long
    )

    private val states = ConcurrentHashMap<UUID, AimState>()
    private var registered = false

    fun register() {
        if (registered) return
        registered = true
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            clear(handler.player)
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, _ ->
            clear(newPlayer)
        }
    }

    fun setAiming(player: ServerPlayer, requested: Boolean): Boolean {
        if (!requested) {
            states.remove(player.uuid)
            return false
        }
        val stack = player.mainHandItem
        val weapon = stack.item as? DestinyRangedWeapon
        if (weapon?.aimProfile(stack)?.enabled != true) {
            states.remove(player.uuid)
            return false
        }
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        states.compute(player.uuid) { _, current ->
            if (current?.itemId == itemId && current.slot == player.inventory.selected) {
                current
            } else {
                AimState(itemId, player.inventory.selected, player.level().gameTime)
            }
        }
        return true
    }

    fun progress(player: ServerPlayer): Float {
        val state = states[player.uuid] ?: return 0.0f
        val stack = player.mainHandItem
        val weapon = stack.item as? DestinyRangedWeapon ?: return clearAndZero(player)
        val profile = weapon.aimProfile(stack)
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        if (!profile.enabled || state.itemId != itemId || state.slot != player.inventory.selected) {
            return clearAndZero(player)
        }
        val durationTicks = (profile.aimTimeSeconds.coerceAtLeast(0.001f) * 20.0f)
        return ((player.level().gameTime - state.startedAtTick).coerceAtLeast(0L) / durationTicks)
            .coerceIn(0.0f, 1.0f)
    }

    fun clear(player: ServerPlayer) {
        states.remove(player.uuid)
        WeaponAccuracyRuntime.clear(player.uuid)
    }

    private fun clearAndZero(player: ServerPlayer): Float {
        clear(player)
        return 0.0f
    }
}
