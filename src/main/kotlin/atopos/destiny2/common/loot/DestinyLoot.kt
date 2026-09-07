package atopos.destiny2.common.loot

import atopos.destiny2.common.gear.GearRolls
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.player.GuardianPowerSystem
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.RandomSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object DestinyLoot {
    private val minorCombatantPool = DestinyLootPool(
        listOf(
            DestinyLootEntry(DestinyItems.GLIMMER, 72, 1, 4),
            DestinyLootEntry(DestinyItems.STRANGE_COIN, 5, 1, 1),
            DestinyLootEntry(DestinyItems.EXOTIC_ENGRAM, 1, 1, 1)
        )
    )

    private val majorCombatantPool = DestinyLootPool(
        listOf(
            DestinyLootEntry(DestinyItems.GLIMMER, 64, 4, 10),
            DestinyLootEntry(DestinyItems.STRANGE_COIN, 10, 1, 2),
            DestinyLootEntry(DestinyItems.EXOTIC_ENGRAM, 2, 1, 1)
        )
    )

    fun register() {
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            handleEntityDeath(entity, source)
        }
    }

    private fun handleEntityDeath(entity: LivingEntity, source: DamageSource) {
        val player = sourcePlayer(source) ?: return
        if (entity !is Monster || entity.level().isClientSide) {
            return
        }

        val dropChance = if (entity.maxHealth >= 30.0f) 0.45f else 0.24f
        if (player.random.nextFloat() > dropChance) {
            return
        }

        val pool = if (entity.maxHealth >= 30.0f) majorCombatantPool else minorCombatantPool
        val stack = pool.roll(player, player.random)
        entity.spawnAtLocation(stack)
    }

    private fun sourcePlayer(source: DamageSource): ServerPlayer? {
        val directPlayer = source.entity as? ServerPlayer
        if (directPlayer != null) {
            return directPlayer
        }
        return (source.directEntity as? Projectile)?.owner as? ServerPlayer
    }

    private data class DestinyLootEntry(
        val item: Item,
        val weight: Int,
        val minCount: Int,
        val maxCount: Int
    ) {
        fun stack(player: ServerPlayer, random: RandomSource): ItemStack {
            val count = if (maxCount <= minCount) {
                minCount
            } else {
                minCount + random.nextInt(maxCount - minCount + 1)
            }
            val stack = ItemStack(item, count)
            GearRolls.ensureRoll(
                stack,
                kotlin.random.Random(random.nextLong()),
                GuardianPowerSystem.normalDropPower(player, kotlin.random.Random(random.nextLong()))
            )
            return stack
        }
    }

    private class DestinyLootPool(private val entries: List<DestinyLootEntry>) {
        private val totalWeight = entries.sumOf { it.weight.coerceAtLeast(0) }.coerceAtLeast(1)

        fun roll(player: ServerPlayer, random: RandomSource): ItemStack {
            var cursor = random.nextInt(totalWeight)
            for (entry in entries) {
                cursor -= entry.weight.coerceAtLeast(0)
                if (cursor < 0) {
                    return entry.stack(player, random)
                }
            }
            return entries.last().stack(player, random)
        }
    }
}
