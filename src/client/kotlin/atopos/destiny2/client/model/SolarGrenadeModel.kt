package atopos.destiny2.client.model

import atopos.destiny2.common.entity.SolarGrenadeEntity
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class SolarGrenadeModel : GeoModel<SolarGrenadeEntity>() {
    override fun getModelResource(animatable: SolarGrenadeEntity): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "geo/solar_grenade.geo.json")
    }

    override fun getTextureResource(animatable: SolarGrenadeEntity): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/item/solar_grenade.png")
    }

    override fun getAnimationResource(animatable: SolarGrenadeEntity): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "animations/solar_grenade_and_flare.animation.json")
    }
}
