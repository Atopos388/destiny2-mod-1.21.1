package atopos.destiny2.client.combat

import atopos.destiny2.common.combat.QuickMeleeRules
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Frame-sampled, resistible camera pull for an uncharged-melee target.
 *
 * A local prediction removes the input-to-camera round trip. The server still
 * owns target validation and can replace the predicted entity via its reply.
 */
object QuickMeleeAimClient {
    private const val ASSIST_DURATION_MS = 230.0f
    private const val RESPONSE_PER_SECOND = 22.0f
    private const val MAX_YAW_SPEED = 520.0f
    private const val MAX_PITCH_SPEED = 310.0f
    private const val BREAK_ANGLE = 52.0f
    private const val MAX_FRAME_MS = 50.0f
    private const val PREDICTION_COOLDOWN_MS = 600L

    private var targetEntityId = -1
    private var startedAtNanos = Long.MIN_VALUE
    private var lastFrameNanos = Long.MIN_VALUE
    private var lastPredictionAtMs = Long.MIN_VALUE

    fun start(targetEntityId: Int) {
        val alreadyActive = startedAtNanos != Long.MIN_VALUE
        if (this.targetEntityId == targetEntityId && alreadyActive) return
        this.targetEntityId = targetEntityId
        // A server correction may replace the locally predicted entity. Keep the
        // original assist timing and impulse so the reply cannot create a second
        // camera snap or lunge.
        if (alreadyActive) return
        val now = System.nanoTime()
        startedAtNanos = now
        lastFrameNanos = now
        applyPredictedLunge(Minecraft.getInstance())
    }

    fun predict(client: Minecraft) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastPredictionAtMs < PREDICTION_COOLDOWN_MS) return
        val player = client.player ?: return
        val target = selectLocalTarget(player) ?: return
        lastPredictionAtMs = nowMs
        start(target.id)
    }

    /** Called from GameRenderer.render, so motion is continuous at render FPS. */
    fun updateFrame(player: LocalPlayer) {
        if (startedAtNanos == Long.MIN_VALUE || Minecraft.getInstance().screen != null) return clear()
        val target = player.level().getEntity(targetEntityId) ?: return clear()
        if (!target.isAlive) return clear()

        val now = System.nanoTime()
        val elapsedMs = (now - startedAtNanos) / 1_000_000.0f
        if (elapsedMs >= ASSIST_DURATION_MS) return clear()
        val deltaMs = ((now - lastFrameNanos) / 1_000_000.0f).coerceIn(0.0f, MAX_FRAME_MS)
        lastFrameNanos = now
        if (deltaMs <= 0.0f) return

        val dx = target.x - player.x
        val bounds = target.boundingBox
        val targetY = bounds.minY + bounds.ysize * 0.62
        val dy = targetY - player.eyeY
        val dz = target.z - player.z
        val horizontal = sqrt(dx * dx + dz * dz)
        if (horizontal <= 0.001) return clear()

        val targetYaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90.0f
        val targetPitch = -Math.toDegrees(atan2(dy, horizontal)).toFloat()
        val yawDelta = Mth.wrapDegrees(targetYaw - player.yRot)
        val pitchDelta = Mth.wrapDegrees(targetPitch - player.xRot)
        if (kotlin.math.abs(yawDelta) > BREAK_ANGLE) return clear()

        val envelope = assistEnvelope(elapsedMs / ASSIST_DURATION_MS)
        val deltaSeconds = deltaMs / 1000.0f
        val response = 1.0f - exp(-RESPONSE_PER_SECOND * envelope * deltaSeconds)
        val yawStepLimit = MAX_YAW_SPEED * deltaSeconds
        val pitchStepLimit = MAX_PITCH_SPEED * deltaSeconds

        player.yRot += (yawDelta * response).coerceIn(-yawStepLimit, yawStepLimit)
        player.xRot = (player.xRot +
            (pitchDelta * response).coerceIn(-pitchStepLimit, pitchStepLimit))
            .coerceIn(-90.0f, 90.0f)
    }

    private fun assistEnvelope(progress: Float): Float = when {
        progress < 0.20f -> {
            val t = smoothStep(progress / 0.20f)
            0.45f + t * 0.55f
        }
        progress < 0.72f -> 1.0f
        else -> {
            val t = smoothStep((progress - 0.72f) / 0.28f)
            1.0f - t * 0.90f
        }
    }

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

    private fun selectLocalTarget(player: LocalPlayer): LivingEntity? {
        val eye = player.eyePosition
        val look = player.lookAngle.normalize()
        return player.level().getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(QuickMeleeRules.ASSIST_RANGE)
        ) { candidate ->
            isValidTarget(player, candidate) && player.hasLineOfSight(candidate)
        }.mapNotNull { candidate ->
            val bounds = candidate.boundingBox
            val centerMass = Vec3(candidate.x, bounds.minY + bounds.ysize * 0.62, candidate.z)
            val aimOffset = centerMass.subtract(eye)
            if (aimOffset.lengthSqr() <= 0.0001) return@mapNotNull null
            val closest = Vec3(
                eye.x.coerceIn(bounds.minX, bounds.maxX),
                eye.y.coerceIn(bounds.minY, bounds.maxY),
                eye.z.coerceIn(bounds.minZ, bounds.maxZ)
            )
            val distance = closest.distanceTo(eye)
            val score = QuickMeleeRules.candidateScore(distance, look.dot(aimOffset.normalize()))
                ?: return@mapNotNull null
            Pair(candidate, score)
        }.maxByOrNull { it.second }?.first
    }

    private fun isValidTarget(player: LocalPlayer, candidate: LivingEntity): Boolean {
        if (candidate === player || !candidate.isAlive || player.isAlliedTo(candidate)) return false
        if (candidate is Player && (candidate.isCreative || candidate.isSpectator)) return false
        return true
    }

    private fun applyPredictedLunge(client: Minecraft) {
        val player = client.player ?: return
        val target = client.level?.getEntity(targetEntityId) ?: return
        val horizontal = Vec3(target.x - player.x, 0.0, target.z - player.z)
        val distance = horizontal.length()
        if (distance <= 0.01) return
        val speed = QuickMeleeRules.lungeSpeed(distance)
        if (speed <= 0.0) return

        val direction = horizontal.scale(1.0 / distance)
        val motion = player.deltaMovement
        player.deltaMovement = Vec3(
            motion.x * 0.25 + direction.x * speed,
            if (player.onGround()) 0.02 else motion.y,
            motion.z * 0.25 + direction.z * speed
        )
        player.hasImpulse = true
    }

    private fun clear() {
        targetEntityId = -1
        startedAtNanos = Long.MIN_VALUE
        lastFrameNanos = Long.MIN_VALUE
    }
}
