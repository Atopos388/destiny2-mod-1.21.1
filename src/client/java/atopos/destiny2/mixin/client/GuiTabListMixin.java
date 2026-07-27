package atopos.destiny2.mixin.client;

import atopos.destiny2.client.gui.DestinyNavigationOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla Tab list while the Destiny navigation overlay is active. */
@Mixin(Gui.class)
public abstract class GuiTabListMixin {
    @Inject(method = "renderTabList", at = @At("HEAD"), cancellable = true)
    private void destiny2$hideVanillaTabList(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (DestinyNavigationOverlay.INSTANCE.isOpen()) {
            ci.cancel();
        }
    }
}
