package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.common.item.MicroMissileBurstWeaponItem
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Minecraft::class)
abstract class MinecraftUseItemMixin {
    @Inject(method = ["startUseItem"], at = [At("HEAD")], cancellable = true)
    private fun fireDestinyWeaponInsteadOfVanillaUse(ci: CallbackInfo) {
        if (CinematicCameraClient.isActive()) {
            ci.cancel()
            return
        }
        val client = Minecraft.getInstance()
        if (client.screen != null) return

        val player = client.player ?: return
        val mainHand = player.mainHandItem
        val aimableWeapon = mainHand.item as? DestinyRangedWeapon
        if (aimableWeapon?.aimProfile(mainHand)?.enabled == true) {
            // ADS is driven continuously from the use-key state. Cancel the
            // vanilla interaction so aiming at a block does not use it.
            ci.cancel()
            return
        }

        val hand = when {
            player.mainHandItem.item is MicroMissileBurstWeaponItem -> InteractionHand.MAIN_HAND
            player.offhandItem.item is MicroMissileBurstWeaponItem -> InteractionHand.OFF_HAND
            else -> return
        }

        val look = player.lookAngle
        ClientPlayNetworking.send(DestinyNetworking.FireWeaponPayload(hand, look.x, look.y, look.z))
        ci.cancel()
    }
}
