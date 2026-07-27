package atopos.destiny2.client.cinematic

import atopos.destiny2.client.util.PlayerAnimationHelper
import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/** Client-owned cinematic camera. Gameplay triggers remain server-authoritative. */
object CinematicCameraClient {
    data class CameraPose(
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
        val roll: Float
    )

    private data class Definition(
        val animationResource: ResourceLocation,
        val animationName: String,
        val playerAnimation: ResourceLocation?,
        val fallbackDurationTicks: Int
    )

    private data class PendingStart(
        val payload: DestinyNetworking.StartCinematicPayload,
        var waitedTicks: Int = 0
    )

    private data class Session(
        val sessionId: UUID,
        val definition: Definition,
        val anchor: Vec3,
        val anchorYaw: Float,
        val track: GeckoLibCameraTrackSampler?,
        val durationTicks: Int,
        val level: ClientLevel,
        val player: LocalPlayer,
        val cameraEntity: ArmorStand,
        val previousCameraEntity: Entity?,
        val previousCameraType: CameraType,
        var elapsedTicks: Int = 0,
        var playerAnimationStarted: Boolean = false
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

    fun currentPose(partialTick: Float): CameraPose? {
        val session = activeSession ?: return null
        val sampleTick = if (Minecraft.getInstance().isPaused) {
            session.elapsedTicks.toDouble()
        } else {
            session.elapsedTicks + partialTick.coerceIn(0.0f, 1.0f).toDouble()
        }
        val local = session.track?.sample(sampleTick)
        val localPixels = local?.positionPixels ?: FALLBACK_POSITION_PIXELS
        val localBlocks = localPixels.scale(1.0 / MODEL_PIXELS_PER_BLOCK)
        val yawRadians = Math.toRadians(session.anchorYaw.toDouble())
        val forward = Vec3(-sin(yawRadians), 0.0, cos(yawRadians))
        val right = Vec3(-cos(yawRadians), 0.0, -sin(yawRadians))
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
        tryStartPlayerAnimation(session)

        val pose = currentPose(0.0f)
        if (pose != null) {
            session.cameraEntity.setPos(pose.position)
            session.cameraEntity.yRot = pose.yaw
            session.cameraEntity.xRot = pose.pitch
        }

        if (client.isPaused) return
        session.elapsedTicks++
        if (session.elapsedTicks >= session.durationTicks) {
            finish()
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

        val track = GeckoLibCameraTrackSampler.load(definition.animationResource, definition.animationName)
        if (track == null && pending.waitedTicks++ < RESOURCE_WAIT_TICKS) return
        if (track == null) {
            logger.warn(
                "Cinematic camera track missing: {}#{}; using safe fallback",
                definition.animationResource,
                definition.animationName
            )
        }

        val camera = ArmorStand(level, pending.payload.anchorX, pending.payload.anchorY, pending.payload.anchorZ)
        camera.isInvisible = true
        camera.isNoGravity = true
        val session = Session(
            sessionId = pending.payload.sessionId,
            definition = definition,
            anchor = Vec3(pending.payload.anchorX, pending.payload.anchorY, pending.payload.anchorZ),
            anchorYaw = pending.payload.anchorYaw,
            track = track,
            durationTicks = track?.durationTicks ?: definition.fallbackDurationTicks,
            level = level,
            player = player,
            cameraEntity = camera,
            previousCameraEntity = client.cameraEntity,
            previousCameraType = client.options.cameraType
        )
        pendingStart = null
        activeSession = session
        client.options.cameraType = CameraType.FIRST_PERSON
        client.setCameraEntity(camera)
        tryStartPlayerAnimation(session)
    }

    private fun tryStartPlayerAnimation(session: Session) {
        if (session.playerAnimationStarted) return
        val animationId = session.definition.playerAnimation ?: return
        if (PlayerAnimationRegistry.getAnimation(animationId) == null) return
        session.playerAnimationStarted = PlayerAnimationHelper.playAnimation(
            player = session.player,
            animationId = animationId,
            forceThirdPerson = false,
            cameraResetDelayMs = session.durationTicks * 50L,
            blendInTicks = 3,
            blendOutTicks = 0
        )
    }

    private fun finish() {
        val sessionId = activeSession?.sessionId ?: return
        restoreClientCamera()
        activeSession = null
        acknowledge(sessionId)
    }

    private fun abort(sendAcknowledgement: Boolean) {
        val sessionId = activeSession?.sessionId
        restoreClientCamera()
        activeSession = null
        if (sendAcknowledgement && sessionId != null) acknowledge(sessionId)
    }

    private fun restoreClientCamera() {
        val session = activeSession ?: return
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

    private val definitions = mapOf(
        "guardian_awakening" to Definition(
            animationResource = ResourceLocation.fromNamespaceAndPath(
                "destiny2-mod",
                "animations/guardian_awakening.animation.json"
            ),
            animationName = "animation.destiny2.guardian_awakening",
            playerAnimation = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "guardian_awakening"),
            fallbackDurationTicks = 6 * 20
        )
    )

    private val FALLBACK_POSITION_PIXELS = Vec3(0.0, 40.0, 64.0)
    private const val FALLBACK_YAW = -180.0f
    private const val FALLBACK_PITCH = -10.0f
    private const val MODEL_PIXELS_PER_BLOCK = 16.0
    private const val RESOURCE_WAIT_TICKS = 40
    private const val START_TIMEOUT_TICKS = 20 * 10
}
