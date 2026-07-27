package atopos.destiny2.common.mixin

import net.minecraft.world.entity.player.Player
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Player::class)
abstract class MixinPlayer {
    @Inject(method = ["sweepAttack"], at = [At("HEAD")], cancellable = true)
    private fun destiny2modCancelVanillaSweepAttack(ci: CallbackInfo) {
        ci.cancel()
    }
}
