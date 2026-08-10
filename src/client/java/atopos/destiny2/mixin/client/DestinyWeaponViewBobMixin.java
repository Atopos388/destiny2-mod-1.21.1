package atopos.destiny2.mixin.client;

import atopos.destiny2.client.weapon.DestinyWeaponAimClient;
import atopos.destiny2.common.weapon.TaczGunPackItem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Removes only the first-person hand walking bob while a Destiny gun is in ADS.
 * The world-camera bob is a separate GameRenderer call and remains untouched.
 */
@Mixin(GameRenderer.class)
public class DestinyWeaponViewBobMixin {
    @WrapOperation(
            method = "renderItemInHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"
            )
    )
    private void destiny2$suppressAdsHandBob(
            GameRenderer instance,
            PoseStack poseStack,
            float partialTick,
            Operation<Void> original
    ) {
        Minecraft client = Minecraft.getInstance();
        boolean suppress = client.player != null
                && client.player.getMainHandItem().getItem() instanceof TaczGunPackItem
                && DestinyWeaponAimClient.INSTANCE.progress(partialTick) > 0.001f;
        if (!suppress) {
            original.call(instance, poseStack, partialTick);
        }
    }
}
