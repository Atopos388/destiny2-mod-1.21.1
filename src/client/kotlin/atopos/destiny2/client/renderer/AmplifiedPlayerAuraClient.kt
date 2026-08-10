// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.common.effect.DestinyEffects
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.RenderType
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Client-only, short-lived procedural lightning used by the Amplified status effect. */
object AmplifiedPlayerAuraClient {
    private const val MAX_RENDER_DISTANCE_SQR = 32.0 * 32.0
    private const val REDUCED_DENSITY_DISTANCE_SQR = 18.0 * 18.0
    private const val MAX_ACTIVE_ARCS = 112
    private const val ARC_SEGMENTS = 6

    private val arcs = ArrayList<ZapArc>(MAX_ACTIVE_ARCS)
    private var activeLevel: ClientLevel? = null

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        WorldRenderEvents.AFTER_ENTITIES.register(::render)
    }

    private fun tick(client: Minecraft) {
        val level = client.level
        if (level == null) {
            arcs.clear()
            activeLevel = null
            return
        }
        if (activeLevel !== level) {
            arcs.clear()
            activeLevel = level
        }

        arcs.removeAll { ++it.age >= it.lifetime }
        val cameraPosition = client.gameRenderer.mainCamera.position
        val localPlayer = client.player

        level.players().forEach { player ->
            if (!shouldRender(player)) return@forEach
            val distanceSqr = cameraPosition.distanceToSqr(player.position())
            if (distanceSqr > MAX_RENDER_DISTANCE_SQR) return@forEach
            if (player === localPlayer && CinematicCameraClient.isActive()) return@forEach
            if (player === localPlayer && client.options.cameraType.isFirstPerson) return@forEach

            val reducedDensity = distanceSqr > REDUCED_DENSITY_DISTANCE_SQR
            spawnBodyArcs(player, reducedDensity)
            if (!reducedDensity && player.onGround() && level.gameTime % 2L == 0L) {
                spawnGroundArc(player)
            }
        }

        if (arcs.size > MAX_ACTIVE_ARCS) {
            arcs.subList(0, arcs.size - MAX_ACTIVE_ARCS).clear()
        }
    }

    private fun shouldRender(player: AbstractClientPlayer): Boolean =
        player.hasEffect(DestinyEffects.AMPLIFIED) && !player.isInvisible && !player.isSpectator

    private fun spawnBodyArcs(player: AbstractClientPlayer, reducedDensity: Boolean) {
        val tick = player.level().gameTime
        if (reducedDensity && tick % 3L != 0L) return
        val count = if (player.isSprinting && !reducedDensity) 2 else 1
        val baseSeed = mix64(player.uuid.leastSignificantBits xor player.uuid.mostSignificantBits xor tick)

        repeat(count) { index ->
            val seed = mix64(baseSeed + index * -7046029254386353131L)
            val start = bodyPoint(player, seed, 1, 0.30, 1.68)
            var end = bodyPoint(player, seed, 7, 0.24, 1.72)
            if (start.distanceToSqr(end) < 0.14) {
                end = end.add(0.0, if (end.y < player.y + 1.0) 0.48 else -0.48, 0.0)
            }
            addArc(start, end, seed, jitter = 0.105, lifetime = 3 + positiveIndex(seed, 2))
        }
    }

    private fun bodyPoint(
        player: AbstractClientPlayer,
        seed: Long,
        channel: Int,
        minHeight: Double,
        maxHeight: Double
    ): Vec3 {
        val yaw = Math.toRadians(player.yRot.toDouble())
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw))
        val right = Vec3(forward.z, 0.0, -forward.x)
        val radius = 0.24 + unit(seed, channel) * 0.13
        val angle = unit(seed, channel + 1) * Math.PI * 2.0
        return player.position()
            .add(right.scale(cos(angle) * radius))
            .add(forward.scale(sin(angle) * radius))
            .add(0.0, Mth.lerp(unit(seed, channel + 2), minHeight, maxHeight), 0.0)
    }

    /** Connects the lower body to the actual collision surface, then crawls across that surface. */
    private fun spawnGroundArc(player: AbstractClientPlayer) {
        val level = player.level()
        val tick = level.gameTime
        val seed = mix64(player.uuid.mostSignificantBits xor tick xor 0x36A5C4E17B29D80L)
        val yaw = Math.toRadians(player.yRot.toDouble())
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw))
        val right = Vec3(forward.z, 0.0, -forward.x)
        val side = if (unit(seed, 1) < 0.5) -1.0 else 1.0
        val footCenter = player.position()
            .add(right.scale(side * (0.16 + unit(seed, 2) * 0.10)))
            .add(forward.scale(signed(seed, 3) * 0.12))
        val footContact = findGround(player, footCenter.add(0.0, 0.65, 0.0)) ?: return

        val outward = right.scale(side * signedMagnitude(seed, 4, 0.18, 0.40))
            .add(forward.scale(signedMagnitude(seed, 5, 0.20, 0.52)))
        val crawlContact = findGround(player, footContact.add(outward).add(0.0, 0.55, 0.0)) ?: return
        if (abs(crawlContact.y - footContact.y) > 0.65) return

        val bodyStart = player.position()
            .add(right.scale(side * 0.23))
            .add(forward.scale(signed(seed, 6) * 0.10))
            .add(0.0, 0.55 + unit(seed, 7) * 0.30, 0.0)
        addArc(bodyStart, footContact, seed, jitter = 0.085, lifetime = 3)
        addArc(footContact, crawlContact, seed xor 0x4C91A52DL, jitter = 0.055, lifetime = 3, groundHugging = true)
    }

    private fun findGround(player: AbstractClientPlayer, start: Vec3): Vec3? {
        val hit = player.level().clip(
            ClipContext(start, start.add(0.0, -1.65, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        )
        if (hit.type != HitResult.Type.BLOCK) return null
        val normal = hit.direction.normal
        return hit.location.add(normal.x * 0.018, normal.y * 0.018, normal.z * 0.018)
    }

    private fun addArc(
        start: Vec3,
        end: Vec3,
        seed: Long,
        jitter: Double,
        lifetime: Int,
        groundHugging: Boolean = false
    ) {
        val points = buildArcPoints(start, end, seed, jitter, groundHugging)
        val branch = if (!groundHugging && unit(seed, 31) < 0.24) {
            val branchStart = points[2 + positiveIndex(seed ushr 4, 2)]
            val direction = end.subtract(start)
            var lateral = direction.cross(Vec3(0.0, 1.0, 0.0))
            if (lateral.lengthSqr() < 1.0e-5) lateral = Vec3(1.0, 0.0, 0.0)
            val branchEnd = branchStart
                .add(lateral.normalize().scale(signedMagnitude(seed, 32, 0.10, 0.24)))
                .add(0.0, signed(seed, 33) * 0.14, 0.0)
            buildArcPoints(branchStart, branchEnd, seed xor 0x53AF91C2L, jitter * 0.68, false, segments = 3)
        } else {
            null
        }
        arcs += ZapArc(points, branch, lifetime)
    }

    private fun buildArcPoints(
        start: Vec3,
        end: Vec3,
        seed: Long,
        jitter: Double,
        groundHugging: Boolean,
        segments: Int = ARC_SEGMENTS
    ): List<Vec3> {
        val points = ArrayList<Vec3>(segments + 1)
        points += start
        for (segment in 1 until segments) {
            val progress = segment.toDouble() / segments
            val center = start.lerp(end, progress)
            val envelope = 0.35 + (1.0 - abs(progress * 2.0 - 1.0)) * 0.65
            points += center.add(
                signed(seed, 10 + segment * 3) * jitter * envelope,
                if (groundHugging) unit(seed, 11 + segment * 3) * 0.018 else signed(seed, 11 + segment * 3) * jitter * envelope,
                signed(seed, 12 + segment * 3) * jitter * envelope
            )
        }
        points += end
        return points
    }

    private fun render(context: WorldRenderContext) {
        if (arcs.isEmpty()) return
        val poseStack = context.matrixStack() ?: return
        val buffers = context.consumers() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val consumer = buffers.getBuffer(RenderType.lightning())

        arcs.forEach { arc ->
            val remaining = 1.0f - (arc.age + partialTick).coerceAtMost(arc.lifetime.toFloat()) / arc.lifetime
            val pulse = if ((arc.age and 1) == 0) 1.0f else 0.78f
            val alpha = remaining * pulse
            renderElectricArc(consumer, poseStack, arc.points, camera, alpha)
            arc.branch?.let { renderElectricArc(consumer, poseStack, it, camera, alpha * 0.72f) }
        }
    }

    private fun renderElectricArc(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        points: List<Vec3>,
        camera: Vec3,
        alpha: Float
    ) {
        renderPolyline(consumer, poseStack, points, camera, 0.030f, 50, 124, 255, (82 * alpha).toInt())
        renderPolyline(consumer, poseStack, points, camera, 0.010f, 218, 247, 255, (245 * alpha).toInt())
    }

    private fun renderPolyline(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        points: List<Vec3>,
        camera: Vec3,
        width: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        if (alpha <= 0) return
        for (index in 0 until points.lastIndex) {
            emitCrossedSegment(
                consumer,
                poseStack,
                points[index].subtract(camera),
                points[index + 1].subtract(camera),
                width,
                red,
                green,
                blue,
                alpha
            )
        }
    }

    private fun emitCrossedSegment(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        from: Vec3,
        to: Vec3,
        width: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val direction = to.subtract(from)
        if (direction.lengthSqr() < 1.0e-7) return
        val normalized = direction.normalize()
        var sideA = normalized.cross(Vec3(0.0, 1.0, 0.0))
        if (sideA.lengthSqr() < 1.0e-5) sideA = normalized.cross(Vec3(1.0, 0.0, 0.0))
        sideA = sideA.normalize().scale(width.toDouble())
        val sideB = normalized.cross(sideA).normalize().scale(width.toDouble())
        emitQuad(consumer, poseStack, from, to, sideA, red, green, blue, alpha)
        emitQuad(consumer, poseStack, from, to, sideB, red, green, blue, alpha)
    }

    private fun emitQuad(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        from: Vec3,
        to: Vec3,
        side: Vec3,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        vertex(consumer, poseStack, from.add(side), red, green, blue, alpha)
        vertex(consumer, poseStack, from.subtract(side), red, green, blue, alpha)
        vertex(consumer, poseStack, to.subtract(side), red, green, blue, alpha)
        vertex(consumer, poseStack, to.add(side), red, green, blue, alpha)
    }

    private fun vertex(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        point: Vec3,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        consumer.addVertex(poseStack.last(), point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
            .setColor(red, green, blue, alpha.coerceIn(0, 255))
    }

    private data class ZapArc(
        val points: List<Vec3>,
        val branch: List<Vec3>?,
        val lifetime: Int,
        var age: Int = 0
    )

    private fun unit(seed: Long, channel: Int): Double {
        val mixed = mix64(seed + channel * -7046029254386353131L)
        return ((mixed ushr 11) and ((1L shl 53) - 1)).toDouble() / (1L shl 53).toDouble()
    }

    private fun signed(seed: Long, channel: Int): Double = unit(seed, channel) * 2.0 - 1.0

    private fun signedMagnitude(seed: Long, channel: Int, min: Double, max: Double): Double {
        val sign = if (unit(seed, channel) < 0.5) -1.0 else 1.0
        return sign * Mth.lerp(unit(seed, channel + 1), min, max)
    }

    private fun positiveIndex(value: Long, bound: Int): Int = ((value and Long.MAX_VALUE) % bound).toInt()

    private fun mix64(value: Long): Long {
        var mixed = value
        mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
        mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
        return mixed xor (mixed ushr 31)
    }
}
