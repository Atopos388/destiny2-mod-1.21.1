package atopos.destiny2.common.weapon

import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.PlayerDestinyDataApi
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack

/** Converts world ammo bricks directly into per-weapon reserve ammunition. */
object WeaponAmmoPickupSystem {
    /** Returns true for every Destiny ammo brick so vanilla never stores it in the inventory. */
    fun handlePickup(player: ServerPlayer, entity: ItemEntity): Boolean {
        val type = ammoType(entity.item) ?: return false
        if (type == DestinyAmmoType.PRIMARY) {
            // Compatibility cleanup for old white bricks. Primary ammo is infinite.
            entity.discard()
            return true
        }

        var remaining = entity.item.count
        val original = remaining
        remaining -= distribute(player, type, remaining)

        val accepted = original - remaining
        if (accepted > 0) {
            player.take(entity, accepted)
            if (remaining <= 0) entity.discard() else entity.item.count = remaining
            player.playSound(SoundEvents.ITEM_PICKUP, 0.25f, 1.2f)
            player.inventoryMenu.broadcastChanges()
            DestinyNetworking.syncWeaponLoadout(player)
            WeaponHudSync.syncNow(player)
        }
        // Leave a brick in the world when every matching weapon is full or absent.
        return true
    }

    /** Converts ammo items already present in old saves without duplicating their rounds. */
    fun migrateLegacyInventory(player: ServerPlayer) {
        for (brick in player.inventory.items + player.inventory.offhand) {
            val type = ammoType(brick) ?: continue
            if (type == DestinyAmmoType.PRIMARY) {
                brick.count = 0
                continue
            }
            val accepted = distribute(player, type, brick.count)
            if (accepted > 0) brick.shrink(accepted)
        }
    }

    private fun distribute(player: ServerPlayer, type: DestinyAmmoType, amount: Int): Int {
        var remaining = amount.coerceAtLeast(0)
        for (weaponStack in pickupPriority(player)) {
            if (remaining <= 0) break
            val weapon = weaponStack.item as? DestinyRangedWeapon ?: continue
            val profile = weapon.combatProfile(weaponStack)
            if (profile.ammoType != type) continue
            remaining -= WeaponAmmoState.addReserve(weaponStack, profile, remaining)
        }
        return amount.coerceAtLeast(0) - remaining
    }

    private fun pickupPriority(player: ServerPlayer): List<ItemStack> {
        val result = ArrayList<ItemStack>()
        fun add(stack: ItemStack) {
            if (!stack.isEmpty && result.none { it === stack }) result += stack
        }

        add(player.mainHandItem)
        add(player.offhandItem)
        for (slot in 0 until 9) add(player.inventory.items[slot])
        player.inventory.items.forEach(::add)
        val reserves = PlayerDestinyDataApi.get(player).weaponReserves
        DestinyWeaponSlot.entries.forEach { slot -> reserves.stacks(slot).forEach(::add) }
        return result
    }

    private fun ammoType(stack: ItemStack): DestinyAmmoType? = when {
        stack.`is`(DestinyItems.PRIMARY_AMMO) -> DestinyAmmoType.PRIMARY
        stack.`is`(DestinyItems.SPECIAL_AMMO) -> DestinyAmmoType.SPECIAL
        stack.`is`(DestinyItems.HEAVY_AMMO) -> DestinyAmmoType.HEAVY
        else -> null
    }
}
