package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.client.combat.QuickMeleeAimClient
import atopos.destiny2.client.camera.ThunderclapCameraClient
import atopos.destiny2.client.weapon.DestinyWeaponAimClient
import atopos.destiny2.client.weapon.DestinyWeaponFeedbackClient
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(GameRenderer::class)
abstract class GameRendererFovMixin {
    @Inject(method = ["render"], at = [At("HEAD")])
    private fun applyDestinyWeaponRecoilEveryFrame(
        deltaTracker: DeltaTracker,
        renderLevel: Boolean,
        ci: CallbackInfo
    ) {
        if (CinematicCameraClient.isActive()) return
        Minecraft.getInstance().player?.let { player ->
            DestinyWeaponFeedbackClient.updateRecoilFrame(player)
            QuickMeleeAimClient.updateFrame(player)
        }
    }

    @Inject(method = ["getFov"], at = [At("RETURN")], cancellable = true)
    private fun applyDestinyWeaponAdsFov(
        camera: Camera,
        partialTick: Float,
        useFovSetting: Boolean,
        cir: CallbackInfoReturnable<Double>
    ) {
        CinematicCameraClient.cinematicFov()?.let {
            cir.returnValue = it
            return
        }
        ThunderclapCameraClient.currentFov(partialTick)?.let {
            cir.returnValue = it
            return
        }
        if (useFovSetting) {
            cir.returnValue = DestinyWeaponAimClient.modifyWorldFov(cir.returnValue, partialTick)
        }
    }
}
