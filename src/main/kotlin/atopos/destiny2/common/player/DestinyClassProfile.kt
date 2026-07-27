package atopos.destiny2.common.player

import net.minecraft.resources.ResourceLocation

data class DestinyClassProfile(
    val type: DestinyClassType,
    val title: String,
    val description: String,
    val baseStats: DestinyStats,
    val defaultSubclass: DestinySubclassType,
    val icon: ResourceLocation
)
