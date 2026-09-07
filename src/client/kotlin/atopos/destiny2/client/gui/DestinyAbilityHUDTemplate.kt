package atopos.destiny2.client.gui

import com.lowdragmc.lowdraglib2.LDLib2
import com.lowdragmc.lowdraglib2.editor.resource.FilePath
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider
import com.lowdragmc.lowdraglib2.editor.resource.UIResource
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate
import net.fabricmc.loader.api.FabricLoader
import org.appliedenergistics.yoga.YogaPositionType
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.min
import kotlin.math.roundToInt

/** Creator-editable LDLib2 layout and the single source of truth for live HUD geometry. */
object DestinyAbilityHUDTemplate {
    const val DESIGN_WIDTH = 198
    const val DESIGN_HEIGHT = 48
    private const val SAFE_MARGIN = 8
    private const val HOTBAR_BASELINE_BOTTOM = 0
    private const val HUD_SIZE_MULTIPLIER = 0.85f
    private const val REFERENCE_VIEWPORT_WIDTH = 640f
    private const val REFERENCE_VIEWPORT_HEIGHT = 360f
    private const val FILE_NAME = "ability_hud.ui.nbt"
    private const val TEMPLATE_VERSION_ID = "ability_hud_template_v8"

    private var cachedWidth = -1
    private var cachedHeight = -1
    private var cachedModified = Long.MIN_VALUE
    private var validatedModified = Long.MIN_VALUE
    private var cachedLayout: AbilityHudLayout? = null
    private val refreshGate = HudTemplateRefreshGate()

    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    data class AbilityHudLayout(
        val cluster: Rect,
        val superSocket: Rect,
        val superMeter: Rect,
        val grenadeSocket: Rect,
        val meleeSocket: Rect,
        val classSocket: Rect,
        val weaponSlot: Rect,
        val scale: Float
    )

    fun prepare() {
        backupLegacyConfiguration()
        ensureTemplate()
    }

    /** Registers the shared Destiny resource folder in LDLib's creator browser. */
    fun prepareVisualEditor() {
        DestinyAspectScreen.prepareVisualEditor()
        prepare()
    }

    fun loadForEditor(): UITemplate {
        prepareVisualEditor()
        return provider().getResource(FilePath(templatePath().toFile()))
            ?: error("Unable to read ability HUD LDLib2 template: ${templatePath()}")
    }

    fun saveFromEditor(template: UITemplate) {
        Files.createDirectories(templatePath().parent)
        check(provider().addResource(FilePath(templatePath().toFile()), template)) {
            "Unable to save ability HUD LDLib2 template: ${templatePath()}"
        }
        invalidate()
    }

    fun reset() {
        Files.createDirectories(templatePath().parent)
        Files.deleteIfExists(templatePath())
        writeDefaultTemplate()
        invalidate()
    }

    fun summary(): String = "LDLib2 template=${templatePath().fileName}, canvas=${DESIGN_WIDTH}x${DESIGN_HEIGHT}, uniform-scale"

    fun resolve(screenWidth: Int, screenHeight: Int): AbilityHudLayout {
        // File metadata belongs to editor hot reload, not the per-frame rendering path.
        cachedLayout?.takeIf {
            cachedWidth == screenWidth && cachedHeight == screenHeight && !refreshGate.shouldCheck()
        }?.let { return it }
        val observedModified = templateModified()
        cachedLayout?.takeIf {
            cachedWidth == screenWidth && cachedHeight == screenHeight && cachedModified == observedModified
        }?.let { return it }

        ensureTemplate(observedModified)
        val modified = templateModified()
        val ui = provider().getResource(FilePath(templatePath().toFile()))?.createUI()
            ?: UI.of(createDefaultRoot())
        val modular = ModularUI.of(ui)
        modular.init(screenWidth.coerceAtLeast(1), screenHeight.coerceAtLeast(1))

        val clusterElement = modular.getElementById("ability_cluster")
        val authoredCluster = clusterElement?.toRect() ?: Rect(SAFE_MARGIN, screenHeight - HOTBAR_BASELINE_BOTTOM - DESIGN_HEIGHT, DESIGN_WIDTH, DESIGN_HEIGHT)
        val scale = uniformScale(screenWidth, screenHeight, authoredCluster.width, authoredCluster.height)
        val scaledWidth = (authoredCluster.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (authoredCluster.height * scale).roundToInt().coerceAtLeast(1)
        val originX = authoredCluster.x.coerceIn(SAFE_MARGIN, (screenWidth - SAFE_MARGIN - scaledWidth).coerceAtLeast(SAFE_MARGIN))
        val authoredBottom = (screenHeight - authoredCluster.y - authoredCluster.height).coerceAtLeast(0)
        val safeBottom = authoredBottom.coerceAtLeast(HOTBAR_BASELINE_BOTTOM)
        val originY = (screenHeight - safeBottom - scaledHeight)
            .coerceIn(SAFE_MARGIN, (screenHeight - HOTBAR_BASELINE_BOTTOM - scaledHeight).coerceAtLeast(SAFE_MARGIN))

        fun child(id: String, fallback: Rect, square: Boolean = false): Rect {
            val source = modular.getElementById(id)?.toRectRelativeTo(clusterElement) ?: fallback
            val width = if (square) min(source.width, source.height) else source.width
            val height = if (square) width else source.height
            return Rect(
                originX + (source.x * scale).roundToInt(),
                originY + (source.y * scale).roundToInt(),
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1)
            )
        }

        val result = AbilityHudLayout(
            cluster = Rect(originX, originY, scaledWidth, scaledHeight),
            superSocket = child("super_socket", Rect(0, 2, 36, 36), square = true),
            superMeter = child("super_meter", Rect(36, 19, 162, 2)),
            grenadeSocket = child("grenade_socket", Rect(36, 24, 22, 22), square = true),
            meleeSocket = child("melee_socket", Rect(60, 24, 22, 22), square = true),
            classSocket = child("class_socket", Rect(84, 24, 22, 22), square = true),
            weaponSlot = child("weapon_slot", Rect(110, 24, 88, 22)),
            scale = scale
        )
        modular.onRemoved()
        cachedWidth = screenWidth
        cachedHeight = screenHeight
        cachedModified = modified
        cachedLayout = result
        return result
    }

    fun invalidate() {
        refreshGate.reset()
        cachedLayout = null
        cachedWidth = -1
        cachedHeight = -1
        cachedModified = Long.MIN_VALUE
        validatedModified = Long.MIN_VALUE
    }

    internal fun uniformScale(screenWidth: Int, screenHeight: Int, width: Int, height: Int): Float {
        val availableWidth = (screenWidth - SAFE_MARGIN * 2).coerceAtLeast(1)
        val availableHeight = (screenHeight - HOTBAR_BASELINE_BOTTOM - SAFE_MARGIN).coerceAtLeast(1)
        val viewportScale = min(
            screenWidth.coerceAtLeast(1) / REFERENCE_VIEWPORT_WIDTH,
            screenHeight.coerceAtLeast(1) / REFERENCE_VIEWPORT_HEIGHT
        )
        val fitScale = min(
            availableWidth / width.coerceAtLeast(1).toFloat(),
            availableHeight / height.coerceAtLeast(1).toFloat()
        )
        return (min(viewportScale, fitScale) * HUD_SIZE_MULTIPLIER).coerceAtLeast(0.01f)
    }

    private fun ensureTemplate(observedModified: Long = templateModified()) {
        if (Files.notExists(templatePath())) {
            writeDefaultTemplate()
            validatedModified = templateModified()
            return
        }
        if (validatedModified == observedModified) return

        val current = provider().getResource(FilePath(templatePath().toFile()))?.createUI()
        if (current?.rootElement?.selectId(TEMPLATE_VERSION_ID)?.findFirst()?.isPresent != true) {
            reset()
            validatedModified = templateModified()
        } else {
            validatedModified = observedModified
        }
    }

    private fun writeDefaultTemplate() {
        Files.createDirectories(templatePath().parent)
        check(provider().addResource(FilePath(templatePath().toFile()), UITemplate.of(createDefaultRoot()))) {
            "Unable to create ability HUD LDLib2 template: ${templatePath()}"
        }
    }

    private fun createDefaultRoot(): UIElement {
        val root = UIElement().setId("ability_hud_root").setActive(false)
        root.layout { it.widthPercent(100f).heightPercent(100f) }
        root.addChild(UIElement().setId(TEMPLATE_VERSION_ID).setActive(false).setDisplay(false))

        val cluster = UIElement().setId("ability_cluster").setActive(false)
        cluster.layout {
            it.positionType(YogaPositionType.ABSOLUTE)
                .left(SAFE_MARGIN.toFloat()).bottom(HOTBAR_BASELINE_BOTTOM.toFloat())
                .width(DESIGN_WIDTH.toFloat()).height(DESIGN_HEIGHT.toFloat())
        }
        cluster.style {
            it.background(ColorRectTexture(0x12070B10))
            it.overlay(ColorBorderTexture(1, 0x3DE8F0F5))
        }

        fun anchor(id: String, x: Float, y: Float, width: Float, height: Float, color: Int, preview: Float): UIElement =
            UIElement().setId(id).setActive(false).layout {
                it.positionType(YogaPositionType.ABSOLUTE).left(x).top(y).width(width).height(height)
            }.style {
                it.background(GuiTextureGroup.of(ColorRectTexture(0x5E101922), ColorBorderTexture(1, color)))
            }.apply {
                addChild(UIElement().setId("${id}_preview").setActive(false).layout {
                    it.positionType(YogaPositionType.ABSOLUTE)
                        .left(2f).right(2f).bottom(2f)
                        .height((height - 4f).coerceAtLeast(1f) * preview.coerceIn(0f, 1f))
                }.style { it.background(ColorRectTexture(color and 0x66FFFFFF)) })
            }

        cluster.addChildren(
            anchor("super_socket", 0f, 2f, 36f, 36f, 0xCCE8F0F5.toInt(), 0.75f),
            anchor("super_meter", 36f, 19f, 162f, 2f, 0xE6F4F8FB.toInt(), 0.75f),
            anchor("grenade_socket", 36f, 24f, 22f, 22f, 0xAACED7DE.toInt(), 0.25f),
            anchor("melee_socket", 60f, 24f, 22f, 22f, 0xAACED7DE.toInt(), 0.6f),
            anchor("class_socket", 84f, 24f, 22f, 22f, 0xAACED7DE.toInt(), 1f),
            anchor("weapon_slot", 110f, 24f, 88f, 22f, 0x88AEB9C2.toInt(), 0f)
        )
        root.addChild(cluster)
        return root
    }

    private fun UIElement.toRect(): Rect = Rect(
        positionX.roundToInt(), positionY.roundToInt(), sizeWidth.roundToInt().coerceAtLeast(1), sizeHeight.roundToInt().coerceAtLeast(1)
    )

    private fun UIElement.toRectRelativeTo(parent: UIElement?): Rect {
        val parentX = parent?.positionX ?: 0f
        val parentY = parent?.positionY ?: 0f
        return Rect(
            (positionX - parentX).roundToInt(), (positionY - parentY).roundToInt(),
            sizeWidth.roundToInt().coerceAtLeast(1), sizeHeight.roundToInt().coerceAtLeast(1)
        )
    }

    private fun templateDirectory() = LDLib2.getAssetsDir().toPath().resolve("destiny2-mod/resources")
    private fun templatePath() = templateDirectory().resolve(FILE_NAME)
    private fun templateModified() = runCatching { Files.getLastModifiedTime(templatePath()).toMillis() }.getOrDefault(0L)
    private fun provider() = FileResourceProvider(UIResource.INSTANCE.resourceInstance, templateDirectory().toFile())

    private fun backupLegacyConfiguration() {
        val legacy = FabricLoader.getInstance().configDir.resolve("destiny2_hud")
        if (Files.notExists(legacy)) return
        val backup = legacy.resolveSibling("destiny2_hud_legacy_backup")
        if (Files.notExists(backup)) {
            runCatching { Files.move(legacy, backup, StandardCopyOption.ATOMIC_MOVE) }
                .recoverCatching { Files.move(legacy, backup) }
        }
    }
}

/** Render-thread gate; explicit editor saves and viewport changes bypass the cached layout. */
class HudTemplateRefreshGate {
    private var lastCheck: Long? = null

    fun shouldCheck(now: Long = System.nanoTime()): Boolean {
        val previous = lastCheck
        if (previous != null && now - previous < 500_000_000L) return false
        lastCheck = now
        return true
    }

    fun reset() { lastCheck = null }
}
