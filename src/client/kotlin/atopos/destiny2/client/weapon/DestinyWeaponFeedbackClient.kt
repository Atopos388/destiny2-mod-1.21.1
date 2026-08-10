// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.weapon

import atopos.destiny2.client.gui.DestinyWeaponHUDState
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponRecoilProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max

/** Frame-sampled recoil, tracer, impact and hit-marker adapter. */
object DestinyWeaponFeedbackClient {
    private var recoilTargetPitch = 0.0f
    private var recoilTargetYaw = 0.0f
    private var recoilAppliedPitch = 0.0f
    private var recoilAppliedYaw = 0.0f
    private var recoilPitchLimit = 0.0f
    private var recoilYawLimit = 0.0f
    private var recoilKickMs = 45
    private var recoilRecoverMs = 240
    private var lastRecoilShotAtMs = Long.MIN_VALUE
    private var lastRecoilFrameNanos = Long.MIN_VALUE
    private var shotBloom = 0.0f

    fun onShot(payload: DestinyNetworking.WeaponShotFeedbackPayload) {
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        DestinyWeaponThirdPersonClient.onShot(payload.shooterId)
        val shooter = level.getPlayerByUUID(payload.shooterId)
        val serverStart = Vec3(payload.startX, payload.startY, payload.startZ)
        val end = Vec3(payload.endX, payload.endY, payload.endZ)
        val visualStart = shooter?.let {
            val look = it.lookAngle.normalize()
            val right = look.cross(Vec3(0.0, 1.0, 0.0)).normalize()
            it.eyePosition.add(look.scale(0.52)).add(right.scale(0.22)).add(0.0, -0.18, 0.0)
        } ?: serverStart
        if (payload.tracerStep > 0.0) {
            spawnTracer(visualStart, end, payload.tracerStep)
        }

        if (payload.showBulletImpact && payload.impactKind != 0) {
            val particle = if (payload.impactKind == 2) ParticleTypes.CRIT else ParticleTypes.SMOKE
            repeat(if (payload.impactKind == 2) 4 else 2) {
                level.addParticle(
                    particle,
                    end.x, end.y, end.z,
                    (level.random.nextDouble() - 0.5) * 0.04,
                    (level.random.nextDouble() - 0.5) * 0.04,
                    (level.random.nextDouble() - 0.5) * 0.04
                )
            }
        }

        if (client.player?.uuid != payload.shooterId) return
        GenericGunAnimationClient.onLocalShotFeedback()
        DestinyWeaponHUDState.weaponHit(payload.hit, payload.precision, payload.killed)
        if (!payload.applyRecoil) return
        val profile = WeaponRecoilProfile(
            kickDurationMs = payload.recoilKickMs,
            recoverDurationMs = payload.recoilRecoverMs,
            aimedMultiplier = payload.aimedRecoilMultiplier
        )
        val aimScale = Mth.lerp(
            DestinyWeaponAimClient.progress(1.0f),
            1.0f,
            profile.aimedMultiplier
        )
        val pitch = payload.recoilPitch * aimScale
        val yaw = payload.recoilYaw * aimScale
        recoilPitchLimit = max(recoilPitchLimit, kotlin.math.abs(pitch) * PITCH_ACCUMULATION_SHOTS)
            .coerceAtLeast(kotlin.math.abs(pitch))
        recoilYawLimit = max(recoilYawLimit, kotlin.math.abs(yaw) * YAW_ACCUMULATION_SHOTS)
            .coerceAtLeast(MIN_YAW_LIMIT)
        recoilTargetPitch = (recoilTargetPitch + pitch).coerceIn(-recoilPitchLimit, recoilPitchLimit)
        recoilTargetYaw = (recoilTargetYaw + yaw).coerceIn(-recoilYawLimit, recoilYawLimit)
        recoilKickMs = profile.kickDurationMs.coerceAtLeast(1)
        recoilRecoverMs = profile.recoverDurationMs.coerceAtLeast(1)
        lastRecoilShotAtMs = System.currentTimeMillis()
        if (lastRecoilFrameNanos == Long.MIN_VALUE) {
            lastRecoilFrameNanos = System.nanoTime()
        }
        shotBloom = (shotBloom + (DestinyWeaponHUDState.snapshot?.crosshair?.shotPenalty ?: 4.0f))
            .coerceAtMost(24.0f)
    }

    fun tick(client: Minecraft) {
        if (client.player == null) {
            clearRecoil()
            shotBloom = 0.0f
            return
        }
        val profile = DestinyWeaponHUDState.snapshot
        shotBloom = (shotBloom - (profile?.crosshair?.shotDecayPerTick ?: 0.75f)).coerceAtLeast(0.0f)
    }

    /**
     * Called from MouseHandler.turnPlayer, which runs at render/input cadence
     * instead of the fixed 20 Hz client tick.
     */
    fun updateRecoilFrame(player: LocalPlayer) {
        if (lastRecoilFrameNanos == Long.MIN_VALUE) return
        val nowNanos = System.nanoTime()
        val deltaMs = ((nowNanos - lastRecoilFrameNanos) / 1_000_000.0f).coerceIn(0.0f, MAX_FRAME_MS)
        lastRecoilFrameNanos = nowNanos
        if (deltaMs <= 0.0f) return

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastRecoilShotAtMs >= RECOVERY_DELAY_MS) {
            val recoveryAlpha = responseAlpha(deltaMs, recoilRecoverMs)
            recoilTargetPitch += (0.0f - recoilTargetPitch) * recoveryAlpha
            recoilTargetYaw += (0.0f - recoilTargetYaw) * recoveryAlpha
        }

        val kickAlpha = responseAlpha(deltaMs, recoilKickMs)
        val nextPitch = recoilAppliedPitch + (recoilTargetPitch - recoilAppliedPitch) * kickAlpha
        val nextYaw = recoilAppliedYaw + (recoilTargetYaw - recoilAppliedYaw) * kickAlpha
        player.xRot -= nextPitch - recoilAppliedPitch
        player.yRot += nextYaw - recoilAppliedYaw
        recoilAppliedPitch = nextPitch
        recoilAppliedYaw = nextYaw

        if (
            kotlin.math.abs(recoilTargetPitch) < SETTLED_EPSILON &&
            kotlin.math.abs(recoilTargetYaw) < SETTLED_EPSILON &&
            kotlin.math.abs(recoilAppliedPitch) < SETTLED_EPSILON &&
            kotlin.math.abs(recoilAppliedYaw) < SETTLED_EPSILON
        ) {
            clearRecoil()
        }
    }

    fun shotBloom(): Float = shotBloom

    private fun responseAlpha(deltaMs: Float, durationMs: Int): Float =
        (1.0f - exp(-RESPONSE_STRENGTH * deltaMs / durationMs.coerceAtLeast(1))).coerceIn(0.0f, 1.0f)

    private fun clearRecoil() {
        recoilTargetPitch = 0.0f
        recoilTargetYaw = 0.0f
        recoilAppliedPitch = 0.0f
        recoilAppliedYaw = 0.0f
        recoilPitchLimit = 0.0f
        recoilYawLimit = 0.0f
        lastRecoilShotAtMs = Long.MIN_VALUE
        lastRecoilFrameNanos = Long.MIN_VALUE
    }

    private fun spawnTracer(start: Vec3, end: Vec3, requestedStep: Double) {
        val level = Minecraft.getInstance().level ?: return
        val delta = end.subtract(start)
        val distance = delta.length()
        if (distance <= 0.001) return
        val count = ceil(distance / requestedStep.coerceAtLeast(0.1)).toInt().coerceIn(2, 96)
        repeat(count) { index ->
            val t = (index + 1.0) / count
            val point = start.add(delta.scale(t))
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 0.0, 0.0, 0.0)
        }
    }

    private const val PITCH_ACCUMULATION_SHOTS = 5.0f
    private const val YAW_ACCUMULATION_SHOTS = 6.0f
    private const val MIN_YAW_LIMIT = 0.65f
    private const val RECOVERY_DELAY_MS = 125L
    private const val RESPONSE_STRENGTH = 4.0f
    private const val MAX_FRAME_MS = 50.0f
    private const val SETTLED_EPSILON = 0.001f
}
