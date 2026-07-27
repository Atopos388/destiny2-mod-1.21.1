package atopos.destiny2.common.equipment

import atopos.destiny2.common.gear.GearRolls
import atopos.destiny2.common.item.DestinyClassItem
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.player.GuardianPowerSystem
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/** One-slot container attached to InventoryMenu; the server copy is persisted in PlayerDestinyData. */
class DestinyClassItemContainer(private val owner: Player) : Container {
    private var clientStack: ItemStack = ItemStack.EMPTY

    private fun current(): ItemStack = if (owner is ServerPlayer) PlayerDestinyDataApi.get(owner).classItem else clientStack
    private fun update(stack: ItemStack) {
        if (owner is ServerPlayer) PlayerDestinyDataApi.get(owner).classItem = stack else clientStack = stack
    }

    override fun getContainerSize(): Int = 1
    override fun isEmpty(): Boolean = current().isEmpty
    override fun getItem(slot: Int): ItemStack = if (slot == 0) current() else ItemStack.EMPTY
    override fun removeItem(slot: Int, amount: Int): ItemStack {
        if (slot != 0) return ItemStack.EMPTY
        val stack = current()
        val removed = if (amount >= stack.count) stack.copy() else stack.split(amount)
        if (!removed.isEmpty) update(if (amount >= stack.count) ItemStack.EMPTY else stack)
        return removed
    }
    override fun removeItemNoUpdate(slot: Int): ItemStack {
        if (slot != 0) return ItemStack.EMPTY
        val old = current()
        update(ItemStack.EMPTY)
        return old
    }
    override fun setItem(slot: Int, stack: ItemStack) {
        if (slot != 0) return
        val copy = stack.copyWithCount(stack.count.coerceAtMost(1))
        if (!copy.isEmpty) {
            val power = (owner as? ServerPlayer)?.let { GuardianPowerSystem.normalDropPower(it) }
            GearRolls.ensureRoll(copy, powerOverride = power)
        }
        update(copy)
        setChanged()
    }
    override fun setChanged() = Unit
    override fun stillValid(player: Player): Boolean = player === owner
    override fun clearContent() = update(ItemStack.EMPTY)
}

class DestinyClassItemSlot(
    container: Container,
    private val owner: Player,
    x: Int,
    y: Int
) : Slot(container, 0, x, y) {
    override fun mayPlace(stack: ItemStack): Boolean {
        val item = stack.item as? DestinyClassItem ?: return false
        return owner !is ServerPlayer || item.requiredClass == PlayerDestinyDataApi.get(owner).destinyClass
    }

    override fun getMaxStackSize(): Int = 1
}
