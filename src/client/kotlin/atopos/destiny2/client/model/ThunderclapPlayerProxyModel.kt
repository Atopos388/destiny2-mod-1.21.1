// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.model

import atopos.destiny2.common.entity.ThunderclapPlayerProxyEntity
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class ThunderclapPlayerProxyModel : GeoModel<ThunderclapPlayerProxyEntity>() {
    override fun getModelResource(animatable: ThunderclapPlayerProxyEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "geo/thunderclap_player.geo.json")

    override fun getTextureResource(animatable: ThunderclapPlayerProxyEntity): ResourceLocation =
        ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")

    override fun getAnimationResource(animatable: ThunderclapPlayerProxyEntity): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "animations/thunderclap_player.animation.json")
}
