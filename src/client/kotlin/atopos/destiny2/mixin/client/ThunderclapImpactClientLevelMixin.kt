// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.mixin.client

import atopos.destiny2.client.camera.ThunderclapCameraClient
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Holds surrounding entity presentation on the contact pose without pausing
 * the local player, server combat, or the render loop. The local player's tick
 * must continue so press/hold/release input cannot be swallowed by hit stop.
 */
@Mixin(ClientLevel::class)
abstract class ThunderclapImpactClientLevelMixin {
    @Inject(method = ["tickNonPassenger"], at = [At("HEAD")], cancellable = true)
    private fun freezeSurroundingEntityPresentation(entity: Entity, ci: CallbackInfo) {
        if (entity !is LocalPlayer && ThunderclapCameraClient.isImpactHitStopActive()) {
            ci.cancel()
        }
    }
}
