package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphics
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Gui::class)
abstract class CinematicGuiMixin {
    @Inject(method = ["render"], at = [At("HEAD")], cancellable = true)
    private fun hideHudDuringCinematic(graphics: GuiGraphics, deltaTracker: DeltaTracker, ci: CallbackInfo) {
        if (CinematicCameraClient.shouldHideHud()) ci.cancel()
    }
}
