package atopos.destiny2.mixin.client

import atopos.destiny2.common.weapon.TaczGunPackItem
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/** Prevent the vanilla melee/mining arm swing while the revolver handles left click as fire. */
@Mixin(LocalPlayer::class)
abstract class LocalPlayerSwingMixin {
    @Inject(
        method = ["swing(Lnet/minecraft/world/InteractionHand;)V"],
        at = [At("HEAD")],
        cancellable = true
    )
    @Suppress("CAST_NEVER_SUCCEEDS")
    private fun suppressVanillaSwingForForgottenName(hand: InteractionHand, ci: CallbackInfo) {
        val player = this as LocalPlayer
        if (
            hand == InteractionHand.MAIN_HAND &&
            player.mainHandItem.item is TaczGunPackItem
        ) {
            ci.cancel()
        }
    }

}
