package atopos.destiny2.client.gear

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import org.lwjgl.glfw.GLFW

/** Per-screen input routing for the currently visible expanded gear card. */
object DestinyPerkTooltipInput {
    fun register() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            ScreenEvents.afterRender(screen).register { _, graphics, mouseX, mouseY, _ ->
                DestinyPerkTooltipController.renderPinned(graphics, mouseX, mouseY)
            }

            ScreenMouseEvents.allowMouseScroll(screen).register { _, _, _, horizontal, vertical ->
                val amount = if (vertical != 0.0) vertical else horizontal
                val handled = when {
                    amount < 0.0 -> DestinyPerkTooltipController.cyclePerk(1)
                    amount > 0.0 -> DestinyPerkTooltipController.cyclePerk(-1)
                    else -> false
                }
                !handled
            }

            ScreenMouseEvents.allowMouseClick(screen).register { _, mouseX, mouseY, _ ->
                !DestinyPerkTooltipController.clickPinned(mouseX, mouseY)
            }

            ScreenKeyboardEvents.allowKeyPress(screen).register { _, key, _, _ ->
                val handled = when (key) {
                    GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> DestinyPerkTooltipController.cyclePerk(-1)
                    GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> DestinyPerkTooltipController.cyclePerk(1)
                    in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_9 ->
                        DestinyPerkTooltipController.selectPerk(key - GLFW.GLFW_KEY_1)
                    in GLFW.GLFW_KEY_KP_1..GLFW.GLFW_KEY_KP_9 ->
                        DestinyPerkTooltipController.selectPerk(key - GLFW.GLFW_KEY_KP_1)
                    else -> false
                }
                !handled
            }

            ScreenKeyboardEvents.allowKeyRelease(screen).register { _, key, _, _ ->
                val shift = key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                !(shift && DestinyPerkTooltipController.togglePinned())
            }

            ScreenEvents.remove(screen).register {
                DestinyPerkTooltipController.clearInteraction()
            }
        }
    }
}
