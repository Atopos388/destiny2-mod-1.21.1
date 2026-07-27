package atopos.destiny2.common.ability

import net.minecraft.resources.ResourceLocation

enum class AbilityType {
    JUMP,
    MELEE,
    GRENADE,
    SUPER,
    CLASS_ABILITY
}

enum class DamageType {
    KINETIC,
    ARC,
    SOLAR,
    VOID,
    STASIS,
    STRAND
}

data class DestinyAbility(
    val id: ResourceLocation,
    val type: AbilityType,
    val name: String,
    val description: String,
    val cooldownSeconds: Int,
    val damageType: DamageType,
    // Synergy tags (e.g., "suppress", "ignite")
    val synergyTags: List<String> = emptyList()
)

data class DestinySubclass(
    val id: ResourceLocation,
    val name: String,
    val element: DamageType,
    val superAbility: DestinyAbility,
    val classAbility: DestinyAbility,
    val jumps: List<DestinyAbility>,
    val melees: List<DestinyAbility>,
    val grenades: List<DestinyAbility>
)
