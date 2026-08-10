package atopos.destiny2.common.weapon

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * Marks a GeckoLib item as being backed by a TaCZ-style gun-pack definition.
 *
 * Gameplay remains server-authoritative in the concrete item. The pack id only
 * selects client resources, authored positioning bones, arms and keyframes.
 */
interface TaczGunPackItem {
    val gunPackId: ResourceLocation

    /**
     * Fixed legacy weapons return [gunPackId]. The generic gun item overrides
     * this and stores the selected definition id on the stack, matching TaCZ's
     * one-item/many-guns design.
     */
    fun gunPackId(stack: ItemStack): ResourceLocation = gunPackId
}

fun ItemStack.gunPackIdOrNull(): ResourceLocation? =
    (item as? TaczGunPackItem)?.gunPackId(this)
