package atopos.destiny2.client.model

import atopos.destiny2.common.entity.FallenSoldierEntity
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class FallenSoldierModel : GeoModel<FallenSoldierEntity>() {
    override fun getModelResource(animatable: FallenSoldierEntity) = id("geo/fallen_soldier.geo.json")
    override fun getTextureResource(animatable: FallenSoldierEntity) = id("textures/entity/fallen_soldier.png")
    override fun getAnimationResource(animatable: FallenSoldierEntity) = id("animations/fallen_soldier.animation.json")
    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
}
