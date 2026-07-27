package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.WellOfRadianceModel
import atopos.destiny2.common.entity.WellOfRadianceEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.renderer.GeoEntityRenderer

class WellOfRadianceRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<WellOfRadianceEntity>(context, WellOfRadianceModel()) {

    init {
        shadowRadius = 0f
        shadowStrength = 0f
    }

    override fun render(
        entity: WellOfRadianceEntity,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int
    ) {
        poseStack.pushPose()
        poseStack.translate(0.0, 1.0, 0.0)
        poseStack.scale(1.5f, 1.5f, 1.5f)
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight)
        poseStack.popPose()
    }

    override fun getRenderType(
        animatable: WellOfRadianceEntity,
        texture: ResourceLocation,
        bufferSource: MultiBufferSource?,
        partialTick: Float
    ): RenderType = RenderType.entityTranslucentEmissive(texture)
}
