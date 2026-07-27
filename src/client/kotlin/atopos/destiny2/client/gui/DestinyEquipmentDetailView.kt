package atopos.destiny2.client.gui

import atopos.destiny2.client.gear.PerkTooltipDataAdapter
import atopos.destiny2.client.gear.PerkTooltipEntry
import atopos.destiny2.client.gear.ArmorModTooltipEntry
import atopos.destiny2.common.gear.GearCategory
import atopos.destiny2.common.gear.GearRarity
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.gear.GearRolls
import atopos.destiny2.common.network.DestinyNetworking
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.appliedenergistics.yoga.YogaPositionType
import kotlin.math.roundToInt

/** Runtime data binder for the online-editable Destiny equipment detail shell. */
object DestinyEquipmentDetailView {
    private const val NO_MENU_SLOT = Int.MIN_VALUE
    private data class DetailSession(val containerId: Int, val menuSlot: Int)
    private data class DetailIdentity(
        val definitionId: ResourceLocation,
        val containerId: Int,
        val menuSlot: Int
    )
    private data class DetailChoice(
        val id: ResourceLocation?,
        val name: String,
        val description: String,
        val icon: ResourceLocation? = null,
        val energyCost: Int? = null,
        val enabled: Boolean = true,
        val disabledReason: String? = null
    )

    private var session: DetailSession? = null
    private var identity: DetailIdentity? = null
    private var boundData: atopos.destiny2.client.gear.PerkTooltipData? = null
    private var selectedPerkId: ResourceLocation? = null

    fun open(ui: UI, stack: ItemStack, containerId: Int = -1, menuSlot: Int = NO_MENU_SLOT): Boolean {
        if (stack.isEmpty) return false
        val data = PerkTooltipDataAdapter.from(stack) ?: return false
        val definition = GearRegistry.definitionFor(stack) ?: return false
        val overlay = element(ui, "equipment_detail_overlay") ?: return false
        val nextIdentity = DetailIdentity(data.definitionId, containerId, menuSlot)
        if (identity != nextIdentity) {
            selectedPerkId = null
            closeChoiceTray(ui)
        }
        identity = nextIdentity
        boundData = data

        val rarityColor = when (data.rarity) {
            GearRarity.EXOTIC -> DestinyNavigationTemplate.Color.EXOTIC
            GearRarity.LEGENDARY -> DestinyNavigationTemplate.Color.LEGENDARY
            else -> DestinyNavigationTemplate.Color.LINE
        }
        element(ui, "equipment_detail_accent")?.style { it.background(ColorRectTexture(rarityColor)) }
        text(ui, "equipment_detail_name")?.apply {
            setText(data.name.string)
            textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(24f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
        }
        text(ui, "equipment_detail_frame")?.setText(
            listOfNotNull(data.frameName, data.elementName).joinToString("  //  ")
        )
        text(ui, "equipment_detail_flavor")?.setText(definition.frame.description)
        text(ui, "equipment_detail_power")?.setText("◆ ${data.power ?: 0}")

        bindItemPreview(element(ui, "equipment_detail_icon"), stack, 58, rarityColor)
        bindItemPreview(element(ui, "equipment_detail_preview"), stack, 184, 0x00FFFFFF)
        session = if (menuSlot != NO_MENU_SLOT) DetailSession(containerId, menuSlot) else null
        bindPerks(ui, stack, data.perks, definition.fixedPerks.map { perk ->
            PerkTooltipEntry(
                perk.id,
                "固有特性",
                perk.displayName,
                perk.description,
                ResourceLocation.fromNamespaceAndPath(perk.id.namespace, "textures/gui/perks/${perk.id.path}.png")
            )
        })
        bindMods(ui, stack, data)
        bindStats(ui, stack, data.category)

        overlay.setDisplay(true)
        overlay.setVisible(true)
        return true
    }

    fun refreshIfOpen(ui: UI) {
        val overlay = element(ui, "equipment_detail_overlay") ?: return
        if (!overlay.isDisplayed) return
        val active = session ?: return
        val player = Minecraft.getInstance().player ?: return
        val liveStack = if (active.menuSlot < 0) {
            val inventorySlot = -active.menuSlot - 1
            if (inventorySlot !in 0 until player.inventory.containerSize) return
            player.inventory.getItem(inventorySlot)
        } else {
            val menu = player.containerMenu
            if (menu.containerId != active.containerId || active.menuSlot !in menu.slots.indices) return
            menu.getSlot(active.menuSlot).item
        }
        if (liveStack.isEmpty) return
        val refreshed = PerkTooltipDataAdapter.from(liveStack) ?: return
        if (refreshed != boundData) {
            open(ui, liveStack.copy(), active.containerId, active.menuSlot)
        }
    }

    fun close(ui: UI): Boolean {
        val overlay = element(ui, "equipment_detail_overlay") ?: return false
        if (!overlay.isDisplayed) return false
        overlay.setVisible(false)
        overlay.setDisplay(false)
        closeChoiceTray(ui)
        session = null
        identity = null
        boundData = null
        selectedPerkId = null
        return true
    }

    private fun bindItemPreview(host: UIElement?, stack: ItemStack, size: Int, border: Int) {
        host ?: return
        host.clearAllChildren()
        val x = ((host.sizeWidth - size) / 2f).coerceAtLeast(0f)
        val y = ((host.sizeHeight - size) / 2f).coerceAtLeast(0f)
        host.addChild(ItemSlot().apply {
            setItem(stack.copy())
            setAllowHitTest(false)
            layout {
                it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y)
                    .width(size.toFloat()).height(size.toFloat()).aspectRatio(1f)
            }
            style { it.background(ColorRectTexture(0x00000000)) }
            slotStyle {
                it.showItemTooltips(false).showSlotOverlayOnlyEmpty(false).slotOverlay(
                    if (border == 0x00FFFFFF) ColorRectTexture(0x00000000)
                    else GuiTextureGroup.of(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT), ColorBorderTexture(1, border))
                )
            }
        })
    }

    private fun bindPerks(
        ui: UI,
        stack: ItemStack,
        selected: List<PerkTooltipEntry>,
        fixed: List<PerkTooltipEntry>
    ) {
        val host = element(ui, "equipment_detail_perks") ?: return
        host.clearAllChildren()
        val definition = GearRegistry.definitionFor(stack)
        val slots = buildList<Pair<PerkTooltipEntry, Int?>> {
            fixed.forEach { add(it to null) }
            selected.forEachIndexed { index, perk ->
                if (none { it.first.id == perk.id }) add(perk to index)
            }
        }.take(6)
        val title = TextElement().apply {
            setId("equipment_detail_selected_perk_title")
            setAllowHitTest(false)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(56f).width(386f).height(16f) }
            textStyle {
                it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(10f)
                    .textColor(0xFF303941.toInt()).textShadow(false).textWrap(TextWrap.WRAP)
            }
        }
        val detail = TextElement().apply {
            setId("equipment_detail_selected_perk")
            setAllowHitTest(false)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(75f).width(386f).height(53f) }
            textStyle {
                it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(8f)
                    .textColor(0xFF4C5862.toInt()).textShadow(false).textWrap(TextWrap.WRAP)
            }
        }
        fun select(perk: PerkTooltipEntry) {
            selectedPerkId = perk.id
            title.setText("${perk.name}  //  ${perk.columnName ?: "特性"}")
            detail.setText(perk.description)
        }
        if (slots.isEmpty()) {
            selectedPerkId = null
            title.setText("没有可显示的特性")
            detail.setText(if (GearRolls.read(stack) == null) "此装备尚未生成 Perk Roll。" else "此装备没有可显示的特性。")
        } else {
            slots.forEachIndexed { index, (perk, columnIndex) ->
                host.addChild(UIElement().apply {
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(index * 52f).top(0f).width(46f).height(46f).aspectRatio(1f) }
                    style {
                        it.background(ColorRectTexture(0xAA748392.toInt()))
                            .overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
                    }
                    if (Minecraft.getInstance().resourceManager.getResource(perk.icon).isPresent) {
                        addChild(UIElement().apply {
                            setAllowHitTest(false)
                            layout { it.positionType(YogaPositionType.ABSOLUTE).left(5f).top(5f).width(36f).height(36f).aspectRatio(1f) }
                            style { it.background(SpriteTexture.of(perk.icon)) }
                        })
                    }
                    addEventListener(UIEvents.CLICK) {
                        select(perk)
                        val active = session
                        val candidates = columnIndex?.let { definition?.perkColumns?.getOrNull(it) }.orEmpty()
                        if (columnIndex != null && candidates.size > 1 && Minecraft.getInstance().player?.isCreative != true) {
                            title.setText("${perk.name}  //  生存模式只读")
                            return@addEventListener
                        }
                        if (active != null && columnIndex != null && candidates.size > 1) {
                            showChoiceTray(
                                ui,
                                "${definition?.rollColumnLabels?.getOrNull(columnIndex) ?: "Perk ${columnIndex + 1}"}  //  选择特性",
                                candidates.map { candidate ->
                                    DetailChoice(
                                        candidate.id,
                                        candidate.displayName,
                                        candidate.description,
                                        ResourceLocation.fromNamespaceAndPath(
                                            candidate.id.namespace,
                                            "textures/gui/perks/${candidate.id.path}.png"
                                        )
                                    )
                                },
                                onPreview = { choice ->
                                    title.setText("${choice.name}  //  候选特性")
                                    detail.setText(choice.description)
                                }
                            ) { choice ->
                                if (Minecraft.getInstance().player?.isCreative != true) return@showChoiceTray
                                val perkId = choice.id ?: return@showChoiceTray
                                ClientPlayNetworking.send(
                                    DestinyNetworking.ConfigureGearPerkPayload(
                                        active.containerId, active.menuSlot, columnIndex, perkId.toString()
                                    )
                                )
                                if (GearRolls.setPerk(stack, columnIndex, perkId)) {
                                    selectedPerkId = perkId
                                    closeChoiceTray(ui)
                                    open(ui, stack, active.containerId, active.menuSlot)
                                }
                            }
                        }
                    }
                })
            }
            select(slots.firstOrNull { it.first.id == selectedPerkId }?.first ?: slots.first().first)
        }
        host.addChild(title)
        host.addChild(detail)
    }

    private fun bindMods(ui: UI, stack: ItemStack, data: atopos.destiny2.client.gear.PerkTooltipData) {
        val host = element(ui, "equipment_detail_mods") ?: return
        host.clearAllChildren()
        if (data.category == GearCategory.ARMOR) {
            text(ui, "equipment_detail_mods_label")?.setText(
                "装备模组  //  能量 ${data.armorEnergyUsed}/${data.armorEnergyCapacity}"
            )
            data.equippedArmorMods.take(4).forEachIndexed { index, mod ->
                val cell = squareModCell(mod?.icon, mod?.energyCost, mod == null)
                mod?.let { installed ->
                    cell.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
                        event.hoverTooltips = armorModHover(installed)
                    }
                }
                cell.addEventListener(UIEvents.CLICK) {
                    val active = session ?: return@addEventListener
                    val current = data.equippedArmorMods.getOrNull(index)
                    val usedWithoutCurrent = data.armorEnergyUsed - (current?.energyCost ?: 0)
                    val options = buildList {
                        add(
                            DetailChoice(
                                null,
                                "卸下模组",
                                "清空这个护甲模组槽位。",
                                enabled = current != null,
                                disabledReason = if (current == null) "当前插槽为空" else null
                            )
                        )
                        data.availableArmorModsBySocket.getOrNull(index).orEmpty().forEach { candidate ->
                            val projectedEnergy = usedWithoutCurrent + candidate.energyCost
                            val alreadyEquipped = current?.id == candidate.id
                            val affordable = projectedEnergy <= data.armorEnergyCapacity
                            add(
                                DetailChoice(
                                    candidate.id,
                                    candidate.name,
                                    candidate.description,
                                    candidate.icon,
                                    candidate.energyCost,
                                    enabled = !alreadyEquipped && affordable,
                                    disabledReason = when {
                                        alreadyEquipped -> "当前已装备"
                                        !affordable -> "能量不足：替换后为 $projectedEnergy/${data.armorEnergyCapacity}"
                                        else -> null
                                    }
                                )
                            )
                        }
                    }
                    showChoiceTray(ui, "模组槽 ${index + 1}  //  选择模组", options) { choice ->
                        ClientPlayNetworking.send(
                            DestinyNetworking.ConfigureArmorModPayload(
                                active.containerId, active.menuSlot, index, choice.id?.toString().orEmpty()
                            )
                        )
                        if (GearRolls.setArmorMod(stack, index, choice.id)) {
                            closeChoiceTray(ui)
                            open(ui, stack, active.containerId, active.menuSlot)
                        }
                    }
                }
                cell.layout {
                    it.positionType(YogaPositionType.ABSOLUTE).left(index * 48f).top(0f)
                        .width(40f).height(40f).aspectRatio(1f)
                }
                host.addChild(cell)
            }
            addArmorEnergyMeter(host, data.armorEnergyUsed, data.armorEnergyCapacity)
        } else if (data.hasCatalystSlot) {
            text(ui, "equipment_detail_mods_label")?.setText("催化剂")
            val catalyst = data.catalysts.firstOrNull { it.id == data.equippedCatalystId }
            val cell = squareModCell(catalyst?.icon, null, catalyst == null)
            catalyst?.let { installed ->
                cell.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
                    event.hoverTooltips = choiceHover(
                        DetailChoice(installed.id, installed.name, installed.description, installed.icon)
                    )
                }
            }
            cell.addEventListener(UIEvents.CLICK) {
                val active = session ?: return@addEventListener
                val options = buildList {
                    add(
                        DetailChoice(
                            null,
                            "卸下催化剂",
                            "清空当前催化剂槽位。",
                            enabled = catalyst != null,
                            disabledReason = if (catalyst == null) "当前插槽为空" else null
                        )
                    )
                    data.catalysts.forEach { candidate ->
                        val selected = candidate.id == data.equippedCatalystId
                        add(
                            DetailChoice(
                                candidate.id,
                                candidate.name,
                                candidate.description,
                                candidate.icon,
                                enabled = !selected,
                                disabledReason = if (selected) "当前已装备" else null
                            )
                        )
                    }
                }
                showChoiceTray(ui, "催化剂  //  选择", options) { choice ->
                    ClientPlayNetworking.send(
                        DestinyNetworking.ConfigureGearCatalystPayload(
                            active.containerId, active.menuSlot, choice.id?.toString().orEmpty()
                        )
                    )
                    if (GearRolls.setCatalyst(stack, choice.id)) {
                        closeChoiceTray(ui)
                        open(ui, stack, active.containerId, active.menuSlot)
                    }
                }
            }
            cell.layout {
                it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(0f)
                    .width(40f).height(40f).aspectRatio(1f)
            }
            host.addChild(cell)
        } else {
            text(ui, "equipment_detail_mods_label")?.setText("装备模组")
            labelsFallback(host)
        }
    }

    private fun squareModCell(icon: ResourceLocation?, energyCost: Int?, empty: Boolean): UIElement = UIElement().apply {
        style {
            it.background(ColorRectTexture(if (empty) 0x385D6771 else 0xAA687582.toInt()))
                .overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
        }
        if (icon != null && Minecraft.getInstance().resourceManager.getResource(icon).isPresent) {
            addChild(UIElement().apply {
                setAllowHitTest(false)
                layout {
                    it.positionType(YogaPositionType.ABSOLUTE).left(4f).top(4f)
                        .width(32f).height(32f).aspectRatio(1f)
                }
                style { it.background(SpriteTexture.of(icon)) }
            })
        } else {
            addChild(TextElement().apply {
                setText("+")
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(8f).width(40f).height(20f) }
                textStyle {
                    it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(13f)
                        .textColor(DestinyNavigationTemplate.Color.MUTED).textShadow(false)
                }
            })
        }
        energyCost?.let { cost ->
            addChild(TextElement().apply {
                setText(cost.toString())
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(2f).top(2f).width(10f).height(10f) }
                textStyle {
                    it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(7f)
                        .textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false)
                }
            })
        }
    }

    private fun addArmorEnergyMeter(host: UIElement, used: Int, capacity: Int) {
        host.addChild(TextElement().apply {
            setText("能量 $used/$capacity")
            setAllowHitTest(false)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(204f).top(1f).width(86f).height(12f) }
            textStyle {
                it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(8f)
                    .textColor(DestinyNavigationTemplate.Color.SECONDARY).textShadow(false)
            }
        })
        repeat(capacity.coerceIn(1, 10)) { index ->
            host.addChild(UIElement().apply {
                setAllowHitTest(false)
                layout {
                    it.positionType(YogaPositionType.ABSOLUTE).left(204f + index * 17f).top(20f)
                        .width(13f).height(7f)
                }
                style {
                    it.background(
                        ColorRectTexture(
                            if (index < used) DestinyNavigationTemplate.Color.EXOTIC else 0x405D6771
                        )
                    ).overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
                }
            })
        }
        host.addChild(TextElement().apply {
            setText(if (used >= capacity) "能量已满" else "剩余 ${capacity - used}")
            setAllowHitTest(false)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(204f).top(34f).width(170f).height(12f) }
            textStyle {
                it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(7f)
                    .textColor(if (used >= capacity) DestinyNavigationTemplate.Color.EXOTIC else DestinyNavigationTemplate.Color.MUTED)
                    .textShadow(false)
            }
        })
    }

    private fun armorModHover(mod: ArmorModTooltipEntry): HoverTooltips = choiceHover(
        DetailChoice(mod.id, mod.name, mod.description, mod.icon, mod.energyCost)
    )

    private fun choiceHover(choice: DetailChoice): HoverTooltips {
        val lines = buildList {
            add(Component.literal(choice.name))
            choice.energyCost?.let { add(Component.literal("能量消耗 $it")) }
            if (choice.description.isNotBlank()) add(Component.literal(choice.description))
            choice.disabledReason?.let { add(Component.literal(it)) }
        }
        return HoverTooltips.empty().append(*lines.toTypedArray())
    }

    @Suppress("unused")
    private fun bindModsLegacy(ui: UI, stack: ItemStack, data: atopos.destiny2.client.gear.PerkTooltipData) {
        val host = element(ui, "equipment_detail_mods") ?: return
        host.clearAllChildren()
        val labels = when (data.category) {
            GearCategory.ARMOR -> data.equippedArmorMods.mapIndexed { index, mod -> mod?.name ?: "空模组 ${index + 1}" }
            else -> buildList {
                if (data.hasCatalystSlot) add(data.catalysts.firstOrNull { it.id == data.equippedCatalystId }?.name ?: "空催化槽")
            }
        }
        if (labels.isEmpty()) labelsFallback(host) else labels.take(4).forEachIndexed { index, label ->
            host.addChild(TextElement().apply {
                setText(label.take(8))
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(index * 70f).top(0f).width(64f).height(32f) }
                style { it.background(ColorRectTexture(0x4C707985)).overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE)) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(8f).textColor(DestinyNavigationTemplate.Color.SECONDARY).textShadow(false) }
                addEventListener(UIEvents.CLICK) {
                    val active = session ?: return@addEventListener
                    if (data.category == GearCategory.ARMOR) {
                        val options = buildList {
                            add(DetailChoice(null, "卸下模组", "清空这个护甲模组槽位。"))
                            data.availableArmorModsBySocket.getOrNull(index).orEmpty().forEach { mod ->
                                add(DetailChoice(mod.id, mod.name, "能量 ${mod.energyCost}  //  ${mod.description}", mod.icon))
                            }
                        }
                        showChoiceTray(ui, "模组槽 ${index + 1}  //  选择模组", options) { choice ->
                            ClientPlayNetworking.send(
                                DestinyNetworking.ConfigureArmorModPayload(
                                    active.containerId, active.menuSlot, index, choice.id?.toString().orEmpty()
                                )
                            )
                            if (GearRolls.setArmorMod(stack, index, choice.id)) {
                                open(ui, stack, active.containerId, active.menuSlot)
                            }
                        }
                    } else if (data.hasCatalystSlot) {
                        val options = buildList {
                            add(DetailChoice(null, "卸下催化剂", "清空当前催化剂槽位。"))
                            data.catalysts.forEach { catalyst ->
                                add(DetailChoice(catalyst.id, catalyst.name, catalyst.description, catalyst.icon))
                            }
                        }
                        showChoiceTray(ui, "催化剂  //  选择", options) { choice ->
                            ClientPlayNetworking.send(
                                DestinyNetworking.ConfigureGearCatalystPayload(
                                    active.containerId, active.menuSlot, choice.id?.toString().orEmpty()
                                )
                            )
                            if (GearRolls.setCatalyst(stack, choice.id)) {
                                open(ui, stack, active.containerId, active.menuSlot)
                            }
                        }
                    }
                }
            })
        }
    }

    private fun showChoiceTray(
        ui: UI,
        title: String,
        choices: List<DetailChoice>,
        onPreview: (DetailChoice) -> Unit = {},
        onChoose: (DetailChoice) -> Unit
    ) {
        val host = element(ui, "equipment_detail_choice_tray") ?: return
        if (choices.isEmpty()) return
        var page = 0
        val pageSize = 8
        val pageCount = ((choices.size + pageSize - 1) / pageSize).coerceAtLeast(1)

        fun renderPage() {
            host.clearAllChildren()
            host.style {
                it.background(ColorRectTexture(0xF02D343C.toInt()))
                    .overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT)).zIndex(110)
            }
            host.addChild(TextElement().apply {
                setText(title)
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(10f).top(7f).width(300f).height(16f) }
                textStyle {
                    it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(9f)
                        .textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false)
                }
            })
            host.addChild(TextElement().apply {
                setText("关闭")
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(340f).top(7f).width(36f).height(16f) }
                textStyle {
                    it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(8f)
                        .textColor(DestinyNavigationTemplate.Color.SECONDARY).textShadow(false)
                }
                addEventListener(UIEvents.CLICK) { closeChoiceTray(ui) }
            })

            choices.drop(page * pageSize).take(pageSize).forEachIndexed { index, choice ->
                val column = index % 4
                val row = index / 4
                val baseBackground = if (choice.enabled) 0xCC5D6771.toInt() else 0x88505760.toInt()
                val baseBorder = if (choice.enabled) DestinyNavigationTemplate.Color.LINE else 0x706A6268
                val cell = UIElement().apply {
                    layout {
                        it.positionType(YogaPositionType.ABSOLUTE)
                            .left(53f + column * 72f).top(29f + row * 70f)
                            .width(64f).height(64f).aspectRatio(1f)
                    }
                    style {
                        it.background(ColorRectTexture(baseBackground))
                            .overlay(ColorBorderTexture(1, baseBorder))
                    }
                    addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
                        event.hoverTooltips = choiceHover(choice)
                    }
                    addEventListener(UIEvents.MOUSE_ENTER) {
                        style {
                            it.background(
                                ColorRectTexture(if (choice.enabled) 0xE0788490.toInt() else 0x99505760.toInt())
                            ).overlay(
                                ColorBorderTexture(
                                    1,
                                    if (choice.enabled) DestinyNavigationTemplate.Color.BRIGHT else 0xA0806068.toInt()
                                )
                            )
                        }
                        onPreview(choice)
                    }
                    addEventListener(UIEvents.MOUSE_LEAVE) {
                        style {
                            it.background(ColorRectTexture(baseBackground))
                                .overlay(ColorBorderTexture(1, baseBorder))
                        }
                    }
                    addEventListener(UIEvents.CLICK) {
                        if (choice.enabled) onChoose(choice)
                    }
                }
                val icon = choice.icon
                val hasIcon = icon != null && Minecraft.getInstance().resourceManager.getResource(icon).isPresent
                if (hasIcon) {
                    cell.addChild(UIElement().apply {
                        setAllowHitTest(false)
                        layout {
                            it.positionType(YogaPositionType.ABSOLUTE).left(15f).top(4f)
                                .width(34f).height(34f).aspectRatio(1f)
                        }
                        style { it.background(SpriteTexture.of(icon)) }
                    })
                }
                choice.energyCost?.let { cost ->
                    cell.addChild(TextElement().apply {
                        setText(cost.toString())
                        setAllowHitTest(false)
                        layout { it.positionType(YogaPositionType.ABSOLUTE).left(3f).top(3f).width(10f).height(10f) }
                        textStyle {
                            it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(7f)
                                .textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false)
                        }
                    })
                }
                cell.addChild(TextElement().apply {
                    setText(choice.name)
                    setAllowHitTest(false)
                    layout {
                        it.positionType(YogaPositionType.ABSOLUTE).left(4f).top(if (hasIcon) 40f else 14f)
                            .width(56f).height(if (hasIcon) 20f else 40f)
                    }
                    textStyle {
                        it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(7f)
                            .textColor(
                                if (choice.enabled) DestinyNavigationTemplate.Color.PRIMARY
                                else DestinyNavigationTemplate.Color.MUTED
                            ).textShadow(false).textWrap(TextWrap.WRAP)
                    }
                })
                host.addChild(cell)
            }

            if (pageCount > 1) {
                host.addChild(TextElement().apply {
                    setText("‹")
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(150f).top(170f).width(24f).height(16f) }
                    textStyle {
                        it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(10f)
                            .textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false)
                    }
                    addEventListener(UIEvents.CLICK) {
                        page = Math.floorMod(page - 1, pageCount)
                        renderPage()
                    }
                })
                host.addChild(TextElement().apply {
                    setText("${page + 1} / $pageCount")
                    setAllowHitTest(false)
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(174f).top(170f).width(46f).height(16f) }
                    textStyle {
                        it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(8f)
                            .textColor(DestinyNavigationTemplate.Color.SECONDARY).textShadow(false)
                    }
                })
                host.addChild(TextElement().apply {
                    setText("›")
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(220f).top(170f).width(24f).height(16f) }
                    textStyle {
                        it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(10f)
                            .textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false)
                    }
                    addEventListener(UIEvents.CLICK) {
                        page = (page + 1) % pageCount
                        renderPage()
                    }
                })
            }
        }

        host.setDisplay(true)
        host.setVisible(true)
        renderPage()
    }

    @Suppress("unused")
    private fun showChoiceTrayLegacy(
        ui: UI,
        title: String,
        choices: List<DetailChoice>,
        onPreview: (DetailChoice) -> Unit = {},
        onChoose: (DetailChoice) -> Unit
    ) {
        val host = element(ui, "equipment_detail_choice_tray") ?: return
        if (choices.isEmpty()) return
        var page = 0
        val pageSize = 16
        val pageCount = ((choices.size + pageSize - 1) / pageSize).coerceAtLeast(1)

        fun renderPage() {
            host.clearAllChildren()
            host.style {
                it.background(ColorRectTexture(0xF02D343C.toInt()))
                    .overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT)).zIndex(110)
            }
            host.addChild(TextElement().apply {
                setText(title)
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(10f).top(8f).width(300f).height(16f) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(9f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
            })
            host.addChild(TextElement().apply {
                setText("关闭")
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(340f).top(8f).width(36f).height(16f) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(8f).textColor(DestinyNavigationTemplate.Color.SECONDARY).textShadow(false) }
                addEventListener(UIEvents.CLICK) { closeChoiceTray(ui) }
            })

            choices.drop(page * pageSize).take(pageSize).forEachIndexed { index, choice ->
                val column = index % 4
                val row = index / 4
                val cell = UIElement().apply {
                    layout {
                        it.positionType(YogaPositionType.ABSOLUTE)
                            .left(8f + column * 94f).top(30f + row * 34f).width(88f).height(29f)
                    }
                    style {
                        it.background(ColorRectTexture(0xCC5D6771.toInt()))
                            .overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
                    }
                    addEventListener(UIEvents.MOUSE_ENTER) {
                        style {
                            it.background(ColorRectTexture(0xE0788490.toInt()))
                                .overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT))
                        }
                        onPreview(choice)
                    }
                    addEventListener(UIEvents.MOUSE_LEAVE) {
                        style {
                            it.background(ColorRectTexture(0xCC5D6771.toInt()))
                                .overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
                        }
                    }
                    addEventListener(UIEvents.CLICK) { onChoose(choice) }
                }
                val icon = choice.icon
                val hasIcon = icon != null && Minecraft.getInstance().resourceManager.getResource(icon).isPresent
                if (hasIcon) {
                    cell.addChild(UIElement().apply {
                        setAllowHitTest(false)
                        layout { it.positionType(YogaPositionType.ABSOLUTE).left(3f).top(3f).width(23f).height(23f).aspectRatio(1f) }
                        style { it.background(SpriteTexture.of(icon)) }
                    })
                }
                cell.addChild(TextElement().apply {
                    setText(choice.name)
                    setAllowHitTest(false)
                    layout {
                        it.positionType(YogaPositionType.ABSOLUTE).left(if (hasIcon) 29f else 5f).top(5f)
                            .width(if (hasIcon) 55f else 78f).height(20f)
                    }
                    textStyle {
                        it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(7f)
                            .textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false).textWrap(TextWrap.WRAP)
                    }
                })
                host.addChild(cell)
            }

            if (pageCount > 1) {
                host.addChild(TextElement().apply {
                    setText("‹")
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(150f).top(170f).width(24f).height(16f) }
                    textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(10f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
                    addEventListener(UIEvents.CLICK) { page = Math.floorMod(page - 1, pageCount); renderPage() }
                })
                host.addChild(TextElement().apply {
                    setText("${page + 1} / $pageCount")
                    setAllowHitTest(false)
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(174f).top(170f).width(46f).height(16f) }
                    textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(8f).textColor(DestinyNavigationTemplate.Color.SECONDARY).textShadow(false) }
                })
                host.addChild(TextElement().apply {
                    setText("›")
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(220f).top(170f).width(24f).height(16f) }
                    textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(10f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
                    addEventListener(UIEvents.CLICK) { page = (page + 1) % pageCount; renderPage() }
                })
            }
        }

        host.setDisplay(true)
        host.setVisible(true)
        renderPage()
    }

    private fun closeChoiceTray(ui: UI) {
        element(ui, "equipment_detail_choice_tray")?.apply {
            clearAllChildren()
            setVisible(false)
            setDisplay(false)
        }
    }

    private fun labelsFallback(host: UIElement) {
        host.addChild(TextElement().apply {
            setText("没有可用模组槽")
            setAllowHitTest(false)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(6f).width(180f).height(18f) }
            textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(9f).textColor(DestinyNavigationTemplate.Color.MUTED).textShadow(false) }
        })
    }

    private fun bindStats(ui: UI, stack: ItemStack, category: GearCategory) {
        val host = element(ui, "equipment_detail_stats") ?: return
        host.clearAllChildren()
        val data = PerkTooltipDataAdapter.from(stack) ?: return
        val definition = GearRegistry.definitionFor(stack) ?: return
        val values = if (category == GearCategory.WEAPON) {
            listOf(
                "伤害" to (data.damage ?: 0.0),
                "射速" to ((data.fireRate ?: 0.0) * 60.0),
                "精准" to (definition.precisionMultiplier * 50.0),
                "弹匣" to (definition.frame.magazineSize + GearRolls.magazineSizeBonus(stack)).toDouble(),
                "装填" to (100.0 / GearRolls.reloadTimeMultiplier(stack).coerceAtLeast(0.1f)),
                "射程" to (definition.frame.projectileSpeed * 32.0)
            )
        } else {
            data.armorStats?.let { baseStats ->
                val stats = baseStats + (GearRolls.equippedArmorStatMod(stack)?.bonusStats
                    ?: atopos.destiny2.common.player.DestinyStats(0, 0, 0, 0, 0, 0))
                listOf(
                    "武器" to stats.weapons.toDouble(), "生命" to stats.health.toDouble(),
                    "职业" to stats.classAbility.toDouble(), "手雷" to stats.grenade.toDouble(),
                    "超能" to stats.superStat.toDouble(), "近战" to stats.melee.toDouble()
                )
            } ?: listOf("装备等级" to data.armorTier.toDouble())
        }
        values.take(7).forEachIndexed { index, (label, raw) ->
            val y = index * 31f
            val display = if (raw % 1.0 == 0.0) raw.roundToInt().toString() else "%.1f".format(raw)
            host.addChild(TextElement().apply {
                setText(label)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(y).width(48f).height(15f) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(9f).textColor(DestinyNavigationTemplate.Color.SECONDARY).textShadow(false) }
            })
            host.addChild(UIElement().apply {
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(50f).top(y + 3f).width(94f).height(8f) }
                style { it.background(ColorRectTexture(0x48707985)) }
                addChild(UIElement().apply {
                    setAllowHitTest(false)
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(0f).width((raw.coerceIn(0.0, 100.0) / 100.0 * 94.0).toFloat()).height(8f) }
                    style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.PRIMARY)) }
                })
            })
            host.addChild(TextElement().apply {
                setText(display)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(148f).top(y).width(26f).height(15f) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(9f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
            })
        }
    }

    private fun element(ui: UI, id: String): UIElement? = ui.rootElement.selectId(id).findFirst().orElse(null)
    private fun text(ui: UI, id: String): TextElement? =
        ui.rootElement.selectId(id, TextElement::class.java).findFirst().orElse(null)
}
