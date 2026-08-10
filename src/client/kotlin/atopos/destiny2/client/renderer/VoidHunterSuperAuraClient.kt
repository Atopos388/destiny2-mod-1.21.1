// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import java.util.UUID
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Short, server-confirmed void overcharge shown while a Nightstalker super is cast.
 *
 * This is a player feature layer instead of a world-space shell, so it inherits the
 * current player pose (including PlayerAnimator actions) and remains attached to every
 * limb. A translucent skin pass supplies the full purple coverage while two counter-
 * scrolling energy passes provide the moving highlights seen in the reference.
 */
object VoidHunterSuperAuraClient {
    private const val FADE_IN_TICKS = 4.0f
    private const val FADE_OUT_TICKS = 8.0f
    private val FLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
        "destiny2-mod",
        "textures/entity/void_hunter_super_flow.png"
    )
    private val active = HashMap<UUID, AuraWindow>()

    private data class AuraWindow(val startTick: Long, val endTick: Long)

    fun register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register { entityType, renderer, helper, _ ->
            if (entityType == EntityType.PLAYER && renderer is PlayerRenderer) {
                helper.register(VoidHunterSuperAuraLayer(renderer))
            }
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> active.clear() }
    }

    fun activate(playerId: UUID, durationTicks: Int) {
        val level = Minecraft.getInstance().level ?: return
        val start = level.gameTime
        active[playerId] = AuraWindow(start, start + durationTicks.coerceIn(1, 20 * 10))
    }

    private fun intensity(player: AbstractClientPlayer, partialTick: Float): Float {
        val window = active[player.uuid] ?: return 0.0f
        val now = player.level().gameTime.toFloat() + partialTick
        if (now >= window.endTick || player.isRemoved) {
            active.remove(player.uuid)
            return 0.0f
        }

        val fadeIn = ((now - window.startTick) / FADE_IN_TICKS).coerceIn(0.0f, 1.0f)
        val fadeOut = ((window.endTick - now) / FADE_OUT_TICKS).coerceIn(0.0f, 1.0f)
        val phase = ((player.uuid.leastSignificantBits and 0xffffL).toFloat() / 65535.0f) * (PI.toFloat() * 2.0f)
        val pulse = 0.90f + sin(now * 0.42f + phase) * 0.10f
        return min(fadeIn, fadeOut) * pulse
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha.coerceIn(0, 255) and 0xff) shl 24) or
            ((red.coerceIn(0, 255) and 0xff) shl 16) or
            ((green.coerceIn(0, 255) and 0xff) shl 8) or
            (blue.coerceIn(0, 255) and 0xff)

    private class VoidHunterSuperAuraLayer(
        private val playerRenderer: PlayerRenderer
    ) : RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(
        playerRenderer as RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
    ) {
        override fun render(
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            packedLight: Int,
            player: AbstractClientPlayer,
            limbSwing: Float,
            limbSwingAmount: Float,
            partialTick: Float,
            ageInTicks: Float,
            netHeadYaw: Float,
            headPitch: Float
        ) {
            val strength = intensity(player, partialTick)
            if (strength <= 0.001f) return

            val age = player.tickCount.toFloat() + partialTick
            val phase = ((player.uuid.mostSignificantBits and 0xffffL).toFloat() / 65535.0f) * (PI.toFloat() * 2.0f)
            val model = parentModel

            // A nearly skin-tight emissive pass keeps the whole silhouette purple,
            // including areas between the moving energy streaks.
            poseStack.pushPose()
            poseStack.scale(1.012f, 1.012f, 1.012f)
            model.renderToBuffer(
                poseStack,
                bufferSource.getBuffer(RenderType.entityTranslucentEmissive(playerRenderer.getTextureLocation(player))),
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                argb((112.0f * strength).roundToInt(), 132, 68, 238)
            )
            poseStack.popPose()

            // Two slightly separated, counter-scrolling shells prevent a flat decal
            // look and make the bright veins appear to travel around the body.
            renderFlowPass(
                poseStack,
                bufferSource,
                model,
                scale = 1.022f,
                u = age * 0.0065f + sin(age * 0.055f + phase) * 0.025f,
                v = -age * 0.018f,
                color = argb((228.0f * strength).roundToInt(), 210, 160, 255)
            )
            renderFlowPass(
                poseStack,
                bufferSource,
                model,
                scale = 1.032f,
                u = -age * 0.010f + sin(age * 0.041f - phase) * 0.018f,
                v = age * 0.012f,
                color = argb((118.0f * strength).roundToInt(), 126, 82, 255)
            )
        }

        private fun renderFlowPass(
            poseStack: PoseStack,
            bufferSource: MultiBufferSource,
            model: PlayerModel<AbstractClientPlayer>,
            scale: Float,
            u: Float,
            v: Float,
            color: Int
        ) {
            poseStack.pushPose()
            poseStack.scale(scale, scale, scale)
            model.renderToBuffer(
                poseStack,
                bufferSource.getBuffer(RenderType.energySwirl(FLOW_TEXTURE, u, v)),
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                color
            )
            poseStack.popPose()
        }
    }
}
