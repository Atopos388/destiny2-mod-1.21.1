package atopos.destiny2.client.gear

import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUIClientElementComponent
import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback

/** Client bootstrap for the shared Destiny gear tooltip component. */
object GearTooltipClient {
    fun register() {
        DestinyPerkTooltipInput.register()
        // LDLib2's migrated tooltip data component needs a Fabric client-side
        // renderer. Returning null leaves every unrelated tooltip component alone.
        TooltipComponentCallback.EVENT.register { component ->
            when (component) {
                is DestinyPerkTooltipComponent -> DestinyPerkClientTooltipComponent(component.layout)
                is ModularUITooltipComponent -> ModularUIClientElementComponent(component)
                else -> null
            }
        }
    }
}
