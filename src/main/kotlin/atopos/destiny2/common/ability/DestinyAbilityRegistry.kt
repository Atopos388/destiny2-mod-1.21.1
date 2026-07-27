package atopos.destiny2.common.ability

import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.player.PlayerDestinyData
import net.minecraft.resources.ResourceLocation

object DestinyAbilityRegistry {
    private const val MOD_ID = "destiny2-mod"

    private val abilities = linkedMapOf<ResourceLocation, ExecutableDestinyAbility>()
    private val loadouts = mutableMapOf<DestinySubclassType, Map<AbilitySlot, ResourceLocation>>()

    private val ARC_TITAN_PLACEHOLDERS = AbilitySlot.entries.map { slot ->
        PlaceholderAbility(
            id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "arc_titan_${slot.key}"),
            slot = slot,
            displayName = "电弧泰坦 ${slot.key}"
        )
    }

    fun register() {
        abilities.clear()
        loadouts.clear()

        registerAll(SolarWarlockAbilities.ALL)
        registerAll(VoidHunterAbilities.ALL)
        registerAll(ARC_TITAN_PLACEHOLDERS)

        loadouts[DestinySubclassType.SOLAR_WARLOCK] = loadout(SolarWarlockAbilities.ALL)
        loadouts[DestinySubclassType.VOID_HUNTER] = loadout(VoidHunterAbilities.ALL)
        loadouts[DestinySubclassType.ARC_TITAN] = loadout(ARC_TITAN_PLACEHOLDERS)
    }

    fun abilityFor(subclass: DestinySubclassType, slot: AbilitySlot): ExecutableDestinyAbility? {
        val abilityId = loadouts[subclass]?.get(slot) ?: return null
        return abilities[abilityId]
    }

    fun abilityFor(data: PlayerDestinyData, slot: AbilitySlot): ExecutableDestinyAbility? {
        val selectedId = data.subclassConfig.selectedAbilities[slot]
        if (!selectedId.isNullOrBlank()) {
            abilities.values.firstOrNull { it.id.toString() == selectedId }?.let { return it }
        }
        return abilityFor(data.subclass, slot)
    }

    private fun registerAll(newAbilities: Iterable<ExecutableDestinyAbility>) {
        newAbilities.forEach { ability ->
            abilities[ability.id] = ability
        }
    }

    private fun loadout(abilities: Iterable<ExecutableDestinyAbility>): Map<AbilitySlot, ResourceLocation> {
        return abilities.associate { it.slot to it.id }
    }
}
