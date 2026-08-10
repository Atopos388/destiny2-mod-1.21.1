package atopos.destiny2.common.item

import atopos.destiny2.common.player.DestinyClassType
import atopos.destiny2.common.player.ClassResonanceRuntime
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerPlayer

class DestinyClassItem(
    val requiredClass: DestinyClassType,
    properties: Properties
) : Item(properties) {
    override fun appendHoverText(
        stack: ItemStack,
        context: TooltipContext,
        tooltipComponents: MutableList<Component>,
        tooltipFlag: TooltipFlag
    ) {
        tooltipComponents += Component.translatable(
            "tooltip.destiny2-mod.class_item.type",
            Component.translatable("class.destiny2-mod.${requiredClass.id}")
        ).withStyle(ChatFormatting.LIGHT_PURPLE)
        tooltipComponents += Component.translatable(
            "tooltip.destiny2-mod.class_item.identity.${requiredClass.id}"
        ).withStyle(ChatFormatting.GRAY)
        tooltipComponents += Component.translatable(
            "tooltip.destiny2-mod.class_item.unattuned"
        ).withStyle(ChatFormatting.DARK_GRAY)
    }

    override fun isFoil(stack: ItemStack): Boolean = true

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (!level.isClientSide && player is ServerPlayer) {
            ClassResonanceRuntime.tryResonate(player, stack, this)
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
}
