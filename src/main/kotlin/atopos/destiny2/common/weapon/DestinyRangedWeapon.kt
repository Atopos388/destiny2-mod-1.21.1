package atopos.destiny2.common.weapon

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

interface DestinyRangedWeapon {
    fun combatProfile(stack: ItemStack): WeaponCombatProfile
    fun aimProfile(stack: ItemStack): WeaponAimProfile = WeaponAimProfile.DISABLED
    fun requestReload(level: Level, player: ServerPlayer, stack: ItemStack): Boolean
    fun requestInspect(level: Level, player: ServerPlayer, stack: ItemStack): Boolean = false
    fun weaponHudStatus(player: ServerPlayer, stack: ItemStack): WeaponHudStatus
    fun cycleFireMode(player: ServerPlayer, stack: ItemStack): WeaponFireMode =
        WeaponFireModeState.cycle(stack, combatProfile(stack))
}
