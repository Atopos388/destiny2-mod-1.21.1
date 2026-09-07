package atopos.destiny2.common.block

import atopos.destiny2.common.item.DestinyItems
import net.minecraft.world.inventory.AbstractContainerMenu
import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.appliedenergistics.yoga.YogaPositionType

/** Native slot synchronization and server-only crafting over the approved painted background. */
private interface WorkbenchCleanup { fun returnContents() }

object TowerWorkbenchUI {
    @JvmStatic
    fun closeMenu(menu: AbstractContainerMenu) {
        ((menu as? IModularUIHolderMenu)?.modularUI as? WorkbenchCleanup)?.returnContents()
    }
    fun create(holder: BlockUIMenuType.BlockUIHolder): ModularUI {
        val session = TowerWorkbenchCrafting(DestinyItems.FORGOTTEN_HEART, DestinyItems.ANCIENT_SHARD, DestinyItems.FORGOTTEN_NAME)
        val root = UIElement().setId("workbench_root")
        root.layout { it.widthPercent(100f).heightPercent(100f) }
        // The root is transparent: only the centered 360x296 desktop is textured.
        val canvas = element("workbench_canvas", 0f, 0f, 360f, 296f)
        canvas.style { it.background(SpriteTexture.of(ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod", "textures/gui/tower_workbench.png"))) }
        root.addChild(canvas)
        canvas.addChild(label("title", "武器制作台", 105f, 16f, 110f, 12f, 9f))
        for (row in 0..2) for (column in 0..2) {
            val index = row * 3 + column
            canvas.addChild(slot("material_$index", Slot(session, index, 0, 0),
                59f + column * 27f, 76f + row * 27f, 22f, 22f, false))
        }
        val output = object : Slot(session, 9, 0, 0) {
            override fun mayPlace(stack: ItemStack): Boolean = false
        }
        canvas.addChild(slot("crafted_weapon", output, 193f, 139f, 24f, 18f, false))
        for (row in 0..2) for (column in 0..8) {
            val index = row * 9 + column + 9
            canvas.addChild(slot("inventory_$index", Slot(holder.player.inventory, index, 0, 0),
                94f + column * 19f, 210f + row * 16f, 13f, 13f, true))
        }
        for (column in 0..8) {
            canvas.addChild(slot("hotbar_$column", Slot(holder.player.inventory, column, 0, 0),
                94f + column * 19f, 270f, 13f, 13f, true))
        }
        val previewTexture = ItemStackTexture(ItemStack.EMPTY)
        canvas.addChild(element("weapon_preview", 212f, 74f, 48f, 48f).style { it.background(previewTexture) })
        val craft = Button().apply {
            setId("craft")
            setText("")
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(224f).top(189f).width(78f).height(14f) }
            buttonStyle {
                it.baseTexture(ColorRectTexture(0x00000000)).hoverTexture(ColorRectTexture(0x20FFF0B0))
                    .pressedTexture(ColorRectTexture(0x30504420))
            }
            setOnServerClick {
                val player = holder.player
                if (player is ServerPlayer && player.containerMenu.stillValid(player)) {
                    val state = player.level().getBlockState(holder.pos)
                    val block = state.block as? TowerWorkbenchBlock
                    if (block != null && block.stillValid(holder)) {
                        session.craft()
                        player.containerMenu.broadcastChanges()
                    }
                }
            }
        }
        canvas.addChild(craft)
        var lastStatus = -1
        root.addEventListener(UIEvents.TICK) {
            val current = if (!session.getItem(9).isEmpty) 2 else if (session.matches()) 1 else 0
            if (current != lastStatus) {
                lastStatus = current
                previewTexture.setItems(if (current == 0) ItemStack.EMPTY else DestinyItems.FORGOTTEN_NAME.defaultInstance)
                craft.setActive(current == 1)
            }
        }
        return object : ModularUI(UI.of(root), holder.player), WorkbenchCleanup {
            override fun init(screenWidth: Int, screenHeight: Int) {
                // Resolve viewport sizing before the first layout/render, not a later tick.
                val scale = minOf(screenWidth / 392f, screenHeight / 328f, 1f)
                canvas.layout { it.left(kotlin.math.floor((screenWidth - 360f * scale) / 2f))
                    .top(kotlin.math.floor((screenHeight - 296f * scale) / 2f)) }
                canvas.style { it.transform2D(Transform2D().pivot(0f, 0f).scale(scale)) }
                super.init(screenWidth, screenHeight)
            }
            override fun returnContents() {
                if (!holder.player.level().isClientSide) {
                    session.closeAndDrain().forEach { stack ->
                        if (holder.player.isAlive && !(holder.player as ServerPlayer).hasDisconnected()) {
                            holder.player.inventory.placeItemBackInInventory(stack)
                        } else holder.player.drop(stack, false)
                    }
                }
            }
            override fun onRemoved() {
                returnContents()
                super.onRemoved()
            }
        }
    }

    private fun slot(id: String, bound: Slot, x: Float, y: Float, w: Float, h: Float, playerSlot: Boolean): ItemSlot =
        ItemSlot(bound).apply {
            setId(id)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(w).height(h) }
            style { it.background(ColorRectTexture(0x00000000)) }
            slotStyle { it.isPlayerSlot(playerSlot).acceptQuickMove(true).hoverOverlay(ColorRectTexture(0x30FFFFFF)) }
        }

    private fun element(id: String, x: Float, y: Float, w: Float, h: Float): UIElement =
        UIElement().setId(id).layout {
            it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(w).height(h)
        }

    private fun label(id: String, value: String, x: Float, y: Float, w: Float, h: Float, size: Float): Label = Label().apply {
        setId(id)
        setValue(Component.literal(value))
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(x + 20f).top(y + 8f).width(w).height(h) }
        textStyle { it.fontSize(size).textColor(0xFFD0CEAB.toInt()).textShadow(false)
            .textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER) }
    }
}






