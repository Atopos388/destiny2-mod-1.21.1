package atopos.destiny2.common.ability

import atopos.destiny2.common.player.AbilitySlot
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

data class DestinyAbilityContext(
    val player: ServerPlayer,
    val slot: AbilitySlot,
    val extraData: Int
)

interface ExecutableDestinyAbility {
    val id: ResourceLocation
    val slot: AbilitySlot
    val displayName: String
    val baseCooldownTicks: Int

    fun cast(context: DestinyAbilityContext): Boolean
}
