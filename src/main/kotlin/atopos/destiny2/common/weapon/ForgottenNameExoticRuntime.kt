package atopos.destiny2.common.weapon

import atopos.destiny2.common.entity.ForgottenRemnantEntity
import atopos.destiny2.common.entity.HuntingMarkEntity
import atopos.destiny2.common.effect.DestinyStatusRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos

object ForgottenNameExoticRuntime {
    private data class EchoState(
        var stacks: Int = 0,
        var expiresAt: Long = 0L,
        val recordedTargets: LinkedHashMap<UUID, Long> = linkedMapOf()
    )

    private data class MarkEntry(
        val target: LivingEntity,
        val marker: HuntingMarkEntity,
        var expiresAt: Long
    )

    private data class MarkChain(val active: LinkedHashMap<UUID, MarkEntry> = linkedMapOf())

    private val echoes = mutableMapOf<UUID, EchoState>()
    private val markChains = mutableMapOf<UUID, MarkChain>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            server.playerList.players.forEach(::tickPlayer)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> clear(handler.player.uuid) }
    }

    fun stacks(player: ServerPlayer): Int {
        expireEchoIfNeeded(player)
        return echoes[player.uuid]?.stacks ?: 0
    }

    fun onPrecisionHit(player: ServerPlayer, target: LivingEntity) {
        val now = player.level().gameTime
        expireEchoIfNeeded(player)
        val state = echoes.getOrPut(player.uuid) { EchoState() }
        state.stacks = (state.stacks + 1).coerceAtMost(ForgottenNameExoticRules.MAX_ECHO_STACKS)
        state.expiresAt = now + ForgottenNameExoticRules.ECHO_DURATION_TICKS
        state.recordedTargets[target.uuid] = state.expiresAt
        DestinyStatusRules.syncTemporaryBuff(
            player,
            ECHO_BUFF_ID,
            ECHO_BUFF_NAME,
            ForgottenNameExoticRules.ECHO_DURATION_TICKS,
            state.stacks
        )
    }

    fun consumeEchoes(player: ServerPlayer) {
        echoes.remove(player.uuid)
        DestinyStatusRules.syncTemporaryBuff(player, ECHO_BUFF_ID, ECHO_BUFF_NAME, 0, 0)
    }

    fun precisionBonusMultiplier(player: ServerPlayer): Float =
        ForgottenNameExoticRules.precisionBonusMultiplier(stacks(player))

    fun reloadTicks(player: ServerPlayer, baseTicks: Int): Int =
        ForgottenNameExoticRules.reloadTicks(baseTicks, stacks(player))

    fun projectileInaccuracy(player: ServerPlayer, baseInaccuracy: Float): Float =
        ForgottenNameExoticRules.projectileInaccuracy(baseInaccuracy, stacks(player))

    fun markedDamageMultiplier(player: ServerPlayer, target: LivingEntity): Float =
        ForgottenNameExoticRules.markedDamageMultiplier(isMarked(player, target))

    fun isMarked(player: ServerPlayer, target: LivingEntity): Boolean {
        val entry = markChains[player.uuid]?.active?.get(target.uuid) ?: return false
        return entry.expiresAt > player.level().gameTime && entry.target.isAlive
    }

    fun adjustAim(player: ServerPlayer, originalLook: Vec3): Vec3 {
        val stackCount = stacks(player)
        if (stackCount <= 0) return originalLook
        val level = player.serverLevel()
        val eye = player.eyePosition
        val look = originalLook.normalize()
        val reach = 40.0
        val bounds = player.boundingBox.expandTowards(look.scale(reach)).inflate(8.0)
        val recorded = echoes[player.uuid]?.recordedTargets ?: emptyMap()
        val cone = ForgottenNameExoticRules.aimAssistConeDegrees(stackCount)
        val minimumDot = cos(Math.toRadians(cone))

        val target = level.getEntitiesOfClass(LivingEntity::class.java, bounds) {
            it !== player && it.isAlive && player.hasLineOfSight(it) &&
                (recorded.containsKey(it.uuid) || isEnemyFor(player, it))
        }.mapNotNull { candidate ->
            val weakPoint = Vec3(candidate.x, candidate.eyeY, candidate.z)
            val delta = weakPoint.subtract(eye)
            val distance = delta.length()
            if (distance <= 0.01 || distance > reach) return@mapNotNull null
            val direction = delta.scale(1.0 / distance)
            val dot = look.dot(direction)
            val recordedBonus = if (recorded.containsKey(candidate.uuid)) 0.035 else 0.0
            if (dot < minimumDot - recordedBonus) return@mapNotNull null
            Triple(candidate, direction, dot + recordedBonus - distance * 0.00025)
        }.maxByOrNull { it.third } ?: return look

        val blend = ForgottenNameExoticRules.aimCorrectionBlend(stackCount)
        return look.scale(1.0 - blend).add(target.second.scale(blend)).normalize()
    }

    fun triggerNameless(player: ServerPlayer, killed: LivingEntity) {
        consumeEchoes(player)
        val markedTargets = spawnRemnantAndSpread(player, killed.position())
        DestinyStatusRules.syncTemporaryBuff(
            player,
            NAMELESS_BUFF_ID,
            NAMELESS_BUFF_NAME,
            ForgottenNameExoticRules.MARK_DURATION_TICKS
        )
        syncForgottenBuff(player, markedTargets)
    }

    fun onMarkedTargetKilled(player: ServerPlayer, killed: LivingEntity) {
        val chain = markChains[player.uuid] ?: return
        val removed = chain.active.remove(killed.uuid) ?: return
        removed.marker.discard()
        val now = player.level().gameTime
        chain.active.values.forEach { entry ->
            entry.expiresAt = now + ForgottenNameExoticRules.MARK_DURATION_TICKS
            entry.marker.refresh(ForgottenNameExoticRules.MARK_DURATION_TICKS)
        }
        syncForgottenBuff(player, spawnRemnantAndSpread(player, killed.position()))
    }

    private fun spawnRemnantAndSpread(player: ServerPlayer, center: Vec3): Int {
        val level = player.serverLevel()
        level.addFreshEntity(ForgottenRemnantEntity(level, center, ForgottenNameExoticRules.MARK_DURATION_TICKS))
        val chain = markChains.getOrPut(player.uuid) { MarkChain() }
        val now = level.gameTime
        chain.active.entries.removeIf { (_, entry) ->
            val expired = !entry.target.isAlive || entry.expiresAt <= now
            if (expired) entry.marker.discard()
            expired
        }
        val available = ForgottenNameExoticRules.remainingMarkSlots(chain.active.size)
        if (available <= 0) return chain.active.size
        level.getEntitiesOfClass(
            LivingEntity::class.java,
            net.minecraft.world.phys.AABB.ofSize(
                center,
                ForgottenNameExoticRules.REMNANT_RADIUS * 2.0,
                ForgottenNameExoticRules.REMNANT_RADIUS * 2.0,
                ForgottenNameExoticRules.REMNANT_RADIUS * 2.0
            )
        ) { target ->
            target !== player && target.isAlive && isEnemyFor(player, target) &&
                !chain.active.containsKey(target.uuid)
        }.sortedBy { it.distanceToSqr(center) }
            .take(available)
            .forEach { target ->
                val marker = HuntingMarkEntity(level, target, ForgottenNameExoticRules.MARK_DURATION_TICKS)
                level.addFreshEntity(marker)
                chain.active[target.uuid] = MarkEntry(
                    target,
                    marker,
                    now + ForgottenNameExoticRules.MARK_DURATION_TICKS
                )
            }
        return chain.active.size
    }

    private fun tickPlayer(player: ServerPlayer) {
        expireEchoIfNeeded(player)
        val now = player.level().gameTime
        echoes[player.uuid]?.recordedTargets?.entries?.removeIf { it.value <= now }
        val chain = markChains[player.uuid] ?: return
        chain.active.entries.removeIf { (_, entry) ->
            val expired = !entry.target.isAlive || entry.expiresAt <= now
            if (expired) entry.marker.discard()
            expired
        }
        if (chain.active.isEmpty()) markChains.remove(player.uuid)
    }

    private fun expireEchoIfNeeded(player: ServerPlayer) {
        val state = echoes[player.uuid] ?: return
        if (state.expiresAt <= player.level().gameTime) echoes.remove(player.uuid)
    }

    private fun clear(playerId: UUID) {
        echoes.remove(playerId)
        markChains.remove(playerId)?.active?.values?.forEach { it.marker.discard() }
    }

    private fun isEnemyFor(player: ServerPlayer, target: LivingEntity): Boolean =
        target is Enemy || (target is ServerPlayer && !player.isAlliedTo(target))

    private fun syncForgottenBuff(player: ServerPlayer, markedTargets: Int) {
        DestinyStatusRules.syncTemporaryBuff(
            player,
            FORGOTTEN_BUFF_ID,
            FORGOTTEN_BUFF_NAME,
            ForgottenNameExoticRules.MARK_DURATION_TICKS,
            markedTargets.coerceAtLeast(1)
        )
    }

    private const val ECHO_BUFF_ID = "destiny2-mod:forgotten_name/echo"
    private const val ECHO_BUFF_NAME = "残响"
    private const val NAMELESS_BUFF_ID = "destiny2-mod:forgotten_name/nameless"
    private const val NAMELESS_BUFF_NAME = "无名"
    private const val FORGOTTEN_BUFF_ID = "destiny2-mod:forgotten_name/forgotten"
    private const val FORGOTTEN_BUFF_NAME = "遗忘"
}
