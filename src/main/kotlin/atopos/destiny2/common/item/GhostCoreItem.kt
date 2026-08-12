package atopos.destiny2.common.item

import atopos.destiny2.common.player.LightCrystalRuntime
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks

/** The permanent Ghost identity key and a reusable interaction catalyst. */
class GhostCoreItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val level = context.level
        val pos = context.clickedPos
        if (!level.getBlockState(pos).`is`(Blocks.AMETHYST_BLOCK)) return InteractionResult.PASS

        if (level.isClientSide) return InteractionResult.SUCCESS
        val player = context.player as? ServerPlayer ?: return InteractionResult.PASS
        when (LightCrystalRuntime.tryBegin(player, pos)) {
            LightCrystalRuntime.StartResult.NOT_AWAKENED -> {
                player.displayClientMessage(
                    Component.translatable("message.destiny2-mod.light_crystal.not_awakened"),
                    true
                )
                return InteractionResult.FAIL
            }
            LightCrystalRuntime.StartResult.NO_SUNLIGHT -> {
                player.displayClientMessage(
                    Component.translatable("message.destiny2-mod.light_crystal.requires_sunlight"),
                    true
                )
                return InteractionResult.FAIL
            }
            LightCrystalRuntime.StartResult.ALREADY_CHARGING -> {
                player.displayClientMessage(
                    Component.translatable("message.destiny2-mod.light_crystal.already_charging"),
                    true
                )
                return InteractionResult.FAIL
            }
            LightCrystalRuntime.StartResult.WRONG_BLOCK -> return InteractionResult.PASS
            LightCrystalRuntime.StartResult.STARTED -> {
                player.displayClientMessage(Component.translatable("message.destiny2-mod.light_crystal.charging"), true)
                return InteractionResult.CONSUME
            }
        }
    }
}
