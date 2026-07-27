// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.model

import atopos.destiny2.common.entity.FallenCaptainEntity
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class FallenCaptainModel : GeoModel<FallenCaptainEntity>() {
    override fun getModelResource(animatable: FallenCaptainEntity): ResourceLocation = MODEL

    override fun getTextureResource(animatable: FallenCaptainEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/entity/fallen_captain.png")

    override fun getAnimationResource(animatable: FallenCaptainEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "animations/fallen_captain.animation.json")

    companion object {
        val MODEL: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "geo/fallen_captain.geo.json")
    }
}
