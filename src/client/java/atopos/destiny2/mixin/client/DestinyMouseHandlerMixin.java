// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.mixin.client;

import atopos.destiny2.client.cinematic.CinematicCameraClient;
import atopos.destiny2.client.camera.ThunderclapCameraClient;
import atopos.destiny2.client.tacz.TaczMath;
import atopos.destiny2.client.weapon.DestinyWeaponAimClient;
import atopos.destiny2.common.weapon.WeaponAimProfile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * TaCZ Refabricated's ADS mouse-sensitivity calculation adapted for Destiny weapons.
 * Source revision: 98ef5f4465bcbf185f6c570a178695d8929a2eac.
 */
@Mixin(MouseHandler.class)
public class DestinyMouseHandlerMixin {
    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void destiny2$reduceAdsSensitivity(
            LocalPlayer player,
            double yaw,
            double pitch,
            Operation<Void> original
    ) {
        if (CinematicCameraClient.INSTANCE.isActive() || ThunderclapCameraClient.INSTANCE.isViewLocked()) {
            return;
        }
        DestinyWeaponAimClient aim = DestinyWeaponAimClient.INSTANCE;
        WeaponAimProfile profile = aim.activeProfile();
        if (profile == null) {
            original.call(player, yaw, pitch);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        float progress = aim.progress(partialTick);
        double originalFov = minecraft.options.fov().get();
        double currentFov = TaczMath.magnificationToFov(
                1 + (profile.getZoom() - 1) * progress,
                originalFov
        );
        double denominator = TaczMath.zoomSensitivityRatio(
                currentFov,
                originalFov,
                SCREEN_DISTANCE_COEFFICIENT
        );
        original.call(player, yaw * denominator, destiny2$getCrawlPitch(player, pitch, denominator));
    }

    @Unique
    private static double destiny2$getCrawlPitch(LocalPlayer player, double pitch, double denominator) {
        double finalPitch = pitch * denominator;
        if (!player.isSwimming() && player.getPose() == Pose.SWIMMING) {
            float playerPitch = -player.getXRot();
            if (playerPitch > 45) {
                finalPitch = Math.max(finalPitch, 0);
            }
            if (playerPitch < -30) {
                finalPitch = Math.min(finalPitch, 0);
            }
        }
        return finalPitch;
    }

    @Unique
    private static final double SCREEN_DISTANCE_COEFFICIENT = 1.33;
}
