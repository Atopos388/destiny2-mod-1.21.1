package atopos.destiny2.common.weapon

import net.minecraft.resources.ResourceLocation

/**
 * Marks a GeckoLib item as being backed by a TaCZ-style gun-pack definition.
 *
 * Gameplay remains server-authoritative in the concrete item. The pack id only
 * selects client resources, authored positioning bones, arms and keyframes.
 */
interface TaczGunPackItem {
    val gunPackId: ResourceLocation
}
