package atopos.destiny2.common.mixin

import atopos.destiny2.common.player.GuardianAwakeningRuntime
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Mob::class)
abstract class MixinMobAwakeningTarget {
    @Inject(method = ["setTarget"], at = [At("HEAD")], cancellable = true)
    private fun destiny2modIgnoreProtectedAwakeningPlayer(
        target: LivingEntity?,
        callback: CallbackInfo
    ) {
        val player = target as? ServerPlayer ?: return
        if (GuardianAwakeningRuntime.isProtected(player)) {
            callback.cancel()
        }
    }
}
