package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.client.camera.ThunderclapCameraClient
import net.minecraft.client.player.Input
import net.minecraft.client.player.KeyboardInput
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Prevents vanilla movement from being reapplied after the cinematic tick gate. */
@Mixin(KeyboardInput::class)
abstract class CinematicKeyboardInputMixin : Input() {
    @Inject(method = ["tick"], at = [At("RETURN")])
    private fun clearCinematicMovement(slowDown: Boolean, slowDownFactor: Float, ci: CallbackInfo) {
        if (!CinematicCameraClient.isActive() && !ThunderclapCameraClient.isMovementLocked()) return
        leftImpulse = 0.0f
        forwardImpulse = 0.0f
        up = false
        down = false
        left = false
        right = false
        jumping = false
        shiftKeyDown = false
    }
}
