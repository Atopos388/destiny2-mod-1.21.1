package atopos.destiny2.common.ability

import atopos.destiny2.common.player.AbilitySlot
import net.minecraft.resources.ResourceLocation

abstract class AbstractExecutableAbility(
    override val id: ResourceLocation,
    override val slot: AbilitySlot,
    override val displayName: String,
    override val baseCooldownTicks: Int
) : ExecutableDestinyAbility
