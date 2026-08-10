// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.ThunderclapPlayerProxyModel
import atopos.destiny2.common.entity.ThunderclapPlayerProxyEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.renderer.GeoEntityRenderer

class ThunderclapPlayerProxyRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<ThunderclapPlayerProxyEntity>(context, ThunderclapPlayerProxyModel()) {

    init {
        shadowRadius = 0.5f
        addRenderLayer(ThunderclapBendyLimbLayer(this))
        addRenderLayer(ThunderclapHandChargeLayer(this))
    }

    override fun getTextureLocation(entity: ThunderclapPlayerProxyEntity): ResourceLocation =
        entity.targetPlayerId
            ?.let { Minecraft.getInstance().level?.getPlayerByUUID(it) as? AbstractClientPlayer }
            ?.skin
            ?.texture()
            ?: super.getTextureLocation(entity)

    override fun applyRotations(
        animatable: ThunderclapPlayerProxyEntity,
        poseStack: PoseStack,
        ageInTicks: Float,
        rotationYaw: Float,
        partialTick: Float,
        nativeScale: Float
    ) {
        super.applyRotations(animatable, poseStack, ageInTicks, animatable.yRot, partialTick, nativeScale)
    }
}
