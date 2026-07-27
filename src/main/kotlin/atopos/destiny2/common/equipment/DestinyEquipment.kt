package atopos.destiny2.common.equipment

import atopos.destiny2.common.classes.DestinyClass
import atopos.destiny2.common.stats.StatType
import net.minecraft.resources.ResourceLocation

enum class EquipmentSlot {
    HEAD,
    ARMS,
    CHEST,
    LEGS,
    CLASS_ITEM
}

data class DestinyEquipmentDefinition(
    val id: ResourceLocation,
    val name: String,
    val slot: EquipmentSlot,
    val classRequirement: ResourceLocation?, // Null means any class
    val baseStatBonuses: Map<StatType, Int>,
    val energyType: String, // "Void", "Solar", etc.
    val maxEnergy: Int
)

data class DestinyEquipmentInstance(
    val definitionId: ResourceLocation,
    val energyLevel: Int,
    val stats: Map<StatType, Int>, // Randomly rolled stats
    val installedMods: List<ResourceLocation>
)
