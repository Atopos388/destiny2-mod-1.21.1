package atopos.destiny2.client.renderer

import atopos.destiny2.client.combat.HunterMeleeFirstPersonClient
import atopos.destiny2.client.model.HunterMeleeFirstPersonModel
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.renderer.GeoObjectRenderer
import software.bernie.geckolib.util.RenderUtil
import org.joml.Vector3f

class HunterMeleeFirstPersonRenderer(
    model: HunterMeleeFirstPersonModel
) : GeoObjectRenderer<HunterMeleeFirstPersonClient>(model) {
    override fun getTextureLocation(animatable: HunterMeleeFirstPersonClient): ResourceLocation =
        HunterMeleeFirstPersonModel.TEXTURE

    override fun getInstanceId(animatable: HunterMeleeFirstPersonClient): Long = 0L

    override fun renderRecursively(
        poseStack: PoseStack,
        animatable: HunterMeleeFirstPersonClient,
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
        // Bone names are authored from the model's rig perspective and are
        // visually reversed in first person: charged melee uses lefthand,
        // while the on-screen left-hand grenade throw uses righthand.
        val chargedMeleeActive = HunterMeleeFirstPersonClient.isChargedMeleeActive()
        val throwingBone = if (chargedMeleeActive) "lefthand" else "righthand"
        val moveThrowingHandTowardCamera =
            HunterMeleeFirstPersonClient.isThrowingHandActionActive() && bone.name == throwingBone
        if (moveThrowingHandTowardCamera) {
            poseStack.pushPose()
            poseStack.translate(0.0, 0.0, THROWING_HAND_CAMERA_OFFSET_Z.toDouble())
        }
        try {
            if (HunterMeleeFirstPersonClient.isThrowingHandActionActive() && bone.name == throwingBone) {
                poseStack.pushPose()
                try {
                    RenderUtil.prepMatrixForBone(poseStack, bone)
                    val viewPosition = poseStack.last().pose().transformPosition(
                        Vector3f(
                            if (chargedMeleeActive) -LOCATOR_X else LOCATOR_X,
                            LOCATOR_Y,
                            LOCATOR_Z
                        )
                    )
                    val camera = Minecraft.getInstance().gameRenderer.mainCamera
                    // righthand_pos occupies +X and is the arm visible on screen-left.
                    val worldOffset = camera.rotation().transform(viewPosition, Vector3f())
                    HunterMeleeFirstPersonClient.updateThrowLocator(
                        Minecraft.getInstance().player ?: return,
                        partialTick,
                        camera.position.add(
                            worldOffset.x.toDouble(),
                            worldOffset.y.toDouble(),
                            worldOffset.z.toDouble()
                        )
                    )
                } finally {
                    poseStack.popPose()
                }
            }

            // The knife hierarchy remains hidden during either throwing action.
            if (HunterMeleeFirstPersonClient.isThrowingHandActionActive() && bone.name == "bone") return

            val selectedType = if (bone.name in PLAYER_ARM_BONES) {
                val player = Minecraft.getInstance().player ?: return
                RenderType.entityTranslucent(player.skin.texture())
            } else {
                // righthand_pos owns the authored arm cube but also has the knife
                // below it. Reset every non-arm child to the knife atlas.
                RenderType.entityTranslucent(HunterMeleeFirstPersonModel.TEXTURE)
            }
            super.renderRecursively(
                poseStack,
                animatable,
                bone,
                selectedType,
                bufferSource,
                bufferSource.getBuffer(selectedType),
                isReRender,
                partialTick,
                packedLight,
                packedOverlay,
                colour
            )
        } finally {
            if (moveThrowingHandTowardCamera) poseStack.popPose()
        }
    }

    private companion object {
        val PLAYER_ARM_BONES = setOf("lefthand_pos", "righthand_pos")
        const val LOCATOR_X = 4.39063f / 16.0f
        const val LOCATOR_Y = 18.76563f / 16.0f
        const val LOCATOR_Z = 8.28125f / 16.0f
        const val THROWING_HAND_CAMERA_OFFSET_Z = 2.0f / 16.0f
    }
}
