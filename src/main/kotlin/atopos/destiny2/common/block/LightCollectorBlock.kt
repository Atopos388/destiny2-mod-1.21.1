package atopos.destiny2.common.block

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.Containers
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class LightCollectorBlock(properties: BlockBehaviour.Properties) : Block(properties), EntityBlock, BlockUIMenuType.BlockUI {
    init { registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)) }
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(FACING, HALF) }
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? =
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) LightCollectorBlockEntity(pos, state) else null

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (level.isClientSide || state.getValue(HALF) != DoubleBlockHalf.LOWER ||
            type !== DestinyBlocks.LIGHT_COLLECTOR_BLOCK_ENTITY) return null
        return BlockEntityTicker { _, _, _, entity -> (entity as LightCollectorBlockEntity).serverTick() }
    }

    internal fun menuAnchor(state: BlockState, pos: BlockPos): BlockPos =
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) pos.below() else pos

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        if (player.isShiftKeyDown) return InteractionResult.PASS
        openMenu(state, level, pos, player)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun useItemOn(stack: ItemStack, state: BlockState, level: Level, pos: BlockPos,
                           player: Player, hand: InteractionHand, hit: BlockHitResult): ItemInteractionResult {
        if (player.isShiftKeyDown) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        openMenu(state, level, pos, player)
        return ItemInteractionResult.sidedSuccess(level.isClientSide)
    }

    private fun openMenu(state: BlockState, level: Level, pos: BlockPos, player: Player) {
        val anchor = menuAnchor(state, pos)
        if (!level.isClientSide && player is ServerPlayer && level.getBlockEntity(anchor) is LightCollectorBlockEntity)
            BlockUIMenuType.openUI(player, anchor)
    }

    override fun createUI(holder: BlockUIMenuType.BlockUIHolder): ModularUI = LightCollectorUI.create(holder)

    override fun stillValid(holder: BlockUIMenuType.BlockUIHolder): Boolean {
        val world = holder.player.level()
        val lower = world.getBlockState(holder.pos)
        return lower.`is`(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER &&
            isPartner(lower, world.getBlockState(holder.pos.above())) &&
            world.getBlockEntity(holder.pos) is LightCollectorBlockEntity &&
            holder.player.distanceToSqr(holder.pos.x + .5, holder.pos.y + .5, holder.pos.z + .5) <= 64.0
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block) && !level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            val entity = level.getBlockEntity(pos) as? LightCollectorBlockEntity
            if (entity != null) {
                Containers.dropContents(level, pos, entity.inventory)
                entity.inventory.clearContent()
            }
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val above = context.clickedPos.above()
        if (context.level.isOutsideBuildHeight(above) || !context.level.worldBorder.isWithinBounds(above) ||
            !context.level.getBlockState(above).canBeReplaced(context)) return null
        return defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (level.isClientSide) return
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), UPDATE_ALL)
        (level.getBlockEntity(pos) as? LightCollectorBlockEntity)?.markPlaced()
    }

    override fun updateShape(state: BlockState, direction: Direction, neighbor: BlockState,
        level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        if (direction != partnerDirection(state)) return super.updateShape(state, direction, neighbor, level, pos, neighborPos)
        return if (isPartner(state, neighbor)) state else Blocks.AIR.defaultBlockState()
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide && player.isCreative) {
            val other = pos.relative(partnerDirection(state))
            if (isPartner(state, level.getBlockState(other))) level.setBlock(other, Blocks.AIR.defaultBlockState(), UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    private fun partnerDirection(state: BlockState) = if (state.getValue(HALF) == DoubleBlockHalf.LOWER) Direction.UP else Direction.DOWN
    private fun isPartner(state: BlockState, other: BlockState) = other.`is`(this) &&
        other.getValue(FACING) == state.getValue(FACING) && other.getValue(HALF) != state.getValue(HALF)
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        SHAPES.getValue(state.getValue(FACING))[if (state.getValue(HALF) == DoubleBlockHalf.LOWER) 0 else 1]
    override fun rotate(state: BlockState, rotation: Rotation) = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    override fun mirror(state: BlockState, mirror: Mirror) = state.rotate(mirror.getRotation(state.getValue(FACING)))

    companion object {
        val FACING = BlockStateProperties.HORIZONTAL_FACING
        val HALF = BlockStateProperties.DOUBLE_BLOCK_HALF
        private val BOXES = listOf(
            doubleArrayOf(0.0,0.0,0.0,16.0,8.0,16.0),
            doubleArrayOf(1.0,8.0,6.0,4.0,29.0,10.0),
            doubleArrayOf(12.0,8.0,6.0,15.0,29.0,10.0),
            doubleArrayOf(4.0,8.0,4.0,12.0,12.0,12.0),
            doubleArrayOf(6.0,12.0,6.0,10.0,24.0,10.0),
            doubleArrayOf(1.0,24.0,2.0,15.0,32.0,14.0)
        )
        private val SHAPES = listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST).mapIndexed { turns, facing ->
            facing to (0..1).map { half ->
                BOXES.mapNotNull { source ->
                    val b = source.copyOf()
                    b[1] = maxOf(0.0, b[1] - half * 16.0)
                    b[4] = minOf(16.0, b[4] - half * 16.0)
                    if (b[1] >= b[4]) null else {
                        repeat(turns) { val x0=b[0]; val x1=b[3]; b[0]=16.0-b[5]; b[3]=16.0-b[2]; b[2]=x0; b[5]=x1 }
                        box(b[0],b[1],b[2],b[3],b[4],b[5])
                    }
                }.fold(Shapes.empty(), Shapes::or).optimize()
            }
        }.toMap()
    }
}

