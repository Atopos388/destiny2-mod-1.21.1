package atopos.destiny2.common.mixin

import atopos.destiny2.common.gear.GearPerkRuntime
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.BlockHitResult
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Projectile::class)
abstract class MixinProjectile {
    private val self: Projectile
        get() = this as Any as Projectile

    @Inject(method = ["onHitBlock"], at = [At("HEAD")], cancellable = true)
    fun destiny2modRicochetFletching(hit: BlockHitResult, ci: CallbackInfo) {
        val state = self.level().getBlockState(hit.blockPos)
        if (GearPerkRuntime.onProjectileBlockHit(self, hit, state)) {
            ci.cancel()
        }
    }

    @Inject(method = ["tick"], at = [At("HEAD")])
    fun destiny2modApplyRangedPartStats(ci: CallbackInfo) {
        if (!self.level().isClientSide && self.tickCount == 0) {
            GearPerkRuntime.applyRangedProjectilePartStats(self)
        }
    }
}
