package atopos.destiny2.common.weapon

import net.minecraft.resources.ResourceLocation

data class WeaponPerk(
    val id: ResourceLocation,
    val name: String,
    val description: String,
    // Effect logic would go here, e.g., a modifier function
    val statModifiers: Map<String, Int> = emptyMap()
)

data class WeaponFrame(
    val id: ResourceLocation,
    val name: String,
    val description: String,
    val bonusStats: WeaponStats
)

// The "Database" entry for a weapon
open class DestinyWeaponDefinition(
    val id: ResourceLocation,
    val name: String,
    val type: WeaponType,
    val frame: WeaponFrame,
    val baseStats: WeaponStats,
    val possiblePerksColumn1: List<WeaponPerk>,
    val possiblePerksColumn2: List<WeaponPerk>,
    val possiblePerksColumn3: List<WeaponPerk>,
    val possiblePerksColumn4: List<WeaponPerk>
)

// The specific instance (what the player holds)
data class DestinyWeaponInstance(
    val definitionId: ResourceLocation,
    val selectedPerks: List<ResourceLocation>,
    val masterworkLevel: Int,
    val killCount: Int
)
