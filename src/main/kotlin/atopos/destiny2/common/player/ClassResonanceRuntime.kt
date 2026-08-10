package atopos.destiny2.common.player

import atopos.destiny2.common.equipment.DestinyClassItemContainer
import atopos.destiny2.common.item.DestinyClassItem
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** Server-authoritative class-item resonance and its short presentation. */
object ClassResonanceRuntime {
    private const val EFFECT_TICKS = 50

    private data class ResonanceEffect(
        val destinyClass: DestinyClassType,
        var remainingTicks: Int = EFFECT_TICKS
    )

    private val effects = mutableMapOf<UUID, ResonanceEffect>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tickServer)
    }

    fun tryResonate(player: ServerPlayer, heldStack: ItemStack, item: DestinyClassItem) {
        if (effects.containsKey(player.uuid)) return
        val data = PlayerDestinyDataApi.get(player)
        when (
            ClassProgressionRules.resonanceResult(
                data.journeyStage,
                hasGhostCore(player),
                data.unlockedClasses,
                item.requiredClass
            )
        ) {
            ClassProgressionRules.ResonanceResult.NOT_AWAKENED -> {
                player.displayClientMessage(Component.translatable("message.destiny2-mod.class_resonance.not_awakened"), true)
                return
            }
            ClassProgressionRules.ResonanceResult.MISSING_GHOST_CORE -> {
                player.displayClientMessage(Component.translatable("message.destiny2-mod.class_resonance.missing_ghost_core"), true)
                return
            }
            ClassProgressionRules.ResonanceResult.ALREADY_UNLOCKED -> {
                player.displayClientMessage(Component.translatable("message.destiny2-mod.class_resonance.already_unlocked"), true)
                return
            }
            ClassProgressionRules.ResonanceResult.ALLOWED -> Unit
        }

        val firstClass = data.unlockedClasses.isEmpty()
        unlockBasicClassOptions(data, item.requiredClass)
        if (firstClass) {
            data.setClass(item.requiredClass)
            equipHeldClassItem(player, heldStack)
        }
        data.restrictCurrentSubclassConfig()
        effects[player.uuid] = ResonanceEffect(item.requiredClass)
        player.cooldowns.addCooldown(item, EFFECT_TICKS)
        player.serverLevel().playSound(
            null,
            player.blockPosition(),
            SoundEvents.BEACON_POWER_SELECT,
            SoundSource.PLAYERS,
            0.75f,
            resonancePitch(item.requiredClass)
        )
        player.sendSystemMessage(
            Component.translatable(
                if (firstClass) {
                    "message.destiny2-mod.class_resonance.first_unlocked"
                } else {
                    "message.destiny2-mod.class_resonance.additional_unlocked"
                },
                Component.translatable("class.destiny2-mod.${item.requiredClass.id}")
            )
        )
        DestinyNetworking.syncPlayerData(player)
        player.inventory.setChanged()
        player.inventoryMenu.broadcastChanges()
    }

    fun unlockAllOptions(data: PlayerDestinyData, destinyClass: DestinyClassType) {
        data.unlockedClasses += destinyClass
        val definition = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.defaultFor(destinyClass))
        definition.abilityOptions.values.flatten().mapTo(data.unlockedSubclassOptions) { it.id }
        definition.movementOptions.mapTo(data.unlockedSubclassOptions) { it.id }
        definition.aspectOptions.mapTo(data.unlockedSubclassOptions) { it.id }
        definition.fragmentOptions.mapTo(data.unlockedSubclassOptions) { it.id }
    }

    fun unlockAllForAdmin(data: PlayerDestinyData, destinyClass: DestinyClassType) {
        unlockAllOptions(data, destinyClass)
    }

    fun lockForAdmin(player: ServerPlayer, destinyClass: DestinyClassType) {
        val data = PlayerDestinyDataApi.get(player)
        data.unlockedClasses -= destinyClass
        removeClassOptions(data, destinyClass)
        val equipped = data.classItem.item as? DestinyClassItem
        if (equipped?.requiredClass == destinyClass) unequipClassItem(player)
        if (data.destinyClass == destinyClass) {
            val fallback = data.unlockedClasses.firstOrNull()
            if (fallback != null) {
                data.setClass(fallback)
            } else {
                data.subclassConfig = PlayerSubclassConfiguration()
                data.cooldowns = AbilityCooldowns()
                data.combatState.resetForLoadout()
            }
        }
    }

    fun resetForAdmin(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        data.unlockedClasses.clear()
        data.unlockedSubclassOptions.clear()
        unequipClassItem(player)
        data.subclassConfig = PlayerSubclassConfiguration()
        data.cooldowns = AbilityCooldowns()
        data.combatState.resetForLoadout()
        effects.remove(player.uuid)
    }

    fun switchClass(player: ServerPlayer, requestedClass: DestinyClassType): Boolean {
        val data = PlayerDestinyDataApi.get(player)
        if (data.destinyClass == requestedClass) return data.isClassUnlocked(requestedClass)
        if (
            !ClassProgressionRules.canSwitchClass(
                requestedClass,
                data.unlockedClasses,
                data.combatState.lastDamageGameTime,
                player.serverLevel().gameTime
            )
        ) return false

        val equippedItem = data.classItem.item as? DestinyClassItem
        if (!data.classItem.isEmpty && equippedItem?.requiredClass != requestedClass) {
            unequipClassItem(player)
        }
        data.setClass(requestedClass)
        return true
    }

    private fun unlockBasicClassOptions(data: PlayerDestinyData, destinyClass: DestinyClassType) {
        data.unlockedClasses += destinyClass
        val subclass = DestinySubclassType.defaultFor(destinyClass)
        val definition = DestinySubclassConfigRegistry.definitionFor(subclass)
        definition.abilityOptions[AbilitySlot.CLASS_ABILITY]
            ?.firstOrNull()
            ?.id
            ?.let(data.unlockedSubclassOptions::add)
        definition.movementOptions.firstOrNull()?.id?.let(data.unlockedSubclassOptions::add)
    }

    private fun removeClassOptions(data: PlayerDestinyData, destinyClass: DestinyClassType) {
        val definition = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.defaultFor(destinyClass))
        val ids = buildSet {
            definition.abilityOptions.values.flatten().mapTo(this) { it.id }
            definition.movementOptions.mapTo(this) { it.id }
            definition.aspectOptions.mapTo(this) { it.id }
            definition.fragmentOptions.mapTo(this) { it.id }
        }
        data.unlockedSubclassOptions.removeAll(ids)
        data.restrictCurrentSubclassConfig()
    }

    private fun unequipClassItem(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        if (data.classItem.isEmpty) return
        val displaced = data.classItem.copy()
        data.classItem = ItemStack.EMPTY
        player.inventory.add(displaced)
        if (!displaced.isEmpty) player.drop(displaced, false)
    }

    private fun equipHeldClassItem(player: ServerPlayer, heldStack: ItemStack) {
        val container = DestinyClassItemContainer(player)
        val previous = container.getItem(0).copy()
        if (!previous.isEmpty) {
            player.inventory.add(previous)
            if (!previous.isEmpty) player.drop(previous, false)
        }
        container.setItem(0, heldStack.copyWithCount(1))
        heldStack.shrink(1)
    }

    private fun hasGhostCore(player: ServerPlayer): Boolean {
        for (slot in 0 until player.inventory.containerSize) {
            if (player.inventory.getItem(slot).`is`(DestinyItems.GHOST_CORE)) return true
        }
        return player.offhandItem.`is`(DestinyItems.GHOST_CORE)
    }

    private fun tickServer(server: MinecraftServer) {
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val (playerId, effect) = iterator.next()
            val player = server.playerList.getPlayer(playerId)
            if (player == null || !player.isAlive) {
                iterator.remove()
                continue
            }
            if (effect.remainingTicks % 2 == 0) {
                val level = player.serverLevel()
                level.sendParticles(
                    resonanceParticle(effect.destinyClass),
                    player.x,
                    player.y + 1.0,
                    player.z,
                    4,
                    0.55,
                    0.9,
                    0.55,
                    0.025
                )
                level.sendParticles(ParticleTypes.END_ROD, player.x, player.y + 1.0, player.z, 1, 0.35, 0.7, 0.35, 0.01)
            }
            effect.remainingTicks--
            if (effect.remainingTicks <= 0) {
                player.serverLevel().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS,
                    0.8f,
                    resonancePitch(effect.destinyClass)
                )
                player.displayClientMessage(Component.translatable("message.destiny2-mod.class_resonance.complete"), true)
                iterator.remove()
            }
        }
    }

    private fun resonanceParticle(destinyClass: DestinyClassType): ParticleOptions = when (destinyClass) {
        DestinyClassType.HUNTER -> ParticleTypes.PORTAL
        DestinyClassType.TITAN -> ParticleTypes.ELECTRIC_SPARK
        DestinyClassType.WARLOCK -> ParticleTypes.FLAME
    }

    private fun resonancePitch(destinyClass: DestinyClassType): Float = when (destinyClass) {
        DestinyClassType.HUNTER -> 1.25f
        DestinyClassType.TITAN -> 0.85f
        DestinyClassType.WARLOCK -> 1.05f
    }
}
