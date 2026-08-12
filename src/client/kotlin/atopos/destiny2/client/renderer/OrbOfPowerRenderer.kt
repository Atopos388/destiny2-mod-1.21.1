package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.OrbOfPowerEntity
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Screen-space radial renderer matching the Thunderclap VFX Lab scene.
 *
 * The entity and collision remain fully three-dimensional. A radial billboard
 * is appropriate here because a sphere has the same silhouette from every
 * camera angle, while avoiding translucent nested-mesh depth artifacts.
 */
class OrbOfPowerRenderer(context: EntityRendererProvider.Context) : EntityRenderer<OrbOfPowerEntity>(context) {
    override fun render(
        entity: OrbOfPowerEntity,
        yaw: Float,
        partialTick: Float,
        pose: PoseStack,
        buffers: MultiBufferSource,
        light: Int
    ) {
        val time = entity.tickCount + partialTick
        val bob = sin(time * 0.16f) * 0.008f
        val pulse = 1.0f + sin(time * 0.22f) * 0.055f
        val consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(atlasTexture()))

        pose.pushPose()
        pose.translate(0.0, 0.21 + bob, 0.0)
        pose.mulPose(entityRenderDispatcher.cameraOrientation())

        // Match the Lab's 3D particle orbit: the far half is occluded by the
        // shell, while the near half is drawn over it. All layers share one
        // atlas and buffer so translucent ordering stays deterministic.
        quad(consumer, pose, 0.31f * pulse, 80, 122, 226, 54, CORE_U0, CORE_U1)
        drawMotes(entity, consumer, pose, time, frontHalf = false)
        quad(consumer, pose, 0.225f * pulse, 255, 255, 255, 255, SHELL_U0, SHELL_U1)

        pose.pushPose()
        pose.translate(
            (cos(time * 0.115f) * 0.052f).toDouble(),
            (sin(time * 0.083f) * 0.038f).toDouble(),
            0.001
        )
        quad(consumer, pose, 0.132f * pulse, 244, 252, 255, 250, CORE_U0, CORE_U1)
        pose.translate(-0.032, 0.024, 0.001)
        quad(consumer, pose, 0.057f * pulse, 255, 255, 255, 225, CORE_U0, CORE_U1)
        pose.popPose()

        drawMotes(entity, consumer, pose, time, frontHalf = true)

        pose.popPose()
        super.render(entity, yaw, partialTick, pose, buffers, light)
    }

    private fun drawMotes(
        entity: OrbOfPowerEntity,
        consumer: VertexConsumer,
        pose: PoseStack,
        time: Float,
        frontHalf: Boolean
    ) {
        repeat(14) { index ->
            val seed = index * 131 + 17
            val phase = time * (0.075f + index % 4 * 0.012f) +
                hash(seed) * TWO_PI + entity.id * 0.37f
            val orbit = 0.32f * (0.72f + hash(seed + 9) * 0.54f)
            val depth = sin(phase) * orbit
            if ((depth >= 0.0f) != frontHalf) return@repeat

            val size = 0.025f * (0.70f + hash(seed + 23) * 0.72f)
            val brightness = 0.28f + hash(seed + 41) * 0.42f
            val flicker = 0.82f + sin(time * 0.31f + index * 1.71f) * 0.18f
            val alpha = (255.0f * brightness * flicker).toInt().coerceIn(70, 220)
            pose.pushPose()
            pose.translate(
                (cos(phase) * orbit).toDouble(),
                (sin(phase * 1.63f) * 0.32f * 0.62f).toDouble(),
                if (frontHalf) 0.003 else -0.003
            )
            // Soft blue halo plus a compact bright center keeps the particles
            // readable at normal play distance without recreating END_ROD blobs.
            quad(
                consumer,
                pose,
                size * 1.38f,
                72,
                112,
                218,
                (alpha * 0.42f).toInt(),
                CORE_U0,
                CORE_U1
            )
            quad(
                consumer,
                pose,
                size * 0.58f,
                134,
                173,
                255,
                alpha,
                CORE_U0,
                CORE_U1
            )
            pose.popPose()
        }
    }

    private fun quad(
        consumer: VertexConsumer,
        pose: PoseStack,
        radius: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
        u0: Float,
        u1: Float
    ) {
        vertex(consumer, pose, -radius, -radius, u0, 1.0f, red, green, blue, alpha)
        vertex(consumer, pose, radius, -radius, u1, 1.0f, red, green, blue, alpha)
        vertex(consumer, pose, radius, radius, u1, 0.0f, red, green, blue, alpha)
        vertex(consumer, pose, -radius, radius, u0, 0.0f, red, green, blue, alpha)
    }

    private fun vertex(
        consumer: VertexConsumer,
        pose: PoseStack,
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        consumer.addVertex(pose.last(), x, y, 0.0f)
            .setColor(red, green, blue, alpha)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose.last(), 0.0f, 0.0f, 1.0f)
    }

    override fun getTextureLocation(entity: OrbOfPowerEntity): ResourceLocation = atlasTexture()

    private companion object {
        const val TEXTURE_SIZE = 64
        const val ATLAS_WIDTH = TEXTURE_SIZE * 2
        const val SHELL_U0 = 0.0f
        const val SHELL_U1 = 0.5f
        const val CORE_U0 = 0.5f
        const val CORE_U1 = 1.0f
        const val TWO_PI = 6.2831855f
        val ATLAS_ID: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "dynamic/orb_of_power_lab_atlas")

        private var atlasRegistered = false

        fun atlasTexture(): ResourceLocation {
            if (!atlasRegistered) {
                Minecraft.getInstance().textureManager.register(ATLAS_ID, DynamicTexture(createAtlas()))
                atlasRegistered = true
            }
            return ATLAS_ID
        }

        fun createAtlas(): NativeImage {
            val image = NativeImage(ATLAS_WIDTH, TEXTURE_SIZE, false)
            for (y in 0 until TEXTURE_SIZE) {
                for (x in 0 until TEXTURE_SIZE) {
                    val nx = ((x + 0.5) / TEXTURE_SIZE * 2.0 - 1.0)
                    val ny = ((y + 0.5) / TEXTURE_SIZE * 2.0 - 1.0)
                    val distance = sqrt(nx * nx + ny * ny)

                    if (distance <= 1.0) {
                        val edge = smooth(0.46, 1.0, distance)
                        val shellAlpha = ((0.22 + edge * 0.74) * 255.0).toInt()
                        val shellRed = mix(11, 1, edge)
                        val shellGreen = mix(23, 2, edge)
                        val shellBlue = mix(64, 7, edge)
                        image.setPixelRGBA(x, y, abgr(shellRed, shellGreen, shellBlue, shellAlpha))

                        val coreFalloff = (1.0 - smooth(0.02, 1.0, distance)).pow(1.55)
                        val coreAlpha = (coreFalloff * 255.0).toInt()
                        val coreEdge = smooth(0.0, 1.0, distance)
                        image.setPixelRGBA(
                            x + TEXTURE_SIZE,
                            y,
                            abgr(mix(255, 184, coreEdge), mix(255, 222, coreEdge), 255, coreAlpha)
                        )
                    } else {
                        image.setPixelRGBA(x, y, 0)
                        image.setPixelRGBA(x + TEXTURE_SIZE, y, 0)
                    }
                }
            }
            return image
        }

        fun smooth(start: Double, end: Double, value: Double): Double {
            val t = ((value - start) / (end - start)).coerceIn(0.0, 1.0)
            return t * t * (3.0 - 2.0 * t)
        }

        fun mix(start: Int, end: Int, amount: Double): Int =
            (start + (end - start) * amount).toInt().coerceIn(0, 255)

        fun hash(seed: Int): Float {
            val value = sin(seed * 12.9898f) * 43758.5453f
            return value - kotlin.math.floor(value)
        }

        fun abgr(red: Int, green: Int, blue: Int, alpha: Int): Int =
            ((alpha.coerceIn(0, 255) shl 24) or
                (blue.coerceIn(0, 255) shl 16) or
                (green.coerceIn(0, 255) shl 8) or
                red.coerceIn(0, 255))
    }
}
