package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.tacz.TaczBedrockGunModel
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory

/** Renders the authored poly-mesh sword without routing melee input through the gun runtime. */
class StrykersSureHandItemRenderer : BuiltinItemRendererRegistry.DynamicItemRenderer {
    override fun render(stack: ItemStack, mode: ItemDisplayContext, matrices: PoseStack, vertexConsumers: MultiBufferSource, light: Int, overlay: Int) {
        if (mode == ItemDisplayContext.GUI) {
            GuiItemModelFit.render("StrykersSureHandItemRenderer:" + stack.item.toString() + ":" + stack.components.toString(), matrices, vertexConsumers) { pose, buffers ->
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
        val bedrockModel = model() ?: return
        bedrockModel.resetAnimation()

        matrices.pushPose()
        try {
            // Convert TaCZ/Bedrock's 24-pixel-high model origin into the
            // ordinary item space. The Y rotation exposes the blade's broad
            // YZ face to the standard item camera.
            matrices.translate(0.5, 2.0, 0.5)
            matrices.mulPose(Axis.YP.rotationDegrees(90.0f))
            matrices.scale(-1.0f, -1.0f, 1.0f)

            val consumer = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE))
            bedrockModel.render(
                matrices,
                mode,
                consumer,
                light,
                if (overlay == 0) OverlayTexture.NO_OVERLAY else overlay
            )
        } finally {
            matrices.popPose()
            bedrockModel.resetAnimation()
        }
    }

    private fun model(): TaczBedrockGunModel? {
        cachedModel?.let { return it }
        if (loadFailed) return null

        synchronized(this) {
            cachedModel?.let { return it }
            return try {
                val resource = Minecraft.getInstance().resourceManager
                    .getResource(MODEL)
                    .orElseThrow { IllegalStateException("Missing sword geometry: $MODEL") }
                resource.open().use { input -> TaczBedrockGunModel(input.readAllBytes()) }
                    .also { cachedModel = it }
            } catch (exception: Exception) {
                loadFailed = true
                LOGGER.error("Unable to load Stryker's Sure-Hand geometry", exception)
                null
            }
        }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(StrykersSureHandItemRenderer::class.java)
        private val MODEL = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "bedrock_mesh/strykers_sure_hand.geo.json"
        )
        private val TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "textures/item/strykers_sure_hand.png"
        )

        @Volatile
        private var cachedModel: TaczBedrockGunModel? = null

        @Volatile
        private var loadFailed = false
    }
}
