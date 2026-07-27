package atopos.destiny2.client.model

import atopos.destiny2.common.entity.SolarFlareEntity
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class SolarFlareModel : GeoModel<SolarFlareEntity>() {
    override fun getModelResource(animatable: SolarFlareEntity): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "geo/solar_flare.geo.json")
    }

    override fun getTextureResource(animatable: SolarFlareEntity): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/entity/solar_flare.png")
    }

    override fun getAnimationResource(animatable: SolarFlareEntity): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "animations/solar_grenade_and_flare.animation.json")
    }
}
