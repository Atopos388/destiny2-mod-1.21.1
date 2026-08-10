package atopos.destiny2.client.cinematic

import atopos.destiny2.common.entity.JilingEntity
import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.CameraType
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Client-owned film + world-animation camera. Gameplay triggers remain server-authoritative. */
object CinematicCameraClient {
    data class CameraPose(
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
        val roll: Float
    )

    private enum class Phase {
        VIDEO,
        WAITING_FOR_ACTOR,
        ANIMATION
    }

    private data class Definition(
        val animationResource: ResourceLocation,
        val animationName: String,
        val fallbackAnimationTicks: Int,
        val video: CinematicVideoClient.Definition
    )

    private data class PendingStart(
        val payload: DestinyNetworking.StartCinematicPayload,
        var waitedTicks: Int = 0
    )

    private data class Session(
        val sessionId: UUID,
        val actorEntityId: Int,
        val definition: Definition,
        val anchor: Vec3,
        val anchorYaw: Float,
        var track: GeckoLibCameraTrackSampler?,
        val level: ClientLevel,
        val player: LocalPlayer,
        val cameraEntity: ArmorStand,
        val previousCameraEntity: Entity?,
        val previousCameraType: CameraType,
        var phase: Phase,
        var phaseTicks: Int = 0,
        var animationRequested: Boolean = false
    )

    private val logger = LoggerFactory.getLogger("DestinyCinematic")
    private var pendingStart: PendingStart? = null
    private var activeSession: Session? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            abort(sendAcknowledgement = false)
            pendingStart = null
        }
    }

    fun requestStart(payload: DestinyNetworking.StartCinematicPayload) {
        if (activeSession?.sessionId == payload.sessionId || pendingStart?.payload?.sessionId == payload.sessionId) return
        abort(sendAcknowledgement = false)
        pendingStart = PendingStart(payload)
    }

    fun isActive(): Boolean = activeSession != null

    fun shouldHideHud(): Boolean = isActive()

    fun cinematicFov(): Double? = activeSession?.let { 70.0 }

    fun renderOverlay(graphics: GuiGraphics, deltaTracker: DeltaTracker): Boolean {
        val session = activeSession ?: return false
        return when (session.phase) {
            Phase.VIDEO -> CinematicVideoClient.render(graphics, deltaTracker, session.phaseTicks)
            Phase.WAITING_FOR_ACTOR -> {
                graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), BLACK)
                true
            }
            Phase.ANIMATION -> {
                renderEyeOpening(graphics, deltaTracker, session.phaseTicks)
                AwakeningDialogueClient.render(graphics, deltaTracker)
                true
            }
        }
    }

    fun currentPose(partialTick: Float): CameraPose? {
        val session = activeSession ?: return null
        val phaseTick = if (session.phase == Phase.ANIMATION) session.phaseTicks else 0
        val sampleTick = if (Minecraft.getInstance().isPaused) {
            phaseTick.toDouble()
        } else {
            phaseTick + partialTick.coerceIn(0.0f, 1.0f).toDouble()
        }
        val local = session.track?.sample(sampleTick)
        val localPixels = local?.positionPixels ?: FALLBACK_POSITION_PIXELS
        val localBlocks = localPixels.scale(1.0 / MODEL_PIXELS_PER_BLOCK)
        val yawRadians = Math.toRadians(session.anchorYaw.toDouble())
        val forward = Vec3(-sin(yawRadians), 0.0, cos(yawRadians))
        val right = Vec3(cos(yawRadians), 0.0, sin(yawRadians))
        val worldPosition = session.anchor
            .add(right.scale(localBlocks.x))
            .add(0.0, localBlocks.y, 0.0)
            .add(forward.scale(localBlocks.z))
        return CameraPose(
            position = worldPosition,
            yaw = session.anchorYaw + (local?.yaw ?: FALLBACK_YAW),
            pitch = local?.pitch ?: FALLBACK_PITCH,
            roll = local?.roll ?: 0.0f
        )
    }

    private fun tick(client: Minecraft) {
        startPendingIfReady(client)
        val session = activeSession ?: return

        if (client.level !== session.level || client.player !== session.player) {
            abort(sendAcknowledgement = client.connection != null)
            return
        }

        client.options.cameraType = CameraType.FIRST_PERSON
        if (client.cameraEntity !== session.cameraEntity) {
            client.setCameraEntity(session.cameraEntity)
        }
        nullifyInput(session.player)
        applyCameraEntityPose(session)

        if (client.isPaused) return
        when (session.phase) {
            Phase.VIDEO -> {
                session.phaseTicks++
                if (session.phaseTicks >= session.definition.video.durationTicks) {
                    beginAnimationTransition(session)
                }
            }
            Phase.WAITING_FOR_ACTOR -> {
                session.phaseTicks++
                val actor = session.level.getEntity(session.actorEntityId) as? JilingEntity
                if (actor?.isAwakeningStarted() == true) {
                    session.track = session.track ?: loadTrack(session.definition)
                    session.phase = Phase.ANIMATION
                    session.phaseTicks = 0
                    AwakeningDialogueClient.startTimeline()
                } else if (session.phaseTicks >= ANIMATION_START_TIMEOUT_TICKS) {
                    logger.warn("Awakening actor {} did not start in time", session.actorEntityId)
                    finish()
                }
            }
            Phase.ANIMATION -> {
                session.phaseTicks++
                val duration = session.track?.durationTicks ?: session.definition.fallbackAnimationTicks
                if (session.phaseTicks >= duration) finish()
            }
        }
    }

    private fun startPendingIfReady(client: Minecraft) {
        val pending = pendingStart ?: return
        val definition = definitions[pending.payload.cinematicId]
        if (definition == null) {
            logger.warn("Unknown cinematic id: {}", pending.payload.cinematicId)
            pendingStart = null
            acknowledge(pending.payload.sessionId)
            return
        }
        val player = client.player
        val level = client.level
        if (player == null || level == null || !player.isAlive) {
            pending.waitedTicks++
            if (pending.waitedTicks > START_TIMEOUT_TICKS) {
                logger.warn("Cinematic {} timed out waiting for a live local player", pending.payload.cinematicId)
                pendingStart = null
            }
            return
        }

        val videoActive = CinematicVideoClient.start(definition.video)
        val camera = ArmorStand(
            level,
            pending.payload.anchorX,
            pending.payload.anchorY,
            pending.payload.anchorZ
        ).apply {
            isInvisible = true
            isNoGravity = true
        }
        val session = Session(
            sessionId = pending.payload.sessionId,
            actorEntityId = pending.payload.actorEntityId,
            definition = definition,
            anchor = Vec3(pending.payload.anchorX, pending.payload.anchorY, pending.payload.anchorZ),
            anchorYaw = pending.payload.anchorYaw,
            track = loadTrack(definition),
            level = level,
            player = player,
            cameraEntity = camera,
            previousCameraEntity = client.cameraEntity,
            previousCameraType = client.options.cameraType,
            phase = if (videoActive) Phase.VIDEO else Phase.WAITING_FOR_ACTOR
        )
        pendingStart = null
        activeSession = session
        client.options.cameraType = CameraType.FIRST_PERSON
        client.setCameraEntity(camera)
        applyCameraEntityPose(session)
        if (!videoActive) requestAnimationStart(session)
    }

    private fun beginAnimationTransition(session: Session) {
        CinematicVideoClient.stop()
        session.phase = Phase.WAITING_FOR_ACTOR
        session.phaseTicks = 0
        requestAnimationStart(session)
    }

    private fun requestAnimationStart(session: Session) {
        if (session.animationRequested) return
        session.animationRequested = true
        if (ClientPlayNetworking.canSend(DestinyNetworking.BeginCinematicAnimationPayload.ID)) {
            ClientPlayNetworking.send(DestinyNetworking.BeginCinematicAnimationPayload(session.sessionId))
        }
    }

    private fun loadTrack(definition: Definition): GeckoLibCameraTrackSampler? =
        GeckoLibCameraTrackSampler.load(definition.animationResource, definition.animationName).also {
            if (it == null) {
                logger.warn(
                    "Cinematic camera track missing: {}#{}; using safe fallback",
                    definition.animationResource,
                    definition.animationName
                )
            }
        }

    private fun applyCameraEntityPose(session: Session) {
        val pose = currentPose(0.0f) ?: return
        session.cameraEntity.setPos(pose.position)
        session.cameraEntity.yRot = pose.yaw
        session.cameraEntity.xRot = pose.pitch
    }

    private fun finish() {
        val session = activeSession ?: return
        restoreClientCamera()
        activeSession = null
        acknowledge(session.sessionId)
    }

    private fun abort(sendAcknowledgement: Boolean) {
        val sessionId = activeSession?.sessionId
        restoreClientCamera()
        activeSession = null
        if (sendAcknowledgement && sessionId != null) acknowledge(sessionId)
    }

    private fun restoreClientCamera() {
        val session = activeSession ?: return
        CinematicVideoClient.stop()
        AwakeningDialogueClient.stop()
        val client = Minecraft.getInstance()
        val replacement = session.previousCameraEntity
            ?.takeIf { !it.isRemoved && it.level() === client.level }
            ?: client.player
        if (replacement != null) client.setCameraEntity(replacement)
        client.options.cameraType = session.previousCameraType
    }

    private fun acknowledge(sessionId: UUID) {
        if (ClientPlayNetworking.canSend(DestinyNetworking.FinishCinematicPayload.ID)) {
            ClientPlayNetworking.send(DestinyNetworking.FinishCinematicPayload(sessionId))
        }
    }

    private fun nullifyInput(player: LocalPlayer) {
        player.input.leftImpulse = 0.0f
        player.input.forwardImpulse = 0.0f
        player.input.up = false
        player.input.down = false
        player.input.left = false
        player.input.right = false
        player.input.jumping = false
        player.input.shiftKeyDown = false
    }

    private fun renderEyeOpening(graphics: GuiGraphics, deltaTracker: DeltaTracker, elapsedTicks: Int) {
        val partialTick = if (Minecraft.getInstance().isPaused) {
            0.0f
        } else {
            deltaTracker.getGameTimeDeltaPartialTick(false).coerceIn(0.0f, 1.0f)
        }
        val age = elapsedTicks + partialTick
        if (age >= EYE_OPEN_TOTAL_TICKS) return

        val progress = ((age - EYE_CLOSED_HOLD_TICKS) / EYE_OPENING_TICKS).coerceIn(0.0f, 1.0f)
        val openness = progress * progress * (3.0f - 2.0f * progress)
        val fade = if (progress <= EYE_EDGE_FADE_START) {
            1.0f
        } else {
            ((1.0f - progress) / (1.0f - EYE_EDGE_FADE_START)).coerceIn(0.0f, 1.0f)
        }
        val alpha = (255.0f * fade).roundToInt().coerceIn(0, 255)
        if (alpha <= 0) return

        val width = graphics.guiWidth()
        val height = graphics.guiHeight()
        val centerY = height / 2.0
        val segmentWidth = maxOf(4, width / 96)
        var left = 0
        while (left < width) {
            val right = minOf(width, left + segmentWidth)
            val normalizedX = (((left + right) * 0.5 / width) * 2.0 - 1.0).coerceIn(-1.0, 1.0)
            val eyeCurve = (1.0 - normalizedX * normalizedX).coerceAtLeast(0.0).pow(0.55)
            val halfOpening = centerY * openness * eyeCurve
            val upperEdge = (centerY - halfOpening).roundToInt().coerceIn(0, height)
            val lowerEdge = (centerY + halfOpening).roundToInt().coerceIn(0, height)
            graphics.fill(left, 0, right, upperEdge, alpha shl 24)
            graphics.fill(left, lowerEdge, right, height, alpha shl 24)
            left = right
        }
    }

    private val definitions = mapOf(
        "guardian_awakening" to Definition(
            animationResource = ResourceLocation.fromNamespaceAndPath(
                "destiny2-mod",
                "animations/entity/jiling.animation.json"
            ),
            animationName = "animation.destiny2.jiling.awakening",
            fallbackAnimationTicks = 541,
            video = CinematicVideoClient.Definition(
                framePathFormat = "textures/cinematic/guardian_awakening/frame_%04d.jpg",
                frameCount = 312,
                framesPerSecond = 24,
                sourceWidth = 1280,
                sourceHeight = 720
            )
        )
    )

    private val FALLBACK_POSITION_PIXELS = Vec3(0.0, 6.26563, -11.03125)
    private const val FALLBACK_YAW = 0.0f
    private const val FALLBACK_PITCH = 57.0f
    private const val MODEL_PIXELS_PER_BLOCK = 16.0
    private const val START_TIMEOUT_TICKS = 20 * 10
    private const val ANIMATION_START_TIMEOUT_TICKS = 20 * 10
    private const val EYE_CLOSED_HOLD_TICKS = 3.0f
    private const val EYE_OPENING_TICKS = 24.0f
    private const val EYE_OPEN_TOTAL_TICKS = EYE_CLOSED_HOLD_TICKS + EYE_OPENING_TICKS
    private const val EYE_EDGE_FADE_START = 0.82f
    private const val BLACK = -0x1000000
}
