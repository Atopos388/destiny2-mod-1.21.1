// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.JilingModel
import atopos.destiny2.common.entity.JilingEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.renderer.GeoEntityRenderer
import kotlin.math.sin

class JilingRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<JilingEntity>(context, JilingModel()) {
    init {
        shadowRadius = 0.22f
    }

    override fun render(
        entity: JilingEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        poseStack.pushPose()
        if (!entity.isAwakeningActor()) {
            poseStack.translate(0.0, sin((entity.tickCount + partialTick) * 0.08) * 0.04, 0.0)
        }
        super.render(
            entity,
            entityYaw,
            partialTick,
            poseStack,
            bufferSource,
            if (entity.isAwakeningActor()) LightTexture.FULL_BRIGHT else packedLight
        )
        poseStack.popPose()
    }

    override fun renderRecursively(
        poseStack: PoseStack,
        animatable: JilingEntity,
        bone: GeoBone,
        renderType: RenderType,
        bufferSource: MultiBufferSource,
        buffer: VertexConsumer,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        colour: Int
    ) {
        val material = GeckoLibMultiTextureResources.definition(JilingModel.MATERIALS)?.bones?.get(bone.name)
        if (material != null) {
            val boneRenderType = if (material.translucent) {
                RenderType.entityTranslucent(material.texture)
            } else {
                RenderType.entityCutoutNoCull(material.texture)
            }
            super.renderRecursively(
                poseStack,
                animatable,
                bone,
                boneRenderType,
                bufferSource,
                bufferSource.getBuffer(boneRenderType),
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                colour
            )
            return
        }

        super.renderRecursively(
            poseStack,
            animatable,
            bone,
            renderType,
            bufferSource,
            buffer,
            isReRender,
            partialTick,
            packedLight,
            packedOverlay,
            colour
        )
    }
}
