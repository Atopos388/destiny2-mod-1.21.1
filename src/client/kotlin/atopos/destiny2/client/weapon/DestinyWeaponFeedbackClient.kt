// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.weapon

import atopos.destiny2.client.gui.DestinyWeaponHUDState
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponRecoilProfile
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/** TaCZ-style recoil curve, tracer, impact and hit-marker adapter. */
object DestinyWeaponFeedbackClient {
    private data class RecoilImpulse(
        val startedAt: Long,
        val pitch: Float,
        val yaw: Float,
        val profile: WeaponRecoilProfile,
        var lastPitch: Float = 0.0f,
        var lastYaw: Float = 0.0f
    )

    private val impulses = ArrayList<RecoilImpulse>()
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
        impulses += RecoilImpulse(
            System.currentTimeMillis(),
            payload.recoilPitch * aimScale,
            payload.recoilYaw * aimScale,
            profile
        )
        shotBloom = (shotBloom + (DestinyWeaponHUDState.snapshot?.crosshair?.shotPenalty ?: 4.0f))
            .coerceAtMost(24.0f)
    }

    fun tick(client: Minecraft) {
        val player = client.player ?: run {
            impulses.clear()
            shotBloom = 0.0f
            return
        }
        val profile = DestinyWeaponHUDState.snapshot
        shotBloom = (shotBloom - (profile?.crosshair?.shotDecayPerTick ?: 0.75f)).coerceAtLeast(0.0f)

        val now = System.currentTimeMillis()
        val iterator = impulses.iterator()
        while (iterator.hasNext()) {
            val impulse = iterator.next()
            val elapsed = (now - impulse.startedAt).coerceAtLeast(0L)
            val pitch: Float
            val yaw: Float
            if (elapsed <= impulse.profile.kickDurationMs) {
                val t = elapsed.toFloat() / impulse.profile.kickDurationMs.coerceAtLeast(1)
                val eased = 1.0f - (1.0f - t) * (1.0f - t)
                pitch = impulse.pitch * eased
                yaw = impulse.yaw * eased
            } else {
                val recoveryElapsed = elapsed - impulse.profile.kickDurationMs
                val t = recoveryElapsed.toFloat() / impulse.profile.recoverDurationMs.coerceAtLeast(1)
                if (t >= 1.0f) {
                    player.xRot -= 0.0f - impulse.lastPitch
                    player.yRot += 0.0f - impulse.lastYaw
                    iterator.remove()
                    continue
                }
                val remaining = (1.0f - t)
                pitch = impulse.pitch * remaining * remaining
                yaw = impulse.yaw * remaining * remaining
            }
            player.xRot -= pitch - impulse.lastPitch
            player.yRot += yaw - impulse.lastYaw
            impulse.lastPitch = pitch
            impulse.lastYaw = yaw
        }
    }

    fun shotBloom(): Float = shotBloom

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
}
