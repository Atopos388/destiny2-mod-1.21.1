package atopos.destiny2.client.gui

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.registries.BuiltInRegistries
import atopos.destiny2.client.weapon.DestinyWeaponAimClient
import atopos.destiny2.client.weapon.DestinyWeaponFeedbackClient
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import kotlin.math.roundToInt

class DestinyWeaponHUDOverlay : HudRenderCallback {
    override fun onHudRender(context: GuiGraphics, tickDelta: DeltaTracker) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (client.options.hideGui) return
        val state = DestinyWeaponHUDState.snapshot
        if (state != null && BuiltInRegistries.ITEM.getKey(player.mainHandItem.item).toString() == state.weaponId) {
            renderAmmoPanel(context, state, client.window.guiScaledWidth, client.window.guiScaledHeight)
        }
        renderWeaponCrosshair(context, client)
    }

    private fun renderAmmoPanel(
        context: GuiGraphics,
        state: DestinyWeaponHUDState.Snapshot,
        screenW: Int,
        screenH: Int
    ) {
        val pose = context.pose()
        pose.pushPose()
        pose.translate((screenW - EDGE_MARGIN).toFloat(), (screenH - EDGE_MARGIN).toFloat(), 0f)
        pose.scale(HUD_SCALE, HUD_SCALE, 1f)

        // Draw in a local bottom-right coordinate space so changing HUD_SCALE
        // never changes the screen-edge anchor.
        val x = -PANEL_WIDTH
        val y = -PANEL_HEIGHT
        val right = 0
        val accent = state.ammoType.hudColor
        val lowAmmo = state.magazine <= (state.capacity / 4).coerceAtLeast(1)
        val ammoColor = when {
            state.magazine <= 0 -> EMPTY_AMMO
            lowAmmo -> LOW_AMMO
            else -> TEXT_PRIMARY
        }
        val font = Minecraft.getInstance().font

        drawWedgePanel(context, x, y, right, y + PANEL_HEIGHT, accent)

        val magazineText = state.magazine.toString().padStart(2, '0')
        drawScaledRightAligned(context, magazineText, x + 53, y + 7, 1.85f, ammoColor)

        val reserveText = if (state.reserve == Int.MAX_VALUE || state.reserve < 0) "∞" else state.reserve.toString()
        context.fill(x + 59, y + 9, x + 60, y + 24, BORDER_SOFT)
        context.drawString(font, reserveText, x + 68, y + 13, TEXT_SECONDARY, false)

        val progress = DestinyWeaponHUDState.reloadProgress(state)
        if (state.reloadRemaining > 0) {
            val phaseName = when (state.reloadPhase) {
                atopos.destiny2.common.weapon.WeaponReloadPhase.STARTING -> "拆卸"
                atopos.destiny2.common.weapon.WeaponReloadPhase.FEEDING -> "装填"
                atopos.destiny2.common.weapon.WeaponReloadPhase.FINISHING -> "复位"
                else -> "换弹"
            }
            val progressText = "$phaseName ${(progress * 100).roundToInt().coerceIn(0, 100)}%"
            context.drawString(font, progressText, x + 12, y + 29, TEXT_PRIMARY, false)
            context.fill(x + 12, y + 40, right - 11, y + 43, EMPTY_SEGMENT)
            context.fill(x + 12, y + 40, x + 12 + ((PANEL_WIDTH - 23) * progress).roundToInt(), y + 43, accent)
        } else {
            drawAmmoDiamond(context, x + 12, y + 31, accent)
            context.drawString(font, state.ammoType.displayName, x + 21, y + 29, accent, false)
            val actionText = when {
                state.boltRemaining > 0 -> "拉栓"
                state.chamberEmpty -> "空仓"
                else -> state.fireMode.displayName
            }
            context.drawString(
                font,
                actionText,
                right - 11 - font.width(actionText),
                y + 29,
                if (state.chamberEmpty) EMPTY_AMMO else TEXT_SECONDARY,
                false
            )
            drawMagazineSegments(context, state, x + 12, y + 40, right - 11, ammoColor)
        }
        pose.popPose()
    }

    private fun drawWedgePanel(context: GuiGraphics, left: Int, top: Int, right: Int, bottom: Int, accent: Int) {
        // One continuous translucent shell with 5px clipped corners.
        context.fill(left + 5, top, right - 5, bottom, PANEL_BACKGROUND)
        context.fill(left, top + 5, right, bottom - 5, PANEL_BACKGROUND)
        context.fill(left + 2, top + 2, right - 2, bottom - 2, PANEL_BACKGROUND)

        context.fill(left + 5, top, right - 5, top + 1, BORDER)
        context.fill(left + 5, bottom - 1, right - 5, bottom, BORDER)
        context.fill(left, top + 5, left + 1, bottom - 5, BORDER)
        context.fill(right - 1, top + 5, right, bottom - 5, BORDER)
        repeat(5) { step ->
            context.fill(left + step, top + 5 - step, left + step + 1, top + 6 - step, BORDER)
            context.fill(right - 5 + step, top + step, right - 4 + step, top + step + 1, BORDER)
            context.fill(left + step, bottom - 6 + step, left + step + 1, bottom - 5 + step, BORDER)
            context.fill(right - 5 + step, bottom - 1 - step, right - 4 + step, bottom - step, BORDER)
        }

        context.fill(left + 6, top + 4, left + 8, bottom - 4, withAlpha(accent, 0xA8))
        context.fill(left + 8, top + 7, left + 12, top + 8, withAlpha(accent, 0x70))
    }

    private fun drawScaledRightAligned(
        context: GuiGraphics,
        text: String,
        right: Int,
        top: Int,
        scale: Float,
        color: Int
    ) {
        val font = Minecraft.getInstance().font
        val pose = context.pose()
        pose.pushPose()
        pose.translate(right - font.width(text) * scale, top.toFloat(), 0f)
        pose.scale(scale, scale, 1f)
        context.drawString(font, text, 0, 0, color, false)
        pose.popPose()
    }

    private fun drawAmmoDiamond(context: GuiGraphics, centerX: Int, centerY: Int, color: Int) {
        context.fill(centerX, centerY - 3, centerX + 1, centerY + 4, color)
        context.fill(centerX - 1, centerY - 2, centerX + 2, centerY + 3, color)
        context.fill(centerX - 2, centerY - 1, centerX + 3, centerY + 2, color)
        context.fill(centerX - 1, centerY, centerX + 2, centerY + 1, PANEL_BACKGROUND)
    }

    private fun drawMagazineSegments(
        context: GuiGraphics,
        state: DestinyWeaponHUDState.Snapshot,
        left: Int,
        top: Int,
        right: Int,
        loadedColor: Int
    ) {
        val count = state.capacity.coerceIn(1, 16)
        val gap = 2
        val width = (right - left).coerceAtLeast(count * 2)
        val segmentWidth = ((width - gap * (count - 1)) / count).coerceAtLeast(2)
        val usedWidth = segmentWidth * count + gap * (count - 1)
        val startX = right - usedWidth
        val loaded = if (state.capacity <= 0) 0 else
            (state.magazine.toFloat() / state.capacity * count).roundToInt().coerceIn(0, count)
        repeat(count) { index ->
            val segmentX = startX + index * (segmentWidth + gap)
            context.fill(segmentX, top, segmentX + segmentWidth, top + 3, if (index < loaded) loadedColor else EMPTY_SEGMENT)
        }
    }

    private fun renderWeaponCrosshair(context: GuiGraphics, client: Minecraft) {
        val player = client.player ?: return
        val stack = player.mainHandItem
        val weapon = stack.item as? DestinyRangedWeapon ?: return
        if (!client.options.cameraType.isFirstPerson) return

        val state = DestinyWeaponHUDState.snapshot
        val crosshair = state?.crosshair ?: weapon.combatProfile(stack).crosshair
        val reloading = state?.reloadRemaining?.let { it > 0 } == true
        val aim = DestinyWeaponAimClient.progress(1.0f)
        val cx = client.window.guiScaledWidth / 2
        val cy = client.window.guiScaledHeight / 2

        if (!reloading && aim < crosshair.hideAimProgress) {
            val movement = player.deltaMovement.horizontalDistance().toFloat().coerceAtMost(0.45f)
            val gap = (
                crosshair.baseGap +
                    movement * crosshair.movingPenalty * 10.0f +
                    (if (player.onGround()) 0.0f else crosshair.airbornePenalty) +
                    DestinyWeaponFeedbackClient.shotBloom()
                ).roundToInt().coerceIn(3, 28)
            val color = 0xE8F4F7FA.toInt()
            context.fill(cx - gap - 5, cy, cx - gap, cy + 1, color)
            context.fill(cx + gap, cy, cx + gap + 5, cy + 1, color)
            context.fill(cx, cy - gap - 5, cx + 1, cy - gap, color)
            context.fill(cx, cy + gap, cx + 1, cy + gap + 5, color)
            context.fill(cx, cy, cx + 1, cy + 1, color)
        }

        val now = System.currentTimeMillis()
        if (now >= DestinyWeaponHUDState.hitMarkerUntil) return
        val markerColor = when {
            now < DestinyWeaponHUDState.killMarkerUntil -> 0xFFFF5D57.toInt()
            DestinyWeaponHUDState.lastHitPrecision -> 0xFFFFA62B.toInt()
            else -> 0xFFF5F7FA.toInt()
        }
        val markerGap = if (now < DestinyWeaponHUDState.killMarkerUntil) 6 else 4
        context.fill(cx - markerGap - 4, cy - markerGap - 1, cx - markerGap, cy - markerGap, markerColor)
        context.fill(cx + markerGap, cy - markerGap - 1, cx + markerGap + 4, cy - markerGap, markerColor)
        context.fill(cx - markerGap - 4, cy + markerGap, cx - markerGap, cy + markerGap + 1, markerColor)
        context.fill(cx + markerGap, cy + markerGap, cx + markerGap + 4, cy + markerGap + 1, markerColor)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    private companion object {
        const val PANEL_WIDTH = 112
        const val PANEL_HEIGHT = 48
        const val EDGE_MARGIN = 10
        const val HUD_SCALE = 0.80f
        const val PANEL_BACKGROUND = 0x780A0F15
        const val BORDER = 0xA88B9AA7.toInt()
        const val BORDER_SOFT = 0x708B9AA7
        const val TEXT_PRIMARY = 0xFFF5F7FA.toInt()
        const val TEXT_SECONDARY = 0xFFB2BCC7.toInt()
        const val LOW_AMMO = 0xFFFFB45B.toInt()
        const val EMPTY_AMMO = 0xFFFF6B5E.toInt()
        const val EMPTY_SEGMENT = 0x554E5864
    }
}
