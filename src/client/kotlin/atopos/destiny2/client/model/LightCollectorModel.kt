package atopos.destiny2.client.model

import atopos.destiny2.common.block.LightCollectorBlockEntity
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class LightCollectorModel : GeoModel<LightCollectorBlockEntity>() {
    override fun getModelResource(animatable: LightCollectorBlockEntity) = id("geo/light_collector.geo.json")
    override fun getTextureResource(animatable: LightCollectorBlockEntity) = id("textures/block/light_collector.png")
    override fun getAnimationResource(animatable: LightCollectorBlockEntity) = id("animations/light_collector.animation.json")
    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
}
