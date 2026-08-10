package atopos.destiny2.common.weapon

import atopos.destiny2.common.aspect.SolarWarlockFragmentRuntime
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Server-authoritative accuracy cone and per-shooter bloom state. */
object WeaponAccuracyRuntime {
    private data class Key(val playerId: UUID, val weaponId: ResourceLocation)
    private data class BloomState(var bloom: Float = 0.0f, var lastShotTick: Long = Long.MIN_VALUE)

    data class ShotCone(val degrees: Float, val bloomBeforeShot: Float, val bloomAfterShot: Float)

    private val states = ConcurrentHashMap<Key, BloomState>()

    fun shotDirection(
        player: ServerPlayer,
        weaponId: ResourceLocation,
        profile: WeaponAccuracyProfile,
        validatedDirection: Vec3,
        random: RandomSource,
        accuracyScale: Float = 1.0f
    ): Vec3 {
        val state = states.computeIfAbsent(Key(player.uuid, weaponId)) { BloomState() }
        val aimProgress = WeaponAimRuntime.progress(player)
        val movingAmount = (player.deltaMovement.horizontalDistance() / 0.25).toFloat().coerceIn(0.0f, 1.0f)
        val cone = advance(
            profile = profile,
            previousBloom = state.bloom,
            elapsedTicks = elapsedTicks(player.level().gameTime, state.lastShotTick),
            aimProgress = aimProgress,
            movingAmount = movingAmount,
            airborne = !player.onGround(),
            accuracyScale = accuracyScale * SolarWarlockFragmentRuntime.airborneWeaponSpreadMultiplier(player)
        )
        state.bloom = cone.bloomAfterShot
        state.lastShotTick = player.level().gameTime
        return spread(validatedDirection, cone.degrees, random.nextFloat(), random.nextFloat())
    }

    fun advance(
        profile: WeaponAccuracyProfile,
        previousBloom: Float,
        elapsedTicks: Long,
        aimProgress: Float,
        movingAmount: Float,
        airborne: Boolean,
        accuracyScale: Float = 1.0f
    ): ShotCone {
        val recoveredBloom = recoveredBloom(profile, previousBloom, elapsedTicks)
        val aim = aimProgress.coerceIn(0.0f, 1.0f)
        val resting = Mth.lerp(aim, profile.hipBaseDegrees, profile.aimedBaseDegrees)
        val movement = profile.movingPenaltyDegrees * movingAmount.coerceIn(0.0f, 1.0f) *
            Mth.lerp(aim, 1.0f, 0.35f)
        val airbornePenalty = if (airborne) profile.airbornePenaltyDegrees else 0.0f
        val scale = accuracyScale.coerceAtLeast(0.0f)
        val degrees = (resting + movement + airbornePenalty + recoveredBloom) * scale
        return ShotCone(
            degrees = degrees.coerceAtLeast(0.0f),
            bloomBeforeShot = recoveredBloom,
            bloomAfterShot = (recoveredBloom + profile.bloomPerShotDegrees)
                .coerceAtMost(profile.maxBloomDegrees)
        )
    }

    fun recoveredBloom(profile: WeaponAccuracyProfile, previousBloom: Float, elapsedTicks: Long): Float {
        if (elapsedTicks == Long.MAX_VALUE) return 0.0f
        val decayTicks = (elapsedTicks - profile.settleDelayTicks).coerceAtLeast(0L)
        return (previousBloom - decayTicks * profile.bloomDecayPerTick).coerceAtLeast(0.0f)
    }

    fun spread(direction: Vec3, coneDegrees: Float, radiusRoll: Float, angleRoll: Float): Vec3 {
        val forward = direction.normalize()
        if (forward.lengthSqr() < 1.0e-8 || coneDegrees <= 0.0f) return forward
        val referenceUp = if (abs(forward.y) > 0.99) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val right = forward.cross(referenceUp).normalize()
        val up = right.cross(forward).normalize()
        val radius = sqrt(radiusRoll.coerceIn(0.0f, 1.0f).toDouble()) *
            tan(Math.toRadians(coneDegrees.toDouble()))
        val angle = angleRoll.coerceIn(0.0f, 1.0f) * PI * 2.0
        return forward
            .add(right.scale(cos(angle) * radius))
            .add(up.scale(sin(angle) * radius))
            .normalize()
    }

    fun clear(playerId: UUID) {
        states.keys.removeIf { it.playerId == playerId }
    }

    private fun elapsedTicks(now: Long, previous: Long): Long =
        if (previous == Long.MIN_VALUE) Long.MAX_VALUE else (now - previous).coerceAtLeast(0L)
}
