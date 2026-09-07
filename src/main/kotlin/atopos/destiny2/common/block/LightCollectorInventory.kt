package atopos.destiny2.common.block

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.SimpleContainer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Shared persistent machine inventory. Output items never automatically feed back into input. */
class LightCollectorInventory(
    private val crystal: Item,
    private val glimmer: Item,
    private val dirty: () -> Unit = {}
) : SimpleContainer(4) {
    var progress = 0
        private set
    private var recipeId = 0
    private data class Recipe(val id: Int, val inputCount: Int, val output: Item, val count: Int, val ticks: Int)
    private val crystalRecipe = Recipe(1, 2, crystal, 1, CRYSTAL_TICKS)
    private val glimmerRecipe = Recipe(2, 1, glimmer, OUTPUT_COUNT, CYCLE_TICKS)

    private fun recipe(): Recipe? = when {
        getItem(0).`is`(Items.AMETHYST_SHARD) -> crystalRecipe
        getItem(0).`is`(crystal) -> glimmerRecipe
        else -> null
    }
    override fun setChanged() { super.setChanged(); dirty() }
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        slot == 0 && (stack.`is`(crystal) || stack.`is`(Items.AMETHYST_SHARD))

    private fun room(slot: Int, output: Item): Int {
        val stack = getItem(slot)
        return if (stack.isEmpty) minOf(output.defaultInstance.maxStackSize, maxStackSize)
        else if (ItemStack.isSameItemSameComponents(stack, output.defaultInstance))
            (minOf(stack.maxStackSize, maxStackSize) - stack.count).coerceAtLeast(0)
        else 0
    }
    /** Also used on synchronized client slots for purely decorative flow animation. */
    fun canProcess(): Boolean {
        val r = recipe() ?: return false
        return getItem(0).count >= r.inputCount && (1..3).sumOf { room(it, r.output) } >= r.count
    }

    fun restoreProgress(value: Int) {
        val r = recipe()
        recipeId = r?.id ?: 0
        progress = value.coerceIn(0, (r?.ticks ?: 1) - 1)
    }
    fun save(tag: CompoundTag, registries: HolderLookup.Provider) {
        tag.putInt("ProductionProgress", progress)
        tag.putInt("ProductionRecipe", recipeId)
        for (slot in 0..3) tag.put("Inventory$slot", getItem(slot).saveOptional(registries))
    }
    fun load(tag: CompoundTag, registries: HolderLookup.Provider) {
        for (slot in 0..3) setItem(slot, ItemStack.parseOptional(registries, tag.getCompound("Inventory$slot")))
        restoreProgress(tag.getInt("ProductionProgress"))
        if (tag.contains("ProductionRecipe") && tag.getInt("ProductionRecipe") != recipeId) progress = 0
    }

    fun tickProduction(): Boolean {
        val r = recipe()
        if (r == null || getItem(0).count < r.inputCount) {
            if (progress != 0 || recipeId != 0) { progress = 0; recipeId = 0; setChanged() }
            return false
        }
        if (recipeId != r.id) { progress = 0; recipeId = r.id; setChanged() }
        if (!canProcess()) return false
        progress++
        if (progress < r.ticks) { setChanged(); return false }
        progress = 0
        removeItem(0, r.inputCount)
        var remaining = r.count
        for (slot in 1..3) {
            val amount = minOf(room(slot, r.output), remaining)
            if (amount <= 0) continue
            val stack = getItem(slot)
            if (stack.isEmpty) setItem(slot, ItemStack(r.output, amount))
            else { stack.grow(amount); setChanged() }
            remaining -= amount
        }
        check(remaining == 0)
        return true
    }

    companion object {
        const val CYCLE_TICKS = 600
        const val OUTPUT_COUNT = 20
        const val CRYSTAL_TICKS = 160
    }
}
