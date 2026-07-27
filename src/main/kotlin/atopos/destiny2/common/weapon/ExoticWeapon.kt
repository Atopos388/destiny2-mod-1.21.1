package atopos.destiny2.common.weapon

import net.minecraft.resources.ResourceLocation

class ExoticWeaponDefinition(
    id: ResourceLocation,
    name: String,
    type: WeaponType,
    frame: WeaponFrame,
    baseStats: WeaponStats,
    val uniquePerk: WeaponPerk,
    // Exotics usually have fixed perks
    fixedPerks: List<WeaponPerk>,
    val visualEffects: ResourceLocation, // Path to special renderer/shader
    val questId: ResourceLocation? // Link to a quest
) : DestinyWeaponDefinition(
    id, name, type, frame, baseStats,
    // For exotics, pools are just the fixed perk
    listOf(fixedPerks[0]), 
    if (fixedPerks.size > 1) listOf(fixedPerks[1]) else emptyList(),
    if (fixedPerks.size > 2) listOf(fixedPerks[2]) else emptyList(),
    emptyList()
)
