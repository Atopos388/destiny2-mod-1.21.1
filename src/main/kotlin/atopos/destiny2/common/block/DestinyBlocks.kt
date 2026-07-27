package atopos.destiny2.common.block

import net.fabricmc.fabric.api.`object`.builder.v1.block.entity.FabricBlockEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object DestinyBlocks {
    private val blockItems = mutableListOf<Item>()

    val SHIMMERING_CRYSTAL_ORE: Block = registerBlockWithItem(
        "shimmering_crystal_ore",
        ShimmeringCrystalOreBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(3.0f, 3.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .lightLevel { 7 }
                .sound(SoundType.AMETHYST)
        )
    )

    val SPHERE_MODEL_BLOCK: Block = registerBlockWithItem(
        "sphere_model_block",
        SphereModelBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(1.5f, 6.0f)
                .noOcclusion()
                .lightLevel { 5 }
                .sound(SoundType.AMETHYST)
        )
    )

    val SHIMMERING_CRYSTAL_ORE_BLOCK_ENTITY: BlockEntityType<ShimmeringCrystalOreBlockEntity> = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "shimmering_crystal_ore"),
        FabricBlockEntityTypeBuilder.create(::ShimmeringCrystalOreBlockEntity, SHIMMERING_CRYSTAL_ORE).build()
    )

    val SPHERE_MODEL_BLOCK_ENTITY: BlockEntityType<SphereModelBlockEntity> = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "sphere_model_block"),
        FabricBlockEntityTypeBuilder.create(::SphereModelBlockEntity, SPHERE_MODEL_BLOCK).build()
    )

    fun register() {
        // Touching this object registers all block constants above.
    }

    fun creativeTabItems(): List<Item> {
        return blockItems.toList()
    }

    private fun registerBlockWithItem(path: String, block: Block): Block {
        val id = ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
        val registeredBlock = Registry.register(BuiltInRegistries.BLOCK, id, block)
        val item = Registry.register(
            BuiltInRegistries.ITEM,
            id,
            BlockItem(registeredBlock, Item.Properties())
        )
        blockItems += item
        return registeredBlock
    }
}
