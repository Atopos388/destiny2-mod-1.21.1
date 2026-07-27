package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.TaczGunPackModel
import atopos.destiny2.client.particle.bedrock.BedrockParticleEngine
import atopos.destiny2.client.weapon.DestinyWeaponAimClient
import atopos.destiny2.client.weapon.TaczGunPackResources
import atopos.destiny2.common.item.TaczGunPackWeaponItem
import atopos.destiny2.common.weapon.TaczGunPackItem
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.renderer.GeoItemRenderer
import software.bernie.geckolib.util.RenderUtil

/**
 * One renderer for every TaCZ-style gun pack.
 *
 * GeckoLib renderer for registered gun-pack items.
 *
 * TaCZ positioning nodes belong to TaCZ's Bedrock renderer coordinate system
 * and must not be multiplied into this renderer's PoseStack. Converted
 * GeckoLib rigs continue to use their camera/constraint compatibility mapping.
 */
class TaczGunPackItemRenderer :
    GeoItemRenderer<TaczGunPackWeaponItem>(TaczGunPackModel()),
    BuiltinItemRendererRegistry.DynamicItemRenderer {

    private fun isFirstPerson(): Boolean =
        renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
            renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND

    override fun render(
        stack: ItemStack,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        overlay: Int
    ) {
        val packItem = stack.item as? TaczGunPackItem
        val definition = packItem?.let { TaczGunPackResources.definition(it.gunPackId) }
        val firstPerson =
            mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
                mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND

        matrices.pushPose()
        try {
            if (firstPerson) {
                publishRig(definition)
                val partialTick = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)
                DestinyWeaponAimClient.applyFirstPersonTransform(matrices, partialTick)
            }

            val attachmentKey = packItem?.gunPackId?.toString() ?: "unknown"
            val modelResource = packItem?.let { TaczGunPackResources.model(it.gunPackId) }
            if (modelResource == null) {
                renderByItem(stack, mode, matrices, vertexConsumers, light, overlay)
            } else {
                BedrockParticleEngine.withAttachmentContext(attachmentKey, modelResource) {
                    renderByItem(stack, mode, matrices, vertexConsumers, light, overlay)
                }
            }
            if (firstPerson) publishRig(definition)
        } finally {
            matrices.popPose()
        }
    }

    override fun renderRecursively(
        poseStack: PoseStack,
        animatable: TaczGunPackWeaponItem,
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
        val definition = TaczGunPackResources.definition(animatable.gunPackId)
        if (!isFirstPerson() && bone.name in definition?.firstPersonOnlyBones.orEmpty()) return

        if (isFirstPerson()) {
            if (bone.name == TaczGunPackResources.CAMERA) {
                DestinyWeaponAimClient.publishCameraBone(bone)
            } else if (bone.name == TaczGunPackResources.CONSTRAINT) {
                DestinyWeaponAimClient.publishConstraintBone(bone)
            }

            poseStack.pushPose()
            RenderUtil.prepMatrixForBone(poseStack, bone)
            BedrockParticleEngine.renderAttached(
                animatable.gunPackId.toString(),
                TaczGunPackResources.model(animatable.gunPackId),
                bone.name,
                poseStack,
                bufferSource,
                partialTick
            )
            poseStack.popPose()
        }

        if (isFirstPerson() && bone.name in definition?.skinBones.orEmpty()) {
            val player = Minecraft.getInstance().player ?: return
            val armType = RenderType.entityTranslucent(player.skin.texture())
            super.renderRecursively(
                poseStack,
                animatable,
                bone,
                armType,
                bufferSource,
                bufferSource.getBuffer(armType),
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

    private fun publishRig(definition: TaczGunPackResources.GunPackDefinition?) {
        geoModel.getBone(TaczGunPackResources.CAMERA).ifPresent(DestinyWeaponAimClient::publishCameraBone)
        if (definition?.usesStandardPositioning == true) {
            // TaCZ view matrices are authored for its own Bedrock renderer.
            // Applying them here double-counts the render origin and moves the
            // weapon vertically in GeckoLib.
            DestinyWeaponAimClient.clearPositioningViews()
        } else {
            DestinyWeaponAimClient.clearPositioningViews()
            geoModel.getBone(TaczGunPackResources.CONSTRAINT)
                .ifPresent(DestinyWeaponAimClient::publishConstraintBone)
        }
    }
}
