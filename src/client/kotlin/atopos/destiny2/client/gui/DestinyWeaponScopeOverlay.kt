package atopos.destiny2.client.gui

import atopos.destiny2.client.weapon.DestinyWeaponAimClient
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Circular optic fallback for built-in scopes whose gun pack supplies an
 * iron_view but no TaCZ ocular/division attachment model.
 *
 * The first-person weapon renderer stops drawing the opaque scope body near
 * full ADS. This overlay then masks only the area outside the optic, leaving
 * the real rendered world visible through its center.
 */
object DestinyWeaponScopeOverlay : HudRenderCallback {
    override fun onHudRender(graphics: GuiGraphics, tickDelta: DeltaTracker) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (client.options.hideGui || !client.options.cameraType.isFirstPerson) return

        val stack = player.mainHandItem
        val weapon = stack.item as? DestinyRangedWeapon ?: return
        if (!weapon.aimProfile(stack).scopeOverlay) return

        val aim = DestinyWeaponAimClient.progress(1.0f)
        val visibility = smoothStep(
            ((aim - OVERLAY_START_PROGRESS) /
                (1.0f - OVERLAY_START_PROGRESS)).coerceIn(0.0f, 1.0f)
        )
        if (visibility <= 0.001f) return

        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val centerX = width / 2
        val centerY = height / 2
        val radius = (minOf(width, height) * SCOPE_RADIUS).roundToInt().coerceAtLeast(24)
        val alpha = (255 * visibility).roundToInt().coerceIn(0, 255)
        val mask = alpha shl 24

        drawCircularMask(graphics, width, height, centerX, centerY, radius, mask)
        drawScopeRing(graphics, centerX, centerY, radius, visibility)
        drawReticle(graphics, centerX, centerY, radius, visibility)
    }

    private fun drawCircularMask(
        graphics: GuiGraphics,
        width: Int,
        height: Int,
        centerX: Int,
        centerY: Int,
        radius: Int,
        color: Int
    ) {
        val top = (centerY - radius).coerceAtLeast(0)
        val bottom = (centerY + radius).coerceAtMost(height)
        graphics.fill(0, 0, width, top, color)
        graphics.fill(0, bottom, width, height, color)

        val radiusSquared = radius.toLong() * radius
        for (y in top until bottom) {
            val dy = y - centerY
            val halfWidth = sqrt((radiusSquared - dy.toLong() * dy).coerceAtLeast(0).toDouble())
                .roundToInt()
            graphics.fill(0, y, (centerX - halfWidth).coerceAtLeast(0), y + 1, color)
            graphics.fill((centerX + halfWidth).coerceAtMost(width), y, width, y + 1, color)
        }
    }

    private fun drawScopeRing(
        graphics: GuiGraphics,
        centerX: Int,
        centerY: Int,
        radius: Int,
        visibility: Float
    ) {
        val outerColor = withAlpha(0x392E54, (210 * visibility).roundToInt())
        val innerColor = withAlpha(0xA99AD2, (150 * visibility).roundToInt())
        repeat(RING_SEGMENTS) { index ->
            val angle = index * (PI * 2.0 / RING_SEGMENTS)
            val cos = cos(angle)
            val sin = sin(angle)
            val x = (centerX + cos * radius).roundToInt()
            val y = (centerY + sin * radius).roundToInt()
            graphics.fill(x - 1, y - 1, x + 2, y + 2, outerColor)

            val innerRadius = radius - 2
            val ix = (centerX + cos * innerRadius).roundToInt()
            val iy = (centerY + sin * innerRadius).roundToInt()
            graphics.fill(ix, iy, ix + 1, iy + 1, innerColor)
        }
    }

    private fun drawReticle(
        graphics: GuiGraphics,
        centerX: Int,
        centerY: Int,
        radius: Int,
        visibility: Float
    ) {
        val line = withAlpha(0xD9D2ED, (185 * visibility).roundToInt())
        val accent = withAlpha(0x9C7DDB, (225 * visibility).roundToInt())
        val gap = (radius * 0.055f).roundToInt().coerceAtLeast(5)
        val length = (radius * 0.24f).roundToInt().coerceAtLeast(14)

        graphics.fill(centerX - gap - length, centerY, centerX - gap, centerY + 1, line)
        graphics.fill(centerX + gap, centerY, centerX + gap + length, centerY + 1, line)
        graphics.fill(centerX, centerY - gap - length, centerX + 1, centerY - gap, line)
        graphics.fill(centerX, centerY + gap, centerX + 1, centerY + gap + length, line)
        graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, accent)
    }

    private fun smoothStep(value: Float): Float = value * value * (3.0f - 2.0f * value)

    private fun withAlpha(rgb: Int, alpha: Int): Int =
        (rgb and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private const val OVERLAY_START_PROGRESS = 0.70f
    private const val SCOPE_RADIUS = 0.43f
    private const val RING_SEGMENTS = 180
}
