package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.tacz.TaczBedrockGunModel
import atopos.destiny2.client.weapon.DestinyWeaponAimClient
import atopos.destiny2.client.weapon.GenericGunAnimationClient
import atopos.destiny2.client.weapon.TaczGunPackResources
import atopos.destiny2.common.item.GenericGunPackItem
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic TaCZ-rig renderer that uses TaCZ's own Bedrock coordinate rules.
 *
 * First person is called directly from ItemInHandRendererSwingMixin at the
 * same clean render boundary used by TaCZ's RenderHandEvent. No GeckoLib or
 * per-gun corrective transform participates.
 */
class GenericGunPackItemRenderer private constructor() :
    BuiltinItemRendererRegistry.DynamicItemRenderer {

    override fun render(stack: ItemStack, mode: ItemDisplayContext, matrices: PoseStack, vertexConsumers: MultiBufferSource, light: Int, overlay: Int) {
        if (mode == ItemDisplayContext.GUI) {
            GuiItemModelFit.render("GenericGunPackItemRenderer:" + stack.item.toString() + ":" + stack.components.toString(), matrices, vertexConsumers) { pose, buffers ->
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
        if (mode.firstPerson()) return
        val runtime = runtime(stack) ?: return

        if (mode == ItemDisplayContext.GUI) {
            GenericGunAnimationClient.applyStatic(runtime.gunId, runtime.definition.animation, runtime.model)
        } else {
            GenericGunAnimationClient.apply(runtime.gunId, runtime.definition.animation, runtime.model)
        }
        matrices.pushPose()
        try {
            // Exact non-first-person origin used by TaCZ's item renderer.
            matrices.translate(0.5, 2.0, 0.5)
            matrices.scale(-1.0f, -1.0f, 1.0f)
            when (mode) {
                ItemDisplayContext.GUI,
                ItemDisplayContext.FIXED ->
                    runtime.model.positioningInverse(TaczGunPackResources.FIXED)?.let(matrices::mulPose)
                ItemDisplayContext.GROUND ->
                    runtime.model.positioningInverse(TaczGunPackResources.GROUND)?.let(matrices::mulPose)
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND ->
                    runtime.model.positioningInverse(TaczGunPackResources.THIRD_PERSON_HAND)?.let(matrices::mulPose)
                else -> Unit
            }
            val displayScale = when (mode) {
                ItemDisplayContext.FIXED -> runtime.definition.displayScale(TaczGunPackResources.FIXED_SCALE)
                ItemDisplayContext.GROUND -> runtime.definition.displayScale(TaczGunPackResources.GROUND_SCALE)
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND ->
                    runtime.definition.displayScale(TaczGunPackResources.THIRD_PERSON_SCALE)
                else -> null
            }
            displayScale?.let { matrices.scale(it.x, it.y, it.z) }
            val consumer = vertexConsumers.getBuffer(RenderType.entityCutout(runtime.texture))
            runtime.model.render(matrices, mode, consumer, light, overlay)
        } finally {
            matrices.popPose()
            GenericGunAnimationClient.reset(runtime.model)
        }
    }

    fun renderFirstPerson(
        player: LocalPlayer,
        stack: ItemStack,
        mode: ItemDisplayContext,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        partialTick: Float
    ) {
        val runtime = runtime(stack) ?: return
        GenericGunAnimationClient.apply(runtime.gunId, runtime.definition.animation, runtime.model)

        val idle = runtime.definition.idleViewPivot?.let { pivot ->
            Matrix4f().translation(-pivot.x / 16f, pivot.y / 16f, -pivot.z / 16f)
        } ?: runtime.model.positioningInverse(TaczGunPackResources.IDLE_VIEW)
        val iron = when {
            runtime.definition.aimViewPivot != null -> runtime.definition.aimViewPivot.let { pivot ->
                // Compatibility view for rigs that placed iron_view on a
                // moving/rotated weapon bone. X/Y are the actual aperture
                // center; Z remains the authored eye-relief plane.
                Matrix4f().translation(-pivot.x / 16f, pivot.y / 16f, -pivot.z / 16f)
            }
            runtime.definition.aimXyFromConstraint ->
                runtime.definition.matrix(TaczGunPackResources.IRON_VIEW)
            else -> runtime.model.positioningInverse(TaczGunPackResources.IRON_VIEW)
        }
        if (idle != null && iron != null) {
            DestinyWeaponAimClient.publishPositioningViews(idle, iron)
        } else {
            DestinyWeaponAimClient.clearPositioningViews()
        }

        val aimProfile = (stack.item as? DestinyRangedWeapon)?.aimProfile(stack)
        if (
            aimProfile?.scopeOverlay == true &&
            DestinyWeaponAimClient.progress(partialTick) >= SCOPE_MODEL_HIDE_PROGRESS
        ) {
            GenericGunAnimationClient.reset(runtime.model)
            return
        }

        matrices.pushPose()
        try {
            // TaCZ removes vanilla hand lag and reapplies a smaller custom lag
            // before entering Bedrock model space.
            val xRotOffset = net.minecraft.util.Mth.lerp(partialTick, player.xBobO, player.xBob)
            val yRotOffset = net.minecraft.util.Mth.lerp(partialTick, player.yBobO, player.yBob)
            val xRot = player.getViewXRot(partialTick) - xRotOffset
            val yRot = player.getViewYRot(partialTick) - yRotOffset
            // Hip fire keeps a little hand lag for weight. ADS progressively
            // locks the weapon to the sight line so walking does not shake it.
            val aimProgress = DestinyWeaponAimClient.progress(partialTick)
            val handLagScale = net.minecraft.util.Mth.lerp(aimProgress, -0.1f, 0.0f)
            matrices.mulPose(Axis.XP.rotationDegrees(xRot * handLagScale))
            matrices.mulPose(Axis.YP.rotationDegrees(yRot * handLagScale))

            // TaCZ GunItemRendererWrapper.renderFirstPerson:
            // render origin -> Bedrock origin -> flip model -> view inverse.
            matrices.translate(0.0, 1.5, 0.0)
            matrices.mulPose(Axis.ZP.rotationDegrees(180.0f))
            DestinyWeaponAimClient.positioningView(partialTick)?.let { positioning ->
                matrices.translate(0.0, 1.5, 0.0)
                matrices.mulPose(positioning)
                matrices.translate(0.0, -1.5, 0.0)
            }
            // TaCZ ICA keeps the authored sight constraint stable while ADS
            // without repurposing it as the iron_view camera position.
            runtime.model.applyAnimationConstraint(matrices, aimProgress)
            // TaCZ consumes the authored camera-node quaternion on both sides:
            // the world camera and the first-person gun model remain coupled.
            matrices.mulPose(DestinyWeaponAimClient.modelCameraRotation(partialTick))

            val consumer = vertexConsumers.getBuffer(RenderType.entityCutout(runtime.texture))
            runtime.model.render(
                matrices,
                mode,
                consumer,
                light,
                OverlayTexture.NO_OVERLAY
            )
            renderHands(
                player,
                runtime.model,
                vertexConsumers,
                light,
                runtime.definition.reversedPlayerArms
            )
        } finally {
            matrices.popPose()
            GenericGunAnimationClient.reset(runtime.model)
        }
    }

    private fun renderHands(
        player: LocalPlayer,
        model: TaczBedrockGunModel,
        buffers: MultiBufferSource,
        light: Int,
        reversedPlayerArms: Boolean
    ) {
        val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(player) as? PlayerRenderer
            ?: return
        if (reversedPlayerArms) {
            model.rightHandPose?.let { pose ->
                renderer.renderLeftHand(handPose(pose), buffers, light, player)
            }
            model.leftHandPose?.let { pose ->
                renderer.renderRightHand(handPose(pose), buffers, light, player)
            }
            return
        }
        model.leftHandPose?.let { pose ->
            renderer.renderLeftHand(handPose(pose), buffers, light, player)
        }
        model.rightHandPose?.let { pose ->
            renderer.renderRightHand(handPose(pose), buffers, light, player)
        }
    }

    /**
     * Builds the vanilla arm renderer's matrix from the TaCZ hand locator.
     *
     * TaczBedrockGunModel already performs TaCZ's 180-degree Bedrock hand
     * conversion while capturing the locator. `reversedPlayerArms` only swaps
     * which vanilla arm is rendered for rigs whose guide-cube UV/layout names
     * are reversed; applying another rotation here unfolds the full arm across
     * the first-person camera.
     */
    private fun handPose(pose: TaczBedrockGunModel.HandPose): PoseStack {
        val handStack = PoseStack()
        handStack.last().pose().mul(pose.pose())
        handStack.last().normal().mul(pose.normal())
        return handStack
    }

    private fun runtime(stack: ItemStack): RuntimeGun? {
        val gunId = GenericGunPackItem.id(stack)
        val definition = TaczGunPackResources.definition(gunId) ?: return null
        val model = GenericBedrockGunModelCache.load(gunId, definition.model) ?: return null
        val texture = GenericGunTextureCache.resolve(definition.texture) ?: return null
        return RuntimeGun(gunId, definition, model, texture)
    }

    private data class RuntimeGun(
        val gunId: ResourceLocation,
        val definition: TaczGunPackResources.GunPackDefinition,
        val model: TaczBedrockGunModel,
        val texture: ResourceLocation
    )

    companion object {
        @JvmField
        val INSTANCE = GenericGunPackItemRenderer()

        private const val SCOPE_MODEL_HIDE_PROGRESS = 0.88f
    }
}

private object GenericBedrockGunModelCache {
    private data class Cached(val hash: Int, val model: TaczBedrockGunModel)

    private val logger = LoggerFactory.getLogger("DestinyBedrockGunModelCache")
    private val cache = ConcurrentHashMap<ResourceLocation, Cached>()
    private val rejectedHashes = ConcurrentHashMap<ResourceLocation, Int>()

    fun load(gunId: ResourceLocation, location: ResourceLocation): TaczBedrockGunModel? {
        val bytes = TaczGunPackResources.resourceBytes(location) ?: return null
        val hash = bytes.contentHashCode()
        val cached = cache[gunId]
        cached?.takeIf { it.hash == hash }?.let { return it.model }
        if (rejectedHashes[gunId] == hash) return cached?.model
        val model = runCatching { TaczBedrockGunModel(bytes) }
            .onFailure {
                rejectedHashes[gunId] = hash
                logger.warn(
                    "Ignoring invalid Bedrock geometry snapshot {} for {}; keeping the last valid model",
                    location,
                    gunId,
                    it
                )
            }
            .getOrNull()
            ?: return cached?.model
        rejectedHashes.remove(gunId)
        cache[gunId] = Cached(hash, model)
        return model
    }
}

private object GenericGunTextureCache {
    private data class Cached(val hash: Int, val location: ResourceLocation)

    private val cache = ConcurrentHashMap<ResourceLocation, Cached>()

    fun resolve(location: ResourceLocation): ResourceLocation? {
        val manager = Minecraft.getInstance().resourceManager
        if (manager.getResource(location).isPresent) return location

        val bytes = TaczGunPackResources.resourceBytes(location) ?: return null
        val hash = bytes.contentHashCode()
        cache[location]?.takeIf { it.hash == hash }?.let { return it.location }

        val dynamicLocation = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "external_gunpack/${location.namespace}/${location.path.removeSuffix(".png")}_${hash.toUInt()}"
        )
        val image = bytes.inputStream().use(NativeImage::read)
        Minecraft.getInstance().textureManager.register(dynamicLocation, DynamicTexture(image))
        cache[location] = Cached(hash, dynamicLocation)
        return dynamicLocation
    }
}
