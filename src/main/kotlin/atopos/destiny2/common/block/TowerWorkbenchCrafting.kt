package atopos.destiny2.common.block

import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/** Per-open-menu inventory; never shared with another player or persisted on the block. */
class TowerWorkbenchCrafting(private val heart: Item, private val shard: Item, private val weapon: Item) : SimpleContainer(10) {
    private var closed = false

    fun matches(): Boolean = !closed && (0..8).all { index ->
        val stack = getItem(index)
        when (index) {
            3, 5 -> stack.`is`(shard) && stack.count >= 1
            4 -> stack.`is`(heart) && stack.count >= 1
            else -> stack.isEmpty
        }
    }

    fun canCraft(): Boolean = matches() && getItem(9).isEmpty

    /** Called only by the server UI callback, after its menu/structure/distance validation. */
    fun craft(): Boolean {
        if (!canCraft()) return false
        val result = ItemStack(weapon)
        for (index in 3..5) removeItem(index, 1)
        setItem(9, result)
        setChanged()
        return true
    }

    /** Drain before returning/dropping, so duplicate removal callbacks never duplicate items. */
    fun closeAndDrain(): List<ItemStack> {
        if (closed) return emptyList()
        closed = true
        return (0..9).map { removeItemNoUpdate(it) }.filterNot { it.isEmpty }
    }
}
