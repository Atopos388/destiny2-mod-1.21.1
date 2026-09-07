package atopos.destiny2.common.block

import atopos.destiny2.common.item.DestinyItems
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import org.appliedenergistics.yoga.YogaPositionType

/** Slot packets render actual items; the authored background contains no baked-in items. */
object LightCollectorUI {
    fun create(holder: BlockUIMenuType.BlockUIHolder): ModularUI {
        val entity = holder.player.level().getBlockEntity(holder.pos) as? LightCollectorBlockEntity
        val inventory = entity?.inventory ?: LightCollectorInventory(DestinyItems.LIGHT_CRYSTAL, DestinyItems.GLIMMER)
        val root = UIElement().setId("collector_root")
        root.layout { it.widthPercent(100f).heightPercent(100f) }
        val canvas = UIElement().setId("collector_canvas").layout {
            it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(0f).width(360f).height(288f)
        }
        canvas.style { it.background(SpriteTexture.of(ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod", "textures/gui/light_collector.png"))) }
        root.addChild(canvas)
        canvas.addChild(slot("light_crystal", object : Slot(inventory, 0, 0, 0) {
            override fun mayPlace(stack: ItemStack) = inventory.canPlaceItem(0, stack)
        }, 74f, 59f))
        for (i in 1..3) {
            canvas.addChild(slot("glimmer_$i", object : Slot(inventory, i, 0, 0) {
                override fun mayPlace(stack: ItemStack) = false
            }, 253f + (i - 1) * 20f, 85f))
        }
        for (row in 0..2) for (column in 0..8) {
            val index = row * 9 + column + 9
            canvas.addChild(slot("inventory_$index", Slot(holder.player.inventory, index, 0, 0),
                89f + column * 20f, 164f + row * 22f, true))
        }
        for (column in 0..8) canvas.addChild(slot("hotbar_$column",
            Slot(holder.player.inventory, column, 0, 0), 89f + column * 20f, 245f, true))
        val collect = Button().apply {
            setId("collect_glimmer"); setText("")
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(249f).top(118f).width(61f).height(17f) }
            buttonStyle { it.baseTexture(ColorRectTexture(0)).hoverTexture(ColorRectTexture(0x20FFFFFF))
                .pressedTexture(ColorRectTexture(0x30404A50)) }
            setOnServerClick {
                val player = holder.player
                if (player is ServerPlayer && player.containerMenu.stillValid(player) &&
                    entity != null && player.level().getBlockEntity(holder.pos) === entity) {
                    for (i in 1..3) {
                        val stack = inventory.getItem(i)
                        if (!stack.isEmpty) {
                            val remainder = stack.copy()
                            player.inventory.add(remainder)
                            inventory.removeItem(i, stack.count - remainder.count)
                        }
                    }
                    player.containerMenu.broadcastChanges()
                }
            }
        }
        canvas.addChild(collect)
        // Five reusable UI pixels only; no world particles, per-tick packets or item creation.
        val flow = List(5) { index ->
            UIElement().setId("transfer_particle_$index").apply {
                layout { it.positionType(YogaPositionType.ABSOLUTE).width(2f).height(2f) }
                style { it.background(ColorRectTexture(0xE8FFFFFF.toInt())) }
                setActive(false)
                setVisible(false)
                canvas.addChild(this)
            }
        }
        root.addEventListener(UIEvents.TICK) {
            if (holder.player.level().isClientSide) {
                val running = inventory.canProcess()
                val clock = holder.player.level().gameTime % 50L
                flow.forEachIndexed { index, dot ->
                    dot.setVisible(running)
                    if (running) {
                        val phase = ((clock + index * 10L) % 50L) / 50f
                        val (x, y) = conduitPoint(phase)
                        dot.layout { it.left(kotlin.math.floor(x) - 1f).top(kotlin.math.floor(y) - 1f) }
                    }
                }
            }
        }
        // Contents belong to the block entity, not to the menu; closing never drains them.
        return object : ModularUI(UI.of(root), holder.player) {
            override fun init(screenWidth: Int, screenHeight: Int) {
                // Resolve viewport sizing before the first layout/render, not a later tick.
                val scale = minOf(screenWidth / 392f, screenHeight / 320f, 1f)
                canvas.layout { it.left(kotlin.math.floor((screenWidth - 360f * scale) / 2f))
                    .top(kotlin.math.floor((screenHeight - 288f * scale) / 2f)) }
                canvas.style { it.transform2D(Transform2D().pivot(0f, 0f).scale(scale)) }
                super.init(screenWidth, screenHeight)
            }
        }
    }

    internal fun conduitPoint(t: Float): Pair<Float, Float> {
        val p = t.coerceIn(0f, 1f)
        val u = 1f - p
        return Pair(
            u*u*u*125f + 3f*u*u*p*167f + 3f*u*p*p*205f + p*p*p*247f,
            u*u*u*111f + 3f*u*u*p*149f + 3f*u*p*p*99f + p*p*p*99f
        )
    }

    private fun slot(id: String, bound: Slot, x: Float, y: Float, playerSlot: Boolean = false): ItemSlot =
        ItemSlot(bound).apply {
            setId(id)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(16f).height(16f) }
            style { it.background(ColorRectTexture(0)) }
            slotStyle { it.isPlayerSlot(playerSlot).acceptQuickMove(true).hoverOverlay(ColorRectTexture(0x30FFFFFF)) }
        }
}

