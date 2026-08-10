package atopos.destiny2.common.classes

import atopos.destiny2.common.stats.StatType
import net.minecraft.resources.ResourceLocation

data class DestinyClass(
    val id: ResourceLocation,
    val baseStats: Map<StatType, Int>,
    val growthCurves: Map<StatType, GrowthCurve>,
    // Placeholder for visual features (e.g., path to model/texture)
    val visualFeatures: ResourceLocation
) {
    companion object {
        val HUNTER = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "hunter")
        val WARLOCK = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "warlock")
        val TITAN = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "titan")
    }
}

enum class GrowthCurveType {
    LINEAR,
    EXPONENTIAL,
    LOGARITHMIC
}

data class GrowthCurve(
    val type: GrowthCurveType,
    val factor: Float
) {
    fun calculate(level: Int, base: Int): Float {
        return when (type) {
            GrowthCurveType.LINEAR -> base + (level * factor)
            GrowthCurveType.EXPONENTIAL -> base * Math.pow(factor.toDouble(), level.toDouble()).toFloat()
            GrowthCurveType.LOGARITHMIC -> base + (Math.log(level.toDouble() + 1) * factor).toFloat()
        }
    }
}
