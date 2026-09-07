package atopos.destiny2.common.block

import atopos.destiny2.common.item.DestinyItems
import net.minecraft.world.item.ItemStack
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import software.bernie.geckolib.animatable.GeoBlockEntity
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

class LightCollectorBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(DestinyBlocks.LIGHT_COLLECTOR_BLOCK_ENTITY, pos, state), GeoBlockEntity {
    private val cache = GeckoLibUtil.createInstanceCache(this)
    private var placedAt = -1L
    val inventory = LightCollectorInventory(DestinyItems.LIGHT_CRYSTAL, DestinyItems.GLIMMER) { setChanged() }

    fun serverTick() {
        val world = level ?: return
        if (world.isClientSide || isRemoved) return
        val upper = world.getBlockState(blockPos.above())
        if (!upper.`is`(blockState.block) ||
            upper.getValue(LightCollectorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ||
            upper.getValue(LightCollectorBlock.FACING) != blockState.getValue(LightCollectorBlock.FACING)) return
        inventory.tickProduction()
    }

    fun markPlaced() {
        val world = level ?: return
        placedAt = world.gameTime
        setChanged()
        world.sendBlockUpdated(blockPos, blockState, blockState, 3)
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        tag.putLong("PlacedAt", placedAt)
        inventory.save(tag, registries)
    }
    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        placedAt = if (tag.contains("PlacedAt")) tag.getLong("PlacedAt") else -1L
        inventory.load(tag, registries)
    }
    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
    override fun getAnimatableInstanceCache() = cache
    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "collector", 0) { state ->
            val age = (level?.gameTime ?: 0L) - placedAt
            state.setAndContinue(if (placedAt >= 0 && age in 0L until 30L) PLACE else OPERATE)
        })
    }
    companion object {
        private val PLACE = RawAnimation.begin().thenPlay("animation.light_collector.place")
        private val OPERATE = RawAnimation.begin().thenLoop("animation.light_collector.operate")
    }
}


