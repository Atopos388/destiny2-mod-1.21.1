package atopos.destiny2.mixin.client

import atopos.destiny2.client.action.IncineratorSnapFirstPersonClient
import atopos.destiny2.client.combat.HunterMeleeFirstPersonClient
import atopos.destiny2.client.renderer.GenericGunPackItemRenderer
import atopos.destiny2.common.item.GenericGunPackItem
import atopos.destiny2.common.weapon.TaczGunPackItem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.HumanoidArm
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyVariable
import org.spongepowered.asm.mixin.injection.Redirect
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Removes the already-interpolated vanilla swing transform at the final
 * first-person render boundary. The firearm keeps its own GeckoLib recoil.
 */
@Mixin(ItemInHandRenderer::class)
abstract class ItemInHandRendererSwingMixin {
    @Inject(method = ["renderArmWithItem"], at = [At("HEAD")], cancellable = true)
    private fun renderIncineratorSnapFirstPersonArm(
        player: AbstractClientPlayer,
        partialTick: Float,
        pitch: Float,
        hand: InteractionHand,
        swingProgress: Float,
        stack: ItemStack,
        equipProgress: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        combinedLight: Int,
        ci: CallbackInfo
    ) {
        if (IncineratorSnapFirstPersonClient.isActive(player)) {
            if (hand == InteractionHand.MAIN_HAND) {
                IncineratorSnapFirstPersonClient.render(
                    player = player,
                    partialTick = partialTick,
                    poseStack = poseStack,
                    bufferSource = bufferSource,
                    combinedLight = combinedLight
                )
            }
            ci.cancel()
            return
        }

        if (HunterMeleeFirstPersonClient.isActive(player)) {
            if (hand == InteractionHand.MAIN_HAND) {
                HunterMeleeFirstPersonClient.render(
                    player = player,
                    partialTick = partialTick,
                    poseStack = poseStack,
                    bufferSource = bufferSource,
                    combinedLight = combinedLight
                )
            }
            ci.cancel()
            return
        }

        // TaCZ intercepts this method before vanilla applies its fixed hand,
        // swing and equip transforms. The authored idle_view is the only
        // first-person positioning source for a generic gun pack.
        if (player.mainHandItem.item is GenericGunPackItem) {
            if (hand == InteractionHand.MAIN_HAND) {
                GenericGunPackItemRenderer.INSTANCE.renderFirstPerson(
                    player = Minecraft.getInstance().player ?: return,
                    stack = stack,
                    mode = net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    matrices = poseStack,
                    vertexConsumers = bufferSource,
                    light = combinedLight,
                    partialTick = partialTick
                )
            }
            ci.cancel()
        }
    }

    /**
     * The Gecko weapon owns the complete Blockbench camera transform. Vanilla's
     * fixed hand offset (0.56, -0.52, -0.72) would otherwise be applied first
     * and make the authored view impossible to reproduce.
     */
    @Inject(method = ["applyItemArmTransform"], at = [At("HEAD")], cancellable = true)
    private fun suppressForgottenNameVanillaHandTransform(
        poseStack: PoseStack,
        arm: HumanoidArm,
        equipProgress: Float,
        ci: CallbackInfo
    ) {
        val player = Minecraft.getInstance().player ?: return
        if (player.mainHandItem.item is TaczGunPackItem) ci.cancel()
    }

    @ModifyVariable(
        method = ["renderArmWithItem"],
        at = At("HEAD"),
        argsOnly = true,
        index = 5
    )
    private fun suppressForgottenNameSwingProgress(swingProgress: Float): Float {
        val player = Minecraft.getInstance().player ?: return swingProgress
        if (player.mainHandItem.item !is TaczGunPackItem) {
            return swingProgress
        }
        return 0.0f
    }

    /**
     * Magazine synchronization changes the stack's CustomData after every shot. Vanilla treats
     * that component update as a different held stack and lowers the item before equipping it
     * again. A firearm remains the same held item while its magazine data changes.
     */
    @Redirect(
        method = ["tick"],
        at = At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"
        )
    )
    private fun keepForgottenNameEquipped(previous: ItemStack, current: ItemStack): Boolean {
        val previousPack = previous.item as? TaczGunPackItem
        val currentPack = current.item as? TaczGunPackItem
        if (previousPack != null && currentPack != null && previousPack.gunPackId == currentPack.gunPackId) return true
        return ItemStack.matches(previous, current)
    }
}
