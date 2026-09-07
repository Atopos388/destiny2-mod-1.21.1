package atopos.destiny2.common.weapon

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.GuardianPowerRuntime
import atopos.destiny2.common.player.PlayerDestinyDataApi
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** Server authority for the three equipped hotbar columns and their nine reserve slots. */
object WeaponLoadoutRuntime {
    private const val INVENTORY_MIN = 0
    private const val INVENTORY_MAX = 35

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            server.playerList.players.forEach(::collectInventoryWeapons)
        }
    }

    fun initializeLegacyLoadout(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        if (data.weaponLoadoutInitialized) return

        DestinyWeaponSlot.entries.forEach { slot ->
            if (DestinyWeaponSlot.forStack(player.inventory.getItem(slot.hotbarIndex)) == slot) return@forEach
            val source = (INVENTORY_MIN..INVENTORY_MAX).firstOrNull { index ->
                index != slot.hotbarIndex &&
                    DestinyWeaponSlot.forStack(player.inventory.getItem(index)) == slot &&
                    DestinyWeaponSlot.entries.none { fixed ->
                        fixed != slot && fixed.hotbarIndex == index &&
                            DestinyWeaponSlot.forStack(player.inventory.getItem(index)) == fixed
                    }
            } ?: return@forEach
            swapInventory(player, source, slot.hotbarIndex)
        }
        data.weaponLoadoutInitialized = true
        player.inventoryMenu.broadcastChanges()
    }

    /** Moves unequipped guns out of vanilla inventory and into their character weapon column. */
    fun collectInventoryWeapons(player: ServerPlayer): Boolean {
        val reserves = PlayerDestinyDataApi.get(player).weaponReserves
        var changed = false
        for (source in INVENTORY_MIN..INVENTORY_MAX) {
            val incoming = player.inventory.getItem(source)
            val slot = DestinyWeaponSlot.forStack(incoming) ?: continue
            val target = slot.hotbarIndex
            if (source == target) continue

            val targetStack = player.inventory.getItem(target)
            if (targetStack.isEmpty) {
                player.inventory.setItem(target, incoming)
                player.inventory.setItem(source, ItemStack.EMPTY)
            } else {
                val reserveIndex = reserves.firstEmpty(slot)
                if (reserveIndex < 0) continue
                reserves.set(slot, reserveIndex, incoming)
                player.inventory.setItem(source, ItemStack.EMPTY)
            }
            if (player.inventory.selected == source) player.inventory.selected = target
            changed = true
        }
        DestinyWeaponSlot.entries.forEach { slot ->
            val target = slot.hotbarIndex
            if (!player.inventory.getItem(target).isEmpty) return@forEach
            val reserveIndex = reserves.stacks(slot).indexOfFirst { !it.isEmpty }
            if (reserveIndex < 0) return@forEach
            player.inventory.setItem(target, reserves.get(slot, reserveIndex))
            reserves.set(slot, reserveIndex, ItemStack.EMPTY)
            changed = true
        }
        if (changed) finishChange(player)
        return changed
    }

    fun isEquipped(player: ServerPlayer, stack: ItemStack): Boolean {
        if (stack.isEmpty || player.mainHandItem !== stack) return false
        val slot = DestinyWeaponSlot.forStack(stack) ?: return false
        return player.inventory.selected == slot.hotbarIndex
    }

    fun equipFromInventory(player: ServerPlayer, source: Int, slot: DestinyWeaponSlot): Boolean {
        if (source !in INVENTORY_MIN..INVENTORY_MAX) return false
        val incoming = player.inventory.getItem(source)
        if (incoming.isEmpty || DestinyWeaponSlot.forStack(incoming) != slot) return false
        val target = slot.hotbarIndex
        if (source == target) return true

        val outgoing = player.inventory.getItem(target)
        if (outgoing.isEmpty) {
            player.inventory.setItem(target, incoming)
            player.inventory.setItem(source, ItemStack.EMPTY)
        } else if (DestinyWeaponSlot.forStack(outgoing) == slot) {
            val emptyReserve = PlayerDestinyDataApi.get(player).weaponReserves.firstEmpty(slot)
            if (emptyReserve >= 0) {
                PlayerDestinyDataApi.get(player).weaponReserves.set(slot, emptyReserve, outgoing)
                player.inventory.setItem(target, incoming)
                player.inventory.setItem(source, ItemStack.EMPTY)
            } else {
                swapInventory(player, source, target)
            }
        } else {
            swapInventory(player, source, target)
        }
        finishChange(player)
        return true
    }

    fun equipFromReserve(player: ServerPlayer, reserveIndex: Int, slot: DestinyWeaponSlot): Boolean {
        if (reserveIndex !in 0 until DestinyWeaponReserves.CAPACITY_PER_SLOT) return false
        val reserves = PlayerDestinyDataApi.get(player).weaponReserves
        val incoming = reserves.get(slot, reserveIndex)
        if (incoming.isEmpty || DestinyWeaponSlot.forStack(incoming) != slot) return false

        val target = slot.hotbarIndex
        val outgoing = player.inventory.getItem(target)
        player.inventory.setItem(target, incoming)
        if (outgoing.isEmpty || DestinyWeaponSlot.forStack(outgoing) == slot) {
            reserves.set(slot, reserveIndex, outgoing)
        } else {
            reserves.set(slot, reserveIndex, ItemStack.EMPTY)
            if (!player.inventory.add(outgoing)) player.drop(outgoing, false)
        }
        finishChange(player)
        return true
    }

    private fun finishChange(player: ServerPlayer) {
        player.inventoryMenu.broadcastChanges()
        DestinyNetworking.syncWeaponLoadout(player)
        DestinyNetworking.syncNavigationState(player, GuardianPowerRuntime.refresh(player))
        WeaponHudSync.syncNow(player)
    }

    private fun swapInventory(player: ServerPlayer, first: Int, second: Int) {
        val firstStack = player.inventory.getItem(first)
        val secondStack = player.inventory.getItem(second)
        player.inventory.setItem(first, secondStack)
        player.inventory.setItem(second, firstStack)
    }
}
