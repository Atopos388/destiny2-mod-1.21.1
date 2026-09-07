package atopos.destiny2.common.mixin;

import atopos.destiny2.common.block.TowerWorkbenchUI;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** LDLib 2.5 does not forward server-side menu removal to ModularUI.onRemoved. */
@Mixin(AbstractContainerMenu.class)
public abstract class TowerWorkbenchMenuCleanupMixin {
    @Inject(method = "removed", at = @At("TAIL"))
    private void destiny2$returnWorkbenchMaterials(Player player, CallbackInfo ci) {
        if (!player.level().isClientSide) {
            TowerWorkbenchUI.closeMenu((AbstractContainerMenu) (Object) this);
        }
    }
}
