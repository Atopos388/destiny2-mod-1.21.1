package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.HealingRiftEntity
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu

/**
 * The legacy GeckoLib geometry is intentionally hidden. The rift is rendered
 * by [HealingRiftWorldRenderer] as a ground-bound energy field.
 */
class HealingRiftRenderer(context: EntityRendererProvider.Context) :
    EntityRenderer<HealingRiftEntity>(context) {

    override fun getTextureLocation(entity: HealingRiftEntity): ResourceLocation =
        InventoryMenu.BLOCK_ATLAS
}
