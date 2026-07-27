package atopos.destiny2.client.model

import atopos.destiny2.common.item.DestinyArmorItem
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class DestinyArmorModel : GeoModel<DestinyArmorItem>() {
    override fun getModelResource(animatable: DestinyArmorItem): ResourceLocation {
        return animatable.modelSet.model
    }

    override fun getTextureResource(animatable: DestinyArmorItem): ResourceLocation {
        return animatable.modelSet.texture
    }

    override fun getAnimationResource(animatable: DestinyArmorItem): ResourceLocation {
        return animatable.modelSet.animation
    }
}
