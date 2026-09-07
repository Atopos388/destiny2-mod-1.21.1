package atopos.destiny2.client.gui

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import atopos.destiny2.common.gear.WeaponLore
import net.minecraft.client.Minecraft
import org.appliedenergistics.yoga.YogaOverflow
import org.appliedenergistics.yoga.YogaPositionType
import kotlin.math.ceil

/** Data-driven contents of the editable collections template shell. */
class DestinyCollectionsView(private val ui: UI) {
    private val folders = DestinyCollectionsDataAdapter.snapshot()
    private val overview = element("collections_overview_host")
    private val folder = element("collections_folder_host")
    private var active: DestinyCollectionFolder? = null
    private var activeSection: DestinyCollectionSection? = null
    private var page = 0

    fun bind() {
        overview?.setOverflow(YogaOverflow.HIDDEN)
        folder?.setOverflow(YogaOverflow.HIDDEN)
        buildOverview()
        showOverview()
    }

    private fun buildOverview() {
        val host = overview ?: return
        host.clearAllChildren()
        folders.forEachIndexed { index, collection ->
            val column = index % 3
            val row = index / 3
            val width = if (row == 1) 396f else 260f
            val x = if (row == 1) column * 414f else column * 280f
            val y = row * 150f
            host.addChild(categoryCard(collection, x, y, width, 130f))
        }
    }

    private fun categoryCard(data: DestinyCollectionFolder, x: Float, y: Float, width: Float, height: Float): UIElement =
        UIElement().apply {
            setOverflow(YogaOverflow.HIDDEN)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(width).height(height) }
            style {
                it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT))
                it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
                it.tooltips(data.category.title, data.category.description)
            }
            addChild(label(data.category.glyph, 18f, 19f, 42f, 42f, 24f, DestinyNavigationTemplate.Color.BRIGHT))
            addChild(label(data.category.title, 72f, 18f, width - 92f, 22f, 14f, DestinyNavigationTemplate.Color.PRIMARY))
            addChild(label("${data.sections.size} 个文件夹  //  ${data.entryCount} 项", 72f, 47f, width - 92f, 16f, 9f, DestinyNavigationTemplate.Color.MUTED))
            addChild(label(data.category.description, 18f, 82f, width - 36f, 34f, 9f, DestinyNavigationTemplate.Color.SECONDARY, true))
            addEventListener(UIEvents.MOUSE_ENTER) {
                style { style -> style.background(ColorRectTexture(DestinyNavigationTemplate.Color.HOVER)) }
            }
            addEventListener(UIEvents.MOUSE_LEAVE) {
                style { style -> style.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT)) }
            }
            addEventListener(UIEvents.CLICK) { showFolder(data) }
        }

    private fun showOverview() {
        active = null
        activeSection = null
        page = 0
        overview?.apply { setDisplay(true); setVisible(true) }
        folder?.apply { setDisplay(false); setVisible(false) }
        element("collections_rule")?.apply { setDisplay(true); setVisible(true) }
        text("collections_title")?.setText("收藏品")
        text("collections_subtitle")?.setText("档案总览  //  选择一个分类打开收藏文件夹")
    }

    private fun showFolder(data: DestinyCollectionFolder) {
        active = data
        activeSection = null
        page = 0
        overview?.apply { setDisplay(false); setVisible(false) }
        folder?.apply { setDisplay(true); setVisible(true) }
        element("collections_rule")?.apply { setDisplay(false); setVisible(false) }
        text("collections_title")?.setText("")
        text("collections_subtitle")?.setText("")
        buildSectionIndex(data)
    }

    private fun buildSectionIndex(data: DestinyCollectionFolder) {
        val host = folder ?: return
        host.clearAllChildren()
        host.addChild(actionButton("collection_back", "‹  收藏总览", 0f, 0f, 118f, 30f) { showOverview() })
        host.addChild(label(data.category.title, 136f, 2f, 360f, 24f, 17f, DestinyNavigationTemplate.Color.PRIMARY))
        host.addChild(label("${data.sections.size} 个文件夹  //  ${data.entryCount} 项", 620f, 7f, 200f, 18f, 9f, DestinyNavigationTemplate.Color.MUTED))
        host.addChild(rule(0f, 42f, 820f))

        data.sections.forEachIndexed { index, section ->
            val column = index % 3
            val row = index / 3
            host.addChild(sectionCard(data, section, column * 280f, 62f + row * 112f))
        }
        if (data.sections.isEmpty()) {
            host.addChild(label("这个分类目前没有可用的子文件夹", 0f, 88f, 820f, 30f, 12f, DestinyNavigationTemplate.Color.MUTED))
        }
    }

    private fun sectionCard(folder: DestinyCollectionFolder, section: DestinyCollectionSection, x: Float, y: Float): UIElement =
        UIElement().apply {
            setOverflow(YogaOverflow.HIDDEN)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(260f).height(92f) }
            style {
                it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT))
                it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
                it.tooltips(section.title, section.description)
            }
            addChild(label("▰", 16f, 18f, 34f, 34f, 18f, DestinyNavigationTemplate.Color.BRIGHT))
            addChild(label(section.title, 58f, 14f, 184f, 22f, 12f, DestinyNavigationTemplate.Color.PRIMARY))
            addChild(label("${section.entries.size} 项收藏", 58f, 43f, 184f, 16f, 9f, DestinyNavigationTemplate.Color.MUTED))
            addChild(label(section.description, 16f, 67f, 226f, 16f, 8f, DestinyNavigationTemplate.Color.SECONDARY))
            addEventListener(UIEvents.MOUSE_ENTER) { style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.HOVER)) } }
            addEventListener(UIEvents.MOUSE_LEAVE) { style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT)) } }
            addEventListener(UIEvents.CLICK) { showSection(folder, section, 0) }
        }

    private fun showSection(data: DestinyCollectionFolder, section: DestinyCollectionSection, requestedPage: Int) {
        active = data
        activeSection = section
        val pages = ceil(section.entries.size.coerceAtLeast(1) / ENTRIES_PER_PAGE.toDouble()).toInt().coerceAtLeast(1)
        page = requestedPage.coerceIn(0, pages - 1)
        buildEntries(data, section, pages)
    }

    private fun buildEntries(data: DestinyCollectionFolder, section: DestinyCollectionSection, pages: Int) {
        val host = folder ?: return
        host.clearAllChildren()
        host.addChild(actionButton("collection_back", "‹  ${data.category.title}", 0f, 0f, 118f, 30f) { showFolder(data) })
        host.addChild(label(section.title, 136f, 2f, 400f, 24f, 17f, DestinyNavigationTemplate.Color.PRIMARY))
        host.addChild(label("${section.entries.size} 项", 720f, 7f, 100f, 18f, 9f, DestinyNavigationTemplate.Color.MUTED))
        host.addChild(rule(0f, 42f, 820f))

        val visible = section.entries.drop(page * ENTRIES_PER_PAGE).take(ENTRIES_PER_PAGE)
        if (visible.isEmpty()) {
            host.addChild(label("这个收藏文件夹目前没有已注册内容", 0f, 88f, 820f, 30f, 12f, DestinyNavigationTemplate.Color.MUTED))
        } else {
            visible.forEachIndexed { index, entry ->
                val x = (index % COLUMNS) * 136f
                val y = 58f + (index / COLUMNS) * 59f
                host.addChild(entryCard(data, section, entry, x, y))
            }
        }

        val initial = visible.firstOrNull()
        host.addChild(rule(0f, 360f, 820f))
        host.addChild(label("collection_selection_title", initial?.title ?: "未选择条目", 0f, 370f, 250f, 18f, 11f, DestinyNavigationTemplate.Color.PRIMARY))
        host.addChild(label("collection_selection_meta", initial?.meta.orEmpty(), 260f, 372f, 260f, 16f, 8f, DestinyNavigationTemplate.Color.MUTED))
        host.addChild(label("collection_selection_description", initial?.description ?: "", 0f, 392f, 570f, 24f, 8f, DestinyNavigationTemplate.Color.SECONDARY, true))
        host.addChild(actionButton("collection_prev", "‹", 650f, 374f, 42f, 30f) {
            showSection(data, section, Math.floorMod(page - 1, pages))
        })
        host.addChild(label("${page + 1} / $pages", 700f, 382f, 62f, 16f, 9f, DestinyNavigationTemplate.Color.SECONDARY))
        host.addChild(actionButton("collection_next", "›", 778f, 374f, 42f, 30f) {
            showSection(data, section, Math.floorMod(page + 1, pages))
        })
    }

    private fun entryCard(data: DestinyCollectionFolder, section: DestinyCollectionSection, entry: DestinyCollectionEntry, x: Float, y: Float): UIElement = UIElement().apply {
        setOverflow(YogaOverflow.HIDDEN)
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(126f).height(50f) }
        style {
            it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY))
            it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
            it.tooltips(entry.title, entry.meta, entry.description)
        }
        when {
            !entry.item.isEmpty -> addChild(ItemSlot().apply {
                setItem(entry.item.copy())
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(4f).top(4f).width(42f).height(42f).aspectRatio(1f) }
                slotStyle { it.showItemTooltips(false).showSlotOverlayOnlyEmpty(false).slotOverlay(ColorRectTexture(0x00000000)) }
            })
            entry.icon != null && Minecraft.getInstance().resourceManager.getResource(entry.icon).isPresent -> addChild(UIElement().apply {
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(7f).top(7f).width(36f).height(36f).aspectRatio(1f) }
                style { it.background(SpriteTexture.of(entry.icon)) }
            })
            else -> addChild(label("◇", 12f, 13f, 26f, 26f, 16f, DestinyNavigationTemplate.Color.BRIGHT))
        }
        addChild(label(entry.title, 51f, 7f, 70f, 35f, 8f, DestinyNavigationTemplate.Color.PRIMARY, true))
        addEventListener(UIEvents.MOUSE_ENTER) {
            style { style -> style.background(ColorRectTexture(DestinyNavigationTemplate.Color.HOVER)) }
        }
        addEventListener(UIEvents.MOUSE_LEAVE) {
            style { style -> style.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY)) }
        }
        addEventListener(UIEvents.CLICK) {
            if (entry.weapon != null) {
                showWeapon(data, section, entry, 0, 0, 0)
            } else {
                showArchiveEntry(data, section, entry, 0)
            }
        }
    }

    private fun showArchiveEntry(
        data: DestinyCollectionFolder,
        section: DestinyCollectionSection,
        entry: DestinyCollectionEntry,
        requestedRelatedPage: Int
    ) {
        val host = folder ?: return
        val related = section.entries.filterNot { it.id == entry.id }
        val relatedPages = ceil(related.size.coerceAtLeast(1) / RELATED_PER_PAGE.toDouble()).toInt().coerceAtLeast(1)
        val relatedPage = requestedRelatedPage.coerceIn(0, relatedPages - 1)
        val visibleRelated = related.drop(relatedPage * RELATED_PER_PAGE).take(RELATED_PER_PAGE)
        host.clearAllChildren()
        host.addChild(actionButton("archive_back", "‹  ${section.title}", 0f, 0f, 132f, 30f) {
            showSection(data, section, page)
        })
        host.addChild(label("${data.category.title}档案", 150f, 2f, 300f, 24f, 17f, DestinyNavigationTemplate.Color.PRIMARY))
        host.addChild(rule(0f, 42f, 820f))

        host.addChild(UIElement().apply {
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(58f).width(282f).height(346f) }
            style {
                it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY))
                it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
            }
        })
        when {
            !entry.item.isEmpty -> host.addChild(ItemSlot().apply {
                setItem(entry.item.copy())
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(16f).top(76f).width(82f).height(82f).aspectRatio(1f) }
                slotStyle {
                    it.showItemTooltips(false).showSlotOverlayOnlyEmpty(false)
                        .slotOverlay(GuiTextureGroup.of(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT), ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT)))
                }
            })
            entry.icon != null && Minecraft.getInstance().resourceManager.getResource(entry.icon).isPresent -> host.addChild(UIElement().apply {
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(20f).top(80f).width(74f).height(74f).aspectRatio(1f) }
                style { it.background(SpriteTexture.of(entry.icon)) }
            })
            else -> host.addChild(label(data.category.glyph, 38f, 96f, 42f, 42f, 26f, DestinyNavigationTemplate.Color.BRIGHT))
        }
        host.addChild(label(entry.title, 114f, 75f, 150f, 44f, 14f, DestinyNavigationTemplate.Color.PRIMARY, true))
        host.addChild(label(entry.meta, 114f, 126f, 150f, 34f, 8f, DestinyNavigationTemplate.Color.MUTED, true))
        host.addChild(rule(16f, 174f, 250f))
        host.addChild(label(entry.description, 16f, 190f, 250f, 190f, 9f, DestinyNavigationTemplate.Color.SECONDARY, true))

        host.addChild(label("档案信息", 314f, 58f, 220f, 18f, 10f, DestinyNavigationTemplate.Color.MUTED))
        if (entry.facts.isEmpty()) {
            host.addChild(label("当前条目没有额外注册字段", 314f, 88f, 506f, 22f, 9f, DestinyNavigationTemplate.Color.MUTED))
        } else {
            entry.facts.take(5).forEachIndexed { index, fact ->
                host.addChild(label(fact.first, 314f, 86f + index * 24f, 150f, 18f, 9f, DestinyNavigationTemplate.Color.MUTED))
                host.addChild(label(fact.second, 470f, 86f + index * 24f, 350f, 18f, 9f, DestinyNavigationTemplate.Color.PRIMARY))
            }
        }
        host.addChild(rule(314f, 210f, 506f))
        host.addChild(label("相关收藏  //  ${section.title}", 314f, 220f, 330f, 18f, 10f, DestinyNavigationTemplate.Color.MUTED))
        if (relatedPages > 1) {
            host.addChild(actionButton("archive_related_prev", "‹", 704f, 214f, 32f, 24f) {
                showArchiveEntry(data, section, entry, Math.floorMod(relatedPage - 1, relatedPages))
            })
            host.addChild(label("${relatedPage + 1}/$relatedPages", 744f, 220f, 36f, 14f, 8f, DestinyNavigationTemplate.Color.MUTED))
            host.addChild(actionButton("archive_related_next", "›", 788f, 214f, 32f, 24f) {
                showArchiveEntry(data, section, entry, Math.floorMod(relatedPage + 1, relatedPages))
            })
        }
        if (visibleRelated.isEmpty()) {
            host.addChild(label("这个文件夹没有其他相关条目", 314f, 254f, 506f, 24f, 9f, DestinyNavigationTemplate.Color.MUTED))
        } else {
            visibleRelated.forEachIndexed { index, relatedEntry ->
                host.addChild(relatedCard(data, section, relatedEntry, 314f + (index % 3) * 170f, 250f + (index / 3) * 54f))
            }
        }
    }

    private fun relatedCard(
        data: DestinyCollectionFolder,
        section: DestinyCollectionSection,
        entry: DestinyCollectionEntry,
        x: Float,
        y: Float
    ): UIElement = UIElement().apply {
        setOverflow(YogaOverflow.HIDDEN)
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(158f).height(46f) }
        style {
            it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY))
            it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
            it.tooltips(entry.title, entry.meta)
        }
        addChild(label(if (entry.weapon != null) "◆" else data.category.glyph, 10f, 11f, 24f, 24f, 14f, DestinyNavigationTemplate.Color.BRIGHT))
        addChild(label(entry.title, 40f, 7f, 110f, 32f, 8f, DestinyNavigationTemplate.Color.PRIMARY, true))
        addEventListener(UIEvents.MOUSE_ENTER) { style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.HOVER)) } }
        addEventListener(UIEvents.MOUSE_LEAVE) { style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY)) } }
        addEventListener(UIEvents.CLICK) {
            if (entry.weapon != null) showWeapon(data, section, entry, 0, 0, 0)
            else showArchiveEntry(data, section, entry, 0)
        }
    }

    private fun showWeapon(
        data: DestinyCollectionFolder,
        section: DestinyCollectionSection,
        entry: DestinyCollectionEntry,
        selectedSlot: Int,
        requestedPerkPage: Int,
        requestedStoryPage: Int
    ) {
        val weapon = entry.weapon ?: return
        val host = folder ?: return
        val slotIndex = selectedSlot.coerceIn(0, 3)
        val column = weapon.columns[slotIndex]
        val perkPages = ceil(column.perks.size.coerceAtLeast(1) / PERKS_PER_PAGE.toDouble()).toInt().coerceAtLeast(1)
        val perkPage = requestedPerkPage.coerceIn(0, perkPages - 1)
        val storyPages = WeaponLore.pages(entry.description)
        val storyPage = requestedStoryPage.coerceIn(0, storyPages.lastIndex)
        host.clearAllChildren()
        host.addChild(actionButton("weapon_back", "‹  ${section.title}", 0f, 0f, 132f, 30f) {
            showSection(data, section, page)
        })
        host.addChild(label("武器档案", 150f, 2f, 300f, 24f, 17f, DestinyNavigationTemplate.Color.PRIMARY))
        host.addChild(rule(0f, 42f, 820f))

        host.addChild(UIElement().apply {
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(0f).top(58f).width(282f).height(330f) }
            style {
                it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY))
                it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
            }
        })
        host.addChild(ItemSlot().apply {
            setItem(entry.item.copy())
            setAllowHitTest(false)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(16f).top(76f).width(76f).height(76f).aspectRatio(1f) }
            slotStyle {
                it.showItemTooltips(false).showSlotOverlayOnlyEmpty(false)
                    .slotOverlay(GuiTextureGroup.of(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT), ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT)))
            }
        })
        host.addChild(label(entry.title, 108f, 73f, 158f, 42f, 14f, DestinyNavigationTemplate.Color.PRIMARY, true))
        host.addChild(label(entry.meta, 108f, 123f, 158f, 34f, 8f, DestinyNavigationTemplate.Color.MUTED, true))
        host.addChild(rule(16f, 171f, 250f))
        val stats = buildList {
            add("伤害                 ${"%.2f".format(weapon.damage)}")
            add("精准倍率             ×${"%.2f".format(weapon.precisionMultiplier)}")
            add("射速                 ${weapon.roundsPerMinute} RPM")
            weapon.magazineSize?.let { add("弹匣                 $it") }
            weapon.reloadTicks?.let { add("装填                 ${"%.2f".format(it / 20f)} 秒") }
            weapon.range?.let { add("射程                 ${"%.0f".format(it)} 格") }
            if (weapon.explosionRadius > 0f) add("爆炸半径             ${"%.2f".format(weapon.explosionRadius)}")
        }
        host.addChild(label(stats.joinToString("\n"), 16f, 188f, 250f, 134f, 9f, DestinyNavigationTemplate.Color.SECONDARY, true))
        host.addChild(label("右侧武器详情收录了这把武器的传奇故事与特性档案。", 16f, 330f, 250f, 44f, 8f, DestinyNavigationTemplate.Color.MUTED, true))

        host.addChild(label("PERK 槽位", 314f, 58f, 200f, 18f, 10f, DestinyNavigationTemplate.Color.MUTED))
        weapon.columns.forEachIndexed { index, perkColumn ->
            host.addChild(perkSlotButton(index, perkColumn, 314f + index * 126f, 82f, index == slotIndex) {
                showWeapon(data, section, entry, index, 0, storyPage)
            })
        }
        host.addChild(label("槽位 ${slotIndex + 1}  //  ${column.label}", 314f, 132f, 330f, 20f, 12f, DestinyNavigationTemplate.Color.PRIMARY))
        if (perkPages > 1) {
            host.addChild(actionButton("weapon_perk_prev", "‹", 704f, 128f, 32f, 24f) {
                showWeapon(data, section, entry, slotIndex, Math.floorMod(perkPage - 1, perkPages), storyPage)
            })
            host.addChild(label("${perkPage + 1}/$perkPages", 744f, 134f, 36f, 14f, 8f, DestinyNavigationTemplate.Color.MUTED))
            host.addChild(actionButton("weapon_perk_next", "›", 788f, 128f, 32f, 24f) {
                showWeapon(data, section, entry, slotIndex, Math.floorMod(perkPage + 1, perkPages), storyPage)
            })
        }
        host.addChild(rule(314f, 158f, 506f))
        if (column.perks.isEmpty()) {
            host.addChild(label("这个槽位当前没有已注册 Perk", 314f, 184f, 506f, 24f, 10f, DestinyNavigationTemplate.Color.MUTED))
        } else {
            column.perks.drop(perkPage * PERKS_PER_PAGE).take(PERKS_PER_PAGE).forEachIndexed { index, perk ->
                host.addChild(weaponPerkCard(perk, 314f + (index % 3) * 170f, 176f + (index / 3) * 58f))
            }
        }
        host.addChild(UIElement().apply {
            setOverflow(YogaOverflow.HIDDEN)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(314f).top(304f).width(506f).height(112f) }
            style {
                it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY))
                it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
            }
        })
        host.addChild(label("weapon_perk_column", "武器传奇故事  //  ${storyPage + 1}/${storyPages.size}", 326f, 316f, 136f, 16f, 8f, DestinyNavigationTemplate.Color.MUTED, true))
        host.addChild(actionButton("weapon_lore", "武器传奇故事\n点击返回故事", 326f, 340f, 136f, 38f) {
            text("weapon_perk_column")?.setText("武器传奇故事  //  ${storyPage + 1}/${storyPages.size}")
            text("weapon_perk_description")?.setText(storyPages[storyPage])
            setStoryControlsVisible(true)
        })
        host.addChild(actionButton("weapon_story_prev", "‹", 326f, 384f, 32f, 20f) {
            showWeapon(data, section, entry, slotIndex, perkPage, Math.floorMod(storyPage - 1, storyPages.size))
        })
        host.addChild(label("weapon_story_page", "${storyPage + 1}/${storyPages.size}", 364f, 387f, 60f, 14f, 8f, DestinyNavigationTemplate.Color.MUTED))
        host.addChild(actionButton("weapon_story_next", "›", 430f, 384f, 32f, 20f) {
            showWeapon(data, section, entry, slotIndex, perkPage, Math.floorMod(storyPage + 1, storyPages.size))
        })
        host.addChild(UIElement().apply {
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(474f).top(316f).width(1f).height(88f) }
            style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.LINE)) }
        })
        host.addChild(label("weapon_perk_description", storyPages[storyPage], 488f, 316f, 318f, 88f, 8f, DestinyNavigationTemplate.Color.SECONDARY, true))
    }

    private fun perkSlotButton(
        index: Int,
        column: DestinyCollectionPerkColumn,
        x: Float,
        y: Float,
        selected: Boolean,
        click: () -> Unit
    ): Button = actionButton("weapon_perk_slot_${index + 1}", "${index + 1}\n${column.label}", x, y, 112f, 40f, click).apply {
        buttonStyle {
            it.baseTexture(GuiTextureGroup.of(
                ColorRectTexture(if (selected) DestinyNavigationTemplate.Color.SELECTED else DestinyNavigationTemplate.Color.SLOT),
                ColorBorderTexture(1, if (selected) DestinyNavigationTemplate.Color.BRIGHT else DestinyNavigationTemplate.Color.LINE)
            ))
            it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(DestinyNavigationTemplate.Color.HOVER), ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT)))
            it.pressedTexture(ColorRectTexture(DestinyNavigationTemplate.Color.SELECTED))
        }
        textStyle {
            it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(8f)
                .textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false)
                .textWrap(TextWrap.WRAP).adaptiveHeight(false)
        }
    }

    private fun weaponPerkCard(perk: DestinyCollectionPerk, x: Float, y: Float): UIElement = UIElement().apply {
        setOverflow(YogaOverflow.HIDDEN)
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(158f).height(50f) }
        style {
            it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY))
            it.overlay(ColorBorderTexture(1, DestinyNavigationTemplate.Color.LINE))
        }
        if (Minecraft.getInstance().resourceManager.getResource(perk.icon).isPresent) {
            addChild(UIElement().apply {
                setAllowHitTest(false)
                layout { it.positionType(YogaPositionType.ABSOLUTE).left(6f).top(6f).width(40f).height(40f).aspectRatio(1f) }
                style { it.background(SpriteTexture.of(perk.icon)) }
            })
        } else {
            addChild(label("◇", 14f, 14f, 24f, 24f, 15f, DestinyNavigationTemplate.Color.BRIGHT))
        }
        addChild(label(perk.title, 52f, 11f, 100f, 30f, 9f, DestinyNavigationTemplate.Color.PRIMARY, true))
        addEventListener(UIEvents.MOUSE_ENTER) { style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.HOVER)) } }
        addEventListener(UIEvents.MOUSE_LEAVE) { style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT_EMPTY)) } }
        addEventListener(UIEvents.CLICK) {
            text("weapon_perk_column")?.setText("特性  //  ${perk.title}")
            text("weapon_perk_description")?.setText(perk.description)
            setStoryControlsVisible(false)
        }
    }

    private fun setStoryControlsVisible(visible: Boolean) {
        listOf("weapon_story_prev", "weapon_story_page", "weapon_story_next").forEach { id ->
            element(id)?.apply {
                setDisplay(visible)
                setVisible(visible)
            }
        }
    }

    private fun actionButton(id: String, value: String, x: Float, y: Float, width: Float, height: Float, click: () -> Unit): Button =
        Button().apply {
            setId(id)
            setText(value)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(width).height(height) }
            buttonStyle {
                it.baseTexture(ColorRectTexture(DestinyNavigationTemplate.Color.SLOT))
                it.hoverTexture(GuiTextureGroup.of(ColorRectTexture(DestinyNavigationTemplate.Color.HOVER), ColorBorderTexture(1, DestinyNavigationTemplate.Color.BRIGHT)))
                it.pressedTexture(ColorRectTexture(DestinyNavigationTemplate.Color.SELECTED))
            }
            textStyle { it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(9f).textColor(DestinyNavigationTemplate.Color.PRIMARY).textShadow(false) }
            setOnClick { click() }
        }

    private fun label(id: String, value: String, x: Float, y: Float, width: Float, height: Float, size: Float, color: Int, wrap: Boolean = false): TextElement =
        label(value, x, y, width, height, size, color, wrap).apply { setId(id) }

    private fun label(value: String, x: Float, y: Float, width: Float, height: Float, size: Float, color: Int, wrap: Boolean = false): TextElement =
        TextElement().apply {
            setText(value)
            setAllowHitTest(false)
            setOverflow(YogaOverflow.HIDDEN)
            layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(width).height(height) }
            textStyle {
                it.font(DestinyNavigationTemplate.DIRECTOR_FONT).fontSize(size).textColor(color).textShadow(false)
                if (wrap) it.textWrap(TextWrap.WRAP).adaptiveHeight(false).lineSpacing(1f)
            }
        }

    private fun rule(x: Float, y: Float, width: Float): UIElement = UIElement().apply {
        setAllowHitTest(false)
        layout { it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(width).height(1f) }
        style { it.background(ColorRectTexture(DestinyNavigationTemplate.Color.LINE)) }
    }

    private fun element(id: String): UIElement? = ui.rootElement.selectId(id).findFirst().orElse(null)
    private fun text(id: String): TextElement? = ui.rootElement.selectId(id, TextElement::class.java).findFirst().orElse(null)

    companion object {
        private const val COLUMNS = 6
        private const val ENTRIES_PER_PAGE = 30
        private const val PERKS_PER_PAGE = 6
        private const val RELATED_PER_PAGE = 9
    }
}
