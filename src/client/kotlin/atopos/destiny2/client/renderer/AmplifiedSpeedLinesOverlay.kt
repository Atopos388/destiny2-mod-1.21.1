// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.common.effect.DestinyEffects
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth

/** Procedural first-person Arc streaks shown while Speed Booster is active. */
object AmplifiedSpeedLinesOverlay : HudRenderCallback {
    private const val FADE_IN_PER_SECOND = 5.5f
    private const val FADE_OUT_PER_SECOND = 3.8f

    @Volatile
    private var shader: ShaderInstance? = null
    private var strength = 0f
    private var previousFrameNanos = 0L

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "amplified_speed_lines"),
                DefaultVertexFormat.POSITION_TEX
            ) { loadedShader -> shader = loadedShader }
        }
        HudRenderCallback.EVENT.register(this)
    }

    override fun onHudRender(graphics: GuiGraphics, tickCounter: DeltaTracker) {
        val client = Minecraft.getInstance()
        val player = client.player
        val level = client.level
        if (player == null || level == null) {
            reset()
            return
        }

        val now = System.nanoTime()
        val deltaSeconds = if (previousFrameNanos == 0L) 0f else
            ((now - previousFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        previousFrameNanos = now

        val visible = client.options.cameraType.isFirstPerson &&
            !CinematicCameraClient.isActive() &&
            !player.isSpectator &&
            player.hasEffect(DestinyEffects.SPEED_BOOSTER)
        val target = if (visible) 1f else 0f
        val fadeSpeed = if (target > strength) FADE_IN_PER_SECOND else FADE_OUT_PER_SECOND
        strength = Mth.clamp(
            strength + Mth.clamp(target - strength, -fadeSpeed * deltaSeconds, fadeSpeed * deltaSeconds),
            0f,
            1f
        )
        if (strength <= 0.002f || client.screen != null || client.options.hideGui) return

        val activeShader = shader ?: return
        val width = graphics.guiWidth().toFloat()
        val height = graphics.guiHeight().toFloat()
        if (width <= 0f || height <= 0f) return

        val partialTick = tickCounter.getGameTimeDeltaPartialTick(true)
        activeShader.getUniform("Strength")?.set(strength)
        activeShader.getUniform("Time")?.set((level.gameTime + partialTick) * 0.05f)
        activeShader.getUniform("Aspect")?.set(width / height)

        RenderSystem.enableBlend()
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)

        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        buffer.addVertex(0f, height, 0f).setUv(0f, 1f)
        buffer.addVertex(width, height, 0f).setUv(1f, 1f)
        buffer.addVertex(width, 0f, 0f).setUv(1f, 0f)
        buffer.addVertex(0f, 0f, 0f).setUv(0f, 0f)
        RenderSystem.setShader { activeShader }
        BufferUploader.drawWithShader(buffer.buildOrThrow())

        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun reset() {
        strength = 0f
        previousFrameNanos = 0L
    }
}
