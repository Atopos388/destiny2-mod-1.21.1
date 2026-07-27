package atopos.destiny2.client.model

import atopos.destiny2.common.entity.WellOfRadianceEntity
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class WellOfRadianceModel : GeoModel<WellOfRadianceEntity>() {
    override fun getModelResource(animatable: WellOfRadianceEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "geo/well_of_radiance.geo.json")

    override fun getTextureResource(animatable: WellOfRadianceEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/entity/well_of_radiance.png")

    override fun getAnimationResource(animatable: WellOfRadianceEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "animations/well_of_radiance.animation.json")
}
