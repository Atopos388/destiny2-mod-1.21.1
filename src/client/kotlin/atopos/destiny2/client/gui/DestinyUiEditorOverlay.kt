package atopos.destiny2.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

class DestinyUiEditorOverlay(
    private val pageId: String,
    private val layoutProvider: () -> DestinyUiEditorLayout,
    private val addHit: (Int, Int, Int, Int, () -> Unit) -> Unit,
    private val handleAction: (String) -> Unit
) {
    var editMode = false
    private var selectedElementId: String? = null

    fun render(context: GuiGraphics) {
        val layout = layoutProvider()
        DestinyUiLayoutSettings.elements(pageId).filter { it.visible }.forEach { element ->
            drawElement(context, layout, element)
            addHit(element.x, element.y, element.width, element.height) {
                if (editMode) {
                    selectedElementId = element.id
                } else {
                    handleAction(element.action)
                }
            }
            if (editMode) {
                drawEditBorder(context, layout, element)
            }
        }
        drawControls(context, layout)
    }

    fun dragSelected(dragX: Double, dragY: Double): Boolean {
        if (!editMode) {
            return false
        }
        val id = selectedElementId ?: return false
        val element = DestinyUiLayoutSettings.elements(pageId).firstOrNull { it.id == id } ?: return false
        val layout = layoutProvider()
        element.x += (dragX / layout.scale).toInt()
        element.y += (dragY / layout.scale).toInt()
        return true
    }

    private fun drawControls(context: GuiGraphics, layout: DestinyUiEditorLayout) {
        button(context, layout, 24, 24, if (editMode) "完成" else "编辑") {
            editMode = !editMode
            if (!editMode) selectedElementId = null
        }

        if (!editMode) return

        button(context, layout, 24, 54, "加按钮") {
            selectedElementId = DestinyUiLayoutSettings.addElement(pageId, DestinyUiElementType.BUTTON).id
        }
        button(context, layout, 104, 54, "加文字") {
            selectedElementId = DestinyUiLayoutSettings.addElement(pageId, DestinyUiElementType.TEXT).id
        }
        button(context, layout, 184, 54, "加图片") {
            selectedElementId = DestinyUiLayoutSettings.addElement(pageId, DestinyUiElementType.IMAGE).id
        }
        button(context, layout, 24, 84, "删除") {
            DestinyUiLayoutSettings.removeElement(pageId, selectedElementId)
            selectedElementId = null
        }
        button(context, layout, 104, 84, "保存") { DestinyUiLayoutSettings.save() }
        button(context, layout, 184, 84, "重置") {
            DestinyUiLayoutSettings.resetPage(pageId)
            selectedElementId = null
        }
        button(context, layout, 24, 114, "+") { resizeSelected(8) }
        button(context, layout, 74, 114, "-") { resizeSelected(-8) }
    }

    private fun drawElement(context: GuiGraphics, layout: DestinyUiEditorLayout, element: DestinyUiElement) {
        when (element.type) {
            DestinyUiElementType.BUTTON -> drawButtonElement(context, layout, element)
            DestinyUiElementType.TEXT -> drawText(context, layout, element.text, element.x, element.y, 0xFFE6B66A.toInt())
            DestinyUiElementType.IMAGE -> drawImageElement(context, layout, element)
        }
    }

    private fun drawButtonElement(context: GuiGraphics, layout: DestinyUiEditorLayout, element: DestinyUiElement) {
        val x = layout.x(element.x)
        val y = layout.y(element.y)
        val w = layout.size(element.width)
        val h = layout.size(element.height)
        context.fill(x, y, x + w, y + h, 0xBB0A0D13.toInt())
        context.fill(x, y, x + w, y + 1, 0xFFE6B66A.toInt())
        context.fill(x, y + h - 1, x + w, y + h, 0xAAE6B66A.toInt())
        context.drawCenteredString(font(), element.text, x + w / 2, y + h / 2 - 4, 0xFFE6B66A.toInt())
    }

    private fun drawImageElement(context: GuiGraphics, layout: DestinyUiEditorLayout, element: DestinyUiElement) {
        val texture = ResourceLocation.tryParse(element.texture) ?: return
        val x = layout.x(element.x)
        val y = layout.y(element.y)
        val w = layout.size(element.width)
        val h = layout.size(element.height)
        context.blit(texture, x, y, 0.0f, 0.0f, w, h, w, h)
    }

    private fun drawEditBorder(context: GuiGraphics, layout: DestinyUiEditorLayout, element: DestinyUiElement) {
        val x = layout.x(element.x)
        val y = layout.y(element.y)
        val w = layout.size(element.width)
        val h = layout.size(element.height)
        val color = if (element.id == selectedElementId) 0xFFFF4040.toInt() else 0xAA40A0FF.toInt()
        context.fill(x, y, x + w, y + 1, color)
        context.fill(x, y + h - 1, x + w, y + h, color)
        context.fill(x, y, x + 1, y + h, color)
        context.fill(x + w - 1, y, x + w, y + h, color)
    }

    private fun button(context: GuiGraphics, layout: DestinyUiEditorLayout, x: Int, y: Int, text: String, action: () -> Unit) {
        val w = if (text.length <= 1) 38 else 70
        val h = 22
        val sx = layout.x(x)
        val sy = layout.y(y)
        val sw = layout.size(w)
        val sh = layout.size(h)
        context.fill(sx, sy, sx + sw, sy + sh, 0xCC080A10.toInt())
        context.fill(sx, sy, sx + sw, sy + 1, 0xFFE6B66A.toInt())
        context.drawCenteredString(font(), text, sx + sw / 2, sy + sh / 2 - 4, 0xFFE6B66A.toInt())
        addHit(x, y, w, h, action)
    }

    private fun resizeSelected(delta: Int) {
        val id = selectedElementId ?: return
        val element = DestinyUiLayoutSettings.elements(pageId).firstOrNull { it.id == id } ?: return
        element.width = (element.width + delta).coerceAtLeast(16)
        element.height = (element.height + delta).coerceAtLeast(12)
    }

    private fun drawText(context: GuiGraphics, layout: DestinyUiEditorLayout, text: String, x: Int, y: Int, color: Int) {
        context.drawString(font(), text, layout.x(x), layout.y(y), color, false)
    }

    private fun font(): Font = Minecraft.getInstance().font
}

data class DestinyUiEditorLayout(
    val x: Int,
    val y: Int,
    val scale: Float
) {
    fun x(value: Int) = x + (value * scale).toInt()
    fun y(value: Int) = y + (value * scale).toInt()
    fun size(value: Int) = (value * scale).toInt().coerceAtLeast(1)
}
