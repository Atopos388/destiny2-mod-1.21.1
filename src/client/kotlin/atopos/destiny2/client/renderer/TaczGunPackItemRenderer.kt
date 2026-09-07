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
import kotlin.math.cos
import kotlin.math.sin

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

    internal fun isFirstPerson(): Boolean =
        renderPerspective == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND ||
            renderPerspective == ItemDisplayContext.FIRST_PERSON_LEFT_HAND

    override fun render(stack: ItemStack, mode: ItemDisplayContext, matrices: PoseStack, vertexConsumers: MultiBufferSource, light: Int, overlay: Int) {
        if (mode == ItemDisplayContext.GUI) {
            GuiItemModelFit.render("TaczGunPackItemRenderer:" + stack.item.toString() + ":" + stack.components.toString(), matrices, vertexConsumers) { pose, buffers ->
                renderUnfitted(stack, mode, pose, buffers, light, overlay)
            }
        } else renderUnfitted(stack, mode, matrices, vertexConsumers, light, overlay)
    }

    private fun renderUnfitted(
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
            if (mode == ItemDisplayContext.GUI) {
                val angles = when (packItem?.gunPackId?.path) {
                    "forgotten_name" -> floatArrayOf(55.94f, -82.44f, 55.83f)
                    "izanagis_burden" -> floatArrayOf(-180f, 82.5f, 138f)
                    else -> null
                }
                angles?.let { a -> matrices.mulPose(org.joml.Quaternionf().rotationXYZ(
                    Math.toRadians(a[0].toDouble()).toFloat(), Math.toRadians(a[1].toDouble()).toFloat(),
                    Math.toRadians(a[2].toDouble()).toFloat())) }
            }

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

        if (renderPerspective == ItemDisplayContext.GUI) {
            renderStaticGuiBone(
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
            return
        }

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

        if (isFirstPerson() && !isReRender && bone.name in definition?.skinBones.orEmpty()) {
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

    /** Keeps item-slot previews at the authored bind pose while gameplay controllers run. */
    private fun renderStaticGuiBone(
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
        val animated = bone.saveSnapshot()
        val initial = bone.initialSnapshot
        if (initial != null) {
            bone.updatePosition(initial.offsetX, initial.offsetY, initial.offsetZ)
            bone.updateRotation(initial.rotX, initial.rotY, initial.rotZ)
            bone.updateScale(initial.scaleX, initial.scaleY, initial.scaleZ)
        }
        try {
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
        } finally {
            bone.updatePosition(animated.offsetX, animated.offsetY, animated.offsetZ)
            bone.updateRotation(animated.rotX, animated.rotY, animated.rotZ)
            bone.updateScale(animated.scaleX, animated.scaleY, animated.scaleZ)
        }
    }

    private fun publishRig(definition: TaczGunPackResources.GunPackDefinition?) {
        geoModel.getBone(TaczGunPackResources.CAMERA).ifPresent(DestinyWeaponAimClient::publishCameraBone)
        if (
            definition?.usesStandardPositioning == true &&
            definition.applyGeckoPositioning
        ) {
            val idle = definition.matrix(TaczGunPackResources.IDLE_VIEW)
            val iron = definition.matrix(TaczGunPackResources.IRON_VIEW)
            if (idle != null && iron != null) {
                DestinyWeaponAimClient.publishPositioningViews(idle, iron)
            } else {
                DestinyWeaponAimClient.clearPositioningViews()
            }
        } else {
            DestinyWeaponAimClient.clearPositioningViews()
            geoModel.getBone(TaczGunPackResources.CONSTRAINT)
                .ifPresent(DestinyWeaponAimClient::publishConstraintBone)
        }
    }

    /**
     * Rotates only the current model basis in the GUI plane. The translation is
     * intentionally left untouched so the icon stays centred in its slot.
     */
    private fun applyScreenSpaceGuiTilt(matrices: PoseStack, degrees: Float) {
        val radians = Math.toRadians(degrees.toDouble()).toFloat()
        val cosine = cos(radians)
        val sine = sin(radians)
        val pose = matrices.last().pose()

        val p00 = pose.m00()
        val p01 = pose.m01()
        val p10 = pose.m10()
        val p11 = pose.m11()
        val p20 = pose.m20()
        val p21 = pose.m21()
        pose.m00(cosine * p00 - sine * p01)
        pose.m01(sine * p00 + cosine * p01)
        pose.m10(cosine * p10 - sine * p11)
        pose.m11(sine * p10 + cosine * p11)
        pose.m20(cosine * p20 - sine * p21)
        pose.m21(sine * p20 + cosine * p21)

        val normal = matrices.last().normal()
        val n00 = normal.m00()
        val n01 = normal.m01()
        val n10 = normal.m10()
        val n11 = normal.m11()
        val n20 = normal.m20()
        val n21 = normal.m21()
        normal.m00(cosine * n00 - sine * n01)
        normal.m01(sine * n00 + cosine * n01)
        normal.m10(cosine * n10 - sine * n11)
        normal.m11(sine * n10 + cosine * n11)
        normal.m20(cosine * n20 - sine * n21)
        normal.m21(sine * n20 + cosine * n21)
    }

    private companion object {
        val FORGOTTEN_NAME_ID: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name")
        const val FORGOTTEN_NAME_GUI_TILT_DEGREES = -42.0f
    }
}

