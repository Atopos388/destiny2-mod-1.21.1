package atopos.destiny2.common.worldgen

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.placement.PlacedFeature

object DestinyWorldgen {
    val SHIMMERING_CRYSTAL_ORE_SURFACE_FEATURE: Feature<*> = Registry.register(
        BuiltInRegistries.FEATURE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "shimmering_crystal_ore_surface"),
        ShimmeringCrystalOreSurfaceFeature()
    )

    private val SHIMMERING_CRYSTAL_ORE_PLACED_KEY: ResourceKey<PlacedFeature> = ResourceKey.create(
        Registries.PLACED_FEATURE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "shimmering_crystal_ore")
    )

    fun register() {
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            SHIMMERING_CRYSTAL_ORE_PLACED_KEY
        )
    }
}
