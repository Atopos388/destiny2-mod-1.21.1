package atopos.destiny2.common.mixin;

import atopos.destiny2.common.gear.ArmorModRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class ItemEntityArmorModMixin {
    @Shadow private int pickupDelay;
    @Shadow private UUID target;

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void destiny2$pickupOrb(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer
            && pickupDelay <= 0
            && (target == null || target.equals(serverPlayer.getUUID()))
            && ArmorModRuntime.INSTANCE.onItemPickup(serverPlayer, (ItemEntity)(Object)this)) {
            ci.cancel();
        }
    }
}
