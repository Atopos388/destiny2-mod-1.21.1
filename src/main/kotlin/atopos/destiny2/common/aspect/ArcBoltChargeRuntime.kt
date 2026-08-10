package atopos.destiny2.common.aspect

import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.PlayerDestinyDataApi
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared server authority for the post-Frontiers Arc keywords used by both
 * fragments and Storm's Keep: homing Ionic Traces and ten-stack Bolt Charge.
 */
object ArcBoltChargeRuntime {
    private const val TRACE_DATA_ROOT = "DestinyIonicTrace"
    private const val TRACE_OWNER = "owner"
    private const val TRACE_HOME_RADIUS = 32.0
    private const val TRACE_ACCELERATION = 0.075
    private const val TRACE_MAX_SPEED = 0.72
    private const val TRACE_ABILITY_REFUND_TICKS = 24
    private const val TRACE_BOLT_CHARGE_GAIN = 1

    private val boltChargeStacks = ConcurrentHashMap<UUID, Int>()

    fun tickPlayer(player: ServerPlayer) {
        if (!player.isAlive || player.isSpectator) return
        val level = player.serverLevel()
        val traces = level.getEntitiesOfClass(
            ItemEntity::class.java,
            player.boundingBox.inflate(TRACE_HOME_RADIUS)
        ) { entity ->
            entity.isAlive && entity.item.`is`(DestinyItems.IONIC_TRACE) &&
                traceOwner(entity.item) == player.uuid
        }
        traces.forEach { trace ->
            val desired = player.eyePosition.subtract(trace.position())
            if (desired.lengthSqr() <= 0.0001) return@forEach
            val velocity = trace.deltaMovement.scale(0.82)
                .add(desired.normalize().scale(TRACE_ACCELERATION))
            trace.deltaMovement = if (velocity.length() > TRACE_MAX_SPEED) {
                velocity.normalize().scale(TRACE_MAX_SPEED)
            } else velocity
            trace.hasImpulse = true
            if (level.gameTime % 2L == 0L) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, trace.x, trace.y + 0.12, trace.z, 2, 0.06, 0.06, 0.06, 0.02)
            }
        }
    }

    fun clearPlayer(playerId: UUID) {
        boltChargeStacks.remove(playerId)
    }

    fun stacks(player: ServerPlayer): Int = boltChargeStacks[player.uuid] ?: 0

    fun hasBoltCharge(player: ServerPlayer): Boolean = stacks(player) > 0

    fun addBoltCharge(player: ServerPlayer, baseGain: Int, sourceName: String): Int {
        if (baseGain <= 0) return stacks(player)
        val gain = ArcTitanFragmentRules.modifiedBoltChargeGain(
            baseGain,
            DestinyAspectRuntime.hasFragment(player, ArcTitanFragmentRules.SPARK_OF_FREQUENCY),
            player.hasEffect(DestinyEffects.AMPLIFIED)
        )
        val updated = ArcTitanAspectRules.addBoltChargeStacks(stacks(player), gain)
        if (updated <= 0) boltChargeStacks.remove(player.uuid) else boltChargeStacks[player.uuid] = updated
        DestinyStatusRules.syncTemporaryBuff(
            player,
            "destiny2-mod:status/bolt_charge",
            "电光充能 · $sourceName",
            10 * 20,
            updated
        )
        return updated
    }

    fun consumeAll(player: ServerPlayer): Int {
        val consumed = boltChargeStacks.remove(player.uuid) ?: 0
        if (consumed > 0) {
            DestinyStatusRules.syncTemporaryBuff(player, "destiny2-mod:status/bolt_charge", "电光充能", 1, 0)
        }
        return consumed
    }

    fun spawnIonicTrace(owner: ServerPlayer, x: Double, y: Double, z: Double): ItemEntity {
        val stack = ItemStack(DestinyItems.IONIC_TRACE)
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
            data.update { tag ->
                val trace = CompoundTag()
                trace.putUUID(TRACE_OWNER, owner.uuid)
                tag.put(TRACE_DATA_ROOT, trace)
            }
        }
        val entity = ItemEntity(owner.serverLevel(), x, y, z, stack)
        entity.setNoPickUpDelay()
        entity.deltaMovement = Vec3(0.0, 0.10, 0.0)
        owner.serverLevel().addFreshEntity(entity)
        owner.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 18, 0.22, 0.22, 0.22, 0.08)
        return entity
    }

    /** Returns true to consume/cancel vanilla pickup handling. */
    fun handleIonicTracePickup(player: ServerPlayer, entity: ItemEntity): Boolean {
        val ownerId = traceOwner(entity.item)
        if (ownerId != null && ownerId != player.uuid) return true
        entity.discard()
        addBoltCharge(player, TRACE_BOLT_CHARGE_GAIN, "离子轨迹")
        val data = PlayerDestinyDataApi.get(player)
        AbilitySlot.entries.filterNot { it == AbilitySlot.SUPER }.forEach { slot ->
            data.cooldowns.reduce(slot, player.serverLevel().gameTime, TRACE_ABILITY_REFUND_TICKS)
        }
        DestinyNetworking.syncCooldowns(player)
        player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.x, player.eyeY, player.z, 24, 0.30, 0.50, 0.30, 0.10)
        return true
    }

    private fun traceOwner(stack: ItemStack): UUID? {
        val root = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompound(TRACE_DATA_ROOT) ?: return null
        return if (root.hasUUID(TRACE_OWNER)) root.getUUID(TRACE_OWNER) else null
    }
}
