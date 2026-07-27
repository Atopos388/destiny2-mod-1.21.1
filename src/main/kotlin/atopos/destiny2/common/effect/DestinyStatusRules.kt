package atopos.destiny2.common.effect

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object DestinyStatusRules {
    const val SCORCH_IGNITION_STACKS = 100
    const val SCORCH_MAX_AMPLIFIER = SCORCH_IGNITION_STACKS - 1

    const val SHORT_DURATION = 100
    const val MEDIUM_DURATION = 200
    const val AURA_REFRESH_DURATION = 60
    const val AMPLIFIED_SPRINT_TRIGGER_TICKS = 20
    const val SPEED_BOOSTER_RETAIN_TICKS = 60

    private val amplifiedSprintTicks = mutableMapOf<UUID, Int>()
    private val lastArcPositions = mutableMapOf<UUID, Pair<Double, Double>>()

    fun applyScorch(target: LivingEntity, stacks: Int, durationTicks: Int = SHORT_DURATION, source: ServerPlayer? = null) {
        applyScorchInternal(target, stacks, durationTicks, source, applySourceModifiers = true)
    }

    fun applyScorchExact(target: LivingEntity, stacks: Int, durationTicks: Int = SHORT_DURATION) {
        applyScorchInternal(target, stacks, durationTicks, source = null, applySourceModifiers = false)
    }

    private fun applyScorchInternal(
        target: LivingEntity,
        stacks: Int,
        durationTicks: Int,
        source: ServerPlayer?,
        applySourceModifiers: Boolean
    ) {
        val adjustedStacks = if (applySourceModifiers) DestinyAspectRuntime.modifyScorchStacks(source, stacks) else stacks
        val currentStacks = target.getEffect(DestinyEffects.SCORCH)?.let { it.amplifier + 1 } ?: 0
        val nextStacks = (currentStacks + adjustedStacks).coerceIn(1, SCORCH_IGNITION_STACKS)
        target.addEffect(MobEffectInstance(DestinyEffects.SCORCH, durationTicks, nextStacks - 1))
        syncStatus(target, "scorch", "灼烧", durationTicks, nextStacks)
        DestinyAspectRuntime.onScorchApplied(source)
    }

    fun applyRadiant(target: LivingEntity, durationTicks: Int = MEDIUM_DURATION) {
        val adjustedDuration = DestinyAspectRuntime.modifySolarBuffDuration(target, durationTicks)
        target.addEffect(MobEffectInstance(DestinyEffects.RADIANT, adjustedDuration, 0, false, false, true))
        syncStatus(target, "radiant", "焕光", adjustedDuration)
    }

    fun applyRestoration(target: LivingEntity, durationTicks: Int = AURA_REFRESH_DURATION, level: Int = 1) {
        val adjustedDuration = DestinyAspectRuntime.modifySolarBuffDuration(target, durationTicks)
        target.addEffect(MobEffectInstance(DestinyEffects.RESTORATION, adjustedDuration, level - 1, false, false, true))
        syncStatus(target, "restoration", "恢复", adjustedDuration, level)
    }

    fun applyAmplified(target: LivingEntity, durationTicks: Int = MEDIUM_DURATION) {
        target.addEffect(MobEffectInstance(DestinyEffects.AMPLIFIED, durationTicks, 0, false, false, true))
        syncStatus(target, "amplified", "增幅", durationTicks)
    }

    /**
     * 增幅时持续冲刺 1 秒后激活速度推进。
     * 触发后只要持续移动就会刷新；完全停下才会结束。
     */
    fun tickArcMovement(player: ServerPlayer) {
        val playerId = player.uuid
        val previousPosition = lastArcPositions.put(playerId, player.x to player.z)
        val hasMovedSinceLastTick = previousPosition?.let { (lastX, lastZ) ->
            val deltaX = player.x - lastX
            val deltaZ = player.z - lastZ
            deltaX * deltaX + deltaZ * deltaZ > 0.0001
        } ?: false
        // 使用实际坐标变化，而不是瞬时速度，避免单人服务器将移动速度归零时误判为停下。
        val isMoving = player.isSprinting || hasMovedSinceLastTick

        if (player.hasEffect(DestinyEffects.AMPLIFIED) && player.isSprinting) {
            val sprintTicks = (amplifiedSprintTicks[playerId] ?: 0) + 1
            amplifiedSprintTicks[playerId] = sprintTicks
            if (sprintTicks >= AMPLIFIED_SPRINT_TRIGGER_TICKS) {
                val shouldSync = (player.getEffect(DestinyEffects.SPEED_BOOSTER)?.duration ?: 0) <= 40
                player.addEffect(MobEffectInstance(DestinyEffects.SPEED_BOOSTER, SPEED_BOOSTER_RETAIN_TICKS, 0, false, false, true))
                if (shouldSync) syncStatus(player, "speed_booster", "速度推进", SPEED_BOOSTER_RETAIN_TICKS)
            }
        } else {
            amplifiedSprintTicks.remove(playerId)
        }

        if (!isMoving) {
            player.removeEffect(DestinyEffects.SPEED_BOOSTER)
            return
        }

        // 已触发后，增幅结束也可通过持续走路无限维持速度推进。
        if (player.hasEffect(DestinyEffects.SPEED_BOOSTER)) {
            player.addEffect(MobEffectInstance(DestinyEffects.SPEED_BOOSTER, SPEED_BOOSTER_RETAIN_TICKS, 0, false, false, true))
        }
    }

    fun applyVoidInvisibility(target: LivingEntity, durationTicks: Int = SHORT_DURATION) {
        target.addEffect(MobEffectInstance(DestinyEffects.VOID_INVISIBILITY, durationTicks, 0, false, false, true))
        syncStatus(target, "void_invisibility", "虚空隐身", durationTicks)
    }

    fun applyDevour(target: LivingEntity, durationTicks: Int = MEDIUM_DURATION) {
        target.addEffect(MobEffectInstance(DestinyEffects.DEVOUR, durationTicks, 0, false, false, true))
        syncStatus(target, "devour", "吞噬", durationTicks)
    }

    fun applyVoidOvershield(target: LivingEntity, durationTicks: Int = MEDIUM_DURATION) {
        target.addEffect(MobEffectInstance(DestinyEffects.VOID_OVERSHIELD, durationTicks, 0, false, false, true))
        syncStatus(target, "void_overshield", "虚空护盾", durationTicks)
    }

    fun applyWeaken(target: LivingEntity, durationTicks: Int = SHORT_DURATION) {
        target.addEffect(MobEffectInstance(DestinyEffects.WEAKEN, durationTicks, 0))
        syncStatus(target, "weaken", "虚弱", durationTicks)
    }

    fun applyStrongWeaken(target: LivingEntity, durationTicks: Int = SHORT_DURATION) {
        target.addEffect(MobEffectInstance(DestinyEffects.WEAKEN, durationTicks, 1))
        syncStatus(target, "weaken_strong", "强虚弱", durationTicks)
    }

    fun applySuppression(target: LivingEntity, durationTicks: Int = 40) {
        target.addEffect(MobEffectInstance(DestinyEffects.SUPPRESSION, durationTicks, 0))
        syncStatus(target, "suppression", "压制", durationTicks)
    }

    fun applyVolatile(target: LivingEntity, durationTicks: Int = MEDIUM_DURATION) {
        target.addEffect(MobEffectInstance(DestinyEffects.VOLATILE, durationTicks, 0))
        syncStatus(target, "volatile", "不稳定", durationTicks)
    }

    private fun syncStatus(target: LivingEntity, id: String, name: String, durationTicks: Int, stacks: Int = 1) {
        val player = target as? ServerPlayer ?: return
        syncTemporaryBuff(player, "destiny2-mod:status/$id", name, durationTicks, stacks)
    }

    fun syncTemporaryBuff(player: ServerPlayer, id: String, name: String, durationTicks: Int, stacks: Int = 1) {
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.SyncPerkBuffPayload(id, name, durationTicks, stacks)
        )
    }
}
