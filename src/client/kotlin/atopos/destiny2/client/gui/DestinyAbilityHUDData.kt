package atopos.destiny2.client.gui

import atopos.destiny2.Destiny2MODClient
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinySubclassConfigRegistry
import atopos.destiny2.common.player.DestinySubclassType
import net.minecraft.resources.ResourceLocation

data class DestinyAbilityHUDData(
    val accent: Int,
    val superAbility: Ability,
    val grenade: Ability,
    val melee: Ability,
    val classAbility: Ability
) {
    data class Ability(val icon: ResourceLocation?, val progress: Float)
}

object DestinyAbilityHUDDataAdapter {
    fun snapshot(now: Long = System.currentTimeMillis()): DestinyAbilityHUDData {
        val subclass = resolveSubclass()
        val definition = DestinySubclassConfigRegistry.definitionFor(subclass)

        fun ability(type: Int, slot: AbilitySlot): DestinyAbilityHUDData.Ability {
            val selectedId = DestinyHUDState.selectedAbilityId(type)
            val option = definition.abilityOptions[slot].orEmpty().firstOrNull { it.id == selectedId }
                ?: definition.abilityOptions[slot].orEmpty().firstOrNull()
            val cooldown = Destiny2MODClient.clientCooldowns[type]
            return DestinyAbilityHUDData.Ability(option?.icon, cooldownProgress(cooldown, now))
        }

        return DestinyAbilityHUDData(
            accent = when (subclass) {
                DestinySubclassType.SOLAR_WARLOCK -> 0xFFFFA641.toInt()
                DestinySubclassType.VOID_HUNTER -> 0xFF9E78E8.toInt()
                DestinySubclassType.ARC_TITAN -> 0xFF69D8F2.toInt()
            },
            superAbility = ability(DestinyNetworking.ABILITY_SUPER, AbilitySlot.SUPER)
                .copy(progress = DestinyHUDState.superEnergy / 100.0f),
            grenade = ability(DestinyNetworking.ABILITY_GRENADE, AbilitySlot.GRENADE),
            melee = ability(DestinyNetworking.ABILITY_MELEE, AbilitySlot.MELEE),
            classAbility = ability(DestinyNetworking.ABILITY_CLASS, AbilitySlot.CLASS_ABILITY)
        )
    }

    internal fun cooldownProgress(cooldown: Pair<Long, Long>?, now: Long): Float {
        if (cooldown == null || cooldown.second <= 0L || now >= cooldown.first) return 1f
        val remaining = (cooldown.first - now).coerceAtLeast(0L)
        return (1f - remaining.toFloat() / cooldown.second.toFloat()).coerceIn(0f, 1f)
    }

    private fun resolveSubclass(): DestinySubclassType {
        DestinySubclassType.entries.firstOrNull { it.displayName == DestinyHUDState.subclassName }?.let { return it }
        val selectedIds = listOf(
            DestinyHUDState.selectedAbilityId(DestinyNetworking.ABILITY_SUPER),
            DestinyHUDState.selectedAbilityId(DestinyNetworking.ABILITY_GRENADE),
            DestinyHUDState.selectedAbilityId(DestinyNetworking.ABILITY_MELEE),
            DestinyHUDState.selectedAbilityId(DestinyNetworking.ABILITY_CLASS)
        ).filter(String::isNotBlank).toSet()
        return DestinySubclassType.entries.firstOrNull { subclass ->
            DestinySubclassConfigRegistry.definitionFor(subclass).abilityOptions.values.flatten().any { it.id in selectedIds }
        } ?: DestinySubclassType.DEFAULT
    }
}
