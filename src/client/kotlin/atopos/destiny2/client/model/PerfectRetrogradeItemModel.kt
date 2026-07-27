package atopos.destiny2.client.model

import atopos.destiny2.common.item.PerfectRetrogradeItem
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class PerfectRetrogradeItemModel : GeoModel<PerfectRetrogradeItem>() {
    override fun getModelResource(animatable: PerfectRetrogradeItem): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "geo/perfect_retrograde.geo.json")
    }

    override fun getTextureResource(animatable: PerfectRetrogradeItem): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/item/perfect_retrograde.png")
    }

    override fun getAnimationResource(animatable: PerfectRetrogradeItem): ResourceLocation {
        // 使用用户指定的动画文件 wanmeinixing.animation.json
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "animations/wanmeinixing.animation.json")
    }
}
