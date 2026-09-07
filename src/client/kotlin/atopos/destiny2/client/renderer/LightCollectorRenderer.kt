package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.LightCollectorModel
import atopos.destiny2.common.block.LightCollectorBlockEntity
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import software.bernie.geckolib.renderer.GeoBlockRenderer

class LightCollectorRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) :
    GeoBlockRenderer<LightCollectorBlockEntity>(LightCollectorModel()) {
    // The visible model is two blocks tall; distance culling remains enabled.
    override fun shouldRenderOffScreen(blockEntity: LightCollectorBlockEntity) = true
    override fun getViewDistance() = 48
}
