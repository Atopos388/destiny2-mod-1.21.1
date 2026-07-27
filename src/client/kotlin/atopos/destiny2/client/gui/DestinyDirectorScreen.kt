package atopos.destiny2.client.gui

import atopos.destiny2.client.gear.DestinyPerkTooltipController
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

/** Persistent, non-pausing LDLib2 Director opened by holding vanilla Tab. */
class DestinyDirectorScreen private constructor(modularUI: ModularUI) :
    ModularUIScreen(modularUI, Component.translatable("navigation.destiny2-mod.director.title")) {

    private var tabReleasedSinceOpen = false
    private var tabWasDown = true
    private var closing = false
    private val openedAt = System.currentTimeMillis()

    override fun tick() {
        super.tick()
        val tabDown = Minecraft.getInstance().options.keyPlayerList.isDown
        if (!tabDown) tabReleasedSinceOpen = true
        if (tabReleasedSinceOpen && tabDown && !tabWasDown && System.currentTimeMillis() - openedAt >= 300L) {
            onClose()
            return
        }
        tabWasDown = tabDown
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(graphics, mouseX, mouseY, partialTick)
        DestinyPerkTooltipController.renderDirectorHover(graphics, mouseX, mouseY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (closing) return true
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (DestinyDirectorViewBinder.handleBack(modularUI.ui)) return true
            onClose()
            return true
        }
        // Ignore keyboard repeat from the hold that opened the screen. tick()
        // closes only on a new physical Tab edge after the initial release.
        if (keyCode == GLFW.GLFW_KEY_TAB) return true
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false

    override fun onClose() {
        if (closing) return
        closing = true
        DestinyPerkTooltipController.clearInteraction()
        DestinyDirectorViewBinder.animateClose(modularUI.ui) {
            DestinyNavigationOverlay.closeDirector()
            minecraft?.setScreen(null)
        }
    }

    override fun removed() {
        DestinyPerkTooltipController.clearInteraction()
        super.removed()
    }

    companion object {
        fun create(player: LocalPlayer): DestinyDirectorScreen {
            val ui = DestinyNavigationTemplate.createRuntimeUI()
            DestinyDirectorViewBinder.bindRuntime(ui, player)
            val modular = ModularUI.of(ui, player)
                .shouldCloseOnEsc(false)
                .shouldCloseOnKeyInventory(false)
            return DestinyDirectorScreen(modular)
        }
    }
}
