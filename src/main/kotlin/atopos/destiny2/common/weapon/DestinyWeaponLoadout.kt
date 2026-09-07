package atopos.destiny2.common.weapon

import atopos.destiny2.common.gear.GearCategory
import atopos.destiny2.common.gear.GearRegistry
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

/** Destiny-style character weapon columns. The hotbar index is zero-based. */
enum class DestinyWeaponSlot(val serializedName: String, val displayName: String, val hotbarIndex: Int) {
    KINETIC("kinetic", "动能武器", 0),
    ENERGY("energy", "能量武器", 1),
    POWER("power", "威能武器", 2);

    companion object {
        fun fromSerializedName(value: String?): DestinyWeaponSlot? =
            entries.firstOrNull { it.serializedName.equals(value, ignoreCase = true) }

        fun forStack(stack: ItemStack): DestinyWeaponSlot? {
            if (stack.item !is DestinyRangedWeapon) return null
            val definition = GearRegistry.definitionFor(stack) ?: return null
            if (definition.category != GearCategory.WEAPON) return null
            return forWeapon(definition.ammoType, definition.damageElement)
        }

        fun forWeapon(ammoType: DestinyAmmoType, element: DestinyDamageElement): DestinyWeaponSlot = when {
            ammoType == DestinyAmmoType.HEAVY -> POWER
            element == DestinyDamageElement.KINETIC ||
                element == DestinyDamageElement.STASIS ||
                element == DestinyDamageElement.STRAND -> KINETIC
            else -> ENERGY
        }
    }
}

/** Nine unequipped weapons for each character weapon column. */
class DestinyWeaponReserves private constructor(
    private val contents: MutableMap<DestinyWeaponSlot, MutableList<ItemStack>>
) {
    constructor() : this(newContents())

    fun get(slot: DestinyWeaponSlot, index: Int): ItemStack =
        contents.getValue(slot).getOrElse(index) { ItemStack.EMPTY }

    fun set(slot: DestinyWeaponSlot, index: Int, stack: ItemStack) {
        require(index in 0 until CAPACITY_PER_SLOT) { "Weapon reserve index out of bounds: $index" }
        contents.getValue(slot)[index] = stack
    }

    fun stacks(slot: DestinyWeaponSlot): List<ItemStack> = contents.getValue(slot)

    fun firstEmpty(slot: DestinyWeaponSlot): Int = contents.getValue(slot).indexOfFirst(ItemStack::isEmpty)

    fun copy(): DestinyWeaponReserves = DestinyWeaponReserves(
        contents.mapValuesTo(linkedMapOf()) { (_, stacks) -> stacks.mapTo(mutableListOf(), ItemStack::copy) }
    )

    fun toTag(registries: HolderLookup.Provider): CompoundTag = CompoundTag().also { root ->
        DestinyWeaponSlot.entries.forEach { slot ->
            val slotTag = CompoundTag()
            contents.getValue(slot).forEachIndexed { index, stack ->
                if (!stack.isEmpty) slotTag.put(index.toString(), stack.saveOptional(registries))
            }
            root.put(slot.serializedName, slotTag)
        }
    }

    companion object {
        const val CAPACITY_PER_SLOT = 9

        fun fromTag(tag: CompoundTag, registries: HolderLookup.Provider): DestinyWeaponReserves {
            val result = DestinyWeaponReserves()
            DestinyWeaponSlot.entries.forEach { slot ->
                val slotTag = tag.getCompound(slot.serializedName)
                repeat(CAPACITY_PER_SLOT) { index ->
                    if (slotTag.contains(index.toString())) {
                        val stack = ItemStack.parseOptional(registries, slotTag.getCompound(index.toString()))
                        if (DestinyWeaponSlot.forStack(stack) == slot) result.set(slot, index, stack)
                    }
                }
            }
            return result
        }

        private fun newContents(): MutableMap<DestinyWeaponSlot, MutableList<ItemStack>> =
            DestinyWeaponSlot.entries.associateWithTo(linkedMapOf()) {
                MutableList(CAPACITY_PER_SLOT) { ItemStack.EMPTY }
            }
    }
}
