package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.HuntingMarkEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import org.joml.AxisAngle4f

class HuntingMarkRenderer(context: EntityRendererProvider.Context) : EntityRenderer<HuntingMarkEntity>(context) {
    override fun render(entity: HuntingMarkEntity, yaw: Float, partialTick: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        pose.pushPose()
        pose.mulPose(entityRenderDispatcher.cameraOrientation())
        pose.scale(0.75f, 0.75f, 0.75f)
        val consumer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE))
        val last = pose.last()
        vertex(consumer, last, -0.5f, -0.5f, 0f, 0f, 1f)
        vertex(consumer, last, 0.5f, -0.5f, 0f, 1f, 1f)
        vertex(consumer, last, 0.5f, 0.5f, 0f, 1f, 0f)
        vertex(consumer, last, -0.5f, 0.5f, 0f, 0f, 0f)
        pose.popPose()
        super.render(entity, yaw, partialTick, pose, buffers, light)
    }

    private fun vertex(consumer: VertexConsumer, pose: PoseStack.Pose, x: Float, y: Float, z: Float, u: Float, v: Float) {
        consumer.addVertex(pose, x, y, z).setColor(255, 255, 255, 235).setUv(u, v)
            .setOverlay(0).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0f, 0f, 1f)
    }

    override fun getTextureLocation(entity: HuntingMarkEntity): ResourceLocation = TEXTURE

    private companion object {
        val TEXTURE = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/gui/hunting_mark.png")
    }
}
