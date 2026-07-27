// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.weapon

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponThirdPersonAction
import atopos.destiny2.client.util.PlayerAnimationHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.Pose
import java.util.UUID
import kotlin.math.sin

/**
 * TaCZ-style third-person fallback pose.
 *
 * TaCZ uses this ModelPart path when no player_animator_3rd asset is supplied.
 * The weapon remains rendered by the normal held-item/GeoItemRenderer chain.
 */
object DestinyWeaponThirdPersonClient {
    private val remoteAimTargets = HashMap<UUID, Boolean>()
    private val remoteAimProgress = HashMap<UUID, Float>()
    private val recoilUntilNanos = HashMap<UUID, Long>()
    private val actions = HashMap<UUID, ActionState>()
    private val latestSequences = HashMap<UUID, Long>()
    private var lastSentAiming: Boolean? = null

    private data class ActionState(
        val action: WeaponThirdPersonAction,
        val startedAtNanos: Long,
        val durationNanos: Long
    ) {
        fun progress(now: Long): Float =
            ((now - startedAtNanos).toDouble() / durationNanos.coerceAtLeast(1L))
                .toFloat()
                .coerceIn(0.0f, 1.0f)

        fun active(now: Long): Boolean = now - startedAtNanos < durationNanos
    }

    fun tick(client: Minecraft) {
        val localPlayer = client.player
        if (localPlayer == null || client.level == null) {
            clear()
            return
        }

        val heldWeapon = localPlayer.mainHandItem.item as? DestinyRangedWeapon
        val aiming = heldWeapon != null &&
            heldWeapon.aimProfile(localPlayer.mainHandItem).enabled &&
            client.screen == null &&
            client.options.keyUse.isDown
        if (lastSentAiming != aiming) {
            ClientPlayNetworking.send(DestinyNetworking.SetWeaponAimPayload(aiming))
            lastSentAiming = aiming
        }

        val visiblePlayers = client.level!!.players().mapTo(HashSet()) { it.uuid }
        remoteAimTargets.keys.retainAll(visiblePlayers)
        remoteAimProgress.keys.retainAll(visiblePlayers)
        recoilUntilNanos.keys.retainAll(visiblePlayers)
        actions.keys.retainAll(visiblePlayers)
        latestSequences.keys.retainAll(visiblePlayers)
        remoteAimTargets.forEach { (playerId, target) ->
            val current = remoteAimProgress[playerId] ?: 0.0f
            remoteAimProgress[playerId] = Mth.clamp(
                current + if (target) AIM_STEP_PER_TICK else -AIM_STEP_PER_TICK,
                0.0f,
                1.0f
            )
        }
        recoilUntilNanos.entries.removeIf { it.value <= System.nanoTime() }
        actions.entries.removeIf { !it.value.active(System.nanoTime()) }
    }

    fun setRemoteAim(playerId: UUID, aiming: Boolean) {
        remoteAimTargets[playerId] = aiming
        remoteAimProgress.putIfAbsent(playerId, 0.0f)
    }

    fun onShot(playerId: UUID) {
        recoilUntilNanos[playerId] = System.nanoTime() + RECOIL_DURATION_NANOS
    }

    fun onAction(payload: DestinyNetworking.WeaponThirdPersonActionPayload) {
        val previousSequence = latestSequences[payload.playerId] ?: Long.MIN_VALUE
        if (payload.sequence < previousSequence) return
        latestSequences[payload.playerId] = payload.sequence
        val now = System.nanoTime()
        actions[payload.playerId] = ActionState(
            payload.action,
            now,
            payload.durationTicks.coerceAtLeast(1) * NANOS_PER_TICK
        )
        if (payload.action == WeaponThirdPersonAction.SHOOT) {
            onShot(payload.playerId)
        }
    }

    @JvmStatic
    fun applyPose(
        player: AbstractClientPlayer,
        rightArm: ModelPart,
        leftArm: ModelPart,
        body: ModelPart,
        head: ModelPart
    ) {
        if (player.mainHandItem.item !is DestinyRangedWeapon) return
        if (player.pose == Pose.SLEEPING || player.onClimbable() || player.isSwimming ||
            player.pose == Pose.FALL_FLYING
        ) {
            return
        }

        val client = Minecraft.getInstance()
        if (player == client.player && client.options.cameraType.isFirstPerson) return
        if (PlayerAnimationHelper.isActionActive(player)) return

        val aimProgress = if (player == client.player) {
            DestinyWeaponAimClient.progress(client.timer.getGameTimeDeltaPartialTick(false))
        } else {
            remoteAimProgress[player.uuid] ?: 0.0f
        }
        val mainArm = if (player.mainArm == HumanoidArm.RIGHT) rightArm else leftArm
        val supportArm = if (player.mainArm == HumanoidArm.RIGHT) leftArm else rightArm
        val side = if (player.mainArm == HumanoidArm.RIGHT) 1.0f else -1.0f

        // Adapted from TaCZ ThirdPersonManager.DEFAULT. Vanilla retains
        // ownership of legs and locomotion; this state machine owns upper body.
        val sprintBlend = if (player.isSprinting && aimProgress <= 0.01f) 1.0f else 0.0f
        val mainYaw = Mth.lerp(sprintBlend, Mth.lerp(aimProgress, -0.30f, -0.35f), -0.65f) * side
        val supportYaw = Mth.lerp(sprintBlend, 0.80f, 0.35f) * side
        val mainPitch = Mth.lerp(
            sprintBlend,
            -Mth.lerp(aimProgress, 1.40f, 1.60f),
            -0.72f
        )
        val supportPitch = Mth.lerp(sprintBlend, mainPitch, -0.95f)
        mainArm.yRot = mainYaw + head.yRot
        supportArm.yRot = supportYaw + head.yRot
        mainArm.xRot = mainPitch + head.xRot
        supportArm.xRot = supportPitch + head.xRot
        mainArm.zRot = 0.0f
        supportArm.zRot = 0.0f

        val now = System.nanoTime()
        actions[player.uuid]?.takeIf { it.active(now) }?.let { action ->
            applyActionPose(
                action.action,
                action.progress(now),
                side,
                mainArm,
                supportArm,
                body
            )
        }

        if ((recoilUntilNanos[player.uuid] ?: Long.MIN_VALUE) > now) {
            mainArm.xRot -= RECOIL_PITCH_RADIANS
            supportArm.xRot -= RECOIL_PITCH_RADIANS * 0.65f
            body.xRot = Mth.clamp(body.xRot - RECOIL_PITCH_RADIANS * 0.15f, -0.35f, 0.35f)
        }
    }

    private fun applyActionPose(
        action: WeaponThirdPersonAction,
        progress: Float,
        side: Float,
        mainArm: ModelPart,
        supportArm: ModelPart,
        body: ModelPart
    ) {
        val smooth = progress * progress * (3.0f - 2.0f * progress)
        when (action) {
            WeaponThirdPersonAction.SHOOT -> {
                val impulse = sin(progress * Math.PI).toFloat()
                mainArm.xRot -= impulse * 0.16f
                supportArm.xRot -= impulse * 0.10f
            }

            WeaponThirdPersonAction.DRAW -> {
                val hidden = 1.0f - smooth
                mainArm.xRot += hidden * 1.05f
                supportArm.xRot += hidden * 1.25f
                mainArm.yRot -= hidden * 0.45f * side
                supportArm.yRot += hidden * 0.25f * side
            }

            WeaponThirdPersonAction.PUT_AWAY -> {
                mainArm.xRot += smooth * 1.10f
                supportArm.xRot += smooth * 1.25f
                mainArm.yRot -= smooth * 0.50f * side
            }

            WeaponThirdPersonAction.RELOAD -> {
                val reach = sin(progress * Math.PI).toFloat()
                val feed = sin(progress.coerceIn(0.20f, 0.82f) / 0.82f * Math.PI).toFloat().coerceAtLeast(0.0f)
                mainArm.xRot += reach * 0.22f
                mainArm.yRot -= reach * 0.18f * side
                supportArm.xRot = Mth.lerp(feed, supportArm.xRot, -0.48f)
                supportArm.yRot = Mth.lerp(feed, supportArm.yRot, -0.52f * side)
                supportArm.zRot = Mth.lerp(feed, 0.0f, 0.42f * side)
                body.yRot += reach * 0.10f * side
            }

            WeaponThirdPersonAction.INSPECT -> {
                val lift = sin(progress * Math.PI).toFloat()
                mainArm.xRot -= lift * 0.34f
                mainArm.yRot += lift * 0.48f * side
                mainArm.zRot -= lift * 0.16f * side
                supportArm.xRot += lift * 0.18f
                supportArm.yRot -= lift * 0.22f * side
                body.yRot += lift * 0.16f * side
            }
        }
    }

    private fun clear() {
        lastSentAiming = null
        remoteAimTargets.clear()
        remoteAimProgress.clear()
        recoilUntilNanos.clear()
        actions.clear()
        latestSequences.clear()
    }

    private const val AIM_STEP_PER_TICK = 0.20f
    private const val RECOIL_DURATION_NANOS = 90_000_000L
    private const val RECOIL_PITCH_RADIANS = 0.10f
    private const val NANOS_PER_TICK = 50_000_000L
}
