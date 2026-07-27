package atopos.destiny2.client.gear

import com.lowdragmc.lowdraglib2.LDLib2
import com.lowdragmc.lowdraglib2.editor.resource.FilePath
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider
import com.lowdragmc.lowdraglib2.editor.resource.UIResource
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.utils.XmlUtils
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import java.nio.file.Files
import kotlin.math.roundToInt

/** Owns the creator-editable LDLib2 template and its packaged XML fallback. */
object DestinyPerkTooltipTemplate {
    private const val TEMPLATE_FILE = "gear_perk_tooltip_v7.ui.nbt"
    private val packagedLayout = ResourceLocation.fromNamespaceAndPath(
        "destiny2-mod",
        "ui/gear_perk_tooltip.xml"
    )

    fun prepareVisualEditor() {
        ensureTemplate()
    }

    fun createUI(): UI {
        ensureTemplate()
        return runCatching {
            provider().getResource(FilePath(templatePath().toFile()))?.createUI()
        }.getOrNull() ?: loadPackagedUI()
    }

    /** Resolves the editable template into the runtime-safe visual tokens. */
    fun resolveTheme(availableWidth: Int): DestinyPerkTooltipTheme {
        val maximum = (availableWidth - 16).coerceAtLeast(180).coerceAtMost(220)
        return runCatching {
            val ui = createUI()
            val modularUI = ModularUI(ui)
            modularUI.init(availableWidth.coerceAtLeast(240), 240)
            DestinyPerkTooltipTheme(
                width = modularUI.width.roundToInt().coerceIn(180, maximum),
                background = ui.colorToken("theme_background", DestinyPerkTooltipTheme.DEFAULT.background),
                surface = ui.colorToken("theme_surface", DestinyPerkTooltipTheme.DEFAULT.surface),
                border = ui.colorToken("theme_border", DestinyPerkTooltipTheme.DEFAULT.border),
                mutedText = ui.colorToken("theme_muted", DestinyPerkTooltipTheme.DEFAULT.mutedText),
                descriptionText = ui.colorToken("theme_description", DestinyPerkTooltipTheme.DEFAULT.descriptionText)
            )
        }.getOrDefault(DestinyPerkTooltipTheme.DEFAULT.copy(width = 218.coerceAtMost(maximum)))
    }

    private fun ensureTemplate() {
        val target = templatePath()
        if (Files.exists(target)) return
        Files.createDirectories(target.parent)
        val template = loadPackagedUI().toTemplate()
        check(provider().addResource(FilePath(target.toFile()), template)) {
            "无法创建 LDLib2 Perk Tooltip 模板：$target"
        }
    }

    private fun loadPackagedUI(): UI {
        val document = Minecraft.getInstance().resourceManager
            .getResource(packagedLayout)
            .orElseThrow { IllegalStateException("缺少 Perk Tooltip 布局：$packagedLayout") }
            .open()
            .use(XmlUtils::loadXml)
            ?: error("无法解析 Perk Tooltip 布局：$packagedLayout")
        return UI.of(document)
    }

    private fun templateDirectory() = LDLib2.getAssetsDir().toPath().resolve("destiny2-mod/resources")

    private fun templatePath() = templateDirectory().resolve(TEMPLATE_FILE)

    private fun provider(): FileResourceProvider<UITemplate> =
        FileResourceProvider(UIResource.INSTANCE.resourceInstance, templateDirectory().toFile())

    private fun UI.colorToken(id: String, fallback: Int): Int {
        val token = rootElement.selectId(id).findFirst().orElse(null) ?: return fallback
        return (token.style.backgroundTexture() as? ColorRectTexture)?.color ?: fallback
    }
}

data class DestinyPerkTooltipTheme(
    val width: Int,
    val background: Int,
    val surface: Int,
    val border: Int,
    val mutedText: Int,
    val descriptionText: Int
) {
    companion object {
        val DEFAULT = DestinyPerkTooltipTheme(
            width = 218,
            background = 0xF20A0F15.toInt(),
            surface = 0xF016202A.toInt(),
            border = 0xD58B9AA7.toInt(),
            mutedText = 0xFFA6B1BC.toInt(),
            descriptionText = 0xFFC7CED5.toInt()
        )
    }
}
