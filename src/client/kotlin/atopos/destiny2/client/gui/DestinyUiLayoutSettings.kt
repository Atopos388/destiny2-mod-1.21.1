package atopos.destiny2.client.gui

import net.minecraft.client.Minecraft
import java.util.Properties

enum class DestinyUiElementType {
    BUTTON,
    TEXT,
    IMAGE
}

data class DestinyUiElement(
    var id: String,
    var type: DestinyUiElementType,
    var x: Int,
    var y: Int,
    var width: Int,
    var height: Int,
    var text: String = "",
    var texture: String = "",
    var visible: Boolean = true,
    var action: String = "none"
)

object DestinyUiLayoutSettings {
    private val pages = mutableMapOf<String, MutableList<DestinyUiElement>>()

    fun elements(pageId: String): MutableList<DestinyUiElement> {
        ensurePage(pageId)
        return pages.getValue(pageId)
    }

    fun addElement(pageId: String, type: DestinyUiElementType): DestinyUiElement {
        ensurePage(pageId)
        val elements = pages.getValue(pageId)
        val id = "${type.name.lowercase()}_${System.currentTimeMillis() % 100000}"
        val element = when (type) {
            DestinyUiElementType.BUTTON -> DestinyUiElement(id, type, 410, 248, 140, 32, "按钮", "", true, "none")
            DestinyUiElementType.TEXT -> DestinyUiElement(id, type, 420, 252, 120, 18, "文字", "", true, "none")
            DestinyUiElementType.IMAGE -> DestinyUiElement(
                id,
                type,
                386,
                226,
                188,
                88,
                "",
                "destiny2-mod:textures/gui/loot/loot_chest_bg.png",
                true,
                "none"
            )
        }
        elements.add(element)
        return element
    }

    fun removeElement(pageId: String, elementId: String?) {
        if (elementId == null) {
            return
        }
        pages[pageId]?.removeIf { it.id == elementId }
    }

    fun resetPage(pageId: String) {
        pages[pageId] = defaultPage(pageId).toMutableList()
    }

    fun load() {
        pages.clear()
        val file = configFile()
        if (!file.exists()) {
            return
        }

        val properties = Properties()
        file.inputStream().use(properties::load)
        val pageIds = properties.getProperty("pages").orEmpty().split(",").filter(String::isNotBlank)
        pageIds.forEach { pageId ->
            val count = properties.getProperty("$pageId.count")?.toIntOrNull() ?: 0
            val elements = mutableListOf<DestinyUiElement>()
            for (index in 0 until count) {
                val prefix = "$pageId.$index"
                val type = runCatching {
                    DestinyUiElementType.valueOf(properties.getProperty("$prefix.type", "TEXT"))
                }.getOrDefault(DestinyUiElementType.TEXT)
                elements.add(
                    DestinyUiElement(
                        id = properties.getProperty("$prefix.id", "${type.name.lowercase()}_$index"),
                        type = type,
                        x = properties.getProperty("$prefix.x")?.toIntOrNull() ?: 0,
                        y = properties.getProperty("$prefix.y")?.toIntOrNull() ?: 0,
                        width = properties.getProperty("$prefix.width")?.toIntOrNull() ?: 80,
                        height = properties.getProperty("$prefix.height")?.toIntOrNull() ?: 20,
                        text = properties.getProperty("$prefix.text", ""),
                        texture = properties.getProperty("$prefix.texture", ""),
                        visible = properties.getProperty("$prefix.visible")?.toBooleanStrictOrNull() ?: true,
                        action = properties.getProperty("$prefix.action", "none")
                    )
                )
            }
            pages[pageId] = elements
        }
    }

    fun save() {
        val file = configFile()
        file.parentFile?.mkdirs()

        val properties = Properties()
        properties.setProperty("pages", pages.keys.joinToString(","))
        pages.forEach { (pageId, elements) ->
            properties.setProperty("$pageId.count", elements.size.toString())
            elements.forEachIndexed { index, element ->
                val prefix = "$pageId.$index"
                properties.setProperty("$prefix.id", element.id)
                properties.setProperty("$prefix.type", element.type.name)
                properties.setProperty("$prefix.x", element.x.toString())
                properties.setProperty("$prefix.y", element.y.toString())
                properties.setProperty("$prefix.width", element.width.toString())
                properties.setProperty("$prefix.height", element.height.toString())
                properties.setProperty("$prefix.text", element.text)
                properties.setProperty("$prefix.texture", element.texture)
                properties.setProperty("$prefix.visible", element.visible.toString())
                properties.setProperty("$prefix.action", element.action)
            }
        }
        file.outputStream().use { output ->
            properties.store(output, "Destiny 2 Mod UI pages")
        }
    }

    private fun ensurePage(pageId: String) {
        pages.getOrPut(pageId) { defaultPage(pageId).toMutableList() }
    }

    private fun defaultPage(pageId: String): List<DestinyUiElement> {
        return emptyList()
    }

    private fun configFile() = Minecraft.getInstance().gameDirectory
        .resolve("config")
        .resolve("destiny2_ui_pages.properties")
}
