package atopos.destiny2.client.gui

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext
import net.minecraft.resources.ResourceLocation
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Diamond subclass entry rendered inside the online-editable equipment host. */
class DestinyDiamondElementIcon(
    private val icon: ResourceLocation,
    private val accent: Int
) : UIElement() {
    init {
        setId("equipment_subclass_runtime_icon")
        setAllowHitTest(true)
    }

    override fun drawBackgroundAdditional(context: GUIContext) {
        super.drawBackgroundAdditional(context)
        val size = min(contentWidth, contentHeight).roundToInt().coerceAtLeast(8)
        val left = (contentX + (contentWidth - size) / 2f).roundToInt()
        val top = (contentY + (contentHeight - size) / 2f).roundToInt()

        drawDiamond(context, left, top, size, withAlpha(accent, 0xE8))
        drawDiamond(context, left + 2, top + 2, size - 4, 0xD6292C31.toInt())
        drawDiamond(context, left + 7, top + 7, size - 14, 0x70E7E9EA)
        drawDiamond(context, left + 8, top + 8, size - 16, 0xDD34383E.toInt())

        if (context.mc.resourceManager.getResource(icon).isPresent) {
            val inset = size * 0.23f
            context.drawTexture(
                SpriteTexture.of(icon),
                left + inset,
                top + inset,
                size - inset * 2f,
                size - inset * 2f
            )
        }
    }

    private fun drawDiamond(context: GUIContext, x: Int, y: Int, size: Int, color: Int) {
        if (size <= 0) return
        val center = (size - 1) / 2f
        val radius = (size / 2f).coerceAtLeast(1f)
        for (row in 0 until size) {
            val half = (radius - abs(row - center)).coerceAtLeast(0f).roundToInt()
            val centerX = x + size / 2
            context.graphics.fill(centerX - half, y + row, centerX + half + 1, y + row + 1, color)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255) and 0xFF) shl 24)
}
