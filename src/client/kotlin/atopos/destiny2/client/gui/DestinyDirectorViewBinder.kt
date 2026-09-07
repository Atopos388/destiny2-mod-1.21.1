package atopos.destiny2.client.gui

import atopos.destiny2.client.gear.DestinyPerkTooltipController
import atopos.destiny2.client.gear.DestinyEquipmentTooltipSlot
import atopos.destiny2.client.gear.PerkTooltipData
import atopos.destiny2.client.gear.PerkTooltipDataAdapter
import atopos.destiny2.common.player.GuardianJourneyStage
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.client.gear.PerkTooltipEntry
import atopos.destiny2.common.equipment.DestinyClassItemSlot
import atopos.destiny2.common.item.DestinyClassItem
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.gear.GearRarity
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.DestinyWeaponReserves
import atopos.destiny2.common.weapon.DestinyWeaponSlot
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry
import com.lowdragmc.lowdraglib2.math.interpolate.Eases
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import it.unimi.dsi.fastutil.floats.FloatObjectPair
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.ArmorItem
import org.appliedenergistics.yoga.YogaPositionType
import org.appliedenergistics.yoga.YogaOverflow
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/** Runtime data/controller layer for the editable Director UITemplate. */
object DestinyDirectorViewBinder {
    private val logger = LoggerFactory.getLogger("destiny2-mod/director-ui")
    private const val EQUIPMENT_ICON_ROOT = "textures/gui/director/equipment"
    private enum class Page { EQUIPMENT, INVENTORY, COLLECTIONS, JOURNEY, FIRETEAM }
    private data class EquipmentEntry(
        val target: String,
        val label: String,
        val stack: ItemStack,
        val sourceIndex: Int
    )
    private data class EquipmentChoice(
        val stack: ItemStack,
        val sourceIndex: Int,
        val equipped: Boolean,
        val reserveIndex: Int = -1
    )
    private data class EquipmentStatInfo(
        val title: String,
        val category: String,
        val description: String,
        val effect: String
    )

    fun bindRuntime(ui: UI, player: LocalPlayer) {
        val root = ui.rootElement
        installResponsiveCanvas(ui)
        installPageViewport(ui)
        var page = Page.EQUIPMENT
        var selected = player.mainHandItem.copy()
        var stateKey = ""
        var transitionSerial = 0
        var openEquipmentTarget: String? = null

        fun pageElement(value: Page): UIElement? = element(ui, when (value) {
            Page.EQUIPMENT -> "equipment_page"
            Page.INVENTORY -> "inventory_page"
            Page.COLLECTIONS -> "collections_page"
            Page.JOURNEY -> "journey_page"
            Page.FIRETEAM -> "fireteam_page"
        })

        fun tabGeometry(value: Page): Pair<Float, Float> = when (value) {
            Page.EQUIPMENT -> 440f to 82f
            Page.INVENTORY -> 525f to 82f
            Page.COLLECTIONS -> 610f to 96f
            Page.JOURNEY -> 709f to 76f
            Page.FIRETEAM -> 788f to 132f
        }

        fun updateTabState(active: Page) {
            listOf(
                Page.EQUIPMENT to "tab_equipment",
                Page.INVENTORY to "tab_inventory",
                Page.COLLECTIONS to "tab_collections",
                Page.JOURNEY to "tab_journey",
                Page.FIRETEAM to "tab_fireteam"
            ).forEach { (value, id) ->
                button(ui, id)?.textStyle {
                    it.font(DestinyNavigationTemplate.DIRECTOR_FONT)
                        .fontSize(11f)
                        .textColor(if (value == active) DestinyNavigationTemplate.Color.PRIMARY else DestinyNavigationTemplate.Color.SECONDARY)
                        .textShadow(false)
                }
            }
        }

        fun showPage(next: Page, preserveEquipmentDetail: Boolean = false) {
            if (!preserveEquipmentDetail) DestinyEquipmentDetailView.close(ui)
            val previous = page
            val changed = previous != next
            val (left, width) = tabGeometry(next)

            if (!changed) {
                Page.entries.forEach { value ->
                    pageElement(value)?.apply {
                        setDisplay(value == next)
                        setVisible(value == next)
                        style { it.opacity(1f).transform2D(Transform2D.identity()).zIndex(0) }
                    }
                }
                element(ui, "tab_indicator")?.apply {
                    layout { it.left(left).width(width) }
                    style { it.opacity(1f).transform2D(Transform2D.identity()) }
                }
                updateTabState(next)
                return
            }

            page = next
            val token = ++transitionSerial
            // The outgoing page owns the visible travel direction. Advancing
            // through tabs moves it left; returning moves it right.
            val travelX = if (next.ordinal > previous.ordinal) -32f else 32f
            val incoming = pageElement(next)
            val outgoing = pageElement(previous)

            Page.entries.forEach { value ->
                if (value != next && value != previous) {
                    pageElement(value)?.apply {
                        setDisplay(false)
                        setVisible(false)
                        style { it.opacity(1f).transform2D(Transform2D.identity()).zIndex(0) }
                    }
                }
            }

            outgoing?.apply {
                setDisplay(true)
                setVisible(true)
                // Keep the outgoing plane above the opaque incoming page so
                // its left/right exit motion cannot be covered immediately.
                style { it.opacity(1f).transform2D(Transform2D.identity()).zIndex(3) }
                animation().duration(0.22f).ease(Eases.CUBIC_IN)
                    .style(
                        PropertyRegistry.TRANSFORM_2D,
                        FloatObjectPair.of(1f, Transform2D().translate(travelX, 0f))
                    )
                    .start()
            }

            incoming?.apply {
                setDisplay(true)
                setVisible(true)
                style { it.opacity(1f).transform2D(Transform2D().translate(-travelX, 0f)).zIndex(2) }
                animation().duration(0.24f).ease(Eases.CUBIC_OUT)
                    .style(PropertyRegistry.TRANSFORM_2D, FloatObjectPair.of(1f, Transform2D.identity()))
                    .onFinished {
                        if (transitionSerial == token) {
                            outgoing?.apply {
                                setDisplay(false)
                                setVisible(false)
                                style { style -> style.opacity(1f).transform2D(Transform2D.identity()).zIndex(0) }
                            }
                            style { style -> style.opacity(1f).transform2D(Transform2D.identity()).zIndex(0) }
                        }
                    }
                    .start()
            }

            val (oldLeft, oldWidth) = tabGeometry(previous)
            element(ui, "tab_indicator")?.apply {
                layout { it.left(left).width(width) }
                style {
                    it.opacity(0.72f).transform2D(
                        Transform2D().pivot(0f, 0f)
                            .translate(oldLeft - left, 0f)
                            .scale(oldWidth / width, 1f)
                    )
                }
                animation().duration(0.22f).ease(Eases.CUBIC_OUT)
                    .style(PropertyRegistry.TRANSFORM_2D, FloatObjectPair.of(1f, Transform2D.identity()))
                    .style(PropertyRegistry.OPACITY, 1f)
                    .start()
            }
            updateTabState(next)
            DestinyPerkTooltipController.clearInteraction()
        }

        fun select(stack: ItemStack) {
            selected = stack.copy()
            updateInspector(ui, selected)
        }

        fun closeEquipmentChoices() {
            openEquipmentTarget = null
            closeEquipmentChoiceTray(ui)
        }

        fun toggleEquipmentChoices(entry: EquipmentEntry, alignRight: Boolean) {
            if (openEquipmentTarget == entry.target) {
                closeEquipmentChoices()
                return
            }
            openEquipmentTarget = entry.target
            val choices = equipmentChoices(player, entry)
            showEquipmentChoiceTray(ui, entry, choices, alignRight) { choice ->
                if (choice.stack.isEmpty) return@showEquipmentChoiceTray
                if (alignRight) select(choice.stack)
                if (choice.reserveIndex >= 0) {
                    ClientPlayNetworking.send(
                        DestinyNetworking.EquipDirectorReserveWeaponPayload(
                            choice.reserveIndex,
                            entry.target,
                            BuiltInRegistries.ITEM.getKey(choice.stack.item).toString(),
                            choice.stack.components.hashCode()
                        )
                    )
                } else if (!choice.equipped && choice.sourceIndex >= 0) {
                    ClientPlayNetworking.send(
                        DestinyNetworking.EquipDirectorItemPayload(
                            choice.sourceIndex,
                            entry.target,
                            BuiltInRegistries.ITEM.getKey(choice.stack.item).toString(),
                            choice.stack.components.hashCode()
                        )
                    )
                }
                closeEquipmentChoices()
            }
        }

        fun openEquipmentChoices(entry: EquipmentEntry) {
            if (openEquipmentTarget == entry.target) return
            toggleEquipmentChoices(entry, false)
        }

        fun showEquipmentDetails(entry: EquipmentEntry) {
            if (entry.stack.isEmpty) return
            closeEquipmentChoices()
            select(entry.stack)
            val menuSlot = when (entry.target) {
                "weapon_kinetic", "weapon_energy", "weapon_power" ->
                    entry.sourceIndex.takeIf { it >= 0 }?.let { -it - 1 }
                "armor_head" -> -40
                "armor_chest" -> -39
                "armor_legs" -> -38
                "armor_feet" -> -37
                "class_item" -> classItem(player).second.takeIf { it >= 0 }
                else -> null
            }
            if (menuSlot == null) DestinyEquipmentDetailView.open(ui, entry.stack)
            else DestinyEquipmentDetailView.open(ui, entry.stack, player.inventoryMenu.containerId, menuSlot)
        }

        button(ui, "tab_equipment")?.setOnClick { showPage(Page.EQUIPMENT) }
        button(ui, "tab_inventory")?.setOnClick { closeEquipmentChoices(); showPage(Page.INVENTORY) }
        button(ui, "tab_collections")?.setOnClick { closeEquipmentChoices(); showPage(Page.COLLECTIONS) }
        button(ui, "tab_journey")?.setOnClick { closeEquipmentChoices(); showPage(Page.JOURNEY) }
        button(ui, "tab_fireteam")?.setOnClick { closeEquipmentChoices(); showPage(Page.FIRETEAM) }

        fun refresh() {
            val data = DestinyNavigationDataAdapter.snapshot() ?: return
            text(ui, "guardian_name")?.setText(data.guardianName)
            text(ui, "guardian_meta")?.setText(
                listOf(data.className, data.subclassName, "光等 ${data.power}", data.location).filter(String::isNotBlank).joinToString("  //  ")
            )
            bindEquipment(ui, player, ::select, ::toggleEquipmentChoices, ::showEquipmentDetails, ::openEquipmentChoices)
            bindEquipmentOrnaments(ui, player)
            bindInventory(ui, player, ::select)
            bindJourney(ui, data)
            bindFireteam(ui, data)
            if (selected.isEmpty) selected = player.mainHandItem.copy()
            updateInspector(ui, selected)
            showPage(page, preserveEquipmentDetail = true)
            DestinyEquipmentDetailView.refreshIfOpen(ui)
        }

        root.addEventListener(UIEvents.TICK) {
            val data = DestinyNavigationDataAdapter.snapshot()
            val stacks = (0 until player.inventory.containerSize).joinToString("|") { index ->
                val stack = player.inventory.getItem(index)
                "${stack.item.hashCode()}:${stack.count}:${stack.components.hashCode()}"
            }
            val classItem = classItem(player).first
            val classItemKey = "${classItem.item.hashCode()}:${classItem.count}:${classItem.components.hashCode()}"
            val key = "$stacks|$classItemKey|${data?.superEnergy}|${data?.power}|${data?.location}|${data?.fireteam?.size}|" +
                "${DestinyHUDState.subclassName}|${DestinyHUDState.weapons}|${DestinyHUDState.health}|" +
                "${DestinyHUDState.classAbility}|${DestinyHUDState.grenade}|${DestinyHUDState.superStat}|${DestinyHUDState.melee}|" +
                DestinyWeaponLoadoutState.revision
            if (key != stateKey) {
                stateKey = key
                refresh()
            }
        }
        DestinyCollectionsView(ui).bind()
        bindPlayerPreview(ui, player)
        bindEquipmentStatHover(ui)
        refresh()
        // bindRuntime() runs before ModularUI.of(...). Starting a StyleAnimation
        // here leaves the authored canvas at its initial low opacity because the
        // animation has no ModularUI scheduler yet. Keep the full composition
        // opaque; page-local animations are started later from live UI events.
        normalizeAuthoredComposition(ui)
    }

    /** Slides the authored foreground away while its opaque backdrop stays stable. */
    fun animateClose(ui: UI, onFinished: () -> Unit) {
        val targets = listOf(
            "director_header",
            "equipment_page",
            "inventory_page",
            "collections_page",
            "journey_page",
            "fireteam_page",
            "director_footer"
        ).mapNotNull { element(ui, it) }
        if (targets.isEmpty()) {
            onFinished()
            return
        }
        val callbackTarget = targets.last()
        targets.forEach { target ->
            val animation = target.animation().duration(0.18f).ease(Eases.CUBIC_IN)
                .style(
                    PropertyRegistry.TRANSFORM_2D,
                    FloatObjectPair.of(1f, Transform2D().translate(-28f, 0f))
                )
            if (target === callbackTarget) animation.onFinished { onFinished() }
            animation.start()
        }
    }

    /** Gives the visual editor a representative populated preview. */
    fun bindEditorSimulation(ui: UI) {
        installResponsiveCanvas(ui)
        installPageViewport(ui)
        text(ui, "guardian_name")?.setText("ATOPOS")
        text(ui, "guardian_meta")?.setText("守护者  //  烈日术士  //  高塔")
        text(ui, "equipment_power")?.setText("◆ 1900")
        text(ui, "inventory_count")?.setText("12 / 36")
        val preview = listOf(
            ItemStack(Items.NETHERITE_SWORD), ItemStack(Items.CROSSBOW), ItemStack(Items.GOLDEN_APPLE),
            ItemStack(Items.AMETHYST_SHARD, 16), ItemStack(Items.ENDER_PEARL, 8), ItemStack(Items.COMPASS)
        )
        val previewWeapons = listOf("动能武器", "能量武器", "威能武器").mapIndexed { index, label ->
            EquipmentEntry("preview_weapon_$index", label, preview[index], -1)
        }
        val previewArmor = listOf("头盔", "胸甲", "护腿", "靴子", "职业物品").mapIndexed { index, label ->
            EquipmentEntry("preview_armor_$index", label, (preview + preview)[index], -1)
        }
        fun previewExpand(entry: EquipmentEntry, alignRight: Boolean) {
            showEquipmentChoiceTray(
                ui,
                entry,
                preview.mapIndexed { index, stack -> EquipmentChoice(stack, index, index == 0) },
                alignRight
            ) { closeEquipmentChoiceTray(ui) }
        }
        populateEquipmentRows(
            element(ui, "equipment_weapon_slots"), previewWeapons, false, {}, ::previewExpand,
            { entry -> DestinyEquipmentDetailView.open(ui, entry.stack) },
            { entry -> previewExpand(entry, false) }
        )
        populateEquipmentRows(
            element(ui, "equipment_armor_slots"), previewArmor, true, {}, ::previewExpand,
            { entry -> DestinyEquipmentDetailView.open(ui, entry.stack) }
        )
        bindEquipmentOrnaments(ui, Minecraft.getInstance().player, enableSubclassNavigation = false)
        bindEquipmentStatHover(ui)
        element(ui, "equipment_player_preview")?.apply {
            clearAllChildren()
            addChild(TextElement().apply {
                setText("守护者\n实时模型预览")
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(62f).top(118f).width(140f).height(44f) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(11f).textColor(DestinyNavigationTemplate.Color.MUTED).textShadow(false).textWrap(TextWrap.WRAP) }
            })
        }
        populatePreviewSlots(element(ui, "equipment_slots"), preview.take(4), 48, 8)
        populatePreviewSlots(element(ui, "inventory_main_slots"), preview + preview, 42, 5)
        populatePreviewSlots(element(ui, "inventory_hotbar_slots"), preview, 42, 5)
        updateInspector(ui, preview.first())
        DestinyCollectionsView(ui).bind()
        fun previewPage(id: String) {
            listOf("equipment_page", "inventory_page", "collections_page", "journey_page", "fireteam_page").forEach { pageId ->
                element(ui, pageId)?.apply {
                    setDisplay(pageId == id)
                    setVisible(pageId == id)
                }
            }
        }
        button(ui, "tab_equipment")?.setOnClick { previewPage("equipment_page") }
        button(ui, "tab_inventory")?.setOnClick { previewPage("inventory_page") }
        button(ui, "tab_collections")?.setOnClick { previewPage("collections_page") }
        button(ui, "tab_journey")?.setOnClick { previewPage("journey_page") }
        button(ui, "tab_fireteam")?.setOnClick { previewPage("fireteam_page") }
    }

    private fun bindPlayerPreview(ui: UI, player: LocalPlayer) {
        val host = element(ui, "equipment_player_preview") ?: return
        if (host.selectId("equipment_player_runtime").findFirst().isPresent) return
        host.clearAllChildren()
        host.addChild(DestinyPlayerPreviewElement { player }.apply {
            layout {
                it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(0f)
                    .widthPercent(100f).heightPercent(100f)
            }
        })
    }

    private fun bindEquipment(
        ui: UI,
        player: LocalPlayer,
        onSelect: (ItemStack) -> Unit,
        onExpand: (EquipmentEntry, Boolean) -> Unit,
        onDetail: (EquipmentEntry) -> Unit,
        onWeaponHover: (EquipmentEntry) -> Unit
    ) {
        val weapons = weaponEquipment(player)
        val armor = listOf(
            EquipmentEntry("armor_head", "头盔", player.getItemBySlot(EquipmentSlot.HEAD).copy(), -1),
            EquipmentEntry("armor_chest", "胸甲", player.getItemBySlot(EquipmentSlot.CHEST).copy(), -1),
            EquipmentEntry("armor_legs", "护腿", player.getItemBySlot(EquipmentSlot.LEGS).copy(), -1),
            EquipmentEntry("armor_feet", "靴子", player.getItemBySlot(EquipmentSlot.FEET).copy(), -1),
            EquipmentEntry("class_item", "职业物品", classItem(player).first, -1)
        )
        populateEquipmentRows(element(ui, "equipment_weapon_slots"), weapons, false, onSelect, onExpand, onDetail, onWeaponHover)
        populateEquipmentRows(element(ui, "equipment_armor_slots"), armor, true, onSelect, onExpand, onDetail)
        text(ui, "equipment_power")?.setText("◆ ${DestinyNavigationState.currentPower}")
    }

    private fun weaponEquipment(player: LocalPlayer): List<EquipmentEntry> {
        return DestinyWeaponSlot.entries.map { slot ->
            val stack = player.inventory.getItem(slot.hotbarIndex)
                .takeIf { DestinyWeaponSlot.forStack(it) == slot }
                ?.copy() ?: ItemStack.EMPTY
            EquipmentEntry("weapon_${slot.serializedName}", slot.displayName, stack, slot.hotbarIndex)
        }
    }

    private fun populateEquipmentRows(
        host: UIElement?,
        entries: List<EquipmentEntry>,
        alignRight: Boolean,
        onSelect: (ItemStack) -> Unit,
        onExpand: (EquipmentEntry, Boolean) -> Unit,
        onDetail: (EquipmentEntry) -> Unit,
        onWeaponHover: (EquipmentEntry) -> Unit = {}
    ) {
        host ?: return
        host.clearAllChildren()
        entries.forEachIndexed { index, entry ->
            val stack = entry.stack
            val rowHeight = if (alignRight) 64 else 68
            val size = if (alignRight) 50 else 52
            val slotX = 0
            val row = UIElement().apply {
                layout {
                    it.positionType(YogaPositionType.ABSOLUTE).left(0f).top((index * rowHeight).toFloat())
                        .width(size.toFloat()).height(rowHeight.toFloat())
                }
            }
            row.addChild(equipmentItemSlot(stack, slotX, 0, size).apply {
                addEventListener(UIEvents.CLICK) { event ->
                    if (event.button != 0) return@addEventListener
                    if (alignRight) {
                        if (!stack.isEmpty) onSelect(stack)
                        onExpand(entry, true)
                    }
                }
                addEventListener(UIEvents.MOUSE_DOWN, { event ->
                    if (event.button == 1 && !stack.isEmpty) {
                        event.stopImmediatePropagation()
                        onDetail(entry)
                    }
                }, true)
                if (!alignRight) {
                    addEventListener(UIEvents.MOUSE_ENTER) { onWeaponHover(entry) }
                }
            })
            host.addChild(row)
        }
    }

    private fun bindEquipmentOrnaments(ui: UI, player: LocalPlayer?, enableSubclassNavigation: Boolean = true) {
        val abilityData = DestinyAbilityHUDDataAdapter.snapshot()
        val subclass = DestinySubclassType.entries.firstOrNull { it.displayName == DestinyHUDState.subclassName }
            ?: DestinySubclassType.DEFAULT
        val subclassIcon = equipmentDamageTypeIcon(subclass)
        val subclassHost = element(ui, "equipment_subclass_socket")
        subclassHost?.apply {
            clearAllChildren()
            addChild(DestinyDiamondElementIcon(subclassIcon, abilityData.accent).apply {
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(0f).widthPercent(100f).heightPercent(100f).aspectRatio(1f) }
                if (enableSubclassNavigation) {
                    addEventListener(UIEvents.CLICK) {
                        DestinyNavigationOverlay.closeDirector()
                        DestinyAspectScreen.requestOpen(returnToEquipment = true)
                    }
                }
            })
        }
        element(ui, "equipment_ghost_slot")?.apply {
            clearAllChildren()
            val ghost = player?.let { owner ->
                (0..35).asSequence().map(owner.inventory::getItem)
                    .firstOrNull { it.`is`(DestinyItems.GHOST_CORE) }
            } ?: ItemStack.EMPTY
            addChild(equipmentItemSlot(ghost.copy(), 0, 0, 50))
        }
        text(ui, "equipment_stat_weapons")?.setText(DestinyHUDState.weapons.toString())
        text(ui, "equipment_stat_health")?.setText(DestinyHUDState.health.toString())
        text(ui, "equipment_stat_class")?.setText(DestinyHUDState.classAbility.toString())
        text(ui, "equipment_stat_grenade")?.setText(DestinyHUDState.grenade.toString())
        text(ui, "equipment_stat_super")?.setText(DestinyHUDState.superStat.toString())
        text(ui, "equipment_stat_melee")?.setText(DestinyHUDState.melee.toString())
        listOf(
            "equipment_stat_icon_weapons" to "stat_weapons.png",
            "equipment_stat_icon_health" to "stat_energy.png",
            "equipment_stat_icon_class" to "stat_class.png",
            "equipment_stat_icon_grenade" to "stat_grenade.png",
            "equipment_stat_icon_super" to "stat_super.png",
            "equipment_stat_icon_melee" to "stat_melee.png"
        ).forEach { (hostId, fileName) ->
            element(ui, hostId)?.apply {
                clearAllChildren()
                addChild(equipmentSprite(equipmentIcon(fileName), 16))
            }
        }
    }

    private fun equipmentSprite(icon: net.minecraft.resources.ResourceLocation, size: Int): UIElement = UIElement().apply {
        setAllowHitTest(false)
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(0f).width(size.toFloat()).height(size.toFloat()).aspectRatio(1f) }
        style {
            it.background(
                if (Minecraft.getInstance().resourceManager.getResource(icon).isPresent) SpriteTexture.of(icon)
                else ColorBorderTexture(1, DestinyNavigationTemplate.Color.MUTED)
            )
        }
    }

    private fun bindEquipmentStatHover(ui: UI) {
        val tooltip = element(ui, "equipment_stat_tooltip") ?: return
        val rows = listOf(
            "weapons" to "equipment_stat_row_weapons",
            "health" to "equipment_stat_row_health",
            "class" to "equipment_stat_row_class",
            "grenade" to "equipment_stat_row_grenade",
            "super" to "equipment_stat_row_super",
            "melee" to "equipment_stat_row_melee"
        )
        rows.forEach { (statKey, rowId) ->
            element(ui, rowId)?.apply {
                style { it.background(ColorRectTexture(0x00000000)) }
                addEventListener(UIEvents.MOUSE_ENTER) {
                    val info = equipmentStatInfo(statKey)
                    text(ui, "equipment_stat_tooltip_title")?.setText(info.title)
                    text(ui, "equipment_stat_tooltip_category")?.setText(info.category)
                    text(ui, "equipment_stat_tooltip_description")?.setText(info.description)
                    text(ui, "equipment_stat_tooltip_effect")?.setText(info.effect)
                    style { it.background(ColorRectTexture(0x24FFFFFF)) }
                    tooltip.apply {
                        setDisplay(true)
                        setVisible(true)
                        style {
                            it.opacity(0f).transform2D(Transform2D().translate(6f, 0f)).zIndex(60)
                        }
                        animation().duration(0.14f).ease(Eases.CUBIC_OUT)
                            .style(PropertyRegistry.OPACITY, 1f)
                            .style(PropertyRegistry.TRANSFORM_2D, FloatObjectPair.of(1f, Transform2D.identity()))
                            .start()
                    }
                }
                addEventListener(UIEvents.MOUSE_LEAVE) {
                    style { it.background(ColorRectTexture(0x00000000)) }
                    tooltip.setVisible(false)
                    tooltip.setDisplay(false)
                }
            }
        }
    }

    private fun equipmentStatInfo(statKey: String): EquipmentStatInfo {
        val stats = DestinyStats(
            DestinyHUDState.weapons,
            DestinyHUDState.health,
            DestinyHUDState.classAbility,
            DestinyHUDState.grenade,
            DestinyHUDState.superStat,
            DestinyHUDState.melee
        ).clamped()
        fun percent(multiplier: Float): String = "%.1f%%".format((multiplier - 1f) * 100f)
        fun recharge(value: Int): String = "%.1f%%".format((DestinyStatFormulas.rechargeRate(value) - 1f) * 100f)
        return when (statKey) {
            "weapons" -> EquipmentStatInfo(
                "武器",
                "角色属性  //  ${stats.weapons}/200",
                "提高所有武器造成的伤害，并缩短武器的换弹时间。超过 100 后进入强化区间。",
                "武器伤害  +${percent(DestinyStatFormulas.weaponDamageMultiplier(stats))}\n" +
                    "换弹时间  -${"%.1f%%".format((1f - DestinyStatFormulas.weaponReloadTimeMultiplier(stats)) * 100f)}"
            )
            "health" -> EquipmentStatInfo(
                "生命值",
                "角色属性  //  ${stats.health}/200",
                "提高额外护盾容量，并加快护盾受损后的恢复。超过 100 后继续提高护盾容量和恢复速度。",
                "额外护盾  ${"%.1f".format(DestinyStatFormulas.healthShieldCapacity(stats))}\n" +
                    "恢复延迟  ${"%.1f".format(DestinyStatFormulas.healthShieldRechargeDelayTicks(stats) / 20f)} 秒"
            )
            "class" -> EquipmentStatInfo(
                "职业能力",
                "角色属性  //  ${stats.classAbility}/200",
                "提高职业能力的充能速度。超过 100 后启用职业强化，并增加临时护盾容量。",
                "充能速率  ${signedPercent(recharge(stats.classAbility))}\n" +
                    "临时护盾  ${"%.1f".format(DestinyStatFormulas.classOvershieldCapacity(stats))}"
            )
            "grenade" -> EquipmentStatInfo(
                "手雷",
                "角色属性  //  ${stats.grenade}/200",
                "提高手雷能力的充能速度。超过 100 后进入强化区间，额外提高手雷伤害。",
                "充能速率  ${signedPercent(recharge(stats.grenade))}\n" +
                    "强化伤害  +${percent(DestinyStatFormulas.grenadeDamageMultiplier(stats))}"
            )
            "super" -> EquipmentStatInfo(
                "超能",
                "角色属性  //  ${stats.superStat}/200",
                "提高通过造成伤害获得的超能能量。超过 100 后进入强化区间，额外提高超能伤害。",
                "能量获取  ${signedPercent(recharge(stats.superStat))}\n" +
                    "强化伤害  +${percent(DestinyStatFormulas.superDamageMultiplier(stats))}"
            )
            else -> EquipmentStatInfo(
                "近战",
                "角色属性  //  ${stats.melee}/200",
                "提高近战能力的充能速度。超过 100 后进入强化区间，额外提高近战伤害。",
                "充能速率  ${signedPercent(recharge(stats.melee))}\n" +
                    "强化伤害  +${percent(DestinyStatFormulas.meleeDamageMultiplier(stats))}"
            )
        }
    }

    private fun signedPercent(value: String): String = if (value.startsWith("-")) value else "+$value"

    private fun equipmentDamageTypeIcon(subclass: DestinySubclassType): net.minecraft.resources.ResourceLocation =
        equipmentIcon(
            when (subclass) {
                DestinySubclassType.SOLAR_WARLOCK -> "damage_solar.png"
                DestinySubclassType.VOID_HUNTER -> "damage_void.png"
                DestinySubclassType.ARC_TITAN -> "damage_arc.png"
            }
        )

    private fun equipmentIcon(fileName: String): net.minecraft.resources.ResourceLocation =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("destiny2-mod", "$EQUIPMENT_ICON_ROOT/$fileName")

    private fun equipmentChoices(player: LocalPlayer, entry: EquipmentEntry): List<EquipmentChoice> {
        val weaponSlot = DestinyWeaponSlot.entries.firstOrNull { entry.target == "weapon_${it.serializedName}" }
        if (weaponSlot != null) {
            val choices = mutableListOf<EquipmentChoice>()
            DestinyWeaponLoadoutState.stacks(weaponSlot).forEachIndexed { reserveIndex, stack ->
                if (!stack.isEmpty && choices.size < DestinyWeaponReserves.CAPACITY_PER_SLOT) {
                    choices += EquipmentChoice(stack.copy(), -1, false, reserveIndex)
                }
            }
            (0..35).forEach { index ->
                if (choices.size >= DestinyWeaponReserves.CAPACITY_PER_SLOT || index == entry.sourceIndex) return@forEach
                val stack = player.inventory.getItem(index)
                if (!stack.isEmpty && DestinyWeaponSlot.forStack(stack) == weaponSlot) {
                    choices += EquipmentChoice(stack.copy(), index, false)
                }
            }
            while (choices.size < DestinyWeaponReserves.CAPACITY_PER_SLOT) {
                choices += EquipmentChoice(ItemStack.EMPTY, -1, false)
            }
            return choices
        }
        val result = mutableListOf<EquipmentChoice>()
        if (!entry.stack.isEmpty) result += EquipmentChoice(entry.stack.copy(), entry.sourceIndex, true)
        (0..35).forEach { index ->
            if (index == entry.sourceIndex) return@forEach
            val stack = player.inventory.getItem(index)
            if (stack.isEmpty || !matchesEquipmentTarget(player, stack, entry.target)) return@forEach
            result += EquipmentChoice(stack.copy(), index, false)
        }
        return result
    }

    private fun matchesEquipmentTarget(player: LocalPlayer, stack: ItemStack, target: String): Boolean = when (target) {
        "weapon_kinetic" -> DestinyWeaponSlot.forStack(stack) == DestinyWeaponSlot.KINETIC
        "weapon_energy" -> DestinyWeaponSlot.forStack(stack) == DestinyWeaponSlot.ENERGY
        "weapon_power" -> DestinyWeaponSlot.forStack(stack) == DestinyWeaponSlot.POWER
        "armor_head" -> stack.item is ArmorItem && player.getEquipmentSlotForItem(stack) == EquipmentSlot.HEAD
        "armor_chest" -> stack.item is ArmorItem && player.getEquipmentSlotForItem(stack) == EquipmentSlot.CHEST
        "armor_legs" -> stack.item is ArmorItem && player.getEquipmentSlotForItem(stack) == EquipmentSlot.LEGS
        "armor_feet" -> stack.item is ArmorItem && player.getEquipmentSlotForItem(stack) == EquipmentSlot.FEET
        "class_item" -> (stack.item as? DestinyClassItem)?.let {
            DestinyHUDState.className.isBlank() || it.requiredClass.displayName == DestinyHUDState.className
        } == true
        else -> false
    }

    private fun showEquipmentChoiceTray(
        ui: UI,
        entry: EquipmentEntry,
        choices: List<EquipmentChoice>,
        alignRight: Boolean,
        onChoose: (EquipmentChoice) -> Unit
    ) {
        val host = element(ui, "equipment_choice_tray") ?: return
        host.clearAllChildren()
        if (choices.isEmpty()) {
            closeEquipmentChoiceTray(ui)
            return
        }
        val count = choices.size
        val columns = count.coerceAtMost(3)
        val rows = ceil(count / columns.toDouble()).toInt()
        val gap = 4
        val availableWidth = if (alignRight) 146 else 138
        val availableHeight = 416
        val size = min(
            48,
            min(
                floor((availableWidth - gap * (columns - 1)) / columns.toDouble()).toInt(),
                floor((availableHeight - gap * (rows - 1)) / rows.toDouble()).toInt()
            )
        ).coerceAtLeast(24)
        val gridWidth = columns * size + (columns - 1) * gap
        val gridHeight = rows * size + (rows - 1) * gap
        val slotIndex = when (entry.target) {
            "weapon_kinetic", "armor_head" -> 0
            "weapon_energy", "armor_chest" -> 1
            "weapon_power", "armor_legs" -> 2
            "armor_feet" -> 3
            "class_item" -> 4
            else -> 0
        }
        val anchorCenterY = if (alignRight) 88 + slotIndex * 64 + 25 else 132 + slotIndex * 68 + 26
        val trayTop = (anchorCenterY - gridHeight / 2).coerceIn(20, 436 - gridHeight)
        val trayLeft = if (alignRight) 766 else 220 - 10 - gridWidth

        host.layout {
            it.positionType(YogaPositionType.ABSOLUTE).left(trayLeft.toFloat()).top(trayTop.toFloat())
                .width(gridWidth.toFloat()).height(gridHeight.toFloat())
        }
        host.style {
            it.background(ColorRectTexture(0x00000000)).opacity(0f).zIndex(40)
                .transform2D(Transform2D().pivot(0f, 0f).translate(if (alignRight) -10f else 10f, 0f))
        }
        host.setDisplay(true)
        host.setVisible(true)
        choices.forEachIndexed { index, choice ->
            val column = index % columns
            val x = if (alignRight) {
                column * (size + gap)
            } else {
                gridWidth - size - column * (size + gap)
            }
            val y = (index / columns) * (size + gap)
            host.addChild(equipmentItemSlot(choice.stack, x, y, size).apply {
                if (choice.equipped) addChild(UIElement().apply {
                    setAllowHitTest(false)
                    layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(0f).widthPercent(100f).heightPercent(100f) }
                    style { it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.PRIMARY)) }
                })
                if (!choice.stack.isEmpty) addEventListener(UIEvents.CLICK) { onChoose(choice) }
            })
        }
        host.animation().duration(0.16f).ease(Eases.CUBIC_OUT)
            .style(PropertyRegistry.OPACITY, 1f)
            .style(PropertyRegistry.TRANSFORM_2D, FloatObjectPair.of(1f, Transform2D.identity()))
            .start()
    }

    private fun closeEquipmentChoiceTray(ui: UI) {
        DestinyPerkTooltipController.clearDirectorHoverStack()
        element(ui, "equipment_choice_tray")?.apply {
            clearAllChildren()
            setVisible(false)
            setDisplay(false)
            style { it.opacity(1f).transform2D(Transform2D.identity()).zIndex(0) }
        }
    }

    private fun equipmentItemSlot(
        stack: ItemStack,
        x: Int,
        y: Int,
        size: Int,
        enableDirectorTooltip: Boolean = true
    ): ItemSlot {
        val definition = GearRegistry.definitionFor(stack)
        val border = when (definition?.rarity) {
            GearRarity.EXOTIC -> DestinyNavigationTemplate.Color.EXOTIC
            GearRarity.LEGENDARY -> DestinyNavigationTemplate.Color.LEGENDARY
            else -> DestinyNavigationTemplate.Color.LINE
        }
        return DestinyEquipmentTooltipSlot().apply {
            setItem(stack.copy())
            if (enableDirectorTooltip) {
                addEventListener(UIEvents.MOUSE_ENTER) {
                    DestinyPerkTooltipController.setDirectorHoverStack(stack)
                }
                addEventListener(UIEvents.MOUSE_LEAVE) {
                    DestinyPerkTooltipController.clearDirectorHoverStack(stack)
                }
            }
            layout {
                it.positionType(YogaPositionType.ABSOLUTE).left(x.toFloat()).top(y.toFloat())
                    .width(size.toFloat()).height(size.toFloat()).aspectRatio(1f)
            }
            style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY)) }
            slotStyle {
                it.showItemTooltips(definition == null).showSlotOverlayOnlyEmpty(false)
                    .slotOverlay(
                        GuiTextureGroup.of(
                            ColorRectTexture(DestinyNavigationTemplate.Color.SLOT),
                            ColorBorderTexture(1, border)
                        )
                    )
            }
        }
    }

    /** Gives the Director a nested back target before it starts its close animation. */
    fun handleBack(ui: UI): Boolean {
        if (DestinyEquipmentDetailView.close(ui)) {
            DestinyPerkTooltipController.clearInteraction()
            return true
        }
        return false
    }

    private fun bindInventory(ui: UI, player: LocalPlayer, onSelect: (ItemStack) -> Unit) {
        val equipment = listOf(
            player.mainHandItem to inventoryMenuSlot(player, player.inventory.selected),
            player.offhandItem to inventoryMenuSlot(player, 40),
            player.getItemBySlot(EquipmentSlot.HEAD) to inventoryMenuSlot(player, 39),
            player.getItemBySlot(EquipmentSlot.CHEST) to inventoryMenuSlot(player, 38),
            player.getItemBySlot(EquipmentSlot.LEGS) to inventoryMenuSlot(player, 37),
            player.getItemBySlot(EquipmentSlot.FEET) to inventoryMenuSlot(player, 36),
            classItem(player)
        )
        populateSlots(element(ui, "equipment_slots"), equipment, 48, 8, onSelect)
        text(ui, "equipment_count")?.setText("${equipment.count { !it.first.isEmpty }} / ${equipment.size}")

        val main = (9..35).map { index -> player.inventory.getItem(index) to inventoryMenuSlot(player, index) }
        val hotbar = (0..8).map { index -> player.inventory.getItem(index) to inventoryMenuSlot(player, index) }
        populateSlots(element(ui, "inventory_main_slots"), main, 42, 5, onSelect)
        populateSlots(element(ui, "inventory_hotbar_slots"), hotbar, 42, 5, onSelect)
        text(ui, "inventory_count")?.setText("${player.inventory.items.count { !it.isEmpty }} / 36")
    }

    private fun populateSlots(
        host: UIElement?,
        entries: List<Pair<ItemStack, Int>>,
        size: Int,
        gap: Int,
        onSelect: (ItemStack) -> Unit
    ) {
        host ?: return
        host.clearAllChildren()
        entries.forEachIndexed { index, (stack, _) ->
            val x = (index % 9) * (size + gap)
            val y = (index / 9) * (size + gap)
            val slot = itemSlot(stack, x, y, size)
            if (!stack.isEmpty) {
                slot.addEventListener(UIEvents.CLICK) { onSelect(stack) }
            }
            host.addChild(slot)
        }
    }

    private fun populatePreviewSlots(host: UIElement?, stacks: List<ItemStack>, size: Int, gap: Int) {
        host ?: return
        host.clearAllChildren()
        stacks.forEachIndexed { index, stack -> host.addChild(itemSlot(stack, (index % 9) * (size + gap), (index / 9) * (size + gap), size)) }
    }

    private fun itemSlot(stack: ItemStack, x: Int, y: Int, size: Int): ItemSlot = ItemSlot().apply {
        setItem(stack.copy())
        layout {
            it.positionType(YogaPositionType.ABSOLUTE).left(x.toFloat()).top(y.toFloat())
                .width(size.toFloat()).height(size.toFloat()).aspectRatio(1f)
        }
        style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY)) }
        slotStyle {
            it.showItemTooltips(GearRegistry.definitionFor(stack) == null).showSlotOverlayOnlyEmpty(false)
                .slotOverlay(GuiTextureGroup.of(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT), ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE)))
        }
    }

    private fun updateInspector(ui: UI, stack: ItemStack) {
        val iconHost = element(ui, "selected_item_icon") ?: return
        val perkHost = element(ui, "selected_perk_slots") ?: return
        iconHost.clearAllChildren()
        perkHost.clearAllChildren()
        if (stack.isEmpty) {
            text(ui, "selected_item_name")?.apply {
                setText("选择一件物品")
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(14f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
            }
            text(ui, "selected_item_meta")?.setText("查看装备与特性")
            text(ui, "selected_item_stats")?.setText("")
            setPerkDetail(ui, "未选择特性", "选择装备后点击上方特性图标。", "装备详情")
            return
        }
        iconHost.addChild(itemSlot(stack, 0, 0, 64).apply { setAllowHitTest(false) })
        val gear = PerkTooltipDataAdapter.from(stack)
        if (gear == null) {
            text(ui, "selected_item_name")?.apply {
                setText(stack.hoverName)
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(14f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
            }
            text(ui, "selected_item_meta")?.setText(stack.item.description.string)
            text(ui, "selected_item_stats")?.setText("${stack.count}  //  Minecraft item")
            setPerkDetail(ui, stack.hoverName.string, "此物品没有 Destiny Perk 数据，保留原版物品信息。", "物品信息")
            return
        }
        bindGearInspector(ui, gear, perkHost)
    }

    private fun updateEquipmentInspector(ui: UI, stack: ItemStack) {
        val iconHost = element(ui, "equipment_selected_icon") ?: return
        val perkHost = element(ui, "equipment_selected_perks") ?: return
        iconHost.clearAllChildren()
        perkHost.clearAllChildren()
        if (stack.isEmpty) {
            text(ui, "equipment_selected_name")?.setText("选择一件装备")
            text(ui, "equipment_selected_meta")?.setText("查看装备、光等与当前特性")
            text(ui, "equipment_selected_stats")?.setText("")
            return
        }

        iconHost.addChild(itemSlot(stack, 0, 0, 52).apply { setAllowHitTest(false) })
        val gear = PerkTooltipDataAdapter.from(stack)
        if (gear == null) {
            text(ui, "equipment_selected_name")?.setText(stack.hoverName)
            text(ui, "equipment_selected_meta")?.setText(stack.item.description.string)
            text(ui, "equipment_selected_stats")?.setText("Minecraft 装备  //  数量 ${stack.count}")
            return
        }

        val rarityColor = if (gear.rarity == GearRarity.EXOTIC) {
            DestinyNavigationTemplate.Color.EXOTIC
        } else {
            DestinyNavigationTemplate.Color.LEGENDARY
        }
        text(ui, "equipment_selected_name")?.apply {
            setText(gear.name)
            textStyle {
                it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(12f)
                    .textColor(rarityColor).textShadow(false).textWrap(TextWrap.WRAP)
            }
        }
        text(ui, "equipment_selected_meta")?.setText(
            listOf(gear.rarity.displayName, gear.frameName, gear.elementName)
                .filterNotNull().filter(String::isNotBlank).joinToString("  //  ")
        )
        text(ui, "equipment_selected_stats")?.setText(buildList {
            gear.power?.let { add("光等  $it") }
            gear.damage?.let { add("伤害  ${"%.2f".format(it)}") }
            gear.fireRate?.let { add("射速  ${"%.2f".format(it)}/秒") }
            gear.armorStats?.let { add("护甲等级  ${gear.armorTier}") }
        }.joinToString("\n"))

        (gear.perks + gear.catalysts.filter { it.id == gear.equippedCatalystId })
            .take(6)
            .forEachIndexed { index, perk ->
                perkHost.addChild(perkSocket(perk, index * 48) {
                    text(ui, "equipment_selected_meta")?.setText("特性  //  ${perk.name}")
                }.apply {
                    layout { it.left((index * 48).toFloat()).width(42f).height(42f) }
                })
            }
    }

    private fun bindGearInspector(ui: UI, gear: PerkTooltipData, perkHost: UIElement) {
        val rarityColor = if (gear.rarity == GearRarity.EXOTIC) DestinyNavigationTemplate.Color.EXOTIC else DestinyNavigationTemplate.Color.LEGENDARY
        text(ui, "selected_item_name")?.apply {
            setText(gear.name)
            textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(14f).textColor(rarityColor).textShadow(false).textWrap(TextWrap.WRAP) }
        }
        val meta = listOf(gear.rarity.displayName, gear.category.displayName, gear.frameName, gear.elementName).filterNotNull().filter(String::isNotBlank)
        text(ui, "selected_item_meta")?.setText(meta.joinToString("  //  "))
        val stats = buildList {
            gear.power?.let { add("威能  $it") }
            gear.damage?.let { add("伤害  ${"%.2f".format(it)}") }
            gear.fireRate?.let { add("射速  ${"%.2f".format(it)}/秒") }
            if (gear.armorStats != null) add("护甲等级  ${gear.armorTier}")
        }
        text(ui, "selected_item_stats")?.setText(stats.joinToString("    "))
        val perks = gear.perks + gear.catalysts.filter { it.id == gear.equippedCatalystId }
        fun selectPerk(perk: PerkTooltipEntry) = setPerkDetail(
            ui,
            perk.name,
            perk.description,
            perk.columnName ?: "特性"
        )
        perks.take(5).forEachIndexed { index, perk ->
            perkHost.addChild(perkSocket(perk, index * 52) { selectPerk(perk) })
        }
        if (gear.hasCatalystSlot && perks.size < 5) {
            perkHost.addChild(emptyCatalystSocket(perks.size * 52) {
                setPerkDetail(ui, "未安装催化剂", "该装备预留了催化剂槽，当前尚未安装催化剂。", "催化剂")
            })
        }
        if (perks.isEmpty()) setPerkDetail(ui, "暂无特性", "该装备尚未生成可用特性。", "特性")
        else selectPerk(perks.first())
    }

    private fun setPerkDetail(ui: UI, name: String, details: String, column: String) {
        text(ui, "selected_perk_column")?.setText(column)
        text(ui, "selected_perk_name")?.setText(name)
        text(ui, "selected_perk_details")?.setText(details)
    }

    private fun perkSocket(perk: PerkTooltipEntry, x: Int, onSelect: () -> Unit): UIElement {
        val resource = Minecraft.getInstance().resourceManager.getResource(perk.icon).isPresent
        return UIElement().apply {
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x.toFloat()).top(0f).width(44f).height(44f).aspectRatio(1f) }
            style {
                it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT))
                it.overlay(
                    if (resource) GuiTextureGroup.of(SpriteTexture.of(perk.icon), ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT))
                    else ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT)
                )
            }
            addEventListener(UIEvents.CLICK) { onSelect() }
            if (!resource) addChild(TextElement().apply {
                setText(perk.name.take(1).uppercase())
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(16f).top(15f).width(14f).height(14f) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(10f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
            })
        }
    }

    private fun emptyCatalystSocket(x: Int, onSelect: () -> Unit): UIElement = UIElement().apply {
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(x.toFloat()).top(0f).width(44f).height(44f).aspectRatio(1f) }
        style {
            it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY))
            it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.EXOTIC))
        }
        addEventListener(UIEvents.CLICK) { onSelect() }
    }

    private fun bindJourney(ui: UI, data: DestinyNavigationData) {
        val stage = GuardianJourneyStage.fromId(DestinyNavigationState.journeyStageId)
        text(ui, "journey_stage")?.setText("阶段  //  ${stage.displayName}  //  ${stage.id.uppercase()}")
        text(ui, "journey_objective_title")?.setText(data.objectiveTitle)
        text(ui, "journey_objective_description")?.setText(data.objectiveDescription)
        text(ui, "journey_location")?.setText(data.location)
        text(ui, "journey_coordinates")?.setText(data.coordinates)
        text(ui, "journey_status")?.setText(
            if (data.powerDeficit > 0) {
                "装备光等  ${data.power}\n最高可用  ${data.highestAvailablePower}\n活动推荐  ${data.recommendedPower}\n\n光等压制  -${data.suppressionPercent}% 伤害\n承受伤害提高 ${data.suppressionPercent}%"
            } else if (data.recommendedPower > 0) {
                "装备光等  ${data.power}\n最高可用  ${data.highestAvailablePower}\n活动推荐  ${data.recommendedPower}\n\n已达到当前活动要求\n阶段奖励  ${stage.dropFloor}–${stage.rewardCap}"
            } else {
                "尚未被光能唤醒\n当前阶段不启用光等压制"
            }
        )
        val host = element(ui, "journey_objective_rows") ?: return
        host.clearAllChildren()
        data.objectiveItems.forEachIndexed { index, item ->
            host.addChild(TextElement().apply {
                setText("${if (item.complete) "◆" else "◇"}  ${item.name}                                      ${item.count}")
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(index * 25f).width(500f).height(18f) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(10f).textColor(if (item.complete) DestinyNavigationTemplate.Color.PRIMARY else DestinyNavigationTemplate.Color.MUTED).textShadow(false) }
            })
        }
    }

    private fun bindFireteam(ui: UI, data: DestinyNavigationData) {
        text(ui, "fireteam_count")?.setText("${data.fireteam.size} ONLINE")
        text(ui, "fireteam_identity")?.setText("${data.guardianName}\n${data.className}  //  ${data.subclassName}")
        val host = element(ui, "fireteam_rows") ?: return
        host.clearAllChildren()
        data.fireteam.forEachIndexed { index, member ->
            val row = UIElement().apply {
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(index * 44f).width(522f).height(36f) }
                style { it.background(ColorRectTexture(if (member.local) DestinyNavigationTemplate.Color.SELECTED else DestinyNavigationTemplate.Color.SLOT)) }
            }
            row.addChild(TextElement().apply {
                setText("${if (member.local) "◆" else "◇"}  ${member.name}                                               ${member.latency} ms")
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(12f).top(11f).width(498f).height(16f) }
                textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(10f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
            })
            host.addChild(row)
        }
    }

    private fun installResponsiveCanvas(ui: UI): UIElement {
        val root = ui.rootElement
        root.setVisible(true)
        root.layout {
            it.positionType(YogaPositionType.RELATIVE)
                .widthPercent(100f).heightPercent(100f).paddingAll(0f).gapAll(0f)
        }
        root.selectId("responsive_director").findFirst().orElse(null)?.let { return it }
        val foreground = UIElement().apply {
            setId("responsive_director")
            layout {
                it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(0f)
                    .width(DestinyNavigationTemplate.DESIGN_WIDTH).height(DestinyNavigationTemplate.DESIGN_HEIGHT)
            }
            style { it.transform2D(Transform2D.identity()) }
        }
        foreground.setVisible(true)
        val authored = root.children.toList()
        authored.forEach(root::removeChild)
        authored.forEach(foreground::addChild)
        root.addChild(foreground)
        var lastWidth = -1f
        var lastHeight = -1f
        var loggedLayout = false
        root.addEventListener(UIEvents.TICK) {
            val width = root.contentWidth
            val height = root.contentHeight
            if (width <= 0f || height <= 0f || (width == lastWidth && height == lastHeight)) return@addEventListener
            lastWidth = width
            lastHeight = height
            val scale = min(width / DestinyNavigationTemplate.DESIGN_WIDTH, height / DestinyNavigationTemplate.DESIGN_HEIGHT).coerceAtLeast(0.01f)
            val left = ((width - DestinyNavigationTemplate.DESIGN_WIDTH * scale) / 2f).roundToInt().toFloat()
            val top = ((height - DestinyNavigationTemplate.DESIGN_HEIGHT * scale) / 2f).roundToInt().toFloat()
            foreground.layout {
                it.positionType(YogaPositionType.ABSOLUTE).left(left).top(top)
                    .width(DestinyNavigationTemplate.DESIGN_WIDTH).height(DestinyNavigationTemplate.DESIGN_HEIGHT)
            }
            foreground.style { it.transform2D(Transform2D().pivot(0f, 0f).scale(scale)) }
            if (!loggedLayout) {
                loggedLayout = true
                val canvas = root.selectId("director_canvas").findFirst().orElse(null)
                val header = root.selectId("director_header").findFirst().orElse(null)
                val page = root.selectId("equipment_page").findFirst().orElse(null)
                logger.info(
                    "Director layout root={}x{}, foreground={}x{} @ {},{}, canvas={}x{}, header={}x{}, equipment={}x{} displayed={}",
                    width, height, foreground.sizeWidth, foreground.sizeHeight, foreground.positionX, foreground.positionY,
                    canvas?.sizeWidth, canvas?.sizeHeight, header?.sizeWidth, header?.sizeHeight,
                    page?.sizeWidth, page?.sizeHeight, page?.isDisplayed
                )
            }
        }
        return foreground
    }

    /**
     * Migrates legacy v10 templates to a fixed clipping viewport at runtime.
     * New/reset templates already contain this editable node, while existing
     * user templates keep all authored page contents and positions.
     */
    private fun installPageViewport(ui: UI): UIElement? {
        val canvas = element(ui, "director_canvas") ?: return null
        val viewport = element(ui, "director_page_viewport") ?: UIElement().apply {
            setId("director_page_viewport")
            layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(0f).top(54f).width(960f).height(456f)
            }
            canvas.addChild(this)
        }
        viewport.setOverflow(YogaOverflow.HIDDEN)

        listOf("equipment_page", "inventory_page", "collections_page", "journey_page", "fireteam_page")
            .mapNotNull { element(ui, it) }
            .forEach { page ->
                if (page.parent !== viewport) {
                    page.parent?.removeChild(page)
                    viewport.addChild(page)
                }
                page.layout {
                    it.positionType(YogaPositionType.ABSOLUTE)
                        .left(0f).top(0f).width(960f).height(456f)
                }
            }
        return viewport
    }

    private fun normalizeAuthoredComposition(ui: UI) {
        listOf("director_canvas", "director_header", "equipment_page", "inventory_page", "collections_page", "journey_page", "fireteam_page", "director_footer")
            .mapNotNull { element(ui, it) }
            .forEach { node ->
                node.style { style ->
                    style.opacity(1f).transform2D(Transform2D.identity())
                }
            }
    }

    private fun classItem(player: LocalPlayer): Pair<ItemStack, Int> {
        val slot = player.inventoryMenu.slots.firstOrNull { it is DestinyClassItemSlot }
        return (slot?.item?.copy() ?: ItemStack.EMPTY) to player.inventoryMenu.slots.indexOf(slot)
    }

    private fun inventoryMenuSlot(player: LocalPlayer, inventoryIndex: Int): Int =
        player.inventoryMenu.slots.indexOfFirst { slot -> slot.container === player.inventory && slot.containerSlot == inventoryIndex }

    private fun element(ui: UI, id: String): UIElement? = ui.rootElement.selectId(id).findFirst().orElse(null)
    private fun text(ui: UI, id: String): TextElement? = ui.rootElement.selectId(id, TextElement::class.java).findFirst().orElse(null)
    private fun button(ui: UI, id: String): Button? = ui.rootElement.selectId(id, Button::class.java).findFirst().orElse(null)
}
