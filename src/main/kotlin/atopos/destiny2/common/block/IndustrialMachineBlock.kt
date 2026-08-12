package atopos.destiny2.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.Containers
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.BlockHitResult

class IndustrialMachineBlock(
    val kind: IndustrialMachineKind,
    properties: BlockBehaviour.Properties
) : Block(properties), EntityBlock {
    init {
        registerDefaultState(
            stateDefinition.any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(BlockStateProperties.LIT, false)
        )
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
        IndustrialMachineBlockEntity(pos, state)

    @Suppress("UNCHECKED_CAST")
    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide || type !== DestinyBlocks.INDUSTRIAL_MACHINE_BLOCK_ENTITY) return null
        return BlockEntityTicker { tickLevel, pos, tickState, entity ->
            IndustrialMachineBlockEntity.serverTick(
                tickLevel,
                pos,
                tickState,
                entity as IndustrialMachineBlockEntity
            )
        }
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.horizontalDirection.opposite)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.LIT)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)))

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.MODEL

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        openMenu(level, pos, player)
        return InteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        openMenu(level, pos, player)
        return ItemInteractionResult.sidedSuccess(level.isClientSide)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, movedByPiston: Boolean) {
        if (!state.`is`(newState.block)) {
            val entity = level.getBlockEntity(pos)
            if (entity is IndustrialMachineBlockEntity) {
                Containers.dropContents(level, pos, entity)
                level.updateNeighbourForOutputSignal(pos, this)
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston)
    }

    override fun animateTick(state: BlockState, level: Level, pos: BlockPos, random: RandomSource) {
        if (!state.getValue(BlockStateProperties.LIT) || random.nextFloat() > 0.35f) return
        val facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING)
        val x = pos.x + 0.5 + facing.stepX * 0.54
        val y = pos.y + 0.58 + random.nextDouble() * 0.18
        val z = pos.z + 0.5 + facing.stepZ * 0.54
        val particle = when (kind) {
            IndustrialMachineKind.LIGHT_CAPACITOR -> net.minecraft.core.particles.ParticleTypes.END_ROD
            IndustrialMachineKind.GLIMMER_REFINERY -> net.minecraft.core.particles.ParticleTypes.WAX_ON
            IndustrialMachineKind.MEMORY_FOUNDRY -> net.minecraft.core.particles.ParticleTypes.ENCHANT
        }
        level.addParticle(particle, x, y, z, 0.0, 0.015, 0.0)
    }

    private fun openMenu(level: Level, pos: BlockPos, player: Player) {
        if (!level.isClientSide && player is ServerPlayer) {
            val provider = level.getBlockEntity(pos) as? MenuProvider ?: return
            player.openMenu(provider)
        }
    }
}
