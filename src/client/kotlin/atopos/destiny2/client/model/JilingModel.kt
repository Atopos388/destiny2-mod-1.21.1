// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.model

import atopos.destiny2.common.entity.JilingEntity
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class JilingModel : GeoModel<JilingEntity>() {
    override fun getModelResource(animatable: JilingEntity): ResourceLocation = MODEL

    override fun getTextureResource(animatable: JilingEntity): ResourceLocation = DEFAULT_TEXTURE

    override fun getAnimationResource(animatable: JilingEntity): ResourceLocation = ANIMATION

    companion object {
        val MODEL: ResourceLocation = id("geo/entity/jiling.geo.json")
        val ANIMATION: ResourceLocation = id("animations/entity/jiling.animation.json")
        val DEFAULT_TEXTURE: ResourceLocation = id("textures/entity/jiling/texture3.png")
        val MATERIALS: ResourceLocation = id("geckolib_multitexture/entity/jiling.bone_textures.json")

        private fun id(path: String): ResourceLocation =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
    }
}
