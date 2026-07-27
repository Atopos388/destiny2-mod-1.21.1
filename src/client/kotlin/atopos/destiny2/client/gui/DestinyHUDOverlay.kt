package atopos.destiny2.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import kotlin.math.roundToInt
import kotlin.math.sin

/** Compact, screen-fixed Destiny ability HUD. It never participates in mouse hit testing. */
class DestinyHUDOverlay : HudRenderCallback {
    private val wasReady = BooleanArray(4) { true }
    private val completionFlashUntil = LongArray(4)

    override fun onHudRender(context: GuiGraphics, tickDelta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.player == null || client.options.hideGui) return

        val width = client.window.guiScaledWidth
        val height = client.window.guiScaledHeight
        val layout = runCatching { DestinyAbilityHUDTemplate.resolve(width, height) }.getOrNull() ?: return
        val data = DestinyAbilityHUDDataAdapter.snapshot()
        val now = System.currentTimeMillis()
        val superFlash = updateCompletionFlash(0, data.superAbility.progress, now)
        val grenadeFlash = updateCompletionFlash(1, data.grenade.progress, now)
        val meleeFlash = updateCompletionFlash(2, data.melee.progress, now)
        val classFlash = updateCompletionFlash(3, data.classAbility.progress, now)

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        drawMeter(context, layout.superMeter, data.superAbility.progress, now)
        drawDiamond(context, layout.superSocket, data.superAbility, now, superFlash)
        drawSocket(context, layout.grenadeSocket, data.grenade, grenadeFlash)
        drawSocket(context, layout.meleeSocket, data.melee, meleeFlash)
        drawSocket(context, layout.classSocket, data.classAbility, classFlash)
        drawWeaponSlot(context, layout.weaponSlot)
        drawDestinyShield(context, width, height)
        drawArmorCharge(context, width, height)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.disableBlend()

        DestinyPerkBuffOverlay.render(context, width, height)
    }

    private fun drawMeter(context: GuiGraphics, rect: DestinyAbilityHUDTemplate.Rect, progress: Float, now: Long) {
        context.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, 0x55101820)
        val filled = (rect.width * progress).roundToInt().coerceIn(0, rect.width)
        if (filled > 0) {
            context.fill(rect.x, rect.y, rect.x + filled, rect.y + rect.height, 0xD9E8EFF3.toInt())
            if (progress < 0.999f) {
                context.fill((rect.x + filled - 1).coerceAtLeast(rect.x), rect.y - 1, rect.x + filled, rect.y + rect.height + 1, 0xFFFFFFFF.toInt())
            } else if (rect.width > 8) {
                val sweep = ((now / 24L) % (rect.width + 12)).toInt() - 6
                val sweepX = rect.x + sweep
                context.fill(sweepX.coerceAtLeast(rect.x), rect.y - 1, (sweepX + 3).coerceAtMost(rect.x + rect.width), rect.y + rect.height + 1, 0x44FFFFFF)
            }
        }
    }

    private fun drawSocket(
        context: GuiGraphics,
        rect: DestinyAbilityHUDTemplate.Rect,
        ability: DestinyAbilityHUDData.Ability,
        flash: Float
    ) {
        context.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, 0x8A090D13.toInt())
        drawSocketFrame(context, rect, ability.progress)
        if (flash > 0f) drawCompletionFlash(context, rect, flash, diamond = false)
        drawAbilityIcon(context, rect, ability.icon, ability.progress)
    }

    private fun drawDiamond(
        context: GuiGraphics,
        rect: DestinyAbilityHUDTemplate.Rect,
        ability: DestinyAbilityHUDData.Ability,
        now: Long,
        flash: Float
    ) {
        val size = minOf(rect.width, rect.height)
        val cx = rect.x + size / 2
        for (row in 0 until size) {
            val distance = kotlin.math.abs(row - size / 2)
            val half = (size / 2 - distance).coerceAtLeast(0)
            context.fill(cx - half, rect.y + row, cx + half + 1, rect.y + row + 1, 0xB0090D13.toInt())
        }
        for (row in 0 until size) {
            val distance = kotlin.math.abs(row - size / 2)
            val half = (size / 2 - distance).coerceAtLeast(0)
            val readyBreath = if (ability.progress >= 0.999f) ((sin(now / 620.0) + 1.0) * 0.5) else 0.0
            val color = argb((126 + readyBreath * 58).roundToInt(), 232, 240, 245)
            context.fill(cx - half, rect.y + row, cx - half + 1, rect.y + row + 1, color)
            context.fill(cx + half, rect.y + row, cx + half + 1, rect.y + row + 1, color)
        }
        if (flash > 0f) drawCompletionFlash(context, rect, flash, diamond = true)
        val inset = (size * 0.23f).roundToInt().coerceAtLeast(4)
        drawAbilityIcon(context, DestinyAbilityHUDTemplate.Rect(rect.x + inset, rect.y + inset, size - inset * 2, size - inset * 2), ability.icon, ability.progress)
    }

    private fun drawAbilityIcon(
        context: GuiGraphics,
        rect: DestinyAbilityHUDTemplate.Rect,
        icon: ResourceLocation?,
        progress: Float
    ) {
        val inset = if (rect.width >= 24) 3 else 1
        val x = rect.x + inset
        val y = rect.y + inset
        val size = (minOf(rect.width, rect.height) - inset * 2).coerceAtLeast(1)
        if (icon == null) {
            drawPlaceholder(context, x, y, size)
            return
        }

        RenderSystem.setShaderColor(0.24f, 0.27f, 0.31f, 0.82f)
        context.blit(icon, x, y, 0f, 0f, size, size, size, size)
        val brightHeight = (size * progress).roundToInt().coerceIn(0, size)
        if (brightHeight > 0) {
            val revealY = y + size - brightHeight
            context.enableScissor(x, revealY, x + size, y + size)
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
            context.blit(icon, x, y, 0f, 0f, size, size, size, size)
            context.disableScissor()
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
    }

    private fun drawWeaponSlot(context: GuiGraphics, rect: DestinyAbilityHUDTemplate.Rect) {
        val client = Minecraft.getInstance()
        val stack = client.player?.mainHandItem ?: return
        context.fill(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, 0x73090D13)
        drawCornerBrackets(context, rect, 0x6FCED8DF)
        context.fill(rect.x + 1, rect.y + rect.height - 1, rect.x + rect.width - 1, rect.y + rect.height, 0x879EABB4.toInt())
        if (stack.isEmpty) return

        val itemSize = minOf(16, (rect.height - 4).coerceAtLeast(1))
        val itemX = rect.x + 3
        val itemY = rect.y + (rect.height - itemSize) / 2
        val pose = context.pose()
        pose.pushPose()
        pose.translate(itemX.toFloat(), itemY.toFloat(), 0f)
        val itemScale = itemSize / 16f
        pose.scale(itemScale, itemScale, 1f)
        context.renderItem(stack, 0, 0)
        pose.popPose()

        val textX = itemX + itemSize + 4
        val available = (rect.x + rect.width - textX - 3).coerceAtLeast(0)
        if (available > 4) {
            val name = client.font.plainSubstrByWidth(stack.hoverName.string, available)
            context.drawString(client.font, name, textX, rect.y + (rect.height - 8) / 2, 0xFFD6DEE5.toInt(), false)
        }
    }

    private fun drawDestinyShield(context: GuiGraphics, width: Int, height: Int) {
        val capacity = DestinyHUDState.healthShieldCapacity.coerceAtLeast(0.0f)
        if (capacity <= 0.0f) return
        val base = DestinyHUDState.healthShield.coerceIn(0.0f, capacity)
        val overshield = DestinyHUDState.classOvershield.coerceAtLeast(0.0f)
        val barWidth = 81
        val x = width / 2 - 91
        val y = height - 53
        context.fill(x, y, x + barWidth, y + 3, 0x66101620)
        val baseWidth = (barWidth * (base / capacity)).roundToInt().coerceIn(0, barWidth)
        if (baseWidth > 0) context.fill(x, y, x + baseWidth, y + 3, 0xD98ADCF2.toInt())
        if (overshield > 0.0f) {
            val extraWidth = (barWidth * (overshield / 4.0f)).roundToInt().coerceIn(1, barWidth)
            context.fill(x, y - 2, x + extraWidth, y, 0xE7D5B4FF.toInt())
        }
    }

    private fun drawArmorCharge(context: GuiGraphics, width: Int, height: Int) {
        val max = DestinyHUDState.armorChargeMax.coerceIn(3, 6)
        val active = DestinyHUDState.armorCharge.coerceIn(0, max)
        val size = 5
        val gap = 2
        val total = max * size + (max - 1) * gap
        val x = width / 2 - total / 2
        val y = height - 63
        repeat(max) { index ->
            val left = x + index * (size + gap)
            context.fill(left, y, left + size, y + size, if (index < active) 0xE8DCEBFA.toInt() else 0x663A4652)
            if (index < active) context.fill(left + 1, y + 1, left + size - 1, y + size - 1, 0xFFEAF5FF.toInt())
        }
    }

    private fun drawPlaceholder(context: GuiGraphics, x: Int, y: Int, size: Int) {
        context.fill(x, y, x + size, y + size, 0x8A151B22.toInt())
        context.fill(x + 2, y + size / 2, x + size - 2, y + size / 2 + 1, 0x99A9B4BF.toInt())
        context.fill(x + size / 2, y + 2, x + size / 2 + 1, y + size - 2, 0x99A9B4BF.toInt())
    }

    private fun updateCompletionFlash(index: Int, progress: Float, now: Long): Float {
        val ready = progress >= 0.999f
        if (ready && !wasReady[index]) completionFlashUntil[index] = now + 420L
        wasReady[index] = ready
        return ((completionFlashUntil[index] - now) / 420f).coerceIn(0f, 1f)
    }

    private fun drawSocketFrame(context: GuiGraphics, rect: DestinyAbilityHUDTemplate.Rect, progress: Float) {
        val frame = if (progress >= 0.999f) 0xB8E8F0F5.toInt() else 0x6879858E
        drawCornerBrackets(context, rect, frame)
        val innerWidth = (rect.width - 4).coerceAtLeast(1)
        val chargeWidth = (innerWidth * progress).roundToInt().coerceIn(0, innerWidth)
        context.fill(rect.x + 2, rect.y + rect.height - 2, rect.x + rect.width - 2, rect.y + rect.height - 1, 0x554F5962)
        if (chargeWidth > 0) {
            context.fill(rect.x + 2, rect.y + rect.height - 2, rect.x + 2 + chargeWidth, rect.y + rect.height - 1, 0xE5E8F0F5.toInt())
        }
    }

    private fun drawCompletionFlash(
        context: GuiGraphics,
        rect: DestinyAbilityHUDTemplate.Rect,
        strength: Float,
        diamond: Boolean
    ) {
        val expansion = (1f + (1f - strength) * 3f).roundToInt()
        val alpha = (strength * 132f).roundToInt().coerceIn(0, 132)
        val expanded = DestinyAbilityHUDTemplate.Rect(
            rect.x - expansion,
            rect.y - expansion,
            rect.width + expansion * 2,
            rect.height + expansion * 2
        )
        if (diamond) {
            drawDiamondOutline(context, expanded, argb(alpha, 245, 249, 252))
        } else {
            drawCornerBrackets(context, expanded, argb(alpha, 245, 249, 252))
        }
    }

    private fun drawCornerBrackets(context: GuiGraphics, rect: DestinyAbilityHUDTemplate.Rect, color: Int) {
        val tick = minOf(4, rect.width / 3, rect.height / 3).coerceAtLeast(1)
        context.fill(rect.x, rect.y, rect.x + tick, rect.y + 1, color)
        context.fill(rect.x, rect.y, rect.x + 1, rect.y + tick, color)
        context.fill(rect.x + rect.width - tick, rect.y, rect.x + rect.width, rect.y + 1, color)
        context.fill(rect.x + rect.width - 1, rect.y, rect.x + rect.width, rect.y + tick, color)
        context.fill(rect.x, rect.y + rect.height - 1, rect.x + tick, rect.y + rect.height, color)
        context.fill(rect.x, rect.y + rect.height - tick, rect.x + 1, rect.y + rect.height, color)
        context.fill(rect.x + rect.width - tick, rect.y + rect.height - 1, rect.x + rect.width, rect.y + rect.height, color)
        context.fill(rect.x + rect.width - 1, rect.y + rect.height - tick, rect.x + rect.width, rect.y + rect.height, color)
    }

    private fun drawDiamondOutline(context: GuiGraphics, rect: DestinyAbilityHUDTemplate.Rect, color: Int) {
        val size = minOf(rect.width, rect.height)
        val cx = rect.x + size / 2
        for (row in 0 until size) {
            val distance = kotlin.math.abs(row - size / 2)
            val half = (size / 2 - distance).coerceAtLeast(0)
            context.fill(cx - half, rect.y + row, cx - half + 1, rect.y + row + 1, color)
            context.fill(cx + half, rect.y + row, cx + half + 1, rect.y + row + 1, color)
        }
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (red shl 16) or (green shl 8) or blue

}
