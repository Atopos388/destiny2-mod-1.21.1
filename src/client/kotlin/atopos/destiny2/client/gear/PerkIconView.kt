package atopos.destiny2.client.gear

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics

/** Mechanical 24px Perk socket with clipped corners and an active rail. */
object PerkIconView {
    private const val SIZE = 24

    fun render(
        graphics: GuiGraphics,
        font: Font,
        perk: PerkTooltipEntry,
        x: Int,
        y: Int,
        theme: DestinyPerkTooltipTheme,
        accent: Int,
        selected: Boolean = false
    ) {
        graphics.fill(x + 2, y, x + SIZE - 2, y + SIZE, theme.surface)
        graphics.fill(x, y + 2, x + SIZE, y + SIZE - 2, theme.surface)
        graphics.fill(x + 2, y, x + SIZE - 2, y + 1, theme.border)
        graphics.fill(x, y + 2, x + 1, y + SIZE - 2, theme.border)
        graphics.fill(x + SIZE - 1, y + 2, x + SIZE, y + SIZE - 2, theme.border)
        graphics.fill(x + 2, y + SIZE - 2, x + SIZE - 2, y + SIZE, if (selected) 0xFFFFFFFF.toInt() else accent)

        // Opposing corner brackets read as a socket rather than a generic card.
        graphics.fill(x - 1, y + 3, x + 1, y + 8, accent)
        graphics.fill(x - 1, y + 3, x + 4, y + 5, accent)
        graphics.fill(x + SIZE - 1, y + SIZE - 8, x + SIZE + 1, y + SIZE - 3, theme.border)
        graphics.fill(x + SIZE - 4, y + SIZE - 5, x + SIZE + 1, y + SIZE - 3, theme.border)

        if (selected) {
            graphics.fill(x - 2, y, x + SIZE + 2, y + 1, accent)
            graphics.fill(x - 2, y + SIZE, x + SIZE + 2, y + SIZE + 1, accent)
        }

        if (Minecraft.getInstance().resourceManager.getResource(perk.icon).isPresent) {
            graphics.blit(perk.icon, x + 4, y + 3, 0f, 0f, 16, 16, 16, 16)
        } else {
            val glyph = perk.name.take(1).ifBlank { "◆" }
            graphics.drawString(
                font,
                glyph,
                x + (SIZE - font.width(glyph)) / 2,
                y + 7,
                0xFFF4F7F9.toInt(),
                false
            )
        }
    }

    fun renderEmptyCatalyst(
        graphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        theme: DestinyPerkTooltipTheme,
        accent: Int,
        selected: Boolean = false
    ) {
        val mutedBorder = withAlpha(theme.border, 0x65)
        graphics.fill(x + 2, y, x + SIZE - 2, y + SIZE, withAlpha(theme.surface, 0x78))
        graphics.fill(x, y + 2, x + SIZE, y + SIZE - 2, withAlpha(theme.surface, 0x78))
        graphics.fill(x + 2, y, x + SIZE - 2, y + 1, mutedBorder)
        graphics.fill(x, y + 2, x + 1, y + SIZE - 2, mutedBorder)
        graphics.fill(x + SIZE - 1, y + 2, x + SIZE, y + SIZE - 2, mutedBorder)
        graphics.fill(x + 2, y + SIZE - 1, x + SIZE - 2, y + SIZE, withAlpha(accent, 0x50))
        if (selected) {
            graphics.fill(x - 2, y, x + SIZE + 2, y + 1, accent)
            graphics.fill(x - 2, y + SIZE, x + SIZE + 2, y + SIZE + 1, accent)
        }
        val glyph = "◇"
        graphics.drawString(font, glyph, x + (SIZE - font.width(glyph)) / 2, y + 7, withAlpha(theme.mutedText, 0x88), false)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
}
