package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.client.weapon.DestinyWeaponAimClient
import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(GameRenderer::class)
abstract class GameRendererFovMixin {
    @Inject(method = ["getFov"], at = [At("RETURN")], cancellable = true)
    private fun applyDestinyWeaponAdsFov(
        camera: Camera,
        partialTick: Float,
        useFovSetting: Boolean,
        cir: CallbackInfoReturnable<Double>
    ) {
        if (CinematicCameraClient.isActive()) return
        if (useFovSetting) {
            cir.returnValue = DestinyWeaponAimClient.modifyWorldFov(cir.returnValue, partialTick)
        }
    }
}
