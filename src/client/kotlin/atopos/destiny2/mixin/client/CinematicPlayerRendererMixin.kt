package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.client.renderer.ThunderclapPlayerProxyClient
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** The detached cinematic camera must not render the local player's body into the close-up. */
@Mixin(PlayerRenderer::class)
abstract class CinematicPlayerRendererMixin {
    @Inject(
        method = [
            "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;" +
                "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"
        ],
        at = [At("HEAD")],
        cancellable = true
    )
    private fun hideLocalPlayerDuringCinematic(
        player: AbstractClientPlayer,
        entityYaw: Float,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        ci: CallbackInfo
    ) {
        if (
            (player === Minecraft.getInstance().player && CinematicCameraClient.isActive()) ||
            ThunderclapPlayerProxyClient.isReplacing(player.uuid)
        ) {
            ci.cancel()
        }
    }
}
