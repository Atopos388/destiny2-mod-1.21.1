package atopos.destiny2.common.block

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.phys.BlockHitResult
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
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/** One workbench item occupies two adjacent ordinary blocks, each owning its collision. */
class TowerWorkbenchBlock(properties: BlockBehaviour.Properties) : Block(properties), BlockUIMenuType.BlockUI {
    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0))
    }

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
        if (!level.isClientSide && player is ServerPlayer) {
            val anchor = menuAnchor(state, pos)
            if (isMenuStructureValid(level, anchor)) BlockUIMenuType.openUI(player, anchor)
        }
    }

    internal fun menuAnchor(state: BlockState, pos: BlockPos): BlockPos =
        pos.relative(state.getValue(FACING).clockWise, -state.getValue(PART))

    internal fun isMenuStructureValid(level: BlockGetter, pos: BlockPos): Boolean {
        val state = level.getBlockState(pos)
        return state.`is`(this) && state.getValue(PART) == 0 &&
            isPartner(state, level.getBlockState(pos.relative(state.getValue(FACING).clockWise)))
    }

    override fun createUI(holder: BlockUIMenuType.BlockUIHolder): ModularUI = TowerWorkbenchUI.create(holder)

    override fun stillValid(holder: BlockUIMenuType.BlockUIHolder): Boolean =
        isMenuStructureValid(holder.player.level(), holder.pos) &&
            holder.player.distanceToSqr(holder.pos.x + 0.5, holder.pos.y + 0.5, holder.pos.z + 0.5) <= 64.0

    override fun getUIDisplayName(holder: BlockUIMenuType.BlockUIHolder): Component =
        Component.translatable("block.destiny2-mod.tower_workbench")

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, PART)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val facing = context.horizontalDirection.opposite
        val other = context.clickedPos.relative(facing.clockWise)
        val level = context.level
        // The model is 1.75 blocks tall; reserve clearance above both horizontal cells.
        val cells = listOf(context.clickedPos, other, context.clickedPos.above(), other.above())
        if (cells.any { level.isOutsideBuildHeight(it) || !level.worldBorder.isWithinBounds(it) }) return null
        if (cells.drop(1).any { !level.getBlockState(it).canBeReplaced(context) }) return null
        return defaultBlockState().setValue(FACING, facing)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        if (!level.isClientSide) level.setBlock(
            pos.relative(state.getValue(FACING).clockWise), state.setValue(PART, 1), UPDATE_ALL
        )
    }

    override fun updateShape(
        state: BlockState, direction: Direction, neighbor: BlockState,
        level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos
    ): BlockState {
        if (direction != partnerDirection(state)) return super.updateShape(state, direction, neighbor, level, pos, neighborPos)
        return if (isPartner(state, neighbor)) state else Blocks.AIR.defaultBlockState()
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!level.isClientSide && player.isCreative) {
            val other = pos.relative(partnerDirection(state))
            if (isPartner(state, level.getBlockState(other))) {
                level.setBlock(other, Blocks.AIR.defaultBlockState(), UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
            }
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        SHAPES.getValue(state.getValue(FACING))[state.getValue(PART)]

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        SHAPES.getValue(state.getValue(FACING))[state.getValue(PART)]

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        if (mirror == Mirror.NONE) state else state.setValue(PART, state.getValue(PART) xor 1)
            .rotate(mirror.getRotation(state.getValue(FACING)))

    private fun partnerDirection(state: BlockState): Direction =
        if (state.getValue(PART) == 0) state.getValue(FACING).clockWise else state.getValue(FACING).counterClockWise

    private fun isPartner(state: BlockState, other: BlockState): Boolean =
        other.`is`(this) && other.getValue(FACING) == state.getValue(FACING) && other.getValue(PART) != state.getValue(PART)

    companion object {
        val FACING = BlockStateProperties.HORIZONTAL_FACING
        val PART: IntegerProperty = IntegerProperty.create("part", 0, 1)
        // No horizontal overhang: vanilla broad-phase discovers collision from either side.

        // Three coarse input boxes per cell: solid desk, backboard, and one large prop.
        // Half-unit grid and static caching avoid per-bolt voxel grids and per-frame unions.
        private val NORTH_BOXES = listOf(
            listOf(
                doubleArrayOf(0.0, 0.0, 0.0, 16.0, 20.0, 16.0),
                doubleArrayOf(7.0, 20.0, 13.0, 16.0, 28.0, 16.0),
                doubleArrayOf(1.0, 20.0, 4.0, 7.5, 24.5, 10.5)
            ),
            listOf(
                doubleArrayOf(0.0, 0.0, 0.0, 16.0, 20.0, 16.0),
                doubleArrayOf(0.0, 20.0, 13.0, 9.0, 28.0, 16.0),
                doubleArrayOf(8.0, 20.0, 3.5, 14.5, 25.0, 8.5)
            )
        )

        private val SHAPES = listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
            .mapIndexed { turns, facing ->
                facing to NORTH_BOXES.map { boxes ->
                    boxes.fold(Shapes.empty()) { result, source ->
                        val b = source.copyOf()
                        repeat(turns) {
                            val minX = b[0]
                            val maxX = b[3]
                            b[0] = 16.0 - b[5]
                            b[3] = 16.0 - b[2]
                            b[2] = minX
                            b[5] = maxX
                        }
                        Shapes.or(result, box(b[0], b[1], b[2], b[3], b[4], b[5]))
                    }.optimize()
                }
            }.toMap()
    }
}

