package atopos.destiny2.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/** 命运风格 Perk Buff：固定在屏幕右侧中部，避开底部技能 HUD。 */
object DestinyPerkBuffOverlay {
    private const val TEXT_SCALE = 0.70f

    fun render(context: GuiGraphics, screenWidth: Int, screenHeight: Int) {
        val font = Minecraft.getInstance().font
        val buffs = DestinyPerkBuffState.visible()
        if (buffs.isEmpty()) return

        var y = (screenHeight * 0.44f).toInt()
        val left = 3
        buffs.forEach { buff ->
            val label = buildString {
                append(buff.name)
                if (buff.stacks > 1) append(" x").append(buff.stacks)
                append("  ").append(DestinyPerkBuffState.remainingSeconds(buff)).append('s')
            }
            val width = (font.width(label) * TEXT_SCALE).toInt()
            val x = left
            val right = x + width
            context.fill(x - 3, y - 1, right + 7, y + 8, 0xA0000000.toInt())
            context.fill(x, y - 1, x + 2, y + 8, 0xFFF2D15B.toInt())
            context.pose().pushPose()
            context.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0f)
            context.drawString(font, label, ((x + 5) / TEXT_SCALE).toInt(), (y / TEXT_SCALE).toInt(), 0xFFF6F2E8.toInt(), true)
            context.pose().popPose()
            y += 11
        }
    }
}
