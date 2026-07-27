// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.FallenCaptainModel
import atopos.destiny2.client.particle.bedrock.BedrockParticleEngine
import atopos.destiny2.common.entity.FallenCaptainEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.entity.Entity
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.renderer.GeoEntityRenderer
import software.bernie.geckolib.util.RenderUtil

class FallenCaptainRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<FallenCaptainEntity>(context, FallenCaptainModel()) {
    init {
        shadowRadius = 0.8f
    }

    override fun render(
        entity: FallenCaptainEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        BedrockParticleEngine.withAttachmentContext(attachmentKey(entity), FallenCaptainModel.MODEL) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
        }
    }

    override fun renderRecursively(
        poseStack: PoseStack,
        animatable: FallenCaptainEntity,
        bone: GeoBone,
        renderType: RenderType,
        bufferSource: MultiBufferSource,
        buffer: com.mojang.blaze3d.vertex.VertexConsumer,
        isReRender: Boolean,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int,
        colour: Int
    ) {
        poseStack.pushPose()
        RenderUtil.prepMatrixForBone(poseStack, bone)
        BedrockParticleEngine.renderAttached(
            attachmentKey(animatable),
            FallenCaptainModel.MODEL,
            bone.name,
            poseStack,
            bufferSource,
            partialTick
        )
        poseStack.popPose()
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
            OverlayTexture.NO_OVERLAY,
            colour
        )
    }

    private fun attachmentKey(entity: Entity): String = "fallen_captain_${entity.id}"
}
