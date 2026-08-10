// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.camera

import atopos.destiny2.Destiny2MODClient
import atopos.destiny2.client.cinematic.GeckoLibCameraTrackSampler
import atopos.destiny2.client.renderer.ThunderclapPlayerProxyClient
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.sin

/** Authored third-person camera and body-facing lock for Arc Titan Thunderclap. */
object ThunderclapCameraClient {
    data class CameraPose(
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
        val roll: Float
    )

    private enum class Phase(
        val animationName: String,
        val fallbackDurationTicks: Int
    ) {
        CHARGE("animation.destiny2.player.thunderclap_charge", 41),
        RELEASE("animation.destiny2.player.thunderclap_release", 35)
    }

    private data class BodyLock(
        val yaw: Float,
        var expiresAtPlayerTick: Int
    )

    private data class Session(
        val phase: Phase,
        val startedAtPlayerTick: Int,
        val durationTicks: Int,
        val anchor: Vec3,
        val lockedBodyYaw: Float,
        val phaseViewYaw: Float,
        val phaseViewPitch: Float,
        val previousCameraType: CameraType,
        val cameraTrack: GeckoLibCameraTrackSampler?,
        val fovTrack: GeckoLibCameraTrackSampler?
    )

    private val bodyLocks = WeakHashMap<AbstractClientPlayer, BodyLock>()
    private var localSession: Session? = null
    private var perspectiveGuardUntilNanos = 0L

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear(restorePerspective = false) }
    }

    fun startCharge(player: AbstractClientPlayer, durationTicks: Int) {
        val duration = durationTicks.coerceAtLeast(1)
        val chargeYaw = player.yRot
        // The vanilla player renderer already converts the model's North-facing
        // local front into entity yaw. Adding another 180 degrees here makes the
        // player face the third-person camera instead of the cast direction.
        bodyLocks[player] = BodyLock(chargeYaw, player.tickCount + duration)
        applyBodyYaw(player, chargeYaw)
        if (player !== Minecraft.getInstance().player) return
        startLocal(player, Phase.CHARGE, duration, chargeYaw, player.yRot, player.xRot)
    }

    fun startRelease(player: AbstractClientPlayer, durationTicks: Int) {
        val duration = durationTicks.coerceAtLeast(1)
        // Charge keeps its initial facing, but the authored burst starts in the
        // direction selected by the player's view at the release instant.
        val releaseYaw = player.yRot
        bodyLocks[player] = BodyLock(releaseYaw, player.tickCount + duration)
        applyBodyYaw(player, releaseYaw)
        if (player !== Minecraft.getInstance().player) return
        startLocal(player, Phase.RELEASE, duration, releaseYaw, releaseYaw, player.xRot)
    }

    fun isMovementLocked(): Boolean = localSession != null

    fun isViewLocked(): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val session = localSession ?: return false
        return session.phase == Phase.CHARGE && elapsedTicks(player, session, 0.0f) < CAMERA_ARRIVAL_TICKS
    }

    fun shouldUseInstantPerspectiveChange(): Boolean =
        localSession != null || System.nanoTime() < perspectiveGuardUntilNanos

    fun currentFov(partialTick: Float): Double? {
        val player = Minecraft.getInstance().player ?: return null
        val session = localSession ?: return null
        val elapsed = elapsedTicks(player, session, partialTick)
        val sampleTick = elapsed.coerceIn(0.0, session.durationTicks.toDouble())
        val sampled = session.fovTrack?.sample(sampleTick)?.positionPixels?.x ?: DEFAULT_FOV
        return sampled.coerceIn(MIN_FOV, MAX_FOV)
    }

    fun currentPose(partialTick: Float): CameraPose? {
        val player = Minecraft.getInstance().player ?: return null
        val session = localSession ?: return null
        val elapsed = elapsedTicks(player, session, partialTick)
        val track = session.cameraTrack ?: return null
        val authoredTick = when (session.phase) {
            Phase.CHARGE -> minOf(elapsed, CAMERA_ARRIVAL_TICKS)
            Phase.RELEASE -> elapsed
        }.coerceIn(0.0, session.durationTicks.toDouble())
        val local = track.sample(authoredTick)
        val orbiting = session.phase == Phase.RELEASE || elapsed >= CAMERA_ARRIVAL_TICKS
        val orbitYaw = if (orbiting) player.yRot else session.phaseViewYaw
        val orbitPitchDelta = if (orbiting) {
            Mth.wrapDegrees(player.xRot - session.phaseViewPitch)
        } else {
            0.0f
        }

        val localBlocks = local.positionPixels.scale(1.0 / MODEL_PIXELS_PER_BLOCK)
        val yawRadians = Math.toRadians(orbitYaw.toDouble())
        val forward = Vec3(-sin(yawRadians), 0.0, cos(yawRadians))
        val right = Vec3(cos(yawRadians), 0.0, sin(yawRadians))
        val worldPosition = session.anchor
            .add(right.scale(localBlocks.x))
            .add(0.0, localBlocks.y, 0.0)
            .add(forward.scale(localBlocks.z))
        return CameraPose(
            position = worldPosition,
            yaw = orbitYaw + local.yaw,
            pitch = (local.pitch + orbitPitchDelta).coerceIn(-89.9f, 89.9f),
            roll = local.roll
        )
    }

    private fun startLocal(
        player: AbstractClientPlayer,
        phase: Phase,
        durationTicks: Int,
        lockedBodyYaw: Float,
        viewYaw: Float,
        viewPitch: Float
    ) {
        val client = Minecraft.getInstance()
        val previous = localSession
        localSession = Session(
            phase = phase,
            startedAtPlayerTick = player.tickCount,
            durationTicks = durationTicks,
            anchor = previous?.anchor ?: player.position(),
            lockedBodyYaw = lockedBodyYaw,
            phaseViewYaw = viewYaw,
            phaseViewPitch = viewPitch,
            previousCameraType = previous?.previousCameraType ?: client.options.cameraType,
            cameraTrack = GeckoLibCameraTrackSampler.load(CAMERA_RESOURCE, phase.animationName),
            fovTrack = GeckoLibCameraTrackSampler.load(CAMERA_RESOURCE, phase.animationName, "camera_fov")
        )
        perspectiveGuardUntilNanos = 0L
        Destiny2MODClient.cameraResetTime = -1L
        Destiny2MODClient.shouldResetToFirstPerson = false
        client.options.cameraType = CameraType.THIRD_PERSON_BACK
    }

    private fun tick(client: Minecraft) {
        val bodyIterator = bodyLocks.entries.iterator()
        while (bodyIterator.hasNext()) {
            val (player, lock) = bodyIterator.next()
            if (player.isRemoved || player.tickCount >= lock.expiresAtPlayerTick) {
                bodyIterator.remove()
                continue
            }
            applyBodyYaw(player, lock.yaw)
        }

        val session = localSession ?: return
        val player = client.player
        if (player == null || client.level == null || !player.isAlive) {
            clear(restorePerspective = true)
            return
        }
        if (player.tickCount - session.startedAtPlayerTick >= session.durationTicks) {
            finish(client, session)
            return
        }

        if (client.options.cameraType != CameraType.THIRD_PERSON_BACK) {
            client.options.cameraType = CameraType.THIRD_PERSON_BACK
        }
        player.setPos(session.anchor)
        player.deltaMovement = Vec3.ZERO
        player.fallDistance = 0.0f
    }

    private fun finish(client: Minecraft, session: Session) {
        if (localSession !== session) return
        localSession = null
        perspectiveGuardUntilNanos = System.nanoTime() + PERSPECTIVE_GUARD_NANOS
        client.player?.uuid?.let(ThunderclapPlayerProxyClient::stop)
        client.options.cameraType = session.previousCameraType
        if (session.previousCameraType.isFirstPerson) {
            Destiny2MODClient.firstPersonReturnVisualUntil =
                System.currentTimeMillis() + FIRST_PERSON_RETURN_TRANSITION_MS
        }
    }

    private fun applyBodyYaw(player: AbstractClientPlayer, yaw: Float) {
        player.yBodyRot = yaw
        player.yBodyRotO = yaw
        player.yHeadRot = yaw
        player.yHeadRotO = yaw
    }

    private fun clear(restorePerspective: Boolean) {
        val session = localSession
        localSession = null
        bodyLocks.clear()
        perspectiveGuardUntilNanos = 0L
        Minecraft.getInstance().player?.uuid?.let(ThunderclapPlayerProxyClient::stop)
        if (restorePerspective && session != null) {
            Minecraft.getInstance().options.cameraType = session.previousCameraType
        }
    }

    private fun elapsedTicks(
        player: AbstractClientPlayer,
        session: Session,
        partialTick: Float
    ): Double = (player.tickCount - session.startedAtPlayerTick).toDouble() +
        partialTick.coerceIn(0.0f, 1.0f).toDouble()

    private val CAMERA_RESOURCE = ResourceLocation.fromNamespaceAndPath(
        "destiny2-mod",
        "animations/player/thunderclap.animation.json"
    )
    private const val CAMERA_ARRIVAL_TICKS = 5.0
    private const val MODEL_PIXELS_PER_BLOCK = 16.0
    private const val DEFAULT_FOV = 70.0
    private const val MIN_FOV = 30.0
    private const val MAX_FOV = 110.0
    private const val FIRST_PERSON_RETURN_TRANSITION_MS = 300L
    private const val PERSPECTIVE_GUARD_NANOS = 250_000_000L
}
