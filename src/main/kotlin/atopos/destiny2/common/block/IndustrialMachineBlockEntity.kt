package atopos.destiny2.common.block

import atopos.destiny2.common.item.DestinyItems
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class IndustrialMachineBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(DestinyBlocks.INDUSTRIAL_MACHINE_BLOCK_ENTITY, pos, state), Container, MenuProvider {

    private val items: NonNullList<ItemStack> = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY)
    private var energy = 0
    private var progress = 0

    private val kind: IndustrialMachineKind
        get() = (blockState.block as IndustrialMachineBlock).kind

    override fun getDisplayName(): Component = when (kind) {
        IndustrialMachineKind.LIGHT_CAPACITOR -> Component.translatable(
            "container.destiny2-mod.light_capacitor",
            energy,
            IndustrialMachineRules.CAPACITOR_MAX_ENERGY
        )
        IndustrialMachineKind.GLIMMER_REFINERY -> Component.translatable(
            "container.destiny2-mod.glimmer_refinery",
            progress,
            IndustrialMachineRules.REFINERY_PROCESS_TICKS
        )
        IndustrialMachineKind.MEMORY_FOUNDRY -> Component.translatable(
            "container.destiny2-mod.memory_foundry",
            progress,
            IndustrialMachineRules.MEMORY_FOUNDRY_PROCESS_TICKS
        )
    }

    override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu =
        ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1)

    override fun getContainerSize(): Int = SLOT_COUNT
    override fun isEmpty(): Boolean = items.all(ItemStack::isEmpty)
    override fun getItem(slot: Int): ItemStack = items[slot]
    override fun removeItem(slot: Int, amount: Int): ItemStack =
        ContainerHelper.removeItem(items, slot, amount).also { if (!it.isEmpty) setChanged() }
    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)
    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        if (stack.count > maxStackSize) stack.count = maxStackSize
        setChanged()
    }
    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)
    override fun clearContent() {
        items.clear()
        setChanged()
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = when (kind) {
        IndustrialMachineKind.LIGHT_CAPACITOR -> slot == 0 && stack.`is`(DestinyItems.LIGHT_CRYSTAL)
        IndustrialMachineKind.GLIMMER_REFINERY -> slot == 0 && IndustrialMachineRules.glimmerYield(itemId(stack)) > 0
        IndustrialMachineKind.MEMORY_FOUNDRY -> when (slot) {
            0 -> stack.`is`(net.minecraft.world.item.Items.BOOK)
            1 -> stack.`is`(net.minecraft.world.item.Items.GUNPOWDER)
            2 -> stack.`is`(net.minecraft.world.item.Items.AMETHYST_SHARD)
            3 -> stack.`is`(DestinyItems.GLIMMER)
            else -> false
        }
    }

    override fun saveAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.saveAdditional(tag, provider)
        tag.putInt("Energy", energy)
        tag.putInt("Progress", progress)
        ContainerHelper.saveAllItems(tag, items, provider)
    }

    override fun loadAdditional(tag: CompoundTag, provider: HolderLookup.Provider) {
        super.loadAdditional(tag, provider)
        energy = tag.getInt("Energy").coerceIn(0, IndustrialMachineRules.CAPACITOR_MAX_ENERGY)
        progress = tag.getInt("Progress").coerceAtLeast(0)
        ContainerHelper.loadAllItems(tag, items, provider)
    }

    private fun tick(level: Level, pos: BlockPos, state: BlockState) {
        when (kind) {
            IndustrialMachineKind.LIGHT_CAPACITOR -> tickCapacitor(level, pos)
            IndustrialMachineKind.GLIMMER_REFINERY -> tickRefinery(level, pos)
            IndustrialMachineKind.MEMORY_FOUNDRY -> tickMemoryFoundry(level, pos)
        }
        val active = when (kind) {
            IndustrialMachineKind.LIGHT_CAPACITOR -> energy > 0
            else -> progress > 0
        }
        if (state.getValue(BlockStateProperties.LIT) != active) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, active), 3)
        }
    }

    private fun tickCapacitor(level: Level, pos: BlockPos) {
        val fuel = items[0]
        if (fuel.`is`(DestinyItems.LIGHT_CRYSTAL) && IndustrialMachineRules.canStoreEnergy(energy)) {
            fuel.shrink(1)
            energy += IndustrialMachineRules.LIGHT_CRYSTAL_ENERGY
            progress = 0
            setChanged()
            level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.7f, 1.25f)
        }
    }

    private fun tickRefinery(level: Level, pos: BlockPos) {
        val input = items[0]
        val yield = IndustrialMachineRules.glimmerYield(itemId(input))
        if (yield <= 0 || !canOutput(8, DestinyItems.GLIMMER, yield)) {
            resetProgress()
            return
        }
        if (progress == 0 && !drawAdjacentEnergy(level, pos, IndustrialMachineRules.REFINERY_ENERGY_COST)) return
        progress++
        if (progress < IndustrialMachineRules.REFINERY_PROCESS_TICKS) return

        input.shrink(1)
        addOutput(8, ItemStack(DestinyItems.GLIMMER, yield))
        progress = 0
        setChanged()
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.65f, 0.9f)
    }

    private fun tickMemoryFoundry(level: Level, pos: BlockPos) {
        if (!IndustrialMachineRules.foundryIngredients(items[0].count, items[1].count, items[2].count, items[3].count) ||
            !items[0].`is`(net.minecraft.world.item.Items.BOOK) ||
            !items[1].`is`(net.minecraft.world.item.Items.GUNPOWDER) ||
            !items[2].`is`(net.minecraft.world.item.Items.AMETHYST_SHARD) ||
            !items[3].`is`(DestinyItems.GLIMMER) ||
            !canOutput(8, DestinyItems.GRENADE_MEMORY, 1)
        ) {
            resetProgress()
            return
        }
        if (progress == 0 && !drawAdjacentEnergy(level, pos, IndustrialMachineRules.MEMORY_FOUNDRY_ENERGY_COST)) return
        progress++
        if (progress < IndustrialMachineRules.MEMORY_FOUNDRY_PROCESS_TICKS) return

        items[0].shrink(1)
        items[1].shrink(2)
        items[2].shrink(1)
        items[3].shrink(32)
        addOutput(8, ItemStack(DestinyItems.GRENADE_MEMORY))
        progress = 0
        setChanged()
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.1f)
    }

    private fun drawAdjacentEnergy(level: Level, pos: BlockPos, amount: Int): Boolean {
        var remaining = amount
        val sources = Direction.entries.mapNotNull { direction ->
            level.getBlockEntity(pos.relative(direction)) as? IndustrialMachineBlockEntity
        }.filter { it.kind == IndustrialMachineKind.LIGHT_CAPACITOR }
        if (sources.sumOf { it.energy } < amount) return false
        for (source in sources) {
            val extracted = minOf(source.energy, remaining)
            source.energy -= extracted
            source.setChanged()
            remaining -= extracted
            if (remaining == 0) break
        }
        return true
    }

    private fun canOutput(slot: Int, item: net.minecraft.world.item.Item, count: Int): Boolean {
        val output = items[slot]
        return output.isEmpty || (output.`is`(item) && output.count + count <= output.maxStackSize)
    }

    private fun addOutput(slot: Int, stack: ItemStack) {
        if (items[slot].isEmpty) items[slot] = stack else items[slot].grow(stack.count)
    }

    private fun resetProgress() {
        if (progress == 0) return
        progress = 0
        setChanged()
    }

    private fun itemId(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).toString()

    companion object {
        private const val SLOT_COUNT = 9

        fun serverTick(level: Level, pos: BlockPos, state: BlockState, entity: IndustrialMachineBlockEntity) {
            entity.tick(level, pos, state)
        }
    }
}
