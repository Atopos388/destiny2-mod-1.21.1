package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.WellOfRadianceEntity
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

/**
 * First-person radiance feedback shown while the local player stands inside a
 * Well of Radiance. The effect is screen-space so it softly wraps the viewport
 * instead of appearing as world geometry.
 */
object WellOfRadianceScreenOverlay : HudRenderCallback {
    private const val WELL_RADIUS = 8.0
    private const val FADE_IN_PER_SECOND = 2.8f
    private const val FADE_OUT_PER_SECOND = 4.0f

    @Volatile
    private var shader: ShaderInstance? = null
    private var strength = 0f
    private var previousFrameNanos = 0L

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "well_radiance_screen"),
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
            strength = 0f
            previousFrameNanos = 0L
            return
        }

        val now = System.nanoTime()
        val deltaSeconds = if (previousFrameNanos == 0L) {
            0f
        } else {
            ((now - previousFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        }
        previousFrameNanos = now

        val target = if (isInsideWell(player.x, player.y, player.z)) 1f else 0f
        val speed = if (target > strength) FADE_IN_PER_SECOND else FADE_OUT_PER_SECOND
        strength = Mth.clamp(
            strength + Mth.clamp(target - strength, -speed * deltaSeconds, speed * deltaSeconds),
            0f,
            1f
        )
        if (strength <= 0.002f || client.screen != null) return

        val activeShader = shader ?: return
        val width = graphics.guiWidth().toFloat()
        val height = graphics.guiHeight().toFloat()
        if (width <= 0f || height <= 0f) return

        activeShader.getUniform("Strength")?.set(strength)
        activeShader.getUniform("Time")?.set(
            (level.gameTime + tickCounter.getGameTimeDeltaPartialTick(true)) * 0.05f
        )

        RenderSystem.enableBlend()
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.disableDepthTest()
        RenderSystem.depthMask(false)

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX
        )
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

    private fun isInsideWell(playerX: Double, playerY: Double, playerZ: Double): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val level = client.level ?: return false
        return level.getEntitiesOfClass(
            WellOfRadianceEntity::class.java,
            player.boundingBox.inflate(WELL_RADIUS + 1.0, 5.0, WELL_RADIUS + 1.0)
        ) { it.isAlive }.any { well ->
            val dx = playerX - well.x
            val dz = playerZ - well.z
            dx * dx + dz * dz <= WELL_RADIUS * WELL_RADIUS &&
                playerY >= well.y - 2.0 &&
                playerY <= well.y + 4.0
        }
    }
}
