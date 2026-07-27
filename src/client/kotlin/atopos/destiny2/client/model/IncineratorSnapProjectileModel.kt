package atopos.destiny2.client.model

import atopos.destiny2.common.entity.IncineratorSnapProjectile
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class IncineratorSnapProjectileModel : GeoModel<IncineratorSnapProjectile>() {
    override fun getModelResource(animatable: IncineratorSnapProjectile): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "geo/solar_snap_projectile.geo.json")
    }

    override fun getTextureResource(animatable: IncineratorSnapProjectile): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/entity/solar_snap_projectile.png")
    }

    override fun getAnimationResource(animatable: IncineratorSnapProjectile): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath("destiny2-mod", "animations/solar_snap.animation.json")
    }
}
