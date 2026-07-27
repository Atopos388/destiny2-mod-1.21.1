package atopos.destiny2.common.block

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState

class SphereModelBlock(properties: BlockBehaviour.Properties) : Block(properties), EntityBlock {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return SphereModelBlockEntity(pos, state)
    }
}

class SphereModelBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(DestinyBlocks.SPHERE_MODEL_BLOCK_ENTITY, pos, state) {
    companion object {
        val MODEL: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "bedrock_geo/sphere_model_block.geo.json"
        )
        val TEXTURE: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "textures/block/sphere_model_block.png"
        )
    }
}
