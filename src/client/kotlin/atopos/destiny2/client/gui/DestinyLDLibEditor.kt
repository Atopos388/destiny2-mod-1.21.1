package atopos.destiny2.client.gui

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow
import com.lowdragmc.lowdraglib2.gui.editor.UIEditor
import com.lowdragmc.lowdraglib2.gui.editor.view.UIEditorView
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Development-only entry point for LDLib's visual XML UI editor.
 *
 * This intentionally has no dependency on the removed aspect selection screen.
 */
object DestinyLDLibEditor {
    enum class Target { ASPECT, ABILITY_HUD, NAVIGATION }

    private var requestedTarget: Target? = null

    /**
     * Must be deferred to the next client tick: vanilla closes the chat screen
     * after a client command returns and would otherwise overwrite our screen.
     */
    fun requestOpen(target: Target = Target.ASPECT) {
        requestedTarget = target
    }

    fun openIfRequested(minecraft: Minecraft) {
        val target = requestedTarget ?: return
        requestedTarget = null

        val player = minecraft.player ?: return
        val template = when (target) {
            Target.ASPECT -> {
                DestinyAspectScreen.prepareVisualEditor()
                DestinyAspectScreen.loadVisualTemplateForEditor()
            }
            Target.ABILITY_HUD -> {
                DestinyAbilityHUDTemplate.loadForEditor()
            }
            Target.NAVIGATION -> {
                DestinyNavigationTemplate.loadForEditor()
            }
        }
        val editorWindow = EditorWindow.open(UIEditor.WINDOW_ID) { UIEditor() }
        val editor = editorWindow.currentEditor as? UIEditor ?: return
        val view: UIEditorView = DestinyPreviewEditorView(target)
        view.loadTemplate(template) { edited ->
            when (target) {
                Target.ASPECT -> DestinyAspectScreen.saveVisualTemplateFromEditor(edited)
                Target.ABILITY_HUD -> DestinyAbilityHUDTemplate.saveFromEditor(edited)
                Target.NAVIGATION -> DestinyNavigationTemplate.saveFromEditor(edited)
            }
        }
        editor.centerWindow.leftTop.addView(view)
        val modularUi = ModularUI.of(UI.of(editorWindow), player)

        minecraft.setScreen(
            ModularUIScreen(
                modularUi,
                Component.literal(
                    when (target) {
                        Target.ASPECT -> "LDLib UI Editor"
                        Target.ABILITY_HUD -> "LDLib Ability HUD Editor"
                        Target.NAVIGATION -> "LDLib Navigation Editor"
                    }
                )
            )
        )
    }

    /** Binds behavior immediately after LDLib has created its disposable simulation UI. */
    private class DestinyPreviewEditorView(private val target: Target) : UIEditorView() {
        override fun startSimulation() {
            super.startSimulation()
            canvas.canvasModularUI?.ui?.let { ui ->
                when (target) {
                    Target.ASPECT -> DestinyAspectScreen.bindVisualEditorSimulation(ui)
                    Target.NAVIGATION -> DestinyDirectorViewBinder.bindEditorSimulation(ui)
                    Target.ABILITY_HUD -> Unit
                }
            }
        }
    }
}

