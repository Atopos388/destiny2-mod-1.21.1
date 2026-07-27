package atopos.destiny2.client.gear

import atopos.destiny2.common.gear.GearRarity
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.inventory.tooltip.TooltipComponent
import java.util.Locale

data class DestinyPerkTooltipLayout(
    val data: PerkTooltipData,
    val expanded: Boolean,
    val theme: DestinyPerkTooltipTheme,
    val width: Int,
    val height: Int,
    val leftWidth: Int,
    val selectedPerkIndex: Int,
    val catalystSelected: Boolean,
    val selectedCatalystIndex: Int,
    val catalystPickerOpen: Boolean,
    val selectedArmorSocket: Int,
    val armorModPage: Int,
    val descriptionLines: List<FormattedCharSequence>
)

data class DestinyPerkTooltipComponent(val layout: DestinyPerkTooltipLayout) : TooltipComponent

object DestinyPerkTooltipView {
    private const val COMPACT_HEIGHT = 92
    const val ARMOR_MODS_PER_PAGE = 12
    const val ARMOR_SOCKET_TOP = 39
    const val ARMOR_SOCKET_MAX_SIZE = 36
    const val ARMOR_SOCKET_GAP = 6
    const val ARMOR_SELECTOR_TOP = 103
    const val ARMOR_MOD_GRID_COLUMNS = 4
    const val ARMOR_MOD_MAX_SIZE = 28
    const val ARMOR_MOD_COLUMN_GAP = 6
    const val ARMOR_MOD_ROW_GAP = 6

    data class SquareGridGeometry(val startX: Int, val size: Int, val gap: Int)

    fun squareGridGeometry(
        leftEdge: Int,
        rightEdge: Int,
        columns: Int,
        maxSize: Int,
        preferredGap: Int
    ): SquareGridGeometry {
        val safeColumns = columns.coerceAtLeast(1)
        val available = (rightEdge - leftEdge).coerceAtLeast(safeColumns)
        val gap = if (safeColumns == 1) 0 else preferredGap.coerceAtMost(
            ((available - safeColumns) / (safeColumns - 1)).coerceAtLeast(0)
        )
        val size = minOf(maxSize, (available - gap * (safeColumns - 1)) / safeColumns).coerceAtLeast(1)
        val occupied = size * safeColumns + gap * (safeColumns - 1)
        return SquareGridGeometry(leftEdge + (available - occupied) / 2, size, gap)
    }

    fun create(
        data: PerkTooltipData,
        expanded: Boolean,
        availableWidth: Int,
        selectedPerkIndex: Int,
        catalystSelected: Boolean = false,
        selectedCatalystIndex: Int = 0,
        catalystPickerOpen: Boolean = false,
        selectedArmorSocket: Int = 0,
        armorModPage: Int = 0
    ): DestinyPerkTooltipComponent {
        val theme = DestinyPerkTooltipTemplate.resolveTheme(availableWidth)
        val effectiveExpanded = expanded
        val width = if (effectiveExpanded) {
            (theme.width + if (data.armorStats != null) 220 else 154)
                .coerceAtMost((availableWidth - 16).coerceAtLeast(theme.width))
        } else {
            theme.width
        }
        val leftWidth = when {
            !effectiveExpanded -> width
            data.armorStats != null -> (width - 206).coerceAtLeast(160)
            else -> (width * 0.52f).toInt().coerceIn(146, 194)
        }
        val selected = if (catalystSelected) {
            data.catalysts.firstOrNull { it.id == data.equippedCatalystId }
        } else {
            data.perks.getOrNull(selectedPerkIndex)
        }
        val detailContentWidth = (width - leftWidth - 25).coerceAtLeast(76)
        val lines = if (effectiveExpanded && selected != null) {
            DestinyPerkFont.get().split(Component.literal(selected.description), detailContentWidth)
        } else {
            emptyList()
        }
        val height = if (effectiveExpanded && data.armorStats != null) {
            220
        } else if (effectiveExpanded) {
            (94 + lines.size.coerceAtLeast(1) * 9).coerceAtLeast(142)
        } else if (data.armorStats != null) {
            136
        } else {
            COMPACT_HEIGHT
        }
        return DestinyPerkTooltipComponent(
            DestinyPerkTooltipLayout(
                data = data,
                expanded = effectiveExpanded,
                theme = theme,
                width = width,
                height = height,
                leftWidth = leftWidth,
                selectedPerkIndex = selectedPerkIndex,
                catalystSelected = catalystSelected,
                selectedCatalystIndex = selectedCatalystIndex,
                catalystPickerOpen = catalystPickerOpen,
                selectedArmorSocket = selectedArmorSocket.coerceIn(0, 3),
                armorModPage = armorModPage.coerceAtLeast(0),
                descriptionLines = lines
            )
        )
    }
}

class DestinyPerkClientTooltipComponent(
    private val layout: DestinyPerkTooltipLayout
) : ClientTooltipComponent {
    override fun getHeight(): Int = layout.height
    override fun getWidth(font: Font): Int = layout.width

    override fun renderImage(font: Font, x: Int, y: Int, graphics: GuiGraphics) {
        val accent = rarityColor(layout.data.rarity)
        drawChassis(graphics, x, y, layout.width, layout.height, layout.theme, accent)
        if (layout.expanded) {
            drawExpanded(graphics, font, x, y, accent)
        } else {
            drawCompact(graphics, font, x, y, accent)
        }
    }

    private fun drawCompact(graphics: GuiGraphics, font: Font, x: Int, y: Int, accent: Int) {
        drawHeader(graphics, font, x, y, layout.width, accent)
        layout.data.armorStats?.let {
            drawArmorStats(graphics, font, x, y, accent)
            drawArmorSockets(graphics, font, x + 12, x + layout.width - 12, y + 94, accent, selected = -1)
            return
        }
        drawPerkStrip(graphics, font, x, y, selected = -1)
        graphics.fill(x + 8, y + 82, x + 26, y + 83, withAlpha(accent, 0xB0))
        graphics.drawString(font, "按 [SHIFT] 锁定特性界面", x + 30, y + 78, layout.theme.mutedText, false)
    }

    private fun drawArmorStats(graphics: GuiGraphics, font: Font, x: Int, y: Int, accent: Int) {
        val stats = layout.data.armorStats ?: return
        val values = listOf(
            "武器" to stats.weapons,
            "生命" to stats.health,
            "职业" to stats.classAbility,
            "手雷" to stats.grenade,
            "超能" to stats.superStat,
            "近战" to stats.melee
        )
        val panelWidth = layout.leftWidth
        val columnWidth = (panelWidth - 24) / 3
        val statTop = y + if (layout.expanded) 58 else 48
        values.forEachIndexed { index, (label, value) ->
            val column = index % 3
            val row = index / 3
            val left = x + 12 + column * columnWidth
            val top = statTop + row * 15
            graphics.drawString(font, label, left, top, layout.theme.mutedText, false)
            val valueText = "+$value"
            graphics.drawString(font, valueText, left + columnWidth - 5 - font.width(valueText), top, accent, false)
        }
        val total = values.sumOf { it.second }
        val footer = "T${layout.data.armorTier}  ${layout.data.armorArchetypeName ?: "随机护甲"} · 总计 $total · ${layout.data.armorSlotName ?: "护甲"}"
        val dividerY = statTop + 31
        graphics.fill(x + 11, dividerY, x + panelWidth - 8, dividerY + 1, withAlpha(layout.theme.border, 0x72))
        graphics.drawString(font, font.plainSubstrByWidth(footer, panelWidth - 24), x + 12, dividerY + 5, 0xFFF4F7F9.toInt(), false)
    }

    private fun drawExpanded(graphics: GuiGraphics, font: Font, x: Int, y: Int, accent: Int) {
        if (layout.data.armorStats != null) {
            drawArmorExpanded(graphics, font, x, y, accent)
            return
        }
        val dividerX = x + layout.leftWidth
        drawHeader(graphics, font, x, y, layout.leftWidth, accent)

        // One continuous chassis, split by a thin technical divider rather than
        // two unrelated background cards.
        graphics.fill(dividerX, y + 4, dividerX + 1, y + layout.height - 4, withAlpha(layout.theme.border, 0xB8))
        graphics.fill(dividerX + 1, y + 4, dividerX + 4, y + 25, withAlpha(accent, 0xD8))
        graphics.drawString(font, "特性详情", dividerX + 11, y + 10, 0xFFF8FAFB.toInt(), false)

        drawPerkStrip(graphics, font, x, y, layout.selectedPerkIndex)
        graphics.drawString(font, "ACTIVE ROLL // ${layout.data.perks.size.toString().padStart(2, '0')}", x + 12, y + 91, layout.theme.mutedText, false)
        drawWeaponStats(graphics, font, x, y, accent)

        if (layout.catalystSelected) {
            drawCatalystDetails(graphics, font, x, y, dividerX, accent)
            return
        }

        val perk = layout.data.perks.getOrNull(layout.selectedPerkIndex)
        if (perk == null) {
            graphics.drawString(font, "NO ACTIVE PERK DATA", dividerX + 11, y + 44, layout.theme.mutedText, false)
            return
        }

        val rightX = dividerX + 11
        val rightEdge = x + layout.width - 10
        val page = "${layout.selectedPerkIndex + 1} / ${layout.data.perks.size}"
        graphics.drawString(font, perk.columnName ?: "PERK ${layout.selectedPerkIndex + 1}", rightX, y + 31, accent, false)
        graphics.drawString(font, page, rightEdge - font.width(page), y + 31, layout.theme.mutedText, false)
        graphics.fill(rightX, y + 42, rightEdge, y + 43, withAlpha(layout.theme.border, 0x72))

        graphics.drawString(font, perk.name, rightX, y + 50, 0xFFF4F7F9.toInt(), false)
        var lineY = y + 65
        layout.descriptionLines.forEach { line ->
            graphics.drawString(font, line, rightX, lineY, layout.theme.descriptionText, false)
            lineY += 9
        }

        val hint = "点击图标切换 · SHIFT 关闭"
        graphics.fill(rightX, y + layout.height - 18, rightX + 14, y + layout.height - 17, withAlpha(accent, 0xB0))
        graphics.drawString(font, hint, rightX + 18, y + layout.height - 21, layout.theme.mutedText, false)
    }

    private fun drawArmorExpanded(graphics: GuiGraphics, font: Font, x: Int, y: Int, accent: Int) {
        val data = layout.data
        val dividerX = x + layout.leftWidth
        drawHeader(graphics, font, x, y, layout.leftWidth, accent)
        drawArmorStats(graphics, font, x, y, accent)

        graphics.drawString(font, "护甲配置", x + 12, y + 111, layout.theme.mutedText, false)
        val installed = data.equippedArmorMods.getOrNull(layout.selectedArmorSocket)?.let { "已安装：${it.name}" } ?: "当前插槽为空"
        graphics.drawString(font, font.plainSubstrByWidth(installed, layout.leftWidth - 22), x + 12, y + 125, 0xFFF4F7F9.toInt(), false)
        graphics.drawString(font, if (layout.selectedArmorSocket == 0) "属性模组" else "${data.armorSlotName ?: "护甲"}模组", x + 12, y + 139, accent, false)
        data.equippedArmorMods.getOrNull(layout.selectedArmorSocket)?.let { mod ->
            font.split(Component.literal(mod.description), layout.leftWidth - 24).take(6).forEachIndexed { index, line ->
                graphics.drawString(font, line, x + 12, y + 153 + index * 9, layout.theme.descriptionText, false)
            }
        }

        graphics.fill(dividerX, y + 4, dividerX + 1, y + layout.height - 4, withAlpha(layout.theme.border, 0xB8))
        graphics.fill(dividerX + 1, y + 4, dividerX + 4, y + 25, withAlpha(accent, 0xD8))
        graphics.drawString(font, "护甲模组", dividerX + 11, y + 10, 0xFFF8FAFB.toInt(), false)

        val rightX = dividerX + 11
        val rightEdge = x + layout.width - 10
        val energyValue = "${data.armorEnergyUsed}/${data.armorEnergyCapacity}"
        graphics.drawString(font, "能量", rightX, y + 27, layout.theme.mutedText, false)
        graphics.drawString(font, energyValue, rightEdge - font.width(energyValue), y + 27, 0xFFF4F7F9.toInt(), false)
        val pipCount = data.armorEnergyCapacity.coerceIn(1, 10)
        val pipRight = rightEdge - font.width(energyValue) - 6
        val pipStart = pipRight - pipCount * 4 + 1
        repeat(pipCount) { index ->
            graphics.fill(
                pipStart + index * 4,
                y + 30,
                pipStart + index * 4 + 3,
                y + 32,
                if (index < data.armorEnergyUsed) accent else withAlpha(layout.theme.border, 0x68)
            )
        }
        drawArmorSockets(
            graphics,
            font,
            rightX,
            rightEdge,
            y + DestinyPerkTooltipView.ARMOR_SOCKET_TOP,
            accent,
            layout.selectedArmorSocket
        )

        graphics.fill(rightX, y + 82, rightEdge, y + 83, withAlpha(layout.theme.border, 0x72))
        val selectorTitle = if (layout.selectedArmorSocket == 0) "属性模组" else "${data.armorSlotName ?: "护甲"}模组 ${layout.selectedArmorSocket}"
        graphics.drawString(font, selectorTitle, rightX, y + 89, accent, false)

        val modGrid = DestinyPerkTooltipView.squareGridGeometry(
            rightX,
            rightEdge,
            DestinyPerkTooltipView.ARMOR_MOD_GRID_COLUMNS,
            DestinyPerkTooltipView.ARMOR_MOD_MAX_SIZE,
            DestinyPerkTooltipView.ARMOR_MOD_COLUMN_GAP
        )
        val allMods = data.availableArmorModsBySocket.getOrNull(layout.selectedArmorSocket).orEmpty()
        val pageCount = ((allMods.size + DestinyPerkTooltipView.ARMOR_MODS_PER_PAGE - 1) / DestinyPerkTooltipView.ARMOR_MODS_PER_PAGE).coerceAtLeast(1)
        val page = layout.armorModPage.coerceIn(0, pageCount - 1)
        val pageMods = allMods.drop(page * DestinyPerkTooltipView.ARMOR_MODS_PER_PAGE).take(DestinyPerkTooltipView.ARMOR_MODS_PER_PAGE)
        val pageText = "${page + 1}/$pageCount · 滚轮"
        graphics.drawString(font, pageText, rightEdge - font.width(pageText), y + 89, layout.theme.mutedText, false)
        val gridTop = y + DestinyPerkTooltipView.ARMOR_SELECTOR_TOP
        pageMods.forEachIndexed { index, mod ->
            val column = index % DestinyPerkTooltipView.ARMOR_MOD_GRID_COLUMNS
            val row = index / DestinyPerkTooltipView.ARMOR_MOD_GRID_COLUMNS
            val left = modGrid.startX + column * (modGrid.size + modGrid.gap)
            val top = gridTop + row * (modGrid.size + DestinyPerkTooltipView.ARMOR_MOD_ROW_GAP)
            val selected = data.equippedArmorMods.getOrNull(layout.selectedArmorSocket)?.id == mod.id
            val currentCost = data.equippedArmorMods.getOrNull(layout.selectedArmorSocket)?.energyCost ?: 0
            val affordable = data.armorEnergyUsed - currentCost + mod.energyCost <= data.armorEnergyCapacity
            val border = if (selected) accent else withAlpha(layout.theme.border, if (affordable) 0xE0 else 0x78)
            graphics.fill(left, top, left + modGrid.size, top + modGrid.size, border)
            graphics.fill(
                left + 2,
                top + 2,
                left + modGrid.size - 2,
                top + modGrid.size - 2,
                withAlpha(if (selected) accent else layout.theme.surface, if (selected) 0x62 else if (affordable) 0xD8 else 0x78)
            )
            val textColor = if (selected) 0xFFFFFFFF.toInt() else if (affordable) layout.theme.descriptionText else 0xFF8B7070.toInt()
            val icon = mod.icon
            if (icon != null) {
                val iconSize = (modGrid.size - 8).coerceAtLeast(1)
                graphics.blit(icon, left + 4, top + 4, iconSize, iconSize, 0f, 0f, 96, 96, 96, 96)
            } else {
                val glyph = mod.name.take(1)
                graphics.drawString(font, glyph, left + (modGrid.size - font.width(glyph)) / 2, top + (modGrid.size - 8) / 2, textColor, false)
            }
            graphics.drawString(font, mod.energyCost.toString(), left + 3, top + 3, textColor, false)
            if (selected) {
                graphics.fill(left + 2, top + modGrid.size - 3, left + modGrid.size - 2, top + modGrid.size - 1, accent)
            }
        }

        val actionY = y + layout.height - 16
        val action = if (data.equippedArmorMods.getOrNull(layout.selectedArmorSocket) == null) "选择模组安装到当前框" else "卸载当前框的模组"
        graphics.fill(rightX, actionY, rightEdge, actionY + 14, withAlpha(accent, if (data.equippedArmorMods.getOrNull(layout.selectedArmorSocket) == null) 0x38 else 0x78))
        graphics.drawString(font, action, rightX + (rightEdge - rightX - font.width(action)) / 2, actionY + 3, 0xFFFFFFFF.toInt(), false)
    }

    private fun drawArmorSockets(
        graphics: GuiGraphics,
        font: Font,
        leftEdge: Int,
        rightEdge: Int,
        y: Int,
        accent: Int,
        selected: Int
    ) {
        val labels = listOf("属性", "模组", "模组", "模组")
        val socketGrid = DestinyPerkTooltipView.squareGridGeometry(
            leftEdge,
            rightEdge,
            4,
            DestinyPerkTooltipView.ARMOR_SOCKET_MAX_SIZE,
            DestinyPerkTooltipView.ARMOR_SOCKET_GAP
        )
        repeat(4) { index ->
            val left = socketGrid.startX + index * (socketGrid.size + socketGrid.gap)
            val mod = layout.data.equippedArmorMods.getOrNull(index)
            val border = if (index == selected) accent else withAlpha(layout.theme.border, 0xE0)
            graphics.fill(left, y, left + socketGrid.size, y + socketGrid.size, border)
            graphics.fill(left + 2, y + 2, left + socketGrid.size - 2, y + socketGrid.size - 2, withAlpha(if (mod == null) layout.theme.surface else accent, if (mod == null) 0xD0 else 0x52))
            val icon = mod?.icon
            if (icon != null) {
                val iconSize = (socketGrid.size - 8).coerceAtLeast(1)
                graphics.blit(icon, left + 4, y + 4, iconSize, iconSize, 0f, 0f, 96, 96, 96, 96)
            } else {
                val glyph = mod?.name?.take(1) ?: "+"
                graphics.drawString(font, glyph, left + (socketGrid.size - font.width(glyph)) / 2, y + 6, if (mod == null) layout.theme.mutedText else 0xFFFFFFFF.toInt(), false)
            }
            val text = labels[index]
            graphics.drawString(font, text, left + (socketGrid.size - font.width(text)) / 2, y + socketGrid.size - 11, layout.theme.mutedText, false)
            if (index == selected) {
                graphics.fill(left + 2, y + socketGrid.size - 3, left + socketGrid.size - 2, y + socketGrid.size - 1, accent)
            }
        }
    }

    private fun drawCatalystDetails(
        graphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        dividerX: Int,
        accent: Int
    ) {
        val rightX = dividerX + 11
        val rightEdge = x + layout.width - 10
        val equipped = layout.data.catalysts.firstOrNull { it.id == layout.data.equippedCatalystId }

        // The selector is a separate control. Merely opening the catalyst
        // socket keeps the current catalyst's information visible.
        val selectorRight = (rightX + 72).coerceAtMost(rightEdge)
        graphics.fill(rightX, y + 29, selectorRight, y + 30, withAlpha(accent, 0xC0))
        graphics.fill(rightX, y + 41, selectorRight, y + 42, withAlpha(accent, 0xC0))
        graphics.fill(rightX, y + 29, rightX + 1, y + 42, withAlpha(accent, 0xC0))
        graphics.fill(selectorRight - 1, y + 29, selectorRight, y + 42, withAlpha(accent, 0xC0))
        graphics.drawString(font, if (layout.catalystPickerOpen) "收起选择" else "选择催化", rightX + 6, y + 32, accent, false)
        val status = if (equipped == null) "未安装" else "已安装"
        graphics.drawString(font, status, rightEdge - font.width(status), y + 32, layout.theme.mutedText, false)
        graphics.fill(rightX, y + 42, rightEdge, y + 43, withAlpha(layout.theme.border, 0x72))

        if (equipped == null) {
            graphics.drawString(font, "未安装催化剂", rightX, y + 51, 0xFFF4F7F9.toInt(), false)
            graphics.drawString(font, "点击上方选择框查看可用催化剂。", rightX, y + 64, layout.theme.descriptionText, false)
        } else {
            graphics.drawString(font, equipped.name, rightX, y + 51, 0xFFF4F7F9.toInt(), false)
            graphics.drawString(font, "催化信息", rightX, y + 62, accent, false)
            var lineY = y + 76
            layout.descriptionLines.forEach { line ->
                if (lineY < y + layout.height - 43) {
                    graphics.drawString(font, line, rightX, lineY, layout.theme.descriptionText, false)
                    lineY += 9
                }
            }
        }

        if (equipped != null) {
            val buttonTop = y + layout.height - 38
            graphics.fill(rightX, buttonTop, rightEdge, buttonTop + 14, withAlpha(accent, 0x88))
            val action = "卸载催化"
            graphics.drawString(font, action, rightX + (rightEdge - rightX - font.width(action)) / 2, buttonTop + 3, 0xFFFFFFFF.toInt(), false)
        }
        graphics.drawString(font, "点击选择框 · SHIFT 关闭", rightX, y + layout.height - 18, layout.theme.mutedText, false)

        if (layout.catalystPickerOpen) {
            drawCatalystPicker(graphics, font, rightX, y + 46, rightEdge, accent)
        }
    }

    private fun drawCatalystPicker(
        graphics: GuiGraphics,
        font: Font,
        left: Int,
        top: Int,
        right: Int,
        accent: Int
    ) {
        val catalysts = layout.data.catalysts
        val pickerHeight = (8 + catalysts.size.coerceAtLeast(1) * 23).coerceAtMost(layout.height - 57)
        graphics.fill(left, top, right, top + pickerHeight, 0xFA090F16.toInt())
        graphics.fill(left, top, right, top + 1, withAlpha(accent, 0xD8))
        graphics.fill(left, top + pickerHeight - 1, right, top + pickerHeight, withAlpha(accent, 0xD8))
        graphics.fill(left, top, left + 1, top + pickerHeight, withAlpha(accent, 0xD8))
        graphics.fill(right - 1, top, right, top + pickerHeight, withAlpha(accent, 0xD8))

        if (catalysts.isEmpty()) {
            graphics.drawString(font, "暂无可用催化剂", left + 6, top + 9, layout.theme.mutedText, false)
            return
        }
        catalysts.forEachIndexed { index, catalyst ->
            val rowTop = top + 4 + index * 23
            if (rowTop + 20 >= top + pickerHeight) return@forEachIndexed
            val selected = index == layout.selectedCatalystIndex
            if (selected) graphics.fill(left + 3, rowTop, right - 3, rowTop + 20, withAlpha(accent, 0x35))
            graphics.fill(left + 5, rowTop + 4, left + 7, rowTop + 16, if (selected) accent else withAlpha(accent, 0x90))
            graphics.drawString(font, catalyst.name, left + 11, rowTop + 3, 0xFFF4F7F9.toInt(), false)
            val state = if (layout.data.equippedCatalystId == catalyst.id) "已装备" else "点击安装"
            graphics.drawString(font, state, left + 11, rowTop + 12, if (selected) accent else layout.theme.mutedText, false)
        }
    }

    private fun drawWeaponStats(graphics: GuiGraphics, font: Font, x: Int, y: Int, accent: Int) {
        val damage = layout.data.damage
        val fireRate = layout.data.fireRate
        if (damage == null && fireRate == null) return

        graphics.fill(x + 11, y + 101, x + layout.leftWidth - 8, y + 102, withAlpha(layout.theme.border, 0x55))
        damage?.let {
            drawStatValue(graphics, font, x + 12, y + 106, "伤害", formatStat(it), accent)
        }
        fireRate?.let {
            drawStatValue(graphics, font, x + 12, y + 118, "射速", "${formatStat(it)}/秒", accent)
        }
    }

    private fun drawStatValue(
        graphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        label: String,
        value: String,
        accent: Int
    ) {
        graphics.drawString(font, label, x, y, layout.theme.mutedText, false)
        graphics.fill(x + 29, y + 3, x + 39, y + 4, withAlpha(accent, 0xB0))
        graphics.drawString(font, value, x + 44, y, 0xFFF4F7F9.toInt(), false)
    }

    private fun drawHeader(
        graphics: GuiGraphics,
        font: Font,
        x: Int,
        y: Int,
        panelWidth: Int,
        accent: Int
    ) {
        val data = layout.data
        graphics.fill(x + 5, y + 4, x + panelWidth - 5, y + 25, withAlpha(accent, 0xE8))
        graphics.drawString(font, data.name, x + 11, y + 10, 0xFFF8FAFB.toInt(), false)
        data.power?.let { power ->
            val label = power.toString()
            graphics.drawString(font, label, x + panelWidth - 11 - font.width(label), y + 10, 0xFFFFFFFF.toInt(), false)
        }

        val classification = buildString {
            append(data.rarity.displayName).append(" / ").append(data.category.displayName)
            data.elementName?.let { append(" / ").append(it) }
        }
        val metaWidth = (panelWidth - 22).coerceAtLeast(50)
        if (layout.expanded) {
            graphics.drawString(font, font.plainSubstrByWidth(classification, metaWidth), x + 12, y + 29, layout.theme.mutedText, false)
            graphics.drawString(font, font.plainSubstrByWidth(data.frameName, metaWidth), x + 12, y + 39, layout.theme.mutedText, false)
            graphics.fill(x + 11, y + 50, x + panelWidth - 8, y + 51, withAlpha(layout.theme.border, 0x72))
        } else {
            val classificationText = font.plainSubstrByWidth(classification, (metaWidth * 0.58f).toInt())
            val frameText = font.plainSubstrByWidth(data.frameName, (metaWidth * 0.38f).toInt())
            graphics.drawString(font, classificationText, x + 12, y + 29, layout.theme.mutedText, false)
            graphics.drawString(font, frameText, x + panelWidth - 10 - font.width(frameText), y + 29, layout.theme.mutedText, false)
            graphics.fill(x + 11, y + 41, x + panelWidth - 8, y + 42, withAlpha(layout.theme.border, 0x72))
        }
    }

    private fun drawPerkStrip(graphics: GuiGraphics, font: Font, x: Int, y: Int, selected: Int) {
        val data = layout.data
        if (data.perks.isEmpty()) {
            graphics.drawString(font, if (data.rollMissing) "ROLL DATA // PENDING" else "NO ACTIVE PERKS", x + 12, y + 64, layout.theme.mutedText, false)
            return
        }
        val slotCount = data.perks.size + if (data.hasCatalystSlot) 1 else 0
        val available = layout.leftWidth - 24
        val step = if (slotCount <= 1) 31 else ((available - 24) / (slotCount - 1)).coerceIn(27, 34)
        val labelY = if (layout.expanded) y + 53 else y + 44
        val iconY = if (layout.expanded) y + 62 else y + 53
        data.perks.forEachIndexed { index, perk ->
            val iconX = x + 12 + index * step
            val perkSelected = index == selected && !layout.catalystSelected
            graphics.drawString(font, (index + 1).toString(), iconX + 9, labelY, if (perkSelected) 0xFFFFFFFF.toInt() else withAlpha(layout.theme.mutedText, 0xA0), false)
            PerkIconView.render(graphics, font, perk, iconX, iconY, layout.theme, rarityColor(data.rarity), perkSelected)
            if (index < slotCount - 1) {
                graphics.fill(iconX + 25, iconY + 11, iconX + step, iconY + 12, withAlpha(layout.theme.border, 0x55))
            }
        }
        if (data.hasCatalystSlot) {
            val catalystX = x + 12 + data.perks.size * step
            val catalystSelected = layout.expanded && layout.catalystSelected
            graphics.drawString(font, "C", catalystX + 9, labelY, if (catalystSelected) 0xFFFFFFFF.toInt() else withAlpha(layout.theme.mutedText, 0x88), false)
            val equipped = data.catalysts.firstOrNull { it.id == data.equippedCatalystId }
            if (equipped != null) {
                PerkIconView.render(graphics, font, equipped, catalystX, iconY, layout.theme, rarityColor(data.rarity), catalystSelected)
            } else {
                PerkIconView.renderEmptyCatalyst(graphics, font, catalystX, iconY, layout.theme, rarityColor(data.rarity), catalystSelected)
            }
        }
    }

    private fun drawChassis(
        graphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        theme: DestinyPerkTooltipTheme,
        accent: Int
    ) {
        graphics.fill(x + 4, y, x + width - 9, y + height, theme.background)
        graphics.fill(x, y + 4, x + width, y + height - 4, theme.background)
        graphics.fill(x + 2, y + 2, x + width - 5, y + height - 2, theme.background)
        graphics.fill(x + 4, y, x + width - 9, y + 1, theme.border)
        graphics.fill(x, y + 4, x + 1, y + height - 4, theme.border)
        graphics.fill(x + 4, y + height - 1, x + width - 4, y + height, theme.border)
        graphics.fill(x + width - 1, y + 8, x + width, y + height - 4, theme.border)
        repeat(4) { step ->
            graphics.fill(x + step, y + 4 - step, x + step + 1, y + 5 - step, theme.border)
            graphics.fill(x + width - 9 + step, y + step, x + width - 8 + step, y + step + 1, theme.border)
        }
        graphics.fill(x + 5, y + 28, x + 7, y + height - 10, withAlpha(accent, 0xC0))
    }

    private fun rarityColor(rarity: GearRarity): Int = when (rarity) {
        GearRarity.EXOTIC -> 0xFFCEAE59.toInt()
        GearRarity.LEGENDARY -> 0xFF73528F.toInt()
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)

    private fun formatStat(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}
