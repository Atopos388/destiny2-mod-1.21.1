package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.FallenSoldierModel
import atopos.destiny2.client.renderer.cloth.FallenClothRenderer
import atopos.destiny2.common.entity.FallenSoldierEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.renderer.GeoEntityRenderer

class FallenSoldierRenderer(context: EntityRendererProvider.Context) :
    GeoEntityRenderer<FallenSoldierEntity>(context, FallenSoldierModel()) {
    private val cloth = FallenClothRenderer()
    init { shadowRadius = .45f; withScale(.65f) }
    override fun getDeathMaxRotation(animatable: FallenSoldierEntity) = 0f

    override fun render(entity: FallenSoldierEntity, entityYaw: Float, partialTick: Float,
                        poseStack: PoseStack, bufferSource: MultiBufferSource, packedLight: Int) {
        cloth.begin(entity, partialTick, poseStack)
        try { super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight) }
        finally { cloth.end() }
    }

    override fun renderRecursively(poseStack: PoseStack, animatable: FallenSoldierEntity, bone: GeoBone,
        renderType: RenderType, bufferSource: MultiBufferSource, buffer: VertexConsumer, isReRender: Boolean,
        partialTick: Float, packedLight: Int, packedOverlay: Int, colour: Int) {
        if (bone.name == "root") cloth.collectBody(poseStack, bone)
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer,
            isReRender, partialTick, packedLight, packedOverlay, colour)
    }

    override fun renderCubesOfBone(poseStack: PoseStack, bone: GeoBone, buffer: VertexConsumer,
                                  packedLight: Int, packedOverlay: Int, colour: Int) {
        if (!bone.isHidden && cloth.render(poseStack, bone, buffer, packedLight, packedOverlay, colour)) return
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour)
    }
}
