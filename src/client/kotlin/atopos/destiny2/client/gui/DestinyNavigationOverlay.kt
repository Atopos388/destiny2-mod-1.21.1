package atopos.destiny2.client.gui

import atopos.destiny2.client.cinematic.CinematicCameraClient
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import kotlin.math.roundToInt
import kotlin.math.sin

/** Non-pausing, mouse-free Destiny-style navigation view shown while the player-list key is held. */
object DestinyNavigationOverlay : HudRenderCallback {
    private const val OPEN_FADE_MS = 140L
    private const val DIRECTOR_HOLD_MS = 550L
    private var open = false
    private var latched = false
    private var keyWasDown = false
    private var pressedAt = 0L
    private var directorOpenedThisHold = false
    private var suppressUntilRelease = false
    private var openedAt = 0L

    fun update(client: Minecraft) {
        val player = client.player
        val eligible = player != null &&
            player.isAlive &&
            !client.options.hideGui &&
            !CinematicCameraClient.isActive()
        val keyDown = client.options.keyPlayerList.isDown
        val now = System.currentTimeMillis()

        if (suppressUntilRelease) {
            open = false
            keyWasDown = keyDown
            if (!keyDown) {
                suppressUntilRelease = false
                keyWasDown = false
            }
            return
        }

        if (!eligible || (client.screen != null && client.screen !is DestinyDirectorScreen)) {
            open = false
            keyWasDown = keyDown
            return
        }
        if (client.screen is DestinyDirectorScreen) {
            open = false
            keyWasDown = keyDown
            return
        }

        if (keyDown && !keyWasDown) {
            pressedAt = now
            directorOpenedThisHold = false
            if (!open) openedAt = now
            open = true
        }
        if (keyDown && !directorOpenedThisHold && now - pressedAt >= DIRECTOR_HOLD_MS) {
            directorOpenedThisHold = true
            latched = false
            open = false
            client.player?.let { client.setScreen(DestinyDirectorScreen.create(it)) }
        } else if (!keyDown && keyWasDown && !directorOpenedThisHold) {
            latched = !latched
            if (latched && !open) openedAt = now
            open = latched
        } else if (!keyDown) {
            open = latched
        }
        keyWasDown = keyDown
    }

    fun isOpen(): Boolean = open

    fun close() {
        open = false
        latched = false
        keyWasDown = false
        pressedAt = 0L
        directorOpenedThisHold = false
    }

    fun closeDirector() {
        close()
        suppressUntilRelease = true
    }

    override fun onHudRender(graphics: GuiGraphics, tickDelta: DeltaTracker) {
        if (!open) return
        val client = Minecraft.getInstance()
        val data = DestinyNavigationDataAdapter.snapshot(client) ?: return
        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val layout = runCatching { DestinyNavigationTemplate.resolve(width, height) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val visibility = ((now - openedAt).toFloat() / OPEN_FADE_MS).coerceIn(0f, 1f)
        val fontScale = layout.scale.coerceIn(0.72f, 1.45f)

        graphics.fill(0, 0, width, height, fade(0x9A02070B.toInt(), visibility))
        drawEdgeShade(graphics, width, height, visibility)
        drawLocation(graphics, layout.location, data, visibility, fontScale)
        drawObjective(graphics, layout.objective, data, visibility, fontScale)
        drawGhost(graphics, layout.ghost, visibility, now)
        drawFireteam(graphics, layout.fireteam, data, visibility, fontScale)
        drawGuardian(graphics, layout.guardian, data, visibility, fontScale)
        drawFooter(graphics, layout.footer, visibility, fontScale)
    }

    private fun drawLocation(
        graphics: GuiGraphics,
        rect: DestinyNavigationTemplate.Rect,
        data: DestinyNavigationData,
        visibility: Float,
        fontScale: Float
    ) {
        drawTopRule(graphics, rect, visibility)
        drawText(graphics, Component.translatable("navigation.destiny2-mod.title").string, rect.x, rect.y, 0xFFEAF1F5.toInt(), fontScale, visibility)
        val line = (12f * fontScale).roundToInt()
        drawText(graphics, data.location, rect.x, rect.y + line + 3, 0xFFD4DEE5.toInt(), fontScale, visibility)
        drawText(graphics, data.coordinates, rect.x, rect.y + line * 2 + 5, 0xFF8796A1.toInt(), fontScale * 0.88f, visibility)
    }

    private fun drawObjective(
        graphics: GuiGraphics,
        rect: DestinyNavigationTemplate.Rect,
        data: DestinyNavigationData,
        visibility: Float,
        fontScale: Float
    ) {
        drawPanel(graphics, rect, visibility)
        val padding = (10f * rect.width / 310f).roundToInt().coerceAtLeast(5)
        var y = rect.y + padding
        drawText(graphics, Component.translatable("navigation.destiny2-mod.objective.label").string, rect.x + padding, y, 0xFF8F9DA7.toInt(), fontScale * 0.82f, visibility)
        y += (14f * fontScale).roundToInt()
        drawText(graphics, data.objectiveTitle, rect.x + padding, y, 0xFFF2F6F8.toInt(), fontScale * 1.08f, visibility)
        y += (16f * fontScale).roundToInt()
        y = drawWrappedText(
            graphics, data.objectiveDescription, rect.x + padding, y,
            rect.width - padding * 2, 0xFFC1CCD3.toInt(), fontScale * 0.9f, visibility, 3
        ) + (8f * fontScale).roundToInt()

        val rowHeight = (13f * fontScale).roundToInt().coerceAtLeast(10)
        data.objectiveItems.forEach { item ->
            if (y + rowHeight > rect.y + rect.height - padding) return@forEach
            val marker = if (item.complete) "◆" else "◇"
            val color = if (item.complete) 0xFFE5EDF2.toInt() else 0xFF697782.toInt()
            drawText(graphics, "$marker  ${item.name}", rect.x + padding, y, color, fontScale * 0.86f, visibility)
            drawTextRight(graphics, item.count.toString(), rect.x + rect.width - padding, y, color, fontScale * 0.86f, visibility)
            y += rowHeight
        }
    }

    private fun drawGhost(
        graphics: GuiGraphics,
        rect: DestinyNavigationTemplate.Rect,
        visibility: Float,
        now: Long
    ) {
        val size = minOf(rect.width, rect.height)
        val cx = rect.x + rect.width / 2
        val cy = rect.y + rect.height / 2
        val pulse = ((sin(now / 620.0) + 1.0) * 0.5).toFloat()
        val baseAlpha = ((130 + pulse * 60) * visibility).roundToInt().coerceIn(0, 255)
        val bright = argb(baseAlpha, 230, 239, 244)
        val dim = argb((baseAlpha * 0.42f).roundToInt(), 154, 171, 182)
        val core = (size * 0.13f).roundToInt().coerceAtLeast(5)
        val petalWidth = (size * 0.24f).roundToInt().coerceAtLeast(8)
        val petalHeight = (size * 0.31f).roundToInt().coerceAtLeast(10)
        val offset = (size * 0.27f).roundToInt()

        graphics.fill(cx - offset, cy, cx + offset + 1, cy + 1, dim)
        graphics.fill(cx, cy - offset, cx + 1, cy + offset + 1, dim)
        drawDiamondOutline(graphics, cx, cy, core, core, bright)
        drawDiamondOutline(graphics, cx, cy - offset, petalWidth, petalHeight, bright)
        drawDiamondOutline(graphics, cx + offset, cy, petalHeight, petalWidth, bright)
        drawDiamondOutline(graphics, cx, cy + offset, petalWidth, petalHeight, bright)
        drawDiamondOutline(graphics, cx - offset, cy, petalHeight, petalWidth, bright)

        val sweepWidth = (size * 0.72f).roundToInt()
        val sweepY = rect.y + rect.height + (8f * visibility).roundToInt()
        graphics.fill(cx - sweepWidth / 2, sweepY, cx + sweepWidth / 2, sweepY + 1, dim)
    }

    private fun drawFireteam(
        graphics: GuiGraphics,
        rect: DestinyNavigationTemplate.Rect,
        data: DestinyNavigationData,
        visibility: Float,
        fontScale: Float
    ) {
        drawPanel(graphics, rect, visibility)
        val padding = (10f * rect.width / 222f).roundToInt().coerceAtLeast(5)
        var y = rect.y + padding
        drawText(graphics, Component.translatable("navigation.destiny2-mod.fireteam").string, rect.x + padding, y, 0xFFE6EDF1.toInt(), fontScale, visibility)
        y += (17f * fontScale).roundToInt()
        graphics.fill(rect.x + padding, y, rect.x + rect.width - padding, y + 1, fade(0x627F8E99, visibility))
        y += (8f * fontScale).roundToInt()
        val rowHeight = (16f * fontScale).roundToInt().coerceAtLeast(11)
        val maximumRows = ((rect.y + rect.height - padding - y) / rowHeight).coerceAtLeast(1)
        data.fireteam.take(maximumRows).forEach { member ->
            val marker = if (member.local) "◆" else "◇"
            val color = if (member.local) 0xFFF0F5F7.toInt() else 0xFFB1BEC6.toInt()
            drawText(graphics, "$marker  ${member.name}", rect.x + padding, y, color, fontScale * 0.9f, visibility)
            drawTextRight(graphics, "${member.latency} ms", rect.x + rect.width - padding, y, 0xFF71808A.toInt(), fontScale * 0.72f, visibility)
            y += rowHeight
        }
        if (data.fireteam.size > maximumRows) {
            drawText(graphics, "+${data.fireteam.size - maximumRows}", rect.x + padding, y, 0xFF71808A.toInt(), fontScale * 0.8f, visibility)
        }
    }

    private fun drawGuardian(
        graphics: GuiGraphics,
        rect: DestinyNavigationTemplate.Rect,
        data: DestinyNavigationData,
        visibility: Float,
        fontScale: Float
    ) {
        drawPanel(graphics, rect, visibility)
        val padding = (9f * rect.width / 320f).roundToInt().coerceAtLeast(4)
        val titleY = rect.y + padding
        drawText(graphics, data.guardianName, rect.x + padding, titleY, 0xFFF2F6F8.toInt(), fontScale, visibility)
        drawTextRight(graphics, "${data.superEnergy}%", rect.x + rect.width - padding, titleY, 0xFFE5EDF2.toInt(), fontScale, visibility)
        val lineY = titleY + (15f * fontScale).roundToInt()
        val identity = listOf(data.className, data.subclassName).filter(String::isNotBlank).joinToString("  //  ")
        drawText(graphics, identity, rect.x + padding, lineY, 0xFF9AA8B2.toInt(), fontScale * 0.82f, visibility)
        val weaponY = lineY + (13f * fontScale).roundToInt()
        drawText(graphics, data.weaponName, rect.x + padding, weaponY, 0xFFCAD4DA.toInt(), fontScale * 0.82f, visibility)
        val meterY = rect.y + rect.height - padding - 2
        val meterWidth = rect.width - padding * 2
        graphics.fill(rect.x + padding, meterY, rect.x + padding + meterWidth, meterY + 1, fade(0x45576672, visibility))
        graphics.fill(
            rect.x + padding,
            meterY,
            rect.x + padding + (meterWidth * data.superEnergy / 100f).roundToInt(),
            meterY + 1,
            fade(0xE6E7EFF3.toInt(), visibility)
        )
    }

    private fun drawFooter(
        graphics: GuiGraphics,
        rect: DestinyNavigationTemplate.Rect,
        visibility: Float,
        fontScale: Float
    ) {
        graphics.fill(rect.x, rect.y - 6, rect.x + rect.width, rect.y - 5, fade(0x365F6E78, visibility))
    }

    private fun drawEdgeShade(graphics: GuiGraphics, width: Int, height: Int, visibility: Float) {
        val edge = (width * 0.08f).roundToInt().coerceAtLeast(8)
        graphics.fill(0, 0, edge, height, fade(0x52000000, visibility))
        graphics.fill(width - edge, 0, width, height, fade(0x52000000, visibility))
        graphics.fill(0, 0, width, (height * 0.05f).roundToInt(), fade(0x38000000, visibility))
    }

    private fun drawPanel(graphics: GuiGraphics, rect: DestinyNavigationTemplate.Rect, visibility: Float) {
        graphics.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, fade(0x66070D12, visibility))
        drawCorners(graphics, rect, fade(0x8FA6B4BD.toInt(), visibility))
    }

    private fun drawTopRule(graphics: GuiGraphics, rect: DestinyNavigationTemplate.Rect, visibility: Float) {
        graphics.fill(rect.x, rect.y - 6, rect.x + rect.width, rect.y - 5, fade(0xA8DCE5EA.toInt(), visibility))
        graphics.fill(rect.x, rect.y - 6, rect.x + (rect.width * 0.18f).roundToInt(), rect.y - 4, fade(0xE8F1F5F7.toInt(), visibility))
    }

    private fun drawCorners(graphics: GuiGraphics, rect: DestinyNavigationTemplate.Rect, color: Int) {
        val tick = minOf(7, rect.width / 4, rect.height / 4).coerceAtLeast(1)
        graphics.fill(rect.x, rect.y, rect.x + tick, rect.y + 1, color)
        graphics.fill(rect.x, rect.y, rect.x + 1, rect.y + tick, color)
        graphics.fill(rect.x + rect.width - tick, rect.y, rect.x + rect.width, rect.y + 1, color)
        graphics.fill(rect.x + rect.width - 1, rect.y, rect.x + rect.width, rect.y + tick, color)
        graphics.fill(rect.x, rect.y + rect.height - 1, rect.x + tick, rect.y + rect.height, color)
        graphics.fill(rect.x + rect.width - tick, rect.y + rect.height - 1, rect.x + rect.width, rect.y + rect.height, color)
    }

    private fun drawDiamondOutline(graphics: GuiGraphics, cx: Int, cy: Int, width: Int, height: Int, color: Int) {
        val halfH = (height / 2).coerceAtLeast(1)
        val halfW = (width / 2).coerceAtLeast(1)
        for (row in -halfH..halfH) {
            val x = (halfW * (1f - kotlin.math.abs(row).toFloat() / halfH)).roundToInt()
            graphics.fill(cx - x, cy + row, cx - x + 1, cy + row + 1, color)
            graphics.fill(cx + x, cy + row, cx + x + 1, cy + row + 1, color)
        }
    }

    private fun drawWrappedText(
        graphics: GuiGraphics,
        text: String,
        x: Int,
        y: Int,
        width: Int,
        color: Int,
        scale: Float,
        visibility: Float,
        maxLines: Int
    ): Int {
        val font = Minecraft.getInstance().font
        val lines = font.split(Component.literal(text), (width / scale).roundToInt().coerceAtLeast(1)).take(maxLines)
        var cursorY = y
        val lineHeight = (10f * scale).roundToInt().coerceAtLeast(8)
        lines.forEach { line ->
            drawFormattedText(graphics, line, x, cursorY, color, scale, visibility)
            cursorY += lineHeight
        }
        return cursorY
    }

    private fun drawText(
        graphics: GuiGraphics,
        text: String,
        x: Int,
        y: Int,
        color: Int,
        scale: Float,
        visibility: Float
    ) {
        if (text.isBlank()) return
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0f)
        pose.scale(scale, scale, 1f)
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, fade(color, visibility), false)
        pose.popPose()
    }

    private fun drawFormattedText(
        graphics: GuiGraphics,
        text: FormattedCharSequence,
        x: Int,
        y: Int,
        color: Int,
        scale: Float,
        visibility: Float
    ) {
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(x.toFloat(), y.toFloat(), 0f)
        pose.scale(scale, scale, 1f)
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, fade(color, visibility), false)
        pose.popPose()
    }

    private fun drawTextRight(
        graphics: GuiGraphics,
        text: String,
        right: Int,
        y: Int,
        color: Int,
        scale: Float,
        visibility: Float
    ) {
        val width = (Minecraft.getInstance().font.width(text) * scale).roundToInt()
        drawText(graphics, text, right - width, y, color, scale, visibility)
    }

    private fun fade(color: Int, visibility: Float): Int {
        val alpha = color ushr 24 and 0xFF
        return (alpha * visibility).roundToInt().coerceIn(0, 255) shl 24 or (color and 0x00FFFFFF)
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue
}
