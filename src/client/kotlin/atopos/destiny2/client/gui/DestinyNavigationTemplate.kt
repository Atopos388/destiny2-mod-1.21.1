package atopos.destiny2.client.gui

import com.lowdragmc.lowdraglib2.LDLib2
import com.lowdragmc.lowdraglib2.editor.resource.FilePath
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider
import com.lowdragmc.lowdraglib2.editor.resource.UIResource
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement
import net.minecraft.resources.ResourceLocation
import org.appliedenergistics.yoga.YogaPositionType
import org.appliedenergistics.yoga.YogaOverflow
import java.nio.file.Files
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Creator-editable, full-screen Director composition.
 *
 * Every visual plane and authored text node lives in this UITemplate. Runtime
 * code only binds data and adds disposable item/perk sockets to named hosts.
 */
object DestinyNavigationTemplate {
    const val DESIGN_WIDTH = 960f
    const val DESIGN_HEIGHT = 540f
    private const val FILE_NAME = "navigation_director.ui.nbt"
    private const val TEMPLATE_VERSION_ID = "navigation_director_template_v11"
    private const val EQUIPMENT_LAYOUT_VERSION_ID = "equipment_page_layout_v10"
    private const val TOPLIGHT_BACKGROUND_VERSION_ID = "director_toplight_background_v2"
    private const val STAT_ALIGNMENT_VERSION_ID = "equipment_stat_alignment_v1"
    private val TOPLIGHT_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        "destiny2-mod",
        "textures/gui/director/director_toplight_diffuse.png"
    )
    val DIRECTOR_FONT: ResourceLocation = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "director")
    private val REQUIRED_NODE_IDS = listOf(
        "director_canvas",
        "director_header",
        "tab_equipment",
        "equipment_page",
        "inventory_page",
        "collections_page",
        "journey_page",
        "fireteam_page",
        "director_footer",
        "equipment_weapon_slots",
        "equipment_player_preview",
        "equipment_armor_slots",
        "equipment_subclass_socket",
        "equipment_ghost_slot",
        "equipment_stat_weapons",
        "equipment_stat_health",
        "equipment_stat_class",
        "equipment_stat_grenade",
        "equipment_stat_super",
        "equipment_stat_melee",
        "equipment_stat_icon_weapons",
        "equipment_stat_icon_health",
        "equipment_stat_icon_class",
        "equipment_stat_icon_grenade",
        "equipment_stat_icon_super",
        "equipment_stat_icon_melee",
        "equipment_stat_row_weapons",
        "equipment_stat_row_health",
        "equipment_stat_row_class",
        "equipment_stat_row_grenade",
        "equipment_stat_row_super",
        "equipment_stat_row_melee",
        "equipment_stat_tooltip",
        "equipment_stat_tooltip_title",
        "equipment_stat_tooltip_category",
        "equipment_stat_tooltip_description",
        "equipment_stat_tooltip_effect",
        "equipment_choice_tray",
        "equipment_detail_overlay",
        "equipment_detail_icon",
        "equipment_detail_perks",
        "equipment_detail_stats",
        "equipment_slots",
        "inventory_main_slots",
        "inventory_hotbar_slots",
        "selected_item_icon",
        "selected_perk_slots",
        "selected_perk_card",
        "selected_perk_column",
        "selected_perk_name",
        "selected_perk_details",
        "collections_overview_host",
        "collections_folder_host"
    )

    object Color {
        // Destiny's menus read as layered cold smoke and silver, rather than a
        // generic navy sci-fi dashboard. These remain centralized so an
        // editor-authored template can replace every visual plane later.
        const val ROOT = 0xFFABB1B7.toInt()
        const val LEFT = 0x00000000
        const val RIGHT = 0xFF657482.toInt()
        const val HEADER = 0xFF567485.toInt()
        const val FOOTER = 0xFF626F79.toInt()
        const val SLOT = 0xFF7F7C89.toInt()
        const val SLOT_EMPTY = 0xFF707985.toInt()
        const val HOVER = 0xFFB5ADB8.toInt()
        const val NAV_HOVER = 0xFF718592.toInt()
        const val SELECTED = 0xFFB6A265.toInt()
        const val PRIMARY = 0xFFFFFDF8.toInt()
        const val SECONDARY = 0xFFE8EBEE.toInt()
        const val MUTED = 0xFFC3C8CD.toInt()
        const val LINE = 0xFFD3D5D8.toInt()
        const val BRIGHT = 0xFFF4F3F2.toInt()
        const val EXOTIC = 0xFFE3C46A.toInt()
        const val LEGENDARY = 0xFF927AA9.toInt()
    }

    private var validatedModified = Long.MIN_VALUE

    /** Compatibility geometry for the lightweight short-Tab HUD. */
    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)
    data class Layout(
        val location: Rect,
        val objective: Rect,
        val ghost: Rect,
        val fireteam: Rect,
        val guardian: Rect,
        val footer: Rect,
        val scale: Float
    )

    fun prepare() = ensureTemplate()

    fun createRuntimeUI(): UI {
        prepare()
        return provider().getResource(FilePath(templatePath().toFile()))?.createUI()
            ?: UI.of(createDefaultRoot())
    }

    fun loadForEditor(): UITemplate {
        prepare()
        return provider().getResource(FilePath(templatePath().toFile()))
            ?: error("Unable to read Director LDLib2 template: ${templatePath()}")
    }

    fun saveFromEditor(template: UITemplate) {
        Files.createDirectories(templatePath().parent)
        check(provider().addResource(FilePath(templatePath().toFile()), template)) {
            "Unable to save Director LDLib2 template: ${templatePath()}"
        }
        validatedModified = Long.MIN_VALUE
    }

    fun reset() {
        Files.createDirectories(templatePath().parent)
        Files.deleteIfExists(templatePath())
        writeDefaultTemplate()
        validatedModified = templateModified()
    }

    fun summary(): String = "LDLib2 template=${templatePath().fileName}, canvas=960x540, v10 Director + equipment v8"

    fun resolve(screenWidth: Int, screenHeight: Int): Layout {
        val scale = min(screenWidth.coerceAtLeast(1) / DESIGN_WIDTH, screenHeight.coerceAtLeast(1) / DESIGN_HEIGHT)
            .coerceAtLeast(0.01f)
        val originX = ((screenWidth - DESIGN_WIDTH * scale) / 2f).roundToInt()
        val originY = ((screenHeight - DESIGN_HEIGHT * scale) / 2f).roundToInt()
        fun rect(x: Int, y: Int, width: Int, height: Int) = Rect(
            originX + (x * scale).roundToInt(),
            originY + (y * scale).roundToInt(),
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1)
        )
        return Layout(
            location = rect(48, 38, 360, 58),
            objective = rect(48, 126, 310, 230),
            ghost = rect(420, 118, 120, 120),
            fireteam = rect(690, 90, 222, 266),
            guardian = rect(320, 390, 320, 72),
            footer = rect(48, 500, 864, 18),
            scale = scale
        )
    }

    private fun ensureTemplate() {
        val modified = templateModified()
        if (Files.notExists(templatePath())) {
            writeDefaultTemplate()
            validatedModified = templateModified()
            return
        }
        if (validatedModified == modified) return
        val current = provider().getResource(FilePath(templatePath().toFile()))?.createUI()
        val root = current?.rootElement
        val validVersion = root?.selectId(TEMPLATE_VERSION_ID)?.findFirst()?.isPresent == true
        if (validVersion) {
            val existingRoot = requireNotNull(root)
            var migrated = migrateCollectionsTemplate(existingRoot)
            migrated = migratePerkDetailCard(existingRoot) || migrated
            migrated = migrateEquipmentPage(existingRoot) || migrated
            migrated = migrateToplightBackground(existingRoot) || migrated
            migrated = migrateEquipmentStatAlignment(existingRoot) || migrated
            if (migrated) {
                check(provider().addResource(FilePath(templatePath().toFile()), UITemplate.of(existingRoot))) {
                    "Unable to migrate Director LDLib2 template: ${templatePath()}"
                }
            }
        }
        val validStructure = root != null && REQUIRED_NODE_IDS.all { id ->
            root.selectId(id).findFirst().isPresent
        }
        if (!validVersion || !validStructure) reset() else validatedModified = modified
    }

    private fun writeDefaultTemplate() {
        Files.createDirectories(templatePath().parent)
        check(provider().addResource(FilePath(templatePath().toFile()), UITemplate.of(createDefaultRoot()))) {
            "Unable to create Director LDLib2 template: ${templatePath()}"
        }
    }

    private fun createDefaultRoot(): UIElement {
        val root = UIElement().apply {
            setId("navigation_root")
            addClass("director-root")
            layout { it.widthPercent(100f).heightPercent(100f) }
            style { it.background(ColorRectTexture(Color.ROOT)) }
        }
        root.addChild(UIElement().setId(TEMPLATE_VERSION_ID).setDisplay(false))
        root.addChild(UIElement().setId(EQUIPMENT_LAYOUT_VERSION_ID).setDisplay(false))
        root.addChild(UIElement().setId(TOPLIGHT_BACKGROUND_VERSION_ID).setDisplay(false))
        root.addChild(UIElement().setId(STAT_ALIGNMENT_VERSION_ID).setDisplay(false))

        val canvas = block("director_canvas", 0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, Color.ROOT)
        canvas.style { it.background(toplightBackgroundTexture()) }
        root.addChild(canvas)
        val pageViewport = UIElement().apply {
            setId("director_page_viewport")
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(54f).width(960f).height(456f) }
            setOverflow(YogaOverflow.HIDDEN)
        }
        pageViewport.addChildren(
            directorPage("equipment_page"),
            directorPage("inventory_page").setDisplay(false),
            directorPage("collections_page").setDisplay(false),
            directorPage("journey_page").setDisplay(false),
            directorPage("fireteam_page").setDisplay(false)
        )
        canvas.addChildren(
            block("director_header", 0f, 0f, 960f, 54f, Color.HEADER),
            pageViewport,
            block("director_footer", 0f, 510f, 960f, 30f, Color.FOOTER)
        )

        buildHeader(canvas.selectId("director_header").findFirst().orElseThrow())
        buildEquipment(canvas.selectId("equipment_page").findFirst().orElseThrow())
        buildInventory(canvas.selectId("inventory_page").findFirst().orElseThrow())
        buildCollections(canvas.selectId("collections_page").findFirst().orElseThrow())
        buildJourney(canvas.selectId("journey_page").findFirst().orElseThrow())
        buildFireteam(canvas.selectId("fireteam_page").findFirst().orElseThrow())
        buildFooter(canvas.selectId("director_footer").findFirst().orElseThrow())
        return root
    }

    /**
     * One-time, non-destructive migration for creator-edited v11 templates.
     * Once the marker is present, later editor changes to the background remain
     * authoritative and are not overwritten at startup.
     */
    private fun migrateToplightBackground(root: UIElement): Boolean {
        if (root.selectId(TOPLIGHT_BACKGROUND_VERSION_ID).findFirst().isPresent) return false
        val canvas = root.selectId("director_canvas").findFirst().orElse(null) ?: return false
        canvas.style { it.background(toplightBackgroundTexture()) }
        listOf("equipment_page", "inventory_page", "collections_page", "journey_page", "fireteam_page")
            .mapNotNull { root.selectId(it).findFirst().orElse(null) }
            .forEach { page -> page.style { it.background(toplightBackgroundTexture()) } }
        root.selectId("equipment_detail_overlay").findFirst().orElse(null)?.style {
            it.background(toplightBackgroundTexture())
        }
        listOf(
            "equipment_field",
            "inventory_left_field",
            "collections_field",
            "journey_left_field",
            "fireteam_left_field"
        ).mapNotNull { root.selectId(it).findFirst().orElse(null) }
            .forEach { node -> node.style { it.background(ColorRectTexture(0x00000000)) } }
        root.addChild(UIElement().setId(TOPLIGHT_BACKGROUND_VERSION_ID).setDisplay(false))
        return true
    }

    private fun toplightBackgroundTexture() = GuiTextureGroup.of(
        SpriteTexture.of(TOPLIGHT_BACKGROUND),
        // Retain enough mid-tone contrast for the existing white Director text
        // while preserving the pale top-light character of the source image.
        ColorRectTexture(0x30000000)
    )

    /** Opaque page plane prevents outgoing and incoming page contents from ghosting together. */
    private fun directorPage(id: String): UIElement =
        block(id, 0f, 0f, 960f, 456f, Color.ROOT).apply {
            style { it.background(toplightBackgroundTexture()) }
        }

    private fun migrateEquipmentStatAlignment(root: UIElement): Boolean {
        if (root.selectId(STAT_ALIGNMENT_VERSION_ID).findFirst().isPresent) return false
        val rows = listOf("weapons", "health", "class", "grenade", "super", "melee")
        rows.forEachIndexed { index, id ->
            val iconTop = 132f + index * 38f
            root.selectId("equipment_stat_icon_$id").findFirst().orElse(null)?.layout {
                it.left(611f).top(iconTop).width(16f).height(16f).aspectRatio(1f)
            }
            root.selectId("equipment_stat_$id", TextElement::class.java).findFirst().orElse(null)?.apply {
                layout { it.left(634f).top(iconTop - 3f).width(32f).height(22f) }
                textStyle { it.textAlignVertical(Vertical.CENTER) }
            }
        }
        root.addChild(UIElement().setId(STAT_ALIGNMENT_VERSION_ID).setDisplay(false))
        return true
    }

    private fun buildHeader(header: UIElement) {
        header.addChildren(
            block("guardian_mark", 40f, 10f, 32f, 32f, Color.BRIGHT, Color.PRIMARY),
            text("guardian_name", "GUARDIAN", 84f, 7f, 300f, 18f, 16f, Color.PRIMARY),
            text("guardian_meta", "CLASS  //  LOCATION", 84f, 29f, 380f, 13f, 9f, Color.MUTED),
            tab("tab_equipment", "装备", 440f, 10f, 82f),
            tab("tab_inventory", "物品栏", 525f, 10f, 82f),
            tab("tab_collections", "收藏品", 610f, 10f, 96f),
            tab("tab_journey", "旅程", 709f, 10f, 76f),
            tab("tab_fireteam", "火力战队", 788f, 10f, 132f),
            block("tab_indicator", 440f, 51f, 82f, 2f, Color.PRIMARY)
        )
    }

    /**
     * Migrates only the equipment page shell. Other online-edited Director
     * pages remain untouched while the character layout can advance safely.
     */
    private fun migrateEquipmentPage(root: UIElement): Boolean {
        if (root.selectId(EQUIPMENT_LAYOUT_VERSION_ID).findFirst().isPresent &&
            root.selectId("equipment_page").findFirst().isPresent
        ) return false
        val header = root.selectId("director_header").findFirst().orElse(null) ?: return false
        val viewport = root.selectId("director_page_viewport").findFirst().orElse(null) ?: return false

        root.addChild(UIElement().setId(EQUIPMENT_LAYOUT_VERSION_ID).setDisplay(false))
        val equipmentPage = root.selectId("equipment_page").findFirst().orElse(null)
            ?: directorPage("equipment_page").also(viewport::addChild)
        equipmentPage.clearAllChildren()
        buildEquipment(equipmentPage)
        if (!root.selectId("tab_equipment").findFirst().isPresent) {
            header.addChild(tab("tab_equipment", "装备", 440f, 10f, 82f))
        }
        listOf(
            "tab_equipment" to (440f to 82f),
            "tab_inventory" to (525f to 82f),
            "tab_collections" to (610f to 96f),
            "tab_journey" to (709f to 76f),
            "tab_fireteam" to (788f to 132f)
        ).forEach { (id, geometry) ->
            root.selectId(id).findFirst().orElse(null)?.layout { it.left(geometry.first).width(geometry.second) }
        }
        root.selectId("tab_indicator").findFirst().orElse(null)?.layout { it.left(440f).width(82f) }
        listOf("inventory_page", "collections_page", "journey_page", "fireteam_page")
            .mapNotNull { root.selectId(it).findFirst().orElse(null) }
            .forEach { it.setDisplay(false) }
        root.selectId("equipment_page").findFirst().orElse(null)?.setDisplay(true)
        return true
    }

    private fun buildEquipment(page: UIElement) {
        page.addChildren(
            block("equipment_field", 0f, 0f, 960f, 456f, Color.LEFT),
            block("equipment_center_light", 270f, 0f, 420f, 456f, 0x14998C97),
            host("equipment_subclass_socket", 184f, 18f, 86f, 86f),
            host("equipment_weapon_slots", 220f, 126f, 50f, 204f),
            host("equipment_ghost_slot", 220f, 356f, 50f, 50f),
            block("equipment_model_floor", 318f, 424f, 324f, 1f, 0x62F4F3F2),
            host("equipment_player_preview", 300f, 6f, 360f, 432f),
            text("equipment_power", "◆ 0", 600f, 32f, 126f, 36f, 26f, Color.EXOTIC),
            block("equipment_stat_rail", 604f, 116f, 68f, 260f, 0x18707985, 0x52D3D5D8),
            host("equipment_stat_icon_weapons", 611f, 132f, 16f, 16f),
            host("equipment_stat_icon_health", 611f, 170f, 16f, 16f),
            host("equipment_stat_icon_class", 611f, 208f, 16f, 16f),
            host("equipment_stat_icon_grenade", 611f, 246f, 16f, 16f),
            host("equipment_stat_icon_super", 611f, 284f, 16f, 16f),
            host("equipment_stat_icon_melee", 611f, 322f, 16f, 16f),
            statValueText("equipment_stat_weapons", 132f),
            statValueText("equipment_stat_health", 170f),
            statValueText("equipment_stat_class", 208f),
            statValueText("equipment_stat_grenade", 246f),
            statValueText("equipment_stat_super", 284f),
            statValueText("equipment_stat_melee", 322f),
            host("equipment_stat_row_weapons", 606f, 126f, 70f, 28f),
            host("equipment_stat_row_health", 606f, 164f, 70f, 28f),
            host("equipment_stat_row_class", 606f, 202f, 70f, 28f),
            host("equipment_stat_row_grenade", 606f, 240f, 70f, 28f),
            host("equipment_stat_row_super", 606f, 278f, 70f, 28f),
            host("equipment_stat_row_melee", 606f, 316f, 70f, 28f),
            host("equipment_armor_slots", 706f, 86f, 50f, 320f),
            reserveGrid("equipment_weapon_reserve_0", 178f, 136f),
            reserveGrid("equipment_weapon_reserve_1", 178f, 204f),
            reserveGrid("equipment_weapon_reserve_2", 178f, 272f),
            reserveGrid("equipment_armor_reserve_0", 768f, 94f),
            reserveGrid("equipment_armor_reserve_1", 768f, 158f),
            reserveGrid("equipment_armor_reserve_2", 768f, 222f),
            reserveGrid("equipment_armor_reserve_3", 768f, 286f),
            reserveGrid("equipment_armor_reserve_4", 768f, 350f),
            host("equipment_choice_tray", 0f, 0f, 1f, 1f).setDisplay(false),
            equipmentStatTooltip(),
            equipmentDetailOverlay()
        )
    }

    private fun equipmentDetailOverlay(): UIElement = block(
        "equipment_detail_overlay", 0f, 0f, 960f, 456f, Color.LEFT
    ).apply {
        setDisplay(false)
        style { it.background(toplightBackgroundTexture()).zIndex(80) }
        addChildren(
            block("equipment_detail_accent", 0f, 0f, 960f, 4f, Color.EXOTIC),
            host("equipment_detail_icon", 62f, 34f, 58f, 58f),
            text("equipment_detail_name", "装备名称", 136f, 30f, 430f, 34f, 24f, Color.PRIMARY),
            text("equipment_detail_frame", "装备类型", 136f, 65f, 360f, 20f, 12f, Color.SECONDARY),
            text("equipment_detail_flavor", "装备说明", 64f, 104f, 440f, 32f, 10f, Color.MUTED, wrap = true),
            text("equipment_detail_traits_label", "装备特性", 64f, 150f, 300f, 18f, 11f, Color.SECONDARY),
            block("equipment_detail_traits_rule", 64f, 174f, 386f, 1f, Color.LINE),
            host("equipment_detail_perks", 64f, 188f, 386f, 132f),
            text("equipment_detail_mods_label", "装备模组", 64f, 328f, 300f, 18f, 11f, Color.SECONDARY),
            block("equipment_detail_mods_rule", 64f, 350f, 386f, 1f, Color.LINE),
            host("equipment_detail_mods", 64f, 360f, 386f, 58f),
            host("equipment_detail_choice_tray", 64f, 226f, 386f, 196f).setDisplay(false),
            host("equipment_detail_preview", 438f, 78f, 282f, 270f),
            text("equipment_detail_power", "◆ 0", 520f, 300f, 150f, 44f, 30f, Color.PRIMARY),
            block("equipment_detail_stats_rule", 734f, 150f, 174f, 1f, Color.LINE),
            host("equipment_detail_stats", 734f, 164f, 174f, 236f),
            text("equipment_detail_footer", "ESC  返回装备", 754f, 426f, 154f, 18f, 9f, Color.SECONDARY)
        )
    }

    private fun reserveGrid(id: String, x: Float, y: Float): UIElement = host(id, x, y, 34f, 23f).apply {
        repeat(6) { index ->
            addChild(
                block(
                    "${id}_cell_$index",
                    (index % 3) * 11f,
                    (index / 3) * 11f,
                    10f,
                    10f,
                    0x14707985,
                    0x22D3D5D8
                )
            )
        }
    }

    private fun equipmentStatTooltip(): UIElement = UIElement().apply {
        setId("equipment_stat_tooltip")
        setAllowHitTest(false)
        setDisplay(false)
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(298f).top(172f).width(292f).height(188f) }
        style {
            it.background(ColorRectTexture(0xEE15191F.toInt()))
                .overlay(ColorBorderTexture(1, 0xA8E8EBED.toInt()))
                .zIndex(60)
        }
        addChildren(
            text("equipment_stat_tooltip_title", "属性", 16f, 12f, 250f, 24f, 15f, Color.PRIMARY),
            text("equipment_stat_tooltip_category", "角色属性", 16f, 38f, 250f, 18f, 10f, Color.MUTED),
            block("equipment_stat_tooltip_rule", 16f, 64f, 260f, 1f, 0x68E8EBED),
            text("equipment_stat_tooltip_description", "", 16f, 76f, 260f, 54f, 10f, Color.SECONDARY, wrap = true),
            block("equipment_stat_tooltip_effect_rule", 16f, 136f, 260f, 1f, 0x48E8EBED),
            text("equipment_stat_tooltip_effect", "", 16f, 146f, 260f, 34f, 10f, Color.PRIMARY, wrap = true)
        )
    }

    private fun statValueText(id: String, iconTop: Float): TextElement =
        text(id, "0", 634f, iconTop - 3f, 32f, 22f, 13f, Color.PRIMARY).apply {
            textStyle { it.textAlignVertical(Vertical.CENTER) }
        }

    private fun buildCollections(page: UIElement) {
        page.addChildren(
            block("collections_field", 0f, 0f, 960f, 456f, Color.LEFT),
            text("collections_title", "收藏品", 70f, 24f, 420f, 26f, 18f, Color.PRIMARY),
            text("collections_subtitle", "档案总览  //  选择一个分类打开收藏文件夹", 70f, 56f, 700f, 18f, 9f, Color.MUTED),
            block("collections_rule", 70f, 82f, 820f, 1f, Color.BRIGHT),
            host("collections_overview_host", 70f, 102f, 820f, 320f),
            host("collections_folder_host", 70f, 20f, 820f, 416f).setDisplay(false)
        )
    }

    /** Adds the collections shell without discarding an online-edited v10 template. */
    private fun migrateCollectionsTemplate(root: UIElement): Boolean {
        var changed = false
        val canvas = root.selectId("director_canvas").findFirst().orElse(null) ?: return false
        val header = root.selectId("director_header").findFirst().orElse(null) ?: return false
        val viewport = root.selectId("director_page_viewport").findFirst().orElse(null) ?: UIElement().apply {
            setId("director_page_viewport")
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(54f).width(960f).height(456f) }
            setOverflow(YogaOverflow.HIDDEN)
            canvas.addChild(this)
            changed = true
        }
        val addedPage = !root.selectId("collections_page").findFirst().isPresent
        if (addedPage) {
            val page = directorPage("collections_page").setDisplay(false)
            viewport.addChild(page)
            buildCollections(page)
            changed = true
        }
        listOf("equipment_page", "inventory_page", "collections_page", "journey_page", "fireteam_page")
            .mapNotNull { root.selectId(it).findFirst().orElse(null) }
            .forEach { page ->
                if (page.parent !== viewport) {
                    page.parent?.removeChild(page)
                    viewport.addChild(page)
                    page.layout {
                        it.positionType(YogaPositionType.ABSOLUTE)
                            .left(0f).top(0f).width(960f).height(456f)
                    }
                    changed = true
                }
            }
        val addedTab = !root.selectId("tab_collections").findFirst().isPresent
        if (addedTab) {
            header.addChild(tab("tab_collections", "收藏品", 580f, 10f, 102f))
            changed = true
        }
        if (addedTab) {
            listOf(
                "tab_inventory" to (485f to 92f),
                "tab_collections" to (580f to 102f),
                "tab_journey" to (685f to 82f),
                "tab_fireteam" to (770f to 150f)
            ).forEach { (id, geometry) ->
                root.selectId(id).findFirst().orElse(null)?.layout { it.left(geometry.first).width(geometry.second) }
            }
            root.selectId("tab_indicator").findFirst().orElse(null)?.layout { it.left(485f).width(92f) }
        }
        return changed
    }

    /** Adds the click-driven Perk detail card without replacing an online-edited template. */
    private fun migratePerkDetailCard(root: UIElement): Boolean {
        if (root.selectId("selected_perk_card").findFirst().isPresent) return false
        val inventory = root.selectId("inventory_page").findFirst().orElse(null) ?: return false
        root.selectId("selected_item_description").findFirst().orElse(null)?.setDisplay(false)
        buildPerkDetailCard(inventory)
        root.selectId("selected_item_hint").findFirst().orElse(null)?.let { hint ->
            (hint as? TextElement)?.setText("点击特性查看固定详情")
        }
        return true
    }

    private fun buildInventory(page: UIElement) {
        page.addChildren(
            block("inventory_left_field", 0f, 0f, 628f, 456f, Color.LEFT),
            block("inventory_right_field", 628f, 0f, 332f, 456f, Color.RIGHT),
            block("inventory_divider", 627f, 0f, 1f, 456f, Color.LINE),
            text("equipment_title", "装备", 70f, 24f, 300f, 18f, 12f, Color.SECONDARY),
            text("equipment_count", "0 / 7", 500f, 24f, 92f, 18f, 10f, Color.MUTED),
            block("equipment_rule", 70f, 48f, 522f, 1f, Color.BRIGHT),
            host("equipment_slots", 70f, 58f, 522f, 58f),
            text("inventory_title", "物品", 70f, 145f, 300f, 18f, 12f, Color.SECONDARY),
            text("inventory_count", "0 / 36", 500f, 145f, 92f, 18f, 10f, Color.MUTED),
            block("inventory_rule", 70f, 169f, 522f, 1f, Color.BRIGHT),
            host("inventory_main_slots", 70f, 183f, 428f, 140f),
            text("hotbar_title", "快捷栏", 70f, 335f, 160f, 14f, 9f, Color.MUTED),
            host("inventory_hotbar_slots", 70f, 352f, 428f, 44f),
            text("item_section_title", "装备详情", 28f, 24f, 264f, 18f, 12f, Color.SECONDARY, parentOffsetX = 628f),
            block("item_section_rule", 656f, 48f, 264f, 1f, Color.BRIGHT),
            host("selected_item_icon", 656f, 67f, 64f, 64f),
            text("selected_item_name", "选择一件物品", 732f, 69f, 188f, 34f, 14f, Color.PRIMARY),
            text("selected_item_meta", "查看装备与特性", 732f, 108f, 188f, 22f, 9f, Color.MUTED),
            text("selected_item_stats", "", 656f, 147f, 264f, 40f, 10f, Color.SECONDARY),
            text("perk_title", "特性", 656f, 196f, 120f, 16f, 10f, Color.MUTED),
            host("selected_perk_slots", 656f, 218f, 264f, 48f),
            block("item_detail_rule", 656f, 282f, 264f, 1f, Color.LINE),
            text("selected_item_hint", "点击特性查看固定详情", 656f, 425f, 264f, 15f, 8f, Color.MUTED)
        )
        buildPerkDetailCard(page)
    }

    private fun buildPerkDetailCard(page: UIElement) {
        page.addChildren(
            block("selected_perk_card", 656f, 294f, 264f, 118f, Color.SLOT_EMPTY, Color.LINE),
            text("selected_perk_column", "已选特性", 666f, 304f, 76f, 16f, 8f, Color.MUTED, wrap = true),
            text("selected_perk_name", "点击上方特性", 666f, 328f, 76f, 72f, 10f, Color.PRIMARY, wrap = true),
            block("selected_perk_divider", 750f, 304f, 1f, 98f, Color.LINE),
            text("selected_perk_details", "完整说明会固定显示在这里。", 760f, 304f, 150f, 98f, 8f, Color.SECONDARY, wrap = true)
        )
    }

    private fun buildJourney(page: UIElement) {
        page.addChildren(
            block("journey_left_field", 0f, 0f, 628f, 456f, Color.LEFT),
            block("journey_right_field", 628f, 0f, 332f, 456f, Color.RIGHT),
            text("journey_title", "旅程", 70f, 28f, 470f, 26f, 18f, Color.PRIMARY),
            text("journey_stage", "CURRENT STAGE", 70f, 68f, 470f, 18f, 10f, Color.MUTED),
            block("journey_rule", 70f, 98f, 522f, 1f, Color.BRIGHT),
            text("journey_objective_title", "目标", 70f, 120f, 500f, 28f, 14f, Color.PRIMARY),
            text("journey_objective_description", "", 70f, 160f, 500f, 96f, 10f, Color.SECONDARY, wrap = true),
            host("journey_objective_rows", 70f, 274f, 500f, 142f),
            text("journey_location_title", "当前位置", 656f, 28f, 264f, 18f, 11f, Color.MUTED),
            text("journey_location", "", 656f, 62f, 264f, 58f, 13f, Color.PRIMARY, wrap = true),
            text("journey_coordinates", "", 656f, 130f, 264f, 18f, 9f, Color.MUTED),
            block("journey_side_rule", 656f, 170f, 264f, 1f, Color.LINE),
            text("journey_status", "守护者旅程同步中", 656f, 190f, 264f, 80f, 10f, Color.SECONDARY, wrap = true)
        )
    }

    private fun buildFireteam(page: UIElement) {
        page.addChildren(
            block("fireteam_left_field", 0f, 0f, 628f, 456f, Color.LEFT),
            block("fireteam_right_field", 628f, 0f, 332f, 456f, Color.RIGHT),
            text("fireteam_title", "火力战队", 70f, 28f, 470f, 26f, 18f, Color.PRIMARY),
            text("fireteam_count", "0 ONLINE", 70f, 67f, 470f, 18f, 10f, Color.MUTED),
            block("fireteam_rule", 70f, 98f, 522f, 1f, Color.BRIGHT),
            host("fireteam_rows", 70f, 120f, 522f, 290f),
            text("fireteam_identity_title", "当前守护者", 656f, 28f, 264f, 18f, 11f, Color.MUTED),
            text("fireteam_identity", "", 656f, 62f, 264f, 72f, 14f, Color.PRIMARY, wrap = true),
            block("fireteam_side_rule", 656f, 152f, 264f, 1f, Color.LINE),
            text("fireteam_status", "队伍状态与网络延迟", 656f, 174f, 264f, 50f, 10f, Color.SECONDARY, wrap = true)
        )
    }

    private fun buildFooter(footer: UIElement) {
        footer.addChildren(
            text("footer_left", "TAB  导航界面", 40f, 9f, 300f, 14f, 8f, Color.MUTED),
            text("footer_right", "ESC  返回", 790f, 9f, 130f, 14f, 8f, Color.SECONDARY)
        )
    }

    private fun block(id: String, x: Float, y: Float, width: Float, height: Float, color: Int, border: Int? = null): UIElement =
        UIElement().apply {
            setId(id)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(width).height(height) }
            style {
                it.background(ColorRectTexture(color))
                if (border != null) it.overlay(ColorBorderTexture(1, border))
            }
        }

    private fun host(id: String, x: Float, y: Float, width: Float, height: Float): UIElement =
        UIElement().apply {
            setId(id)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(width).height(height) }
        }

    private fun text(
        id: String,
        value: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        size: Float,
        color: Int,
        wrap: Boolean = false,
        parentOffsetX: Float = 0f
    ): TextElement = TextElement().apply {
        setId(id)
        setText(value)
        setAllowHitTest(false)
        layout {
            it.positionType(YogaPositionType.ABSOLUTE).left(x + parentOffsetX).top(y).width(width).height(height)
        }
        textStyle {
            it.font(DIRECTOR_FONT).fontSize(size).textColor(color).textShadow(false)
            if (wrap) it.textWrap(TextWrap.WRAP).adaptiveHeight(true).lineSpacing(1f)
        }
    }

    private fun tab(id: String, value: String, x: Float, y: Float, width: Float): Button = Button().apply {
        setId(id)
        setText(value)
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(width).height(40f) }
        buttonStyle {
            it.baseTexture(ColorRectTexture(Color.HEADER))
            it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(Color.NAV_HOVER), ColorBorderTexture(1, Color.BRIGHT)))
            it.pressedTexture(ColorRectTexture(Color.SELECTED))
        }
        textStyle { it.font(DIRECTOR_FONT).fontSize(11f).textColor(Color.SECONDARY).textShadow(false) }
    }

    private fun templateDirectory() = LDLib2.getAssetsDir().toPath().resolve("destiny2-mod/resources")
    private fun templatePath() = templateDirectory().resolve(FILE_NAME)
    private fun templateModified() = runCatching { Files.getLastModifiedTime(templatePath()).toMillis() }.getOrDefault(0L)
    private fun provider() = FileResourceProvider(UIResource.INSTANCE.resourceInstance, templateDirectory().toFile())
}
