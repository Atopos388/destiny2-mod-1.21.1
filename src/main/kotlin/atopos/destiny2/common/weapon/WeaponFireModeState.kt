package atopos.destiny2.common.weapon

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

object WeaponFireModeState {
    private const val ROOT = "DestinyWeaponFireMode"
    private const val MODE = "mode"

    fun current(stack: ItemStack, profile: WeaponCombatProfile): WeaponFireMode {
        val supported = profile.supportedFireModes.ifEmpty { listOf(profile.fireMode) }
        val stored = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompound(ROOT)
            ?.getInt(MODE)
            ?.let { WeaponFireMode.entries.getOrNull(it) }
        return stored?.takeIf(supported::contains) ?: supported.first()
    }

    fun cycle(stack: ItemStack, profile: WeaponCombatProfile): WeaponFireMode {
        val supported = profile.supportedFireModes.distinct().ifEmpty { listOf(profile.fireMode) }
        val current = current(stack, profile)
        val next = supported[(supported.indexOf(current) + 1) % supported.size]
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
            data.update { tag ->
                val root = CompoundTag()
                root.putInt(MODE, next.ordinal)
                tag.put(ROOT, root)
            }
        }
        return next
    }
}
