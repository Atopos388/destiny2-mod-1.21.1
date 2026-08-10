package atopos.destiny2.client.gui

import atopos.destiny2.client.gear.DestinyPerkTooltipTemplate
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyConfigOption
import atopos.destiny2.common.player.DestinySubclassConfigDefinition
import atopos.destiny2.common.player.DestinySubclassConfigRegistry
import atopos.destiny2.common.player.DestinySubclassType
import com.lowdragmc.lowdraglib2.LDLib2
import com.lowdragmc.lowdraglib2.editor.resource.FilePath
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider
import com.lowdragmc.lowdraglib2.editor.resource.UIResource
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.math.interpolate.Eases
import com.lowdragmc.lowdraglib2.utils.XmlUtils
import it.unimi.dsi.fastutil.floats.FloatObjectPair
import org.appliedenergistics.yoga.YogaFlexDirection
import org.appliedenergistics.yoga.YogaJustify
import org.appliedenergistics.yoga.YogaPositionType
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Client UI for configuring the two Aspect slots of the active subclass. */
object DestinyAspectScreen {
    private const val DESIGN_WIDTH = 960f
    private const val DESIGN_HEIGHT = 540f
    private const val AUTHORED_FRAGMENT_SLOTS = 4
    private const val FRAGMENT_SLOT_SIZE = 48f
    private const val FRAGMENT_SLOT_GAP = 4f
    private const val FRAGMENT_SLOT_LEFT = 16f
    private const val FRAGMENT_SLOT_TOP = 30f
    private const val FRAGMENT_PANEL_SIDE_PADDING = 16f
    private const val OPTION_TRAY_ANCHOR_GAP = 24f
    private const val OPTION_TRAY_DETAIL_HEIGHT = 96f
    private val glassPanel = 0xB80A0908.toInt()
    private val softGlassPanel = 0x78000000
    private val optionBase = 0xB812100E.toInt()
    private val optionHover = 0xD826211C.toInt()
    private val optionPressed = 0xC034281F.toInt()
    private val subtleBorder = 0x668E8A84
    private val brightBorder = 0xDDE9E4DB.toInt()
    private val solarBackground = ResourceLocation.parse("destiny2-mod:textures/gui/subclass/solar_warlock/dawnblade_background.png")
    private val bundledAspectTemplate = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "ui/aspect_screen.ui.nbt")
    private val logger = LoggerFactory.getLogger("destiny2-mod/aspect-screen")
    private var openRequested = false
    private var returnToEquipmentRequested = false
    private var editorProviderRegistered = false

    fun requestOpen(returnToEquipment: Boolean = false) {
        openRequested = true
        returnToEquipmentRequested = returnToEquipment
    }

    fun openIfRequested(minecraft: Minecraft) {
        if (!openRequested) return
        openRequested = false
        val returnToEquipment = returnToEquipmentRequested
        returnToEquipmentRequested = false
        val player = minecraft.player ?: return
        try {
            minecraft.setScreen(createScreen(player, returnToEquipment))
        } catch (error: Throwable) {
            logger.error("Failed to open the subclass configuration screen", error)
            val reason = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            player.displayClientMessage(Component.literal("星象配置界面打开失败：$reason"), false)
        }
    }

    private fun createScreen(
        player: net.minecraft.client.player.LocalPlayer,
        returnToEquipment: Boolean
    ): ModularUIScreen {
        val subclass = DestinySubclassType.entries.firstOrNull { it.displayName == DestinyHUDState.subclassName }
            ?: DestinySubclassType.DEFAULT
        val definition = DestinySubclassConfigRegistry.definitionFor(subclass)
        val ui = loadVisualLayout()
        val root = ui.rootElement
        applySubclassBackground(root, definition)
        val foreground = installResponsiveForeground(ui)
        hideAuthoredDecoration(ui)
        alignAbilitySectionWithFragments(ui)
        val fragmentPanel = bindFragmentPanel(ui, definition) ?: installFragmentPanel(foreground, definition)
        val accent = subclassAccent(definition.subclass)
        val optionTray = bindOptionTray(ui, accent) ?: installOptionTray(foreground, accent)
        bindSubclassOptionButtons(ui, optionTray, definition, sendChanges = true)
        val slotOne = text(ui, "aspect_slot_1")
        val slotTwo = text(ui, "aspect_slot_2")
        val fragmentCapacity = text(ui, "fragment_capacity")
        definition.aspectOptions.take(2).forEachIndexed { index, option ->
            text(ui, "aspect_detail_$index")?.setText("${option.title}\n${option.description}\n碎片槽 +${option.fragmentSlots}")
            val button = ui.selectId("aspect_option_$index", Button::class.java).findFirst().orElse(null) ?: return@forEachIndexed
            button.setOnClick { ClientPlayNetworking.send(DestinyNetworking.ConfigureSubclassPayload("aspect", "", option.id)) }
            button.addEventListener(UIEvents.TICK) {
                button.setText(option.title + if (DestinyHUDState.selectedAspectIds.contains(option.id)) "  [已装备]" else "")
            }
        }

        var stateKey = ""
        root.addEventListener(UIEvents.TICK) {
            val selected = DestinyHUDState.selectedAspectIds
            val selectedFragments = DestinyHUDState.selectedFragmentIds
            val key = selected.joinToString(",") + "|" + selectedFragments.joinToString(",")
            if (key == stateKey) return@addEventListener
            stateKey = key
            slotOne?.setText(slotText(1, selected.getOrNull(0), definition.aspectOptions))
            slotTwo?.setText(slotText(2, selected.getOrNull(1), definition.aspectOptions))
            val capacity = fragmentCapacity(selected, definition)
            fragmentPanel.update(capacity, selectedFragments)
            val maximumCapacity = DestinySubclassConfigRegistry.maximumFragmentCapacity(definition)
            fragmentCapacity?.setText("碎片槽：$capacity / $maximumCapacity")
        }

        return AspectModularScreen(
            ModularUI.of(ui, player).shouldCloseOnEsc(false),
            returnToEquipment
        )
    }

    private fun text(ui: UI, id: String): TextElement? = ui.rootElement.selectId(id, TextElement::class.java).findFirst().orElse(null)

    private class AspectModularScreen(
        modularUI: ModularUI,
        private val returnToEquipment: Boolean
    ) : ModularUIScreen(modularUI, Component.literal("Destiny Aspect Configuration")) {
        private var navigatingBack = false

        override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                navigateBack()
                return true
            }
            return super.keyPressed(keyCode, scanCode, modifiers)
        }

        override fun onClose() = navigateBack()

        private fun navigateBack() {
            if (navigatingBack) return
            navigatingBack = true
            val client = Minecraft.getInstance()
            val player = client.player
            client.setScreen(
                if (returnToEquipment && player != null) DestinyDirectorScreen.create(player) else null
            )
        }
    }

    private fun hideAuthoredDecoration(ui: UI) {
        listOf("subclass_title", "subclass_subtitle", "footer_hint", "super_label", "super_divider")
            .forEach { id ->
                ui.rootElement.selectId(id).findFirst().orElse(null)?.apply {
                    setDisplay(false)
                    setVisible(false)
                    setActive(false)
                }
            }
    }

    private fun alignAbilitySectionWithFragments(ui: UI) {
        ui.rootElement.selectId("ability_label").findFirst().orElse(null)?.layout { it.leftPercent(34f) }
        ui.rootElement.selectId("ability_divider").findFirst().orElse(null)?.layout { it.leftPercent(34f) }
        (0 until 4).forEach { index ->
            button(ui, "ability_slot_$index")?.layout { it.leftPercent(34f + index * 7f) }
        }
    }

    private fun applySubclassBackground(root: UIElement, definition: DestinySubclassConfigDefinition) {
        definition.background?.let { texture ->
            root.style { it.background(SpriteTexture.of(texture)) }
        }
    }

    /**
     * Keeps the authored 960x540 composition intact and scales it as one unit.
     * The root background deliberately stays outside this canvas so it continues
     * to cover every window aspect ratio without affecting foreground spacing.
     */
    private fun installResponsiveForeground(ui: UI): UIElement {
        val root = ui.rootElement
        // A migrated editor template used to leave the live root at a fixed
        // 680 px height. ModularUIScreen then centred that oversized root and
        // clipped its upper edge. The live root must always be the viewport;
        // only the authored foreground canvas is scaled and centred inside it.
        root.layout {
            it.widthPercent(100f).heightPercent(100f).paddingAll(0f).gapAll(0f)
        }
        root.selectId("responsive_foreground").findFirst().orElse(null)?.let { return it }

        val foreground = UIElement().apply {
            setId("responsive_foreground")
            layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(0f).top(0f)
                    .width(DESIGN_WIDTH).height(DESIGN_HEIGHT)
            }
            style { it.transform2D(Transform2D.identity()) }
        }

        val authoredElements = root.children.toList()
        authoredElements.forEach { root.removeChild(it) }
        authoredElements.forEach(foreground::addChild)
        root.addChild(foreground)

        var lastWidth = -1f
        var lastHeight = -1f
        root.addEventListener(UIEvents.TICK) {
            val width = root.contentWidth
            val height = root.contentHeight
            if (width <= 0f || height <= 0f || (width == lastWidth && height == lastHeight)) {
                return@addEventListener
            }
            lastWidth = width
            lastHeight = height
            val scale = kotlin.math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT).coerceAtLeast(0.01f)
            val left = (width - DESIGN_WIDTH * scale) / 2f
            val top = (height - DESIGN_HEIGHT * scale) / 2f
            foreground.layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(left).top(top)
                    .width(DESIGN_WIDTH).height(DESIGN_HEIGHT)
            }
            foreground.style {
                it.transform2D(Transform2D().pivot(0f, 0f).scale(scale))
            }
        }
        return foreground
    }

    private fun bindFragmentPanel(ui: UI, definition: DestinySubclassConfigDefinition): FragmentPanelBinding? {
        val capacity = text(ui, "fragment_live_capacity") ?: return null
        val description = text(ui, "fragment_description") ?: return null
        val accent = subclassAccent(definition.subclass)
        val panel = ui.rootElement.selectId("fragment_panel").findFirst().orElse(null) ?: return null
        placeFragmentDescriptionBelowPanel(description, panel)
        panel.style {
            it.background(ColorRectTexture(softGlassPanel))
            it.overlay(ColorBorderTexture(1, subtleBorder))
        }
        ui.rootElement.selectId("fragment_divider").findFirst().orElse(null)?.style {
            it.background(ColorRectTexture(accent))
        }
        val templateSlots = (0 until AUTHORED_FRAGMENT_SLOTS).mapNotNull { index ->
            ui.rootElement.selectId("fragment_slot_$index", Button::class.java).findFirst().orElse(null)
        }
        // In new_res.ui.nbt the authored slots are direct children of the
        // fragment panel. Their parent is not a row container; changing its
        // layout here would resize and relocate the entire glass panel.
        val slotContainer = templateSlots.firstOrNull()?.parent ?: return null
        val maximumSlots = DestinySubclassConfigRegistry.maximumFragmentCapacity(definition)
        val slots = (0 until maximumSlots).map { index ->
            ui.rootElement.selectId("fragment_slot_$index", Button::class.java).findFirst().orElse(null)
                ?: createRuntimeFragmentSlot(index).also(slotContainer::addChild)
        }
        templateSlots.drop(maximumSlots).forEach { slot ->
            slot.setVisible(false)
            slot.setDisplay(false)
        }
        val choices = (0 until AUTHORED_FRAGMENT_SLOTS).map { index ->
            ui.rootElement.selectId("fragment_choice_$index", Button::class.java).findFirst().orElse(null)
        }
        // Authored NBT slots retain their old flex distribution even after the
        // row is switched to FLEX_START. Pin only the slot children so the
        // template's panel anchor and dimensions remain completely untouched.
        slots.forEachIndexed { index, slot ->
            slot.layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(FRAGMENT_SLOT_LEFT + index * (FRAGMENT_SLOT_SIZE + FRAGMENT_SLOT_GAP))
                    .top(FRAGMENT_SLOT_TOP)
                    .width(FRAGMENT_SLOT_SIZE).height(FRAGMENT_SLOT_SIZE).aspectRatio(1f)
            }
        }
        return FragmentPanelBinding(panel, capacity, slots, choices.filterNotNull(), description, definition, accent)
    }

    private fun createRuntimeFragmentSlot(index: Int): Button {
        return Button().apply {
            setId("fragment_slot_$index")
            setText("")
            layout { it.widthAuto().heightPercent(48f).aspectRatio(1f) }
            buttonStyle {
                it.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), ColorBorderTexture(1, subtleBorder)))
                it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), ColorBorderTexture(2, brightBorder)))
                it.pressedTexture(ColorRectTexture(optionPressed))
            }
        }
    }

    private fun button(ui: UI, id: String): Button? =
        ui.rootElement.selectId(id, Button::class.java).findFirst().orElse(null)

    private fun subclassAccent(subclass: DestinySubclassType): Int = when (subclass) {
        DestinySubclassType.SOLAR_WARLOCK -> 0xFFFFB45A.toInt()
        DestinySubclassType.VOID_HUNTER -> 0xFFC4A7FF.toInt()
        DestinySubclassType.ARC_TITAN -> 0xFF79D8F2.toInt()
    }

    private fun applyOptionVisual(
        button: Button,
        option: DestinyConfigOption?,
        fallbackText: String,
        selected: Boolean = false,
        accent: Int = brightBorder
    ) {
        val icon = option?.icon
        val baseBorder = ColorBorderTexture(if (selected) 2 else 1, if (selected) accent else subtleBorder)
        val hoverBorder = ColorBorderTexture(2, if (selected) brightBorder else accent)
        if (icon == null) {
            button.setText(fallbackText)
            button.buttonStyle {
                it.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), baseBorder))
                it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), hoverBorder))
                it.pressedTexture(GuiTextureGroup.of(ColorRectTexture(optionPressed), ColorBorderTexture(2, accent)))
            }
            return
        }

        button.setText("")
        val sprite = SpriteTexture.of(icon)
        button.buttonStyle {
            it.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), sprite, baseBorder))
            it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), sprite.copy(), hoverBorder))
            it.pressedTexture(GuiTextureGroup.of(ColorRectTexture(optionPressed), sprite.copy(), ColorBorderTexture(2, accent)))
        }
        button.style { it.tooltips(option.title, option.description) }
    }

    /**
     * Popup choices keep their full-size hit box while rendering the artwork on
     * a smaller, centred child layer. This avoids shrinking the choice button
     * itself (and therefore its click target) just to reduce the icon size.
     */
    private fun applyInsetOptionVisual(
        button: Button,
        option: DestinyConfigOption?,
        fallbackText: String,
        selected: Boolean = false,
        accent: Int = brightBorder,
        iconPercent: Float = 62f
    ) {
        val baseBorder = ColorBorderTexture(if (selected) 2 else 1, if (selected) accent else subtleBorder)
        val hoverBorder = ColorBorderTexture(2, if (selected) brightBorder else accent)
        button.buttonStyle {
            it.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), baseBorder))
            it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), hoverBorder))
            it.pressedTexture(GuiTextureGroup.of(ColorRectTexture(optionPressed), ColorBorderTexture(2, accent)))
        }

        val icon = option?.icon
        val existingIcon = button.selectId("option_inset_icon").findFirst().orElse(null)
        if (icon == null) {
            button.setText(fallbackText)
            existingIcon?.setDisplay(false)
            existingIcon?.setVisible(false)
            return
        }

        button.setText("")
        val inset = iconPercent.coerceIn(24f, 100f)
        val offset = (100f - inset) / 2f
        val iconLayer = existingIcon ?: UIElement().apply {
            setId("option_inset_icon")
            setActive(false)
            button.addChild(this)
        }
        iconLayer.layout {
            it.positionType(YogaPositionType.ABSOLUTE)
                .leftPercent(offset).topPercent(offset)
                .widthPercent(inset).heightPercent(inset)
                .aspectRatio(1f)
        }
        iconLayer.style { it.background(SpriteTexture.of(icon)) }
        iconLayer.setDisplay(true)
        iconLayer.setVisible(true)
        // Popup choices use the persistent detail card below the grid.  A
        // second vanilla tooltip here obscures neighbouring choices and makes
        // long Aspect descriptions much harder to scan.
    }

    /** Keeps the square super artwork upright inside the rotated diamond button. */
    private fun applySuperVisual(
        button: Button,
        option: DestinyConfigOption?,
        fallbackText: String,
        selected: Boolean,
        accent: Int
    ) {
        val baseBorder = ColorBorderTexture(if (selected) 2 else 1, if (selected) accent else subtleBorder)
        val hoverBorder = ColorBorderTexture(2, if (selected) brightBorder else accent)
        button.buttonStyle {
            it.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), baseBorder))
            it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), hoverBorder))
            it.pressedTexture(GuiTextureGroup.of(ColorRectTexture(optionPressed), ColorBorderTexture(2, accent)))
        }

        val icon = option?.icon
        val existingIcon = button.selectId("super_icon_visual").findFirst().orElse(null)
        if (icon == null) {
            button.setText(fallbackText)
            existingIcon?.setDisplay(false)
            existingIcon?.setVisible(false)
            return
        }

        button.setText("")
        val iconLayer = existingIcon ?: UIElement().apply {
            setId("super_icon_visual")
            setActive(false)
            layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .leftPercent(16f).topPercent(16f)
                    .widthPercent(68f).heightPercent(68f)
                    .aspectRatio(1f)
            }
            button.addChild(this)
        }
        val buttonRotation = button.style.transform2D().rotation()
        iconLayer.style {
            it.background(SpriteTexture.of(icon))
            it.transform2D(Transform2D().pivot(0.5f, 0.5f).rotation(-buttonRotation))
            it.zIndex(2)
        }
        iconLayer.setDisplay(true)
        iconLayer.setVisible(true)
        button.style { it.tooltips(option.title, option.description) }
    }

    private fun bindOptionTray(ui: UI, accent: Int): OptionTrayBinding? {
        val tray = ui.rootElement.selectId("option_tray").findFirst().orElse(null) ?: return null
        val title = text(ui, "option_tray_title") ?: return null
        val description = text(ui, "option_tray_description") ?: return null
        val (detailTitle, detailMeta, detailDivider) = ensureOptionTrayDetailNodes(tray)
        tray.style {
            it.background(ColorRectTexture(glassPanel))
            it.overlay(ColorBorderTexture(1, subtleBorder))
            it.zIndex(50)
        }
        tray.selectId("option_tray_accent").findFirst().orElse(null)?.let { accentLine ->
            accentLine.parent?.removeChild(accentLine)
        }
        val choices = (0 until 4).mapNotNull { index -> button(ui, "option_choice_$index") }.toMutableList()
        return OptionTrayBinding(
            tray,
            title,
            detailTitle,
            detailMeta,
            description,
            detailDivider,
            choices,
            accent
        ).also(OptionTrayBinding::close)
    }

    private fun installOptionTray(root: UIElement, accent: Int): OptionTrayBinding {
        val tray = createOptionTray(accent)
        root.addChild(tray)
        val title = tray.selectId("option_tray_title", TextElement::class.java).findFirst().orElseThrow()
        val description = tray.selectId("option_tray_description", TextElement::class.java).findFirst().orElseThrow()
        val (detailTitle, detailMeta, detailDivider) = ensureOptionTrayDetailNodes(tray)
        val choices = mutableListOf<Button>()
        return OptionTrayBinding(
            tray,
            title,
            detailTitle,
            detailMeta,
            description,
            detailDivider,
            choices,
            accent
        ).also(OptionTrayBinding::close)
    }

    private data class OptionTrayDetailNodes(
        val title: TextElement,
        val meta: TextElement,
        val divider: UIElement
    )

    /**
     * Adds the structured detail-card anchors to old creator templates once.
     * Runtime layout still sizes them from the current option count.
     */
    private fun ensureOptionTrayDetailNodes(tray: UIElement): OptionTrayDetailNodes {
        val detailTitle = tray.selectId("option_tray_detail_title", TextElement::class.java).findFirst().orElse(null)
            ?: TextElement().apply {
                setId("option_tray_detail_title")
                setText("选项名称")
                textStyle {
                    it.font(DestinyNavigationTemplate.DIRECTOR_FONT)
                        .fontSize(11f)
                        .textColor(0xFFF4F1EA.toInt())
                        .textShadow(false)
                }
                tray.addChild(this)
            }
        val detailMeta = tray.selectId("option_tray_detail_meta", TextElement::class.java).findFirst().orElse(null)
            ?: TextElement().apply {
                setId("option_tray_detail_meta")
                setText("类型")
                textStyle {
                    it.font(DestinyNavigationTemplate.DIRECTOR_FONT)
                        .fontSize(8f)
                        .textColor(0xFFB9B3AA.toInt())
                        .textShadow(false)
                }
                tray.addChild(this)
            }
        val divider = tray.selectId("option_tray_detail_divider").findFirst().orElse(null)
            ?: UIElement().apply {
                setId("option_tray_detail_divider")
                setAllowHitTest(false)
                style { it.background(ColorRectTexture(subtleBorder)) }
                tray.addChild(this)
            }
        return OptionTrayDetailNodes(detailTitle, detailMeta, divider)
    }

    private fun createOptionTray(accent: Int): UIElement {
        val tray = UIElement().apply {
            setId("option_tray")
            layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .leftPercent(37f).topPercent(35f)
                    .widthPercent(23.54f).heightPercent(16f)
            }
            style {
                it.background(ColorRectTexture(glassPanel))
                it.overlay(ColorBorderTexture(1, subtleBorder))
                it.zIndex(50)
            }
        }
        val title = TextElement().apply {
            setId("option_tray_title")
            setText("技能选项")
            layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .leftPercent(3f).topPercent(6f)
                    .widthPercent(94f).heightPercent(16f)
            }
        }
        val description = TextElement().apply {
            setId("option_tray_description")
            setText("选择一个选项以装备。")
            layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .leftPercent(3f).topPercent(82f)
                    .widthPercent(94f).heightPercent(12f)
            }
        }
        val choices = List(4) { index ->
            Button().apply {
                setId("option_choice_$index")
                setText("选项 ${index + 1}")
                layout {
                    it.positionType(YogaPositionType.ABSOLUTE)
                        .leftPercent(3f + index * 24f).topPercent(27f)
                        .widthPercent(21f).heightPercent(47f)
                }
                buttonStyle {
                    it.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), ColorBorderTexture(1, subtleBorder)))
                    it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), ColorBorderTexture(2, accent)))
                    it.pressedTexture(ColorRectTexture(optionPressed))
                }
            }
        }
        tray.addChildren(title, description, *choices.toTypedArray())
        return tray
    }

    private fun bindSubclassOptionButtons(
        ui: UI,
        tray: OptionTrayBinding,
        definition: DestinySubclassConfigDefinition,
        sendChanges: Boolean
    ) {
        val abilityBindings = listOf(
            Triple("ability_slot_0", AbilitySlot.GRENADE, "手雷"),
            Triple("ability_slot_1", AbilitySlot.MELEE, "近战"),
            Triple("ability_slot_2", AbilitySlot.CLASS_ABILITY, "职业技能")
        )
        abilityBindings.forEach { (buttonId, slot, label) ->
            val target = button(ui, buttonId) ?: return@forEach
            var renderedOptionId: String? = "<uninitialized>"
            target.setOnClick {
                playButtonClickAnimation(target)
                tray.toggle(
                    key = "ability:${slot.key}",
                    titleText = "$label 选项",
                    options = definition.abilityOptions[slot].orEmpty(),
                    anchor = target,
                    widthPercent = 28f,
                    selected = { option -> DestinyHUDState.selectedAbilityId(slot.legacyNetworkId) == option.id },
                    emptyText = "当前子职业没有可用的$label 选项。"
                ) { option ->
                    if (sendChanges) {
                        ClientPlayNetworking.send(DestinyNetworking.ConfigureSubclassPayload("ability", slot.key, option.id))
                    }
                }
            }
            target.addEventListener(UIEvents.TICK) {
                val selectedId = DestinyHUDState.selectedAbilityId(slot.legacyNetworkId)
                if (selectedId != renderedOptionId) {
                    renderedOptionId = selectedId
                    applyOptionVisual(
                        target,
                        definition.abilityOptions[slot].orEmpty().firstOrNull { it.id == selectedId },
                        label,
                        selectedId.isNotBlank(),
                        subclassAccent(definition.subclass)
                    )
                }
            }
        }

        button(ui, "ability_slot_3")?.let { target ->
            var renderedOptionId: String? = "<uninitialized>"
            target.setOnClick {
                playButtonClickAnimation(target)
                tray.toggle(
                    key = "ability:movement",
                    titleText = "移动技能选项",
                    options = definition.movementOptions,
                    anchor = target,
                    widthPercent = 23.54f,
                    selected = { option -> DestinyHUDState.selectedMovementId == option.id },
                    emptyText = "当前子职业没有可用的移动技能。"
                ) { option ->
                    if (sendChanges) {
                        ClientPlayNetworking.send(
                            DestinyNetworking.ConfigureSubclassPayload("movement", "movement", option.id)
                        )
                    }
                }
            }
            target.addEventListener(UIEvents.TICK) {
                val selectedId = DestinyHUDState.selectedMovementId
                if (selectedId != renderedOptionId) {
                    renderedOptionId = selectedId
                    applyOptionVisual(
                        target,
                        definition.movementOptions.firstOrNull { it.id == selectedId },
                        "移动技能",
                        selectedId.isNotBlank(),
                        subclassAccent(definition.subclass)
                    )
                }
            }
        }

        button(ui, "super_button")?.let { target ->
            // The super icon artwork is square. Let width drive height so LDLib
            // cannot stretch the vertical Well of Radiance symbol horizontally.
            target.layout { it.heightAuto().aspectRatio(1f) }
            var renderedOptionId: String? = "<uninitialized>"
            target.setOnClick {
                playButtonClickAnimation(target)
                tray.toggle(
                    key = "ability:super",
                    titleText = "大招选项",
                    options = definition.abilityOptions[AbilitySlot.SUPER].orEmpty(),
                    anchor = target,
                    widthPercent = 26f,
                    anchorGap = OPTION_TRAY_ANCHOR_GAP,
                    selected = { option -> DestinyHUDState.selectedAbilityId(AbilitySlot.SUPER.legacyNetworkId) == option.id },
                    emptyText = "当前子职业没有可用的大招选项。"
                ) { option ->
                    if (sendChanges) {
                        ClientPlayNetworking.send(DestinyNetworking.ConfigureSubclassPayload("ability", AbilitySlot.SUPER.key, option.id))
                    }
                }
            }
            target.addEventListener(UIEvents.TICK) {
                val selectedId = DestinyHUDState.selectedAbilityId(AbilitySlot.SUPER.legacyNetworkId)
                if (selectedId != renderedOptionId) {
                    renderedOptionId = selectedId
                    applySuperVisual(
                        target,
                        definition.abilityOptions[AbilitySlot.SUPER].orEmpty().firstOrNull { it.id == selectedId },
                        "大招",
                        selectedId.isNotBlank(),
                        subclassAccent(definition.subclass)
                    )
                }
            }
        }

        (0 until 2).forEach { slotIndex ->
            val target = button(ui, "aspect_slot_button_$slotIndex") ?: return@forEach
            var renderedOptionId: String? = "<uninitialized>"
            target.setOnClick {
                playButtonClickAnimation(target)
                tray.toggle(
                    key = "aspect:$slotIndex",
                    titleText = "星象选项",
                    options = definition.aspectOptions,
                    anchor = target,
                    widthPercent = 30f,
                    selected = { option -> DestinyHUDState.selectedAspectIds.contains(option.id) },
                    emptyText = "当前子职业没有可用的星象。"
                ) { option ->
                    if (sendChanges) {
                        ClientPlayNetworking.send(DestinyNetworking.ConfigureSubclassPayload("aspect", "", option.id))
                    }
                }
            }
            target.addEventListener(UIEvents.TICK) {
                val selected = DestinyHUDState.selectedAspectIds.getOrNull(slotIndex)
                if (selected != renderedOptionId) {
                    renderedOptionId = selected
                    applyOptionVisual(
                        target,
                        definition.aspectOptions.firstOrNull { it.id == selected },
                        "空星象",
                        selected != null,
                        subclassAccent(definition.subclass)
                    )
                }
            }
        }

        (0 until DestinySubclassConfigRegistry.maximumFragmentCapacity(definition)).forEach { slotIndex ->
            button(ui, "fragment_slot_$slotIndex")?.let { target ->
                target.setOnClick {
                    playButtonClickAnimation(target)
                    tray.toggle(
                        key = "fragment:$slotIndex",
                        titleText = "碎片选项",
                        options = definition.fragmentOptions,
                        anchor = target,
                        widthPercent = 42f,
                        selected = { option -> DestinyHUDState.selectedFragmentIds.contains(option.id) },
                        emptyText = "当前子职业没有可用的碎片。"
                    ) { option ->
                        if (sendChanges) {
                            ClientPlayNetworking.send(DestinyNetworking.ConfigureSubclassPayload("fragment", "", option.id))
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds project-specific behavior to LDLib's disposable simulation UI.
     * Nothing is written back to the template and option choices deliberately
     * avoid sending configuration payloads to the server.
     */
    fun bindVisualEditorSimulation(ui: UI) {
        val subclass = DestinySubclassType.entries.firstOrNull { it.displayName == DestinyHUDState.subclassName }
            ?: DestinySubclassType.DEFAULT
        val definition = DestinySubclassConfigRegistry.definitionFor(subclass)
        applySubclassBackground(ui.rootElement, definition)
        val foreground = installResponsiveForeground(ui)
        hideAuthoredDecoration(ui)
        alignAbilitySectionWithFragments(ui)
        bindFragmentPanel(ui, definition)
        val accent = subclassAccent(definition.subclass)
        val tray = bindOptionTray(ui, accent) ?: installOptionTray(foreground, accent)
        bindSubclassOptionButtons(ui, tray, definition, sendChanges = false)
    }

    private class OptionTrayBinding(
        private val tray: UIElement,
        private val title: TextElement,
        private val detailTitle: TextElement,
        private val detailMeta: TextElement,
        private val description: TextElement,
        private val detailDivider: UIElement,
        private val choices: MutableList<Button>,
        private val accent: Int
    ) {
        private var openKey: String? = null
        private val previewActions = mutableMapOf<Button, () -> Unit>()

        init {
            // Authored template buttons already exist before the dynamic pool
            // grows, so they need the same preview bridge as runtime buttons.
            choices.forEach(::attachPreviewListener)
        }

        private fun attachPreviewListener(choice: Button) {
            choice.addEventListener(UIEvents.MOUSE_ENTER) {
                previewActions[choice]?.invoke()
            }
        }

        /** Creates option widgets from registry data, without requiring matching widgets in the UI template. */
        private fun ensureChoiceCount(required: Int) {
            while (choices.size < required) {
                val index = choices.size
                val choice = Button().apply {
                    setId("runtime_option_choice_$index")
                    setText("")
                    layout {
                        it.positionType(YogaPositionType.ABSOLUTE)
                            .left(0f).top(0f).width(64f).height(64f).aspectRatio(1f)
                    }
                    buttonStyle {
                        it.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), ColorBorderTexture(1, subtleBorder)))
                        it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), ColorBorderTexture(2, accent)))
                        it.pressedTexture(ColorRectTexture(optionPressed))
                    }
                }
                attachPreviewListener(choice)
                tray.addChild(choice)
                choices += choice
            }
        }

        fun toggle(
            key: String,
            titleText: String,
            options: List<DestinyConfigOption>,
            anchor: UIElement,
            widthPercent: Float,
            anchorGap: Float = 0f,
            selected: (DestinyConfigOption) -> Boolean,
            emptyText: String,
            onSelect: (DestinyConfigOption) -> Unit
        ) {
            if (tray.isDisplayed && openKey == key) {
                close()
                return
            }
            openKey = key
            val parent = tray.parent ?: return
            val trayWidth = parent.contentWidth * widthPercent / 100f
            val anchorLeft = anchor.positionX - parent.contentX
            val anchorTop = anchor.positionY - parent.contentY
            val requestedLeft = anchorLeft
            val left = requestedLeft.coerceIn(0f, (parent.contentWidth - trayWidth).coerceAtLeast(0f))
            ensureChoiceCount(options.size)

            val optionCount = options.size.coerceAtLeast(1)
            val horizontalPadding = 8f
            val choiceGap = 4f
            val naturalColumns = kotlin.math.floor(
                (trayWidth - horizontalPadding * 2f + choiceGap) / (64f + choiceGap)
            ).toInt().coerceAtLeast(1)
            val columns = minOf(optionCount, naturalColumns, 5).coerceAtLeast(1)
            val rows = kotlin.math.ceil(options.size.toFloat() / columns).toInt().coerceAtLeast(1)
            val availableChoiceWidth = trayWidth - horizontalPadding * 2f - choiceGap * (columns - 1)
            val naturalChoiceSize = (availableChoiceWidth / columns).coerceIn(42f, 84f)
            val choicesTop = 32f
            val maximumTrayHeight = (parent.contentHeight - 16f).coerceAtLeast(96f)
            val maximumGridHeight = (
                maximumTrayHeight - choicesTop - OPTION_TRAY_DETAIL_HEIGHT - 16f
            ).coerceAtLeast(16f)
            val fittedChoiceSize = ((maximumGridHeight - choiceGap * (rows - 1)) / rows).coerceAtLeast(12f)
            val choiceSize = minOf(naturalChoiceSize, fittedChoiceSize)
            val gridHeight = if (options.isEmpty()) 0f else rows * choiceSize + (rows - 1) * choiceGap
            val descriptionTop = choicesTop + gridHeight + 8f
            val trayHeight = descriptionTop + OPTION_TRAY_DETAIL_HEIGHT + 8f
            val belowTop = anchorTop + anchor.sizeHeight + anchorGap
            val top = when {
                belowTop + trayHeight <= parent.contentHeight -> belowTop
                anchorTop - anchorGap - trayHeight >= 0f -> anchorTop - anchorGap - trayHeight
                else -> (parent.contentHeight - trayHeight).coerceAtLeast(0f)
            }
            tray.layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(left).top(top)
                    .widthPercent(widthPercent).height(trayHeight)
            }
            title.layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(horizontalPadding).top(8f)
                    .width(trayWidth - horizontalPadding * 2f).height(18f)
            }
            detailDivider.layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(horizontalPadding).top(descriptionTop)
                    .width(trayWidth - horizontalPadding * 2f).height(1f)
            }
            detailTitle.layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(horizontalPadding).top(descriptionTop + 8f)
                    .width(trayWidth - horizontalPadding * 2f).height(17f)
            }
            detailMeta.layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(horizontalPadding).top(descriptionTop + 27f)
                    .width(trayWidth - horizontalPadding * 2f).height(13f)
            }
            description.layout {
                it.positionType(YogaPositionType.ABSOLUTE)
                    .left(horizontalPadding).top(descriptionTop + 45f)
                    .width(trayWidth - horizontalPadding * 2f).height(43f)
            }
            description.textStyle {
                it.font(DestinyNavigationTemplate.DIRECTOR_FONT)
                    .fontSize(8f)
                    .textColor(0xFFE1DDD5.toInt())
                    .textShadow(false)
                    .adaptiveWidth(false)
                    .adaptiveHeight(false)
                    .textWrap(TextWrap.WRAP)
                    .lineSpacing(1f)
            }
            description.style { it.overflowVisible(false) }
            title.setText(titleText)
            val initialOption = options.firstOrNull(selected) ?: options.firstOrNull()
            showDetails(initialOption, titleText, initialOption?.let(selected) == true, emptyText)
            choices.forEachIndexed { index, choice ->
                val option = options.getOrNull(index)
                val row = index / columns
                val column = index % columns
                val rowStart = row * columns
                val rowCount = minOf(columns, (options.size - rowStart).coerceAtLeast(0))
                val rowWidth = rowCount * choiceSize + (rowCount - 1).coerceAtLeast(0) * choiceGap
                val rowLeft = (trayWidth - rowWidth) / 2f
                choice.layout {
                    it.positionType(YogaPositionType.ABSOLUTE)
                        .left(rowLeft + column * (choiceSize + choiceGap))
                        .top(choicesTop + row * (choiceSize + choiceGap))
                        .width(choiceSize).height(choiceSize).aspectRatio(1f)
                }
                choice.setDisplay(option != null)
                choice.setVisible(option != null)
                if (option != null) {
                    applyInsetOptionVisual(
                        choice,
                        option,
                        option.title,
                        selected(option),
                        accent
                    )
                    previewActions[choice] = {
                        showDetails(option, titleText, selected(option), emptyText)
                    }
                    choice.setOnClick {
                        // Configuration must not depend on the cosmetic animation
                        // reaching its completion callback (notably in editor simulation).
                        onSelect(option)
                        playOptionSelectionAnimation(choice) {
                            close()
                        }
                    }
                }
                if (option == null) previewActions.remove(choice)
            }
            tray.setDisplay(true)
            tray.setVisible(true)
            playTrayRevealAnimation(tray)
        }

        private fun showDetails(
            option: DestinyConfigOption?,
            sectionTitle: String,
            selected: Boolean,
            emptyText: String
        ) {
            if (option == null) {
                detailTitle.setText("暂无可用选项")
                detailMeta.setText(sectionTitle.removeSuffix("选项"))
                description.setText(emptyText)
                return
            }
            val category = sectionTitle.removeSuffix("选项").trim()
            val slotInfo = if (option.fragmentSlots > 0) " · 碎片槽 +${option.fragmentSlots}" else ""
            val equipped = if (selected) " · 已装备" else ""
            detailTitle.setText(option.title)
            detailMeta.setText("$category$slotInfo$equipped")
            description.setText(option.description)
        }

        fun close() {
            openKey = null
            tray.setVisible(false)
            tray.setDisplay(false)
        }
    }

    private fun playButtonClickAnimation(target: UIElement) {
        val restingTransform = target.style.transform2D().copy()
        target.style {
            it.transform2D(restingTransform.copy())
            it.opacity(1f)
        }
        target.animation()
            .duration(0.22f)
            .ease(Eases.CUBIC_OUT)
            .style(
                PropertyRegistry.TRANSFORM_2D,
                FloatObjectPair.of(0.20f, restingTransform.copy().pivot(0.5f, 0.5f).scale(0.91f)),
                FloatObjectPair.of(0.66f, restingTransform.copy().pivot(0.5f, 0.5f).scale(1.035f)),
                FloatObjectPair.of(1f, restingTransform.copy())
            )
            .start()
    }

    private fun playTrayRevealAnimation(tray: UIElement) {
        tray.style {
            it.transform2D(Transform2D().pivot(0.5f, 0f).scale(1f, 0.12f).translate(0f, -3f))
            it.opacity(0f)
        }
        tray.animation()
            .duration(0.24f)
            .ease(Eases.CUBIC_OUT)
            .style(
                PropertyRegistry.TRANSFORM_2D,
                FloatObjectPair.of(0.72f, Transform2D().pivot(0.5f, 0f).scale(1f, 1.035f)),
                FloatObjectPair.of(1f, Transform2D.identity())
            )
            .style(PropertyRegistry.OPACITY, 1f)
            .start()
    }

    private fun playOptionSelectionAnimation(target: UIElement, onFinished: () -> Unit) {
        val restingTransform = target.style.transform2D().copy()
        target.style {
            it.transform2D(restingTransform.copy())
            it.opacity(1f)
        }
        target.animation()
            .duration(0.16f)
            .ease(Eases.CUBIC_OUT)
            .style(
                PropertyRegistry.TRANSFORM_2D,
                FloatObjectPair.of(0.28f, restingTransform.copy().pivot(0.5f, 0.5f).scale(0.88f)),
                FloatObjectPair.of(0.72f, restingTransform.copy().pivot(0.5f, 0.5f).scale(1.045f)),
                FloatObjectPair.of(1f, restingTransform.copy())
            )
            .onFinished { onFinished() }
            .start()
    }

    private fun placeFragmentDescriptionBelowPanel(description: TextElement, panel: UIElement) {
        panel.style { it.overflowVisible(true) }
        description.layout {
            it.positionType(YogaPositionType.ABSOLUTE)
                .left(10f).right(10f).topPercent(105f).height(30f)
        }
        description.textStyle {
            it.adaptiveWidth(false)
                .adaptiveHeight(false)
                .textWrap(TextWrap.WRAP)
                .lineSpacing(1f)
        }
        description.style { it.overflowVisible(false) }
    }

    /** The editable version saved into the LDLib aspect template. */
    private fun createEditorFragmentPanel(): UIElement {
        val panel = UIElement().apply {
            setId("fragment_panel")
            layout {
                it.widthPercent(100f).height(174f).paddingAll(10f).gapAll(7f)
                it.flexDirection(YogaFlexDirection.COLUMN)
            }
            style {
                it.background(ColorRectTexture(softGlassPanel))
                it.overlay(ColorBorderTexture(1, subtleBorder))
            }
        }

        val header = UIElement().layout {
            it.widthPercent(100f).height(22f).flexDirection(YogaFlexDirection.ROW)
            it.justifyContent(YogaJustify.SPACE_BETWEEN)
        }
        val titleLine = UIElement().layout {
            it.widthPercent(68f).height(22f).gapAll(1f).flexDirection(YogaFlexDirection.COLUMN)
        }
        titleLine.addChildren(
            TextElement().apply { setText("星象碎片"); layout { it.widthPercent(100f).height(16f) } },
            UIElement().apply {
                setId("fragment_divider")
                layout { it.widthPercent(100f).height(1f) }
                style { it.background(ColorRectTexture(brightBorder)) }
            }
        )
        header.addChildren(
            titleLine,
            TextElement().apply { setId("fragment_live_capacity"); setText("碎片槽：0 / --"); layout { it.widthPercent(30f).height(22f) } }
        )

        val slotRow = UIElement().layout {
            it.widthPercent(100f).height(FRAGMENT_SLOT_SIZE).gapAll(FRAGMENT_SLOT_GAP)
                .flexDirection(YogaFlexDirection.ROW)
                .justifyContent(YogaJustify.FLEX_START)
        }
        (0 until 4).map { index ->
            Button().apply {
                setId("fragment_slot_$index")
                setText("锁定")
                layout { it.width(FRAGMENT_SLOT_SIZE).height(FRAGMENT_SLOT_SIZE).aspectRatio(1f) }
                buttonStyle { style ->
                    style.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), ColorBorderTexture(1, subtleBorder)))
                    style.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), ColorBorderTexture(2, brightBorder)))
                }
            }
        }.also { slotRow.addChildren(*it.toTypedArray()) }

        val choiceRow = UIElement().layout {
            it.widthPercent(100f).height(34f).gapAll(4f).flexDirection(YogaFlexDirection.ROW)
            it.justifyContent(YogaJustify.FLEX_START)
        }
        (0 until 4).map { index ->
            Button().apply {
                setId("fragment_choice_$index")
                setText("碎片 ${index + 1}")
                layout { it.widthAuto().height(34f).aspectRatio(1f) }
                buttonStyle { style ->
                    style.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), ColorBorderTexture(1, subtleBorder)))
                    style.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), ColorBorderTexture(2, brightBorder)))
                }
            }
        }.also { choiceRow.addChildren(*it.toTypedArray()) }

        val description = TextElement().apply {
            setId("fragment_description")
            setText("装备星象后可获得碎片槽。")
            placeFragmentDescriptionBelowPanel(this, panel)
        }
        panel.addChildren(header, slotRow, choiceRow, description)
        return panel
    }

    /**
     * Adds the live fragment panel without modifying the creator-owned LDLib
     * template. The lower third is fixed-height and the four slots share one
     * row, keeping their proportions stable at every GUI scale.
     */
    private fun installFragmentPanel(root: UIElement, definition: DestinySubclassConfigDefinition): FragmentPanelBinding {
        val panel = UIElement()
            .setId("runtime_fragment_panel")
            .layout {
                it.widthPercent(100f).height(174f).paddingAll(10f).gapAll(7f)
                it.flexDirection(YogaFlexDirection.COLUMN)
            }
            .style {
                it.background(ColorRectTexture(softGlassPanel))
                it.overlay(ColorBorderTexture(1, subtleBorder))
            }

        val header = UIElement().layout {
            it.widthPercent(100f).height(22f).flexDirection(YogaFlexDirection.ROW)
            it.justifyContent(YogaJustify.SPACE_BETWEEN)
        }
        val title = TextElement().apply {
            setText("星象碎片")
            layout { it.widthPercent(68f).height(22f) }
        }
        val capacityText = TextElement().apply {
            setText("碎片槽：0 / ${DestinySubclassConfigRegistry.maximumFragmentCapacity(definition)}")
            layout { it.widthPercent(30f).height(22f) }
        }
        header.addChildren(title, capacityText)

        val slotRow = UIElement().layout {
            it.widthPercent(100f).height(FRAGMENT_SLOT_SIZE).gapAll(FRAGMENT_SLOT_GAP)
                .flexDirection(YogaFlexDirection.ROW)
                .justifyContent(YogaJustify.FLEX_START)
        }
        val slots = List(DestinySubclassConfigRegistry.maximumFragmentCapacity(definition)) { index ->
            Button().apply {
                setId("fragment_slot_$index")
                layout { it.width(FRAGMENT_SLOT_SIZE).height(FRAGMENT_SLOT_SIZE).aspectRatio(1f) }
                buttonStyle { style ->
                    style.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), ColorBorderTexture(1, subtleBorder)))
                    style.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), ColorBorderTexture(2, subclassAccent(definition.subclass))))
                }
            }
        }
        slotRow.addChildren(*slots.toTypedArray())

        val choiceRow = UIElement().layout {
            it.widthPercent(100f).height(34f).gapAll(4f).flexDirection(YogaFlexDirection.ROW)
            it.justifyContent(YogaJustify.FLEX_START)
        }
        val choices = definition.fragmentOptions.take(4).mapIndexed { index, option ->
            Button().apply {
                setId("runtime_fragment_choice_$index")
                setText(option.title)
                layout { it.widthAuto().height(34f).aspectRatio(1f) }
                buttonStyle { style ->
                    style.baseTexture(GuiTextureGroup.of(ColorRectTexture(optionBase), ColorBorderTexture(1, subtleBorder)))
                    style.hoverTexture(GuiTextureGroup.of(ColorRectTexture(optionHover), ColorBorderTexture(2, subclassAccent(definition.subclass))))
                }
                setOnClick {
                    ClientPlayNetworking.send(DestinyNetworking.ConfigureSubclassPayload("fragment", "", option.id))
                }
            }
        }
        choiceRow.addChildren(*choices.toTypedArray())

        val description = TextElement().apply {
            setId("runtime_fragment_description")
            setText("装备星象后可获得碎片槽。")
            placeFragmentDescriptionBelowPanel(this, panel)
        }

        panel.addChildren(header, slotRow, choiceRow, description)
        root.addChild(panel)
        return FragmentPanelBinding(panel, capacityText, slots, choices, description, definition, subclassAccent(definition.subclass))
    }

    private fun fragmentCapacity(selected: List<String>, definition: DestinySubclassConfigDefinition): Int {
        return selected.sumOf { id -> definition.aspectOptions.firstOrNull { it.id == id }?.fragmentSlots ?: 0 }
            .coerceAtLeast(0)
    }

    private fun fragmentPanelWidth(capacity: Int): Float {
        val displayedSlots = capacity.coerceAtLeast(AUTHORED_FRAGMENT_SLOTS)
        return FRAGMENT_PANEL_SIDE_PADDING * 2f + displayedSlots * FRAGMENT_SLOT_SIZE +
            (displayedSlots - 1).coerceAtLeast(0) * FRAGMENT_SLOT_GAP
    }

    private class FragmentPanelBinding(
        private val panel: UIElement,
        private val capacityText: TextElement,
        private val slots: List<Button>,
        private val choices: List<Button>,
        private val description: TextElement,
        private val definition: DestinySubclassConfigDefinition,
        private val accent: Int
    ) {
        private var visibleCapacity = 0

        init {
            choices.forEachIndexed { index, choice ->
                val option = definition.fragmentOptions.getOrNull(index) ?: return@forEachIndexed
                choice.setOnClick {
                    ClientPlayNetworking.send(DestinyNetworking.ConfigureSubclassPayload("fragment", "", option.id))
                }
            }
        }

        fun update(capacity: Int, selectedIds: List<String>) {
            visibleCapacity = capacity.coerceIn(0, slots.size)
            panel.layout { it.width(fragmentPanelWidth(visibleCapacity)) }
            capacityText.setText("碎片槽：${selectedIds.size} / $capacity")
            slots.forEachIndexed { index, slot ->
                val option = selectedIds.getOrNull(index)?.let { id -> definition.fragmentOptions.firstOrNull { it.id == id } }
                val isAvailable = index < visibleCapacity
                slot.setDisplay(isAvailable)
                slot.setVisible(isAvailable)
                applyInsetOptionVisual(
                    slot,
                    option,
                    "",
                    option != null,
                    accent,
                    82f
                )
                slot.setActive(isAvailable)
            }
            choices.forEachIndexed { index, choice ->
                val option = definition.fragmentOptions.getOrNull(index) ?: return@forEachIndexed
                applyOptionVisual(
                    choice,
                    option,
                    option.title,
                    selectedIds.contains(option.id),
                    accent
                )
            }

            description.setText(
                selectedIds.lastOrNull()
                    ?.let { id -> definition.fragmentOptions.firstOrNull { it.id == id }?.let { option -> "${option.title}：${option.description}" } }
                    ?: if (capacity == 0) "装备星象后可获得碎片槽。" else "点击下方碎片以装配；点击已装备的碎片可卸下。"
            )
        }
    }

    /**
     * Makes the aspect layout available as an LDLib UI Template.  Templates
     * (unlike XML projects) are editable through the in-game visual editor.
     */
    fun prepareVisualEditor() {
        ensureVisualTemplate()
        DestinyPerkTooltipTemplate.prepareVisualEditor()
        if (editorProviderRegistered) return

        val resources = UIResource.INSTANCE.resourceInstance
        val templateRoot = templateDirectory().toAbsolutePath().normalize()

        // LDLib persists custom resource providers between game sessions.  Older
        // versions of this screen blindly appended this provider on every launch,
        // which made the editor's resource panel accumulate identical "Destiny 2
        // UI" folders.  Remove every saved registration for this exact directory
        // first, then add one canonical provider back.
        resources.customProviders.values
            .asSequence()
            .flatten()
            .filterIsInstance<FileResourceProvider<*>>()
            .filter { provider ->
                provider.resourceLocation.toPath().toAbsolutePath().normalize() == templateRoot
            }
            .map { provider ->
                @Suppress("UNCHECKED_CAST")
                (provider as FileResourceProvider<UITemplate>)
            }
            .toList()
            .forEach(resources::removeCustomProvider)

        val provider = FileResourceProvider(resources, templateDirectory().toFile())
        provider.setName("Destiny 2 UI")
        resources.addCustomProvider(provider)
        editorProviderRegistered = true
    }

    fun loadVisualTemplateForEditor(): UITemplate {
        ensureEditorTemplate()
        return templateProvider().getResource(FilePath(editorTemplatePath().toFile()))
            ?: error("无法读取 LDLib 编辑模板：${editorTemplatePath()}")
    }

    fun saveVisualTemplateFromEditor(template: UITemplate) {
        check(templateProvider().addResource(FilePath(editorTemplatePath().toFile()), template)) {
            "无法保存 LDLib 编辑模板：${editorTemplatePath()}"
        }
    }

    private fun loadVisualLayout(): UI {
        ensureEditorTemplate()
        return templateProvider().getResource(FilePath(templatePath().toFile()))
            ?.createUI()
            ?: error("无法读取星象 UI 模板：${templatePath()}")
    }

    private fun ensureEditorTemplate() {
        val runtime = templatePath()
        val target = editorTemplatePath()
        val editorMirroredOldRuntime = Files.exists(runtime) && Files.exists(target) &&
            Files.mismatch(runtime, target) == -1L
        ensureVisualTemplate()
        if (Files.notExists(target) || editorMirroredOldRuntime) {
            Files.copy(runtime, target, StandardCopyOption.REPLACE_EXISTING)
        }
        ensureFragmentPanelTemplate(target)
    }

    private fun ensureVisualTemplate() {
        val template = templatePath()
        Files.createDirectories(template.parent)
        val bundled = Minecraft.getInstance().resourceManager.getResource(bundledAspectTemplate)
        if (bundled.isPresent) {
            bundled.get().open().use { input ->
                Files.copy(input, template, StandardCopyOption.REPLACE_EXISTING)
            }
        } else if (Files.notExists(template)) {
            check(templateProvider().addResource(FilePath(template.toFile()), UI.of(loadEditableLayout()).toTemplate())) {
                "无法创建星象 UI 模板：$template"
            }
        }
        ensureFragmentPanelTemplate(template)
    }

    private fun ensureFragmentPanelTemplate(template: java.nio.file.Path) {
        val path = FilePath(template.toFile())
        val current = templateProvider().getResource(path) ?: return
        val root = current.createUI().rootElement
        root.layout { it.widthPercent(100f).heightPercent(100f) }
        if (root.style.backgroundTexture() !is SpriteTexture) {
            root.style { it.background(SpriteTexture.of(solarBackground)) }
        }
        root.selectId("option_tray").findFirst().orElse(null)?.let(::ensureOptionTrayDetailNodes)
        val existingPanel = root.selectId("fragment_panel").findFirst().orElse(null)
        if (existingPanel != null) {
            existingPanel.style {
                it.background(ColorRectTexture(softGlassPanel))
                it.overlay(ColorBorderTexture(1, subtleBorder))
                it.opacity(1f)
            }
            val refreshed = UI.of(root).toTemplate()
            refreshed.copyStylesFrom(current)
            check(templateProvider().addResource(path, refreshed)) {
                "无法刷新碎片面板样式：$template"
            }
            return
        }

        // One-time migration for whichever template was supplied. The editor
        // template also passes through here so its fragment panel stays usable.
        root.addChild(createEditorFragmentPanel())
        val updated = UI.of(root).toTemplate()
        updated.copyStylesFrom(current)
        check(templateProvider().addResource(path, updated)) {
            "无法升级星象 UI 模板：$template"
        }
    }

    /**
     * A reusable LDLib template for the existing solar HUD artwork.  The
     * artwork remains split into transparent layers so creators can edit,
     * move, or replace each ability icon in the visual editor.
     */
    private fun ensureHudSkillClusterTemplate() {
        val template = hudSkillClusterTemplatePath()
        if (Files.exists(template)) return

        Files.createDirectories(template.parent)
        check(templateProvider().addResource(FilePath(template.toFile()), UI.of(createHudSkillCluster()).toTemplate())) {
            "无法创建技能 HUD 预设：$template"
        }
    }

    private fun createHudSkillCluster(): UIElement {
        val root = UIElement().setId("hud_skill_cluster")
        root.layout { it.width(512f).height(384f) }

        fun layer(id: String, texture: String, x: Float, y: Float, width: Float, height: Float): UIElement {
            return UIElement()
                .setId(id)
                .layout {
                    it.positionType(YogaPositionType.ABSOLUTE)
                        .left(x)
                        .top(y)
                        .width(width)
                        .height(height)
                }
                .style { style ->
                    style.background(SpriteTexture.of(ResourceLocation.parse(texture)))
                }
        }

        root.addChildren(
            layer("hud_frame", "destiny2-mod:textures/gui/hud/solar_warlock_base.png", 0f, 0f, 512f, 384f),
            layer("hud_grenade", "destiny2-mod:textures/gui/hud/skill_grenade.png", 42f, 148f, 118f, 118f),
            layer("hud_melee", "destiny2-mod:textures/gui/hud/skill_melee.png", 352f, 148f, 118f, 118f),
            layer("hud_class", "destiny2-mod:textures/gui/hud/skill_class.png", 197f, 260f, 118f, 118f),
            layer("hud_super", "destiny2-mod:textures/gui/hud/skill_super.png", 174f, 22f, 164f, 164f)
        )
        return root
    }

    private fun templateDirectory() = LDLib2.getAssetsDir().toPath().resolve("destiny2-mod/resources")

    private fun templatePath() = templateDirectory().resolve("aspect_screen.ui.nbt")

    /** The creator-facing LDLib canvas. It is intentionally separate from the live game screen. */
    private fun editorTemplatePath() = templateDirectory().resolve("new_res.ui.nbt")

    private fun hudSkillClusterTemplatePath() = templateDirectory().resolve("hud_skill_cluster.ui.nbt")

    private fun templateProvider() = FileResourceProvider(UIResource.INSTANCE.resourceInstance, templateDirectory().toFile())

    private fun loadEditableLayout(): Document {
        val target = FabricLoader.getInstance().configDir.resolve("destiny2-ui/aspect_screen.xml")
        if (Files.notExists(target)) {
            Files.createDirectories(target.parent)
            Minecraft.getInstance().resourceManager
                .getResource(ResourceLocation.fromNamespaceAndPath("destiny2-mod", "ui/aspect_screen.xml"))
                .orElseThrow()
                .open().use { input -> Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING) }
        }
        return Files.newInputStream(target).use(XmlUtils::loadXml)
            ?: error("无法读取星象 UI XML：$target")
    }

    private fun slotText(index: Int, id: String?, options: List<DestinyConfigOption>): String {
        val name = options.firstOrNull { it.id == id }?.title ?: "未装备"
        return "星象 $index\n$name"
    }
}
