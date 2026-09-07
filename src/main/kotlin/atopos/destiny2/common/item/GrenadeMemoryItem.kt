package atopos.destiny2.common.item

import atopos.destiny2.common.player.GrenadeMemoryRuntime
import atopos.destiny2.common.player.GrenadeMemoryRules
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class GrenadeMemoryItem(
    /** Non-null only for hidden legacy items already present in development saves. */
    val legacyTarget: GrenadeMemoryRules.Target? = null,
    properties: Properties
) : Item(properties) {
    override fun isFoil(stack: ItemStack): Boolean = true

    override fun use(level: Level, player: Player, usedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(usedHand)
        if (!level.isClientSide && player is ServerPlayer) {
            GrenadeMemoryRuntime.tryUnlock(player, stack, this)
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
    }
}
