package atopos.destiny2.common.classes

import atopos.destiny2.common.stats.StatType
import net.minecraft.resources.ResourceLocation

object DestinyClasses {
    val HUNTER = DestinyClass(
        id = DestinyClass.HUNTER,
        baseStats = mapOf(
            StatType.WEAPONS to 30,
            StatType.HEALTH to 30,
            StatType.CLASS to 30,
            StatType.GRENADE to 30,
            StatType.SUPER to 30,
            StatType.MELEE to 30
        ),
        growthCurves = mapOf(
            StatType.WEAPONS to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.HEALTH to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.CLASS to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.GRENADE to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.SUPER to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.MELEE to GrowthCurve(GrowthCurveType.LINEAR, 1.0f)
        ),
        visualFeatures = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/gui/hunter_overlay.png")
    )

    val TITAN = DestinyClass(
        id = DestinyClass.TITAN,
        baseStats = mapOf(
            StatType.WEAPONS to 30,
            StatType.HEALTH to 30,
            StatType.CLASS to 30,
            StatType.GRENADE to 30,
            StatType.SUPER to 30,
            StatType.MELEE to 30
        ),
        growthCurves = mapOf(
            StatType.WEAPONS to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.HEALTH to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.CLASS to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.GRENADE to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.SUPER to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.MELEE to GrowthCurve(GrowthCurveType.LINEAR, 1.0f)
        ),
        visualFeatures = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/gui/titan_overlay.png")
    )

    val WARLOCK = DestinyClass(
        id = DestinyClass.WARLOCK,
        baseStats = mapOf(
            StatType.WEAPONS to 30,
            StatType.HEALTH to 30,
            StatType.CLASS to 30,
            StatType.GRENADE to 30,
            StatType.SUPER to 30,
            StatType.MELEE to 30
        ),
        growthCurves = mapOf(
            StatType.WEAPONS to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.HEALTH to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.CLASS to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.GRENADE to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.SUPER to GrowthCurve(GrowthCurveType.LINEAR, 1.0f),
            StatType.MELEE to GrowthCurve(GrowthCurveType.LINEAR, 1.0f)
        ),
        visualFeatures = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/gui/warlock_overlay.png")
    )

    fun register() {
        // In a real mod, we would register these to a Registry<DestinyClass>
        // For now, this object holds them.
    }
}
