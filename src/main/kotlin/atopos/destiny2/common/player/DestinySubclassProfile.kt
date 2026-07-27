package atopos.destiny2.common.player

import net.minecraft.resources.ResourceLocation

data class DestinySubclassProfile(
    val type: DestinySubclassType,
    val title: String,
    val element: String,
    val description: String,
    val icon: ResourceLocation
)
