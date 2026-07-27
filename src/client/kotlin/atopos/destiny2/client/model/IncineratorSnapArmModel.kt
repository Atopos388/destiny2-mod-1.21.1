package atopos.destiny2.client.model

import atopos.destiny2.client.action.IncineratorSnapFirstPersonClient
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class IncineratorSnapArmModel : GeoModel<IncineratorSnapFirstPersonClient>() {
    override fun getModelResource(animatable: IncineratorSnapFirstPersonClient): ResourceLocation =
        id("geo/incinerator_snap_arm.geo.json")

    override fun getTextureResource(animatable: IncineratorSnapFirstPersonClient): ResourceLocation =
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")

    override fun getAnimationResource(animatable: IncineratorSnapFirstPersonClient): ResourceLocation =
        id("animations/incinerator_snap_arm.animation.json")

    private fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
}
