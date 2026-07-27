package atopos.destiny2.common.worldgen

import atopos.destiny2.common.block.DestinyBlocks
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration

class ShimmeringCrystalOreSurfaceFeature : Feature<NoneFeatureConfiguration>(NoneFeatureConfiguration.CODEC) {
    override fun place(context: FeaturePlaceContext<NoneFeatureConfiguration>): Boolean {
        val origin = context.origin()
        val random = context.random()
        val level = context.level()

        repeat(8) {
            val pos = origin.offset(
                random.nextInt(7) - 3,
                random.nextInt(7) - 3,
                random.nextInt(7) - 3
            )

            val placePos = pos.above()
            if (canGrowOnSurface(level.getBlockState(pos), level.getBlockState(placePos))) {
                level.setBlock(placePos, DestinyBlocks.SHIMMERING_CRYSTAL_ORE.defaultBlockState(), 3)
                return true
            }
        }

        return false
    }

    private fun canGrowOnSurface(
        support: net.minecraft.world.level.block.state.BlockState,
        target: net.minecraft.world.level.block.state.BlockState
    ): Boolean {
        return target.isAir && (support.`is`(BlockTags.STONE_ORE_REPLACEABLES) || support.`is`(BlockTags.DEEPSLATE_ORE_REPLACEABLES))
    }
}
