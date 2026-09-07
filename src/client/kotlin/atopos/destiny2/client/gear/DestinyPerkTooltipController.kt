package atopos.destiny2.client.gear

import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack

/** Shared hover and pinned-inspection state for vanilla and LDLib2 containers. */
object DestinyPerkTooltipController {
    private data class HoverSnapshot(
        val data: PerkTooltipData,
        val containerId: Int,
        val menuSlot: Int,
        val activeNanos: Long,
        val transientStack: ItemStack?
    )

    private var cachedData: PerkTooltipData? = null
    private var cachedExpanded = false
    private var cachedWidth = -1
    private var cachedSelectedIndex = -1
    private var cachedCatalystSelected = false
    private var cachedCatalystIndex = -1
    private var cachedCatalystPickerOpen = false
    private var cachedArmorSocket = -1
    private var cachedArmorPage = -1
    private var cachedComponent: DestinyPerkTooltipComponent? = null
    private var parsedStack = ItemStack.EMPTY
    private var parsedData: PerkTooltipData? = null
    private var parsedCacheInitialized = false
    private var directorHoverStack = ItemStack.EMPTY

    private var selectedPerkIndex = 0
    private var catalystSelected = false
    private var selectedCatalystIndex = 0
    private var catalystPickerOpen = false
    private var selectedArmorSocket = 0
    private var armorModPage = 0
    private var lastActiveNanos = 0L
    private var lastMouseX = 0
    private var lastMouseY = 0
    private var panelX = 0
    private var panelY = 0
    private var panelScale = 1f

    private var pinned = false
    private var pinnedData: PerkTooltipData? = null
    private var pinMouseX = 0
    private var pinMouseY = 0
    private var hoverContainerId = -1
    private var hoverMenuSlot = -1
    private var hoverTransient = false
    private var hoverSnapshot: HoverSnapshot? = null
    private var pinnedContainerId = -1
    private var pinnedMenuSlot = -1
    private var pinnedTransientStack: ItemStack? = null

    @JvmStatic
    fun isPinned(): Boolean = pinned

    @JvmStatic
    fun setHoverTarget(containerId: Int, menuSlot: Int) {
        hoverContainerId = containerId
        hoverMenuSlot = menuSlot
        hoverTransient = false
    }

    @JvmStatic
    fun setTransientHoverTarget() {
        hoverContainerId = -1
        hoverMenuSlot = -1
        hoverTransient = true
    }

    /** Supplies only the compact hover component. Pinned cards render globally after the screen. */
    @JvmStatic
    fun componentFor(stack: ItemStack): DestinyPerkTooltipComponent? = componentForMode(stack, expanded = false)

    /** Supplies the full Destiny inspection card used by equipment slots and their choice trays. */
    @JvmStatic
    fun expandedComponentFor(stack: ItemStack): DestinyPerkTooltipComponent? =
        componentForMode(stack, expanded = true)

    private fun componentForMode(stack: ItemStack, expanded: Boolean): DestinyPerkTooltipComponent? {
        if (pinned) return null
        val data = tooltipDataFor(stack)
        if (data == null) {
            // Do not let the previous valid gear card survive while the cursor
            // is currently over an unrelated or empty stack.
            hoverSnapshot = null
            lastActiveNanos = 0L
            return null
        }
        activateHover(data)
        hoverSnapshot = HoverSnapshot(
            data,
            hoverContainerId,
            hoverMenuSlot,
            lastActiveNanos,
            stack.copy().takeIf { hoverTransient }
        )
        return component(data, expanded)
    }

    private fun tooltipDataFor(stack: ItemStack): PerkTooltipData? {
        if (parsedCacheInitialized && ItemStack.matches(parsedStack, stack)) {
            return parsedData
        }
        parsedStack = stack.copy()
        parsedData = PerkTooltipDataAdapter.from(stack)
        parsedCacheInitialized = true
        return parsedData
    }

    /**
     * Director equipment slots report hover state here instead of constructing
     * one permanent tooltip tree per slot. Data is parsed once on entry and the
     * full card is drawn by [renderDirectorHover] above the completed LDLib UI.
     */
    @JvmStatic
    fun setDirectorHoverStack(stack: ItemStack) {
        if (pinned || stack.isEmpty) {
            directorHoverStack = ItemStack.EMPTY
            return
        }
        val copy = stack.copy()
        if (tooltipDataFor(copy) == null) {
            directorHoverStack = ItemStack.EMPTY
            return
        }
        if (!ItemStack.matches(directorHoverStack, copy)) {
            directorHoverStack = copy
            cachedComponent = null
        }
    }

    @JvmStatic
    fun clearDirectorHoverStack(expected: ItemStack? = null) {
        if (expected == null || ItemStack.matches(directorHoverStack, expected)) {
            directorHoverStack = ItemStack.EMPTY
        }
    }

    @JvmStatic
    fun hasTooltipData(stack: ItemStack): Boolean = !stack.isEmpty && tooltipDataFor(stack) != null

    /** Shift release toggles a stable inspection surface instead of requiring Shift to be held. */
    fun togglePinned(): Boolean {
        if (pinned) {
            closePinned()
            return true
        }
        val snapshot = hoverSnapshot ?: return false
        if (System.nanoTime() - snapshot.activeNanos > HOVER_GRACE_NANOS) return false
        val liveStack = snapshot.transientStack?.copy()
            ?: resolveStack(snapshot.containerId, snapshot.menuSlot)
            ?: return false
        val data = PerkTooltipDataAdapter.from(liveStack) ?: return false
        // The slot may have changed between tooltip rendering and Shift release.
        // Never open a different piece of gear with the previous card state.
        if (data.definitionId != snapshot.data.definitionId) return false
        pinned = true
        pinnedData = data
        selectedPerkIndex = selectedPerkIndex.coerceIn(0, (data.perks.size - 1).coerceAtLeast(0))
        selectedCatalystIndex = selectedCatalystIndex.coerceIn(0, (data.catalysts.size - 1).coerceAtLeast(0))
        catalystSelected = false
        catalystPickerOpen = false
        pinMouseX = lastMouseX
        pinMouseY = lastMouseY
        pinnedContainerId = snapshot.containerId
        pinnedMenuSlot = snapshot.menuSlot
        pinnedTransientStack = snapshot.transientStack?.copy()
        cachedComponent = null
        return true
    }

    fun cyclePerk(direction: Int): Boolean {
        val data = interactiveData() ?: return false
        if (data.armorStats != null) {
            val count = data.availableArmorModsBySocket.getOrNull(selectedArmorSocket).orEmpty().size
            val pages = ((count + DestinyPerkTooltipView.ARMOR_MODS_PER_PAGE - 1) / DestinyPerkTooltipView.ARMOR_MODS_PER_PAGE).coerceAtLeast(1)
            armorModPage = Math.floorMod(armorModPage + direction, pages)
            cachedComponent = null
            return true
        }
        if (catalystSelected) {
            if (!catalystPickerOpen || data.catalysts.isEmpty()) return false
            selectedCatalystIndex = Math.floorMod(selectedCatalystIndex + direction, data.catalysts.size)
        } else {
            if (data.perks.isEmpty()) return false
            selectedPerkIndex = Math.floorMod(selectedPerkIndex + direction, data.perks.size)
        }
        cachedComponent = null
        return true
    }

    fun selectPerk(index: Int): Boolean {
        val data = interactiveData() ?: return false
        if (index !in data.perks.indices) return false
        selectedPerkIndex = index
        catalystSelected = false
        catalystPickerOpen = false
        cachedComponent = null
        return true
    }

    private fun selectCatalystSlot(): Boolean {
        val data = pinnedData ?: return false
        if (!pinned || !data.hasCatalystSlot) return false
        catalystSelected = true
        catalystPickerOpen = false
        selectedCatalystIndex = selectedCatalystIndex.coerceIn(0, (data.catalysts.size - 1).coerceAtLeast(0))
        cachedComponent = null
        return true
    }

    /**
     * Consumes clicks inside the pinned card and selects real Perk sockets.
     * A click outside dismisses the card without consuming the underlying screen click.
     */
    fun clickPinned(mouseX: Double, mouseY: Double): Boolean {
        if (!pinned) return false
        val layout = component(pinnedData ?: return false, expanded = true).layout
        val mx = panelX + ((mouseX - panelX) / panelScale).toInt()
        val my = panelY + ((mouseY - panelY) / panelScale).toInt()
        if (mx !in panelX until (panelX + layout.width) || my !in panelY until (panelY + layout.height)) {
            closePinned()
            return false
        }

        val data = layout.data
        if (data.armorStats != null) {
            val rightX = panelX + layout.leftWidth + 11
            val rightEdge = panelX + layout.width - 10
            val socketGrid = DestinyPerkTooltipView.squareGridGeometry(
                rightX,
                rightEdge,
                4,
                DestinyPerkTooltipView.ARMOR_SOCKET_MAX_SIZE,
                DestinyPerkTooltipView.ARMOR_SOCKET_GAP
            )
            val socketTop = panelY + DestinyPerkTooltipView.ARMOR_SOCKET_TOP
            repeat(4) { socket ->
                val left = socketGrid.startX + socket * (socketGrid.size + socketGrid.gap)
                if (mx in left until (left + socketGrid.size) && my in socketTop until (socketTop + socketGrid.size)) {
                    selectedArmorSocket = socket
                    armorModPage = 0
                    cachedComponent = null
                    return true
                }
            }
            val modGrid = DestinyPerkTooltipView.squareGridGeometry(
                rightX,
                rightEdge,
                DestinyPerkTooltipView.ARMOR_MOD_GRID_COLUMNS,
                DestinyPerkTooltipView.ARMOR_MOD_MAX_SIZE,
                DestinyPerkTooltipView.ARMOR_MOD_COLUMN_GAP
            )
            val gridTop = panelY + DestinyPerkTooltipView.ARMOR_SELECTOR_TOP
            val allMods = data.availableArmorModsBySocket.getOrNull(selectedArmorSocket).orEmpty()
            val pageMods = allMods.drop(armorModPage * DestinyPerkTooltipView.ARMOR_MODS_PER_PAGE)
                .take(DestinyPerkTooltipView.ARMOR_MODS_PER_PAGE)
            pageMods.indices.firstOrNull { index ->
                val column = index % DestinyPerkTooltipView.ARMOR_MOD_GRID_COLUMNS
                val row = index / DestinyPerkTooltipView.ARMOR_MOD_GRID_COLUMNS
                val left = modGrid.startX + column * (modGrid.size + modGrid.gap)
                val top = gridTop + row * (modGrid.size + DestinyPerkTooltipView.ARMOR_MOD_ROW_GAP)
                mx in left until (left + modGrid.size) && my in top until (top + modGrid.size)
            }?.let { index ->
                ClientPlayNetworking.send(
                    DestinyNetworking.ConfigureArmorModPayload(
                        pinnedContainerId,
                        pinnedMenuSlot,
                        selectedArmorSocket,
                        pageMods[index].id.toString()
                    )
                )
                return true
            }

            val actionY = panelY + layout.height - 16
            if (data.equippedArmorMods.getOrNull(selectedArmorSocket) != null &&
                mx in rightX until rightEdge && my in actionY until (actionY + 14)
            ) {
                ClientPlayNetworking.send(
                    DestinyNetworking.ConfigureArmorModPayload(pinnedContainerId, pinnedMenuSlot, selectedArmorSocket, "")
                )
            }
            return true
        }

        val iconY = panelY + 62
        val slotCount = data.perks.size + if (data.hasCatalystSlot) 1 else 0
        val available = layout.leftWidth - 24
        val step = if (slotCount <= 1) 31 else ((available - 24) / (slotCount - 1)).coerceIn(27, 34)
        data.perks.indices.firstOrNull { index ->
            val iconX = panelX + 12 + index * step
            mx in iconX until (iconX + 24) && my in iconY until (iconY + 24)
        }?.let(::selectPerk)

        if (data.hasCatalystSlot) {
            val catalystX = panelX + 12 + data.perks.size * step
            if (mx in catalystX until (catalystX + 24) && my in iconY until (iconY + 24)) {
                selectCatalystSlot()
                return true
            }
        }

        if (catalystSelected) {
            val rightX = panelX + layout.leftWidth + 11
            val rightEdge = panelX + layout.width - 10
            val selectorRight = (rightX + 72).coerceAtMost(rightEdge)
            if (mx in rightX until selectorRight && my in (panelY + 29) until (panelY + 42)) {
                catalystPickerOpen = !catalystPickerOpen
                cachedComponent = null
                return true
            }

            if (catalystPickerOpen) {
                val pickerTop = panelY + 46
                data.catalysts.indices.firstOrNull { index ->
                    val rowTop = pickerTop + 4 + index * 23
                    mx in (rightX + 3) until (rightEdge - 3) && my in rowTop until (rowTop + 20)
                }?.let { index ->
                    selectedCatalystIndex = index
                    val catalyst = data.catalysts[index]
                    ClientPlayNetworking.send(
                        DestinyNetworking.ConfigureGearCatalystPayload(
                            pinnedContainerId,
                            pinnedMenuSlot,
                            catalyst.id.toString()
                        )
                    )
                    catalystPickerOpen = false
                    cachedComponent = null
                    return true
                }
                catalystPickerOpen = false
                cachedComponent = null
                return true
            }

            val buttonTop = panelY + layout.height - 38
            if (data.equippedCatalystId != null &&
                mx in rightX until rightEdge && my in buttonTop until (buttonTop + 14)
            ) {
                ClientPlayNetworking.send(
                    DestinyNetworking.ConfigureGearCatalystPayload(
                        pinnedContainerId,
                        pinnedMenuSlot,
                        ""
                    )
                )
                return true
            }
        }
        return true
    }

    fun isMouseOverPinned(mouseX: Int, mouseY: Int): Boolean {
        val layout = cachedComponent?.layout ?: return false
        val width = (layout.width * panelScale).toInt()
        val height = (layout.height * panelScale).toInt()
        return pinned && mouseX in panelX until (panelX + width) && mouseY in panelY until (panelY + height)
    }

    fun clearInteraction() {
        closePinned()
        directorHoverStack = ItemStack.EMPTY
        lastActiveNanos = 0L
        hoverSnapshot = null
        hoverContainerId = -1
        hoverMenuSlot = -1
        hoverTransient = false
        cachedData = null
        cachedComponent = null
        parsedStack = ItemStack.EMPTY
        parsedData = null
        parsedCacheInitialized = false
    }

    /**
     * Draws one shared expanded equipment card after the Director's LDLib tree.
     * This deliberately bypasses HoverTooltips so the card cannot be discarded
     * by tooltip conversion or covered by another UI plane.
     */
    fun renderDirectorHover(graphics: GuiGraphics, mouseX: Int, mouseY: Int): Boolean {
        if (pinned || directorHoverStack.isEmpty) return false
        val stack = directorHoverStack
        val data = tooltipDataFor(stack) ?: run {
            directorHoverStack = ItemStack.EMPTY
            return false
        }
        setTransientHoverTarget()
        lastMouseX = mouseX
        lastMouseY = mouseY
        activateHover(data)
        hoverSnapshot = HoverSnapshot(data, -1, -1, lastActiveNanos, stack.copy())
        val component = component(data, expanded = true)
        panelScale = expandedScale(component.layout)
        val position = positionFor(mouseX, mouseY + 44, component.layout, panelScale)
        panelX = position.first
        panelY = position.second
        renderComponent(graphics, component, panelX, panelY, panelScale)
        return true
    }

    /** Renders the compact cursor-following card for ordinary container slots. */
    @JvmStatic
    fun render(
        graphics: GuiGraphics,
        stack: ItemStack,
        mouseX: Int,
        mouseY: Int,
        containerId: Int,
        menuSlot: Int
    ): Boolean {
        if (pinned) return false
        setHoverTarget(containerId, menuSlot)
        lastMouseX = mouseX
        lastMouseY = mouseY
        val component = componentFor(stack) ?: return false
        panelScale = 1f
        val position = positionFor(mouseX, mouseY, component.layout)
        panelX = position.first
        panelY = position.second
        renderComponent(graphics, component, panelX, panelY)
        return true
    }

    /** Renders creative-tab catalogue stacks that do not belong to a server-backed menu slot. */
    @JvmStatic
    fun renderTransient(graphics: GuiGraphics, stack: ItemStack, mouseX: Int, mouseY: Int): Boolean {
        if (pinned) return false
        setTransientHoverTarget()
        lastMouseX = mouseX
        lastMouseY = mouseY
        val component = componentFor(stack) ?: return false
        panelScale = 1f
        val position = positionFor(mouseX, mouseY, component.layout)
        panelX = position.first
        panelY = position.second
        renderComponent(graphics, component, panelX, panelY)
        return true
    }

    /** Called from the Fabric after-render hook so the pinned surface survives leaving the item slot. */
    fun renderPinned(graphics: GuiGraphics, mouseX: Int, mouseY: Int): Boolean {
        lastMouseX = mouseX
        lastMouseY = mouseY
        if (!pinned) return false
        refreshPinnedData()
        val data = pinnedData ?: return false
        val component = component(data, expanded = true)
        panelScale = expandedScale(component.layout)
        val position = positionFor(pinMouseX, pinMouseY, component.layout, panelScale)
        panelX = position.first
        panelY = position.second
        renderComponent(graphics, component, panelX, panelY, panelScale)
        return true
    }

    private fun activateHover(data: PerkTooltipData) {
        if (cachedData != data) {
            selectedPerkIndex = 0
            catalystSelected = false
            catalystPickerOpen = false
            selectedCatalystIndex = 0
            selectedArmorSocket = 0
            armorModPage = 0
            cachedSelectedIndex = -1
            cachedComponent = null
        }
        cachedData = data
        selectedPerkIndex = selectedPerkIndex.coerceIn(0, (data.perks.size - 1).coerceAtLeast(0))
        lastActiveNanos = System.nanoTime()
    }

    private fun component(data: PerkTooltipData, expanded: Boolean): DestinyPerkTooltipComponent {
        val width = Minecraft.getInstance().window.guiScaledWidth
        if (cachedComponent == null || cachedData != data || cachedExpanded != expanded ||
            cachedWidth != width || cachedSelectedIndex != selectedPerkIndex ||
            cachedCatalystSelected != catalystSelected || cachedCatalystIndex != selectedCatalystIndex ||
            cachedCatalystPickerOpen != catalystPickerOpen
            || cachedArmorSocket != selectedArmorSocket || cachedArmorPage != armorModPage
        ) {
            cachedData = data
            cachedExpanded = expanded
            cachedWidth = width
            cachedSelectedIndex = selectedPerkIndex
            cachedCatalystSelected = catalystSelected
            cachedCatalystIndex = selectedCatalystIndex
            cachedCatalystPickerOpen = catalystPickerOpen
            cachedArmorSocket = selectedArmorSocket
            cachedArmorPage = armorModPage
            cachedComponent = DestinyPerkTooltipView.create(
                data, expanded, width, selectedPerkIndex, catalystSelected, selectedCatalystIndex, catalystPickerOpen,
                selectedArmorSocket, armorModPage
            )
        }
        return cachedComponent!!
    }

    private fun interactiveData(): PerkTooltipData? = pinnedData?.takeIf { pinned }

    private fun refreshPinnedData() {
        val stack = pinnedTransientStack?.copy() ?: resolveStack(pinnedContainerId, pinnedMenuSlot)
        if (stack == null) {
            closePinned()
            return
        }
        val refreshed = PerkTooltipDataAdapter.from(stack)
        if (refreshed == null) {
            closePinned()
            return
        }
        if (refreshed.definitionId != pinnedData?.definitionId) {
            closePinned()
            return
        }
        if (refreshed != pinnedData) {
            pinnedData = refreshed
            cachedData = refreshed
            selectedPerkIndex = selectedPerkIndex.coerceIn(0, (refreshed.perks.size - 1).coerceAtLeast(0))
            selectedCatalystIndex = selectedCatalystIndex.coerceIn(0, (refreshed.catalysts.size - 1).coerceAtLeast(0))
            catalystPickerOpen = false
            cachedComponent = null
        }
    }

    private fun resolveStack(containerId: Int, menuSlot: Int): ItemStack? {
        val player = Minecraft.getInstance().player ?: return null
        val menu = player.containerMenu
        return if (menuSlot < 0) {
            val inventorySlot = -menuSlot - 1
            if (inventorySlot !in 0 until player.inventory.containerSize) null
            else player.inventory.getItem(inventorySlot)
        } else if (menu.containerId == containerId && menuSlot in menu.slots.indices) {
            menu.getSlot(menuSlot).item
        } else {
            null
        }
    }

    private fun positionFor(
        mouseX: Int,
        mouseY: Int,
        layout: DestinyPerkTooltipLayout,
        scale: Float = 1f
    ): Pair<Int, Int> {
        val minecraft = Minecraft.getInstance()
        val margin = 4
        val cursorGap = 12
        val width = (layout.width * scale).toInt()
        val height = (layout.height * scale).toInt()
        var x = mouseX + cursorGap
        if (x + width + margin > minecraft.window.guiScaledWidth) x = mouseX - width - cursorGap
        x = x.coerceIn(margin, (minecraft.window.guiScaledWidth - width - margin).coerceAtLeast(margin))
        var y = mouseY - 12
        if (y + height + margin > minecraft.window.guiScaledHeight) {
            y = minecraft.window.guiScaledHeight - height - margin
        }
        return x to y.coerceAtLeast(margin)
    }

    private fun renderComponent(
        graphics: GuiGraphics,
        component: DestinyPerkTooltipComponent,
        x: Int,
        y: Int,
        scale: Float = 1f
    ) {
        graphics.pose().pushPose()
        try {
            graphics.pose().translate(x.toFloat(), y.toFloat(), 400f)
            graphics.pose().scale(scale, scale, 1f)
            DestinyPerkClientTooltipComponent(component.layout).renderImage(DestinyPerkFont.get(), 0, 0, graphics)
        } finally {
            graphics.pose().popPose()
        }
    }

    private fun expandedScale(layout: DestinyPerkTooltipLayout): Float {
        val window = Minecraft.getInstance().window
        return minOf(
            1f,
            window.guiScaledWidth * 0.62f / layout.width,
            window.guiScaledHeight * 0.55f / layout.height
        )
    }

    private fun closePinned() {
        pinned = false
        pinnedData = null
        catalystSelected = false
        catalystPickerOpen = false
        pinnedContainerId = -1
        pinnedMenuSlot = -1
        pinnedTransientStack = null
        cachedComponent = null
    }

    private const val HOVER_GRACE_NANOS = 750_000_000L
}
