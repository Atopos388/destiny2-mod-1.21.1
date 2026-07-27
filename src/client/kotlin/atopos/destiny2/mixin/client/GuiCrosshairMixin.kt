// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.mixin.client

import atopos.destiny2.common.weapon.DestinyRangedWeapon
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphics
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Replaces vanilla crosshair only while a Destiny ranged weapon is held. */
@Mixin(Gui::class)
abstract class GuiCrosshairMixin {
    @Inject(method = ["renderCrosshair"], at = [At("HEAD")], cancellable = true)
    private fun useDestinyWeaponCrosshair(
        graphics: GuiGraphics,
        deltaTracker: DeltaTracker,
        ci: CallbackInfo
    ) {
        if (Minecraft.getInstance().player?.mainHandItem?.item is DestinyRangedWeapon) {
            ci.cancel()
        }
    }
}
