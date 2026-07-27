package atopos.destiny2.mixin.client;

import atopos.destiny2.client.weapon.DestinyWeaponAimClient;
import atopos.destiny2.common.item.IzanagiBurdenItem;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Izanagi's No Distractions trait reduces the first-person hurt-camera flinch
 * after the weapon has fully settled into ADS.
 */
@Mixin(GameRenderer.class)
public class IzanagiNoDistractionsMixin {
    @ModifyExpressionValue(
            method = "bobHurt",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hurtTime:I"
            )
    )
    private int destiny2$reduceIzanagiHurtFlinch(int original) {
        Minecraft client = Minecraft.getInstance();
        if (
                client.player != null &&
                client.player.getMainHandItem().getItem() instanceof IzanagiBurdenItem &&
                DestinyWeaponAimClient.INSTANCE.progress(1.0f) >= 0.999f
        ) {
            return Math.round(original * 0.65f);
        }
        return original;
    }
}
