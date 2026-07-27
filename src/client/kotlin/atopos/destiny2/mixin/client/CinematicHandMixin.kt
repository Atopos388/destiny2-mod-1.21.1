package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.MultiBufferSource
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ItemInHandRenderer::class)
abstract class CinematicHandMixin {
    @Inject(method = ["renderHandsWithItems"], at = [At("HEAD")], cancellable = true)
    private fun hideFirstPersonHandsDuringCinematic(
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource.BufferSource,
        player: LocalPlayer,
        combinedLight: Int,
        ci: CallbackInfo
    ) {
        if (CinematicCameraClient.isActive()) ci.cancel()
    }
}
