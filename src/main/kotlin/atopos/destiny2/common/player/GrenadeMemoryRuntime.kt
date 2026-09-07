package atopos.destiny2.common.player

import atopos.destiny2.common.item.GrenadeMemoryItem
import atopos.destiny2.common.network.DestinyNetworking
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack

/** Server-authoritative consumption and presentation for grenade-memory items. */
object GrenadeMemoryRuntime {
    fun tryUnlock(player: ServerPlayer, heldStack: ItemStack, item: GrenadeMemoryItem) {
        val data = PlayerDestinyDataApi.get(player)
        val target = item.legacyTarget ?: GrenadeMemoryRules.targetFor(data.subclass)
        val result = GrenadeMemoryRules.useResult(
            data.journeyStage,
            data.unlockedClasses,
            data.unlockedSubclassOptions,
            target.subclass,
            target.grenadeOptionId
        )
        if (result != GrenadeMemoryRules.UseResult.ALLOWED) {
            player.displayClientMessage(Component.translatable(messageKey(result)), true)
            return
        }
        if (!GrenadeMemoryRules.unlock(data, target.subclass, target.grenadeOptionId)) return

        if (!player.abilities.instabuild) heldStack.shrink(1)
        player.cooldowns.addCooldown(item, 20)
        val level = player.serverLevel()
        level.sendParticles(
            particle(target.subclass),
            player.x,
            player.y + 1.0,
            player.z,
            24,
            0.45,
            0.75,
            0.45,
            0.035
        )
        level.playSound(
            null,
            player.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS,
            0.85f,
            pitch(target.subclass)
        )
        DestinyNetworking.syncPlayerData(player)
        player.inventory.setChanged()
        player.inventoryMenu.broadcastChanges()
    }

    private fun messageKey(result: GrenadeMemoryRules.UseResult): String = when (result) {
        GrenadeMemoryRules.UseResult.NOT_AWAKENED -> "message.destiny2-mod.grenade_memory.not_awakened"
        GrenadeMemoryRules.UseResult.CLASS_LOCKED -> "message.destiny2-mod.grenade_memory.class_locked"
        GrenadeMemoryRules.UseResult.ALREADY_UNLOCKED -> "message.destiny2-mod.grenade_memory.already_unlocked"
        GrenadeMemoryRules.UseResult.ALLOWED -> error("Allowed grenade memories do not have a denial message")
    }

    private fun particle(subclass: DestinySubclassType): ParticleOptions = when (subclass) {
        DestinySubclassType.VOID_HUNTER -> ParticleTypes.PORTAL
        DestinySubclassType.ARC_TITAN -> ParticleTypes.ELECTRIC_SPARK
        DestinySubclassType.SOLAR_WARLOCK -> ParticleTypes.FLAME
    }

    private fun pitch(subclass: DestinySubclassType): Float = when (subclass) {
        DestinySubclassType.VOID_HUNTER -> 0.8f
        DestinySubclassType.ARC_TITAN -> 1.2f
        DestinySubclassType.SOLAR_WARLOCK -> 1.0f
    }
}
