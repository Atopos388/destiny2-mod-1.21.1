package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.TaczGunPackItem
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

/** 在原版攻击开始时上报，空挥和命中实体都会经过这里。 */
@Mixin(Minecraft::class)
abstract class MinecraftAttackMixin {
    @Inject(method = ["startAttack"], at = [At("HEAD")], cancellable = true)
    private fun activateEagerEdgeOnWeaponSwing(cir: CallbackInfoReturnable<Boolean>) {
        if (CinematicCameraClient.isActive()) {
            cir.returnValue = false
            return
        }
        val client = Minecraft.getInstance()
        if (client.screen == null && client.player != null) {
            val player = client.player ?: return
            val heldStack = player.mainHandItem
            if (heldStack.item is TaczGunPackItem) {
                val look = player.lookAngle
                ClientPlayNetworking.send(
                    DestinyNetworking.FireWeaponPayload(
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        look.x,
                        look.y,
                        look.z
                    )
                )
                cir.returnValue = true
                return
            }
            ClientPlayNetworking.send(DestinyNetworking.EagerEdgeActivatePayload())
        }
    }
}
