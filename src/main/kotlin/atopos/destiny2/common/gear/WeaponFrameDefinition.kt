package atopos.destiny2.common.gear

import net.minecraft.resources.ResourceLocation

data class WeaponFrameDefinition(
    val id: ResourceLocation,
    val displayName: String,
    val description: String,
    val burstCount: Int = 1,
    val burstIntervalTicks: Int = 0,
    val projectileSpeed: Float = 1.5f,
    val cooldownTicks: Int = 20,
    val magazineSize: Int = 1,
    val reloadTicks: Int = 36
)
