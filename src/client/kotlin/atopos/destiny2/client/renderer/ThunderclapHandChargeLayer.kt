// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.ThunderclapPlayerProxyEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f
import org.joml.Vector4f
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.cache.`object`.GeoCube
import software.bernie.geckolib.renderer.GeoRenderer
import software.bernie.geckolib.renderer.layer.GeoRenderLayer
import kotlin.math.max
import kotlin.math.sin

/**
 * Thunderclap's three-beat hand language: glass-blue hands, gathering arc current,
 * then a short blue-white release bloom. Geometry follows the animated forearm bones.
 */
class ThunderclapHandChargeLayer(
    renderer: GeoRenderer<ThunderclapPlayerProxyEntity>
) : GeoRenderLayer<ThunderclapPlayerProxyEntity>(renderer) {
    private val limbSamples = HashMap<String, LimbSample>(2)

    override fun preRender(
        poseStack: PoseStack,
        animatable: ThunderclapPlayerProxyEntity,
        bakedModel: BakedGeoModel,
        renderType: RenderType?,
        bufferSource: MultiBufferSource,
        buffer: VertexConsumer?,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int
    ) {
        limbSamples.clear()
    }

    override fun renderForBone(
        poseStack: PoseStack,
        animatable: ThunderclapPlayerProxyEntity,
        bone: GeoBone,
        renderType: RenderType,
        bufferSource: MultiBufferSource,
        buffer: VertexConsumer,
        partialTick: Float,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val cube = bone.cubes.firstOrNull() ?: return
        if (bone.name !in ARM_BONES) return
        val bounds = bounds(cube)
        val phase = visualPhase(animatable, partialTick)

        val forearmLength = bounds.maxY - bounds.minY
        val tip = center(poseStack, bounds, bounds.minY)
        val joint = center(poseStack, bounds, bounds.maxY)
        // This proxy has no separate hand bone: the distal part of the six-pixel
        // forearm cube is the hand. Keep the anchor inside that physical volume instead
        // of placing it almost on the end cap where axial offsets spill into empty space.
        val handCenter = center(poseStack, bounds, bounds.minY + forearmLength * 0.18f)
        val limbSample = LimbSample(
            hand = handCenter
        )
        limbSamples[bone.name] = limbSample

        if (phase.glass > 0.001f) {
            val flicker = 0.90f + sin((animatable.phaseAge(partialTick) * 2.35f).toDouble()).toFloat() * 0.10f
            val glass = phase.glass * flicker
            renderArmTransition(bufferSource.getBuffer(RenderType.lightning()), poseStack, bounds, glass)
        }

        val electricityConsumer = bufferSource.getBuffer(RenderType.lines())

        if (phase.current > 0.001f) {
            // Hold one readable bolt silhouette for three ticks. Per-frame random geometry
            // looks like white noise instead of electricity wrapping a moving limb.
            val electricFrame = animatable.tickCount / ELECTRIC_PATH_LIFETIME_TICKS
            val seed = animatable.id * 97L + electricFrame * 31L + bone.name.hashCode()
            val arcCount = ARM_FILAMENT_COUNT
            val armAxis = Vector3f(tip).sub(joint).normalize()
            var sideA = armAxis.cross(Vector3f(0f, 1f, 0f), Vector3f())
            if (sideA.lengthSquared() < 1.0e-6f) {
                sideA = armAxis.cross(Vector3f(1f, 0f, 0f), Vector3f())
            }
            sideA.normalize()
            val sideB = armAxis.cross(sideA, Vector3f()).normalize()
            val halfWidth = max(bounds.maxX - bounds.minX, bounds.maxZ - bounds.minZ) * 0.5f
            val surfaceDistance = halfWidth + ARM_SURFACE_MARGIN
            repeat(arcCount) { arcIndex ->
                // Seven staggered tracks on each of four faces spread the fixed 28-bolt
                // budget across the whole limb instead of piling it onto the hand cap.
                val face = arcIndex % 4
                val longitudinalSlot = arcIndex / 4
                val startProgress = (
                    (longitudinalSlot + hash01(seed, arcIndex * 11) * 0.58f) /
                        FILAMENTS_PER_FACE
                    ) * 0.78f
                val endProgress = (startProgress + 0.18f + hash01(seed, arcIndex * 11 + 1) * 0.16f)
                    .coerceAtMost(0.99f)
                val surfaceNormal = when (face) {
                    0 -> Vector3f(sideA)
                    1 -> Vector3f(sideA).negate()
                    2 -> Vector3f(sideB)
                    else -> Vector3f(sideB).negate()
                }
                val surfaceTangent = if (face < 2) sideB else sideA
                val startLateral = signed(seed, arcIndex * 11 + 2) * halfWidth * 0.82f
                val endLateral = (
                    startLateral + signed(seed, arcIndex * 11 + 3) * halfWidth * 0.72f
                    ).coerceIn(-halfWidth * 0.92f, halfWidth * 0.92f)
                val start = Vector3f(joint).lerp(tip, startProgress)
                    .add(Vector3f(surfaceNormal).mul(surfaceDistance))
                    .add(Vector3f(surfaceTangent).mul(startLateral))
                val end = Vector3f(joint).lerp(tip, endProgress)
                    .add(Vector3f(surfaceNormal).mul(surfaceDistance))
                    .add(Vector3f(surfaceTangent).mul(endLateral))
                renderArc(
                    electricityConsumer,
                    start,
                    end,
                    seed + arcIndex * 0x51EBL,
                    0.012f + phase.current * 0.014f,
                    (phase.current * 1.18f).coerceAtMost(1.0f),
                    armAxis,
                    surfaceNormal
                )
            }
        }

        val right = limbSamples["right_forearm"]
        val left = limbSamples["left_forearm"]
        if (right != null && left != null && bone.name == "left_forearm") {
            renderGatheringCore(
                bufferSource,
                animatable,
                right,
                left,
                phase,
                partialTick
            )
        }

        // GeoRenderLayer requires the original buffer to be rebound after using lightning
        // and emissive buffers; otherwise later skin bones can inherit a translucent pass.
        bufferSource.getBuffer(renderType)
    }

    private fun renderGatheringCore(
        bufferSource: MultiBufferSource,
        animatable: ThunderclapPlayerProxyEntity,
        right: LimbSample,
        left: LimbSample,
        phase: VisualPhase,
        partialTick: Float
    ) {
        // Both hands are deliberately independent: this pass only draws two separately
        // anchored blooms and never submits geometry between either hand or the torso.
        val pulse = 0.88f + sin((animatable.phaseAge(partialTick) * 1.65f).toDouble()).toFloat() * 0.12f
        val chargeRadius = max(0.0f, phase.current - 0.48f) * 0.34f
        val radius = (chargeRadius + phase.burst * 0.62f) * pulse
        if (radius > 0.012f) {
            // Textured crossed planes keep the bloom round/soft; no solid hand cube is rendered.
            val glowConsumer = bufferSource.getBuffer(RenderType.entityTranslucentEmissive(HAND_GLOW_TEXTURE))
            renderHandBloom(glowConsumer, right.hand, radius, phase.energy)
            renderHandBloom(glowConsumer, left.hand, radius, phase.energy)
        }
    }

    private fun renderHandBloom(
        consumer: VertexConsumer,
        hand: Vector3f,
        radius: Float,
        energy: Float
    ) {
        renderGlowPlane(consumer, hand, radius * 1.32f, Plane.XY, (165 * energy).toInt())
        renderGlowPlane(consumer, hand, radius * 1.32f, Plane.XZ, (150 * energy).toInt())
        renderGlowPlane(consumer, hand, radius * 1.32f, Plane.YZ, (150 * energy).toInt())
        val coreRadius = (radius * 0.30f).coerceIn(0.045f, 0.14f)
        renderGlowPlane(consumer, hand, coreRadius, Plane.XY, (255 * energy).toInt())
        renderGlowPlane(consumer, hand, coreRadius, Plane.XZ, (245 * energy).toInt())
        renderGlowPlane(consumer, hand, coreRadius, Plane.YZ, (245 * energy).toInt())
    }

    /**
     * Builds a shoulder-to-hand spatial fade instead of placing one uniformly bright box
     * over the limb. Bands reveal from the distal end as the temporal charge blend rises.
     */
    private fun renderArmTransition(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        bounds: Bounds,
        glass: Float
    ) {
        repeat(TRANSITION_BANDS) { index ->
            val distal = index.toFloat() / TRANSITION_BANDS
            val proximal = (index + 1).toFloat() / TRANSITION_BANDS
            val distanceFromHand = distal
            val reveal = smoothstep(distanceFromHand * 0.62f, distanceFromHand * 0.62f + 0.30f, glass)
            if (reveal <= 0.001f) return@repeat

            val brightness = 1.0f - distal * 0.48f
            val y0 = bounds.minY + (bounds.maxY - bounds.minY) * distal
            val y1 = bounds.minY + (bounds.maxY - bounds.minY) * proximal
            val expansion = 0.006f + brightness * 0.013f
            val band = Bounds(
                bounds.minX - expansion,
                bounds.maxX + expansion,
                y0,
                y1,
                bounds.minZ - expansion,
                bounds.maxZ + expansion
            )
            renderBox(
                consumer,
                poseStack,
                band,
                (105 + brightness * 95).toInt(),
                (182 + brightness * 53).toInt(),
                255,
                (reveal * glass * (30 + brightness * 84)).toInt(),
                includeCaps = false
            )
        }
    }

    private fun visualPhase(entity: ThunderclapPlayerProxyEntity, partialTick: Float): VisualPhase {
        val progress = entity.phaseProgress(partialTick)
        return when (entity.phase) {
            ThunderclapPlayerProxyEntity.Phase.CHARGE -> VisualPhase(
                glass = smoothstep(0.02f, 0.28f, progress),
                current = smoothstep(0.22f, 0.92f, progress),
                burst = 0.0f
            )
            ThunderclapPlayerProxyEntity.Phase.RELEASE -> {
                val age = entity.phaseAge(partialTick)
                val flash = (1.0f - age / 8.0f).coerceIn(0.0f, 1.0f)
                VisualPhase(
                    glass = (1.0f - age / 15.0f).coerceIn(0.0f, 1.0f),
                    current = (1.0f - age / 11.0f).coerceIn(0.0f, 1.0f),
                    burst = smoothstep(0.0f, 1.6f, age) * flash
                )
            }
        }
    }

    private fun renderBox(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        bounds: Bounds,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
        includeCaps: Boolean = true
    ) {
        if (alpha <= 0) return
        val p = arrayOf(
            transform(poseStack, bounds.minX, bounds.minY, bounds.minZ),
            transform(poseStack, bounds.maxX, bounds.minY, bounds.minZ),
            transform(poseStack, bounds.maxX, bounds.minY, bounds.maxZ),
            transform(poseStack, bounds.minX, bounds.minY, bounds.maxZ),
            transform(poseStack, bounds.minX, bounds.maxY, bounds.minZ),
            transform(poseStack, bounds.maxX, bounds.maxY, bounds.minZ),
            transform(poseStack, bounds.maxX, bounds.maxY, bounds.maxZ),
            transform(poseStack, bounds.minX, bounds.maxY, bounds.maxZ)
        )
        val faces = if (includeCaps) BOX_FACES.asIterable() else BOX_FACES.asIterable().drop(2)
        faces.forEach { face ->
            face.forEach { index -> vertex(consumer, p[index], red, green, blue, alpha) }
        }
    }

    private fun renderGlowPlane(
        consumer: VertexConsumer,
        center: Vector3f,
        radius: Float,
        plane: Plane,
        alpha: Int
    ) {
        if (alpha <= 0) return
        val first = plane.first(radius)
        val second = plane.second(radius)
        val points = arrayOf(
            Vector3f(center).sub(first).sub(second),
            Vector3f(center).add(first).sub(second),
            Vector3f(center).add(first).add(second),
            Vector3f(center).sub(first).add(second)
        )
        val uvs = arrayOf(0.0f to 0.0f, 1.0f to 0.0f, 1.0f to 1.0f, 0.0f to 1.0f)
        points.indices.forEach { index ->
            consumer.addVertex(points[index].x, points[index].y, points[index].z)
                .setColor(168, 218, 255, alpha.coerceIn(0, 255))
                .setUv(uvs[index].first, uvs[index].second)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0f, 1.0f, 0.0f)
        }
    }

    private fun renderArc(
        consumer: VertexConsumer,
        start: Vector3f,
        end: Vector3f,
        seed: Long,
        jitter: Float,
        intensity: Float,
        parallelAxis: Vector3f? = null,
        surfaceNormal: Vector3f? = null
    ) {
        val points = ArrayList<Vector3f>(ARC_SEGMENTS + 1)
        val boltAxis = parallelAxis?.let { Vector3f(it).normalize() }
            ?: Vector3f(end).sub(start).normalize()
        val rememberedOffset = Vector3f()
        points += Vector3f(start)
        for (index in 1 until ARC_SEGMENTS) {
            val progress = index.toFloat() / ARC_SEGMENTS
            // A sine envelope pins both ends to their anchors. Retaining most of the
            // previous displacement produces connected zigzags instead of random spikes.
            val envelope = sin((Math.PI * progress).toFloat())
            val nextOffset = Vector3f(
                signed(seed, index * 3),
                signed(seed, index * 3 + 1),
                signed(seed, index * 3 + 2)
            )
            nextOffset.sub(Vector3f(boltAxis).mul(nextOffset.dot(boltAxis)))
            if (surfaceNormal != null) {
                // Keep the zigzag tangent to its assigned arm face. Removing the inward
                // normal component prevents most of the line from hiding inside the skin.
                nextOffset.sub(Vector3f(surfaceNormal).mul(nextOffset.dot(surfaceNormal)))
            }
            if (nextOffset.lengthSquared() > 1.0e-8f) {
                nextOffset.normalize().mul(jitter * 0.54f)
            }
            rememberedOffset.mul(0.76f).add(nextOffset)
            val maxOffset = jitter * envelope
            if (rememberedOffset.lengthSquared() > maxOffset * maxOffset) {
                rememberedOffset.normalize().mul(maxOffset)
            }
            points += Vector3f(start).lerp(end, progress).add(rememberedOffset)
        }
        points += Vector3f(end)
        renderBoltPolyline(consumer, points, intensity)
    }

    private fun renderBoltPolyline(
        consumer: VertexConsumer,
        points: List<Vector3f>,
        intensity: Float
    ) {
        for (index in 0 until points.lastIndex) {
            val a = points[index]
            val b = points[index + 1]
            renderLineSegment(consumer, a, b, (255 * intensity).toInt())
        }
    }

    /** Real line primitives stay one-pixel thin from every camera angle. */
    private fun renderLineSegment(
        consumer: VertexConsumer,
        start: Vector3f,
        end: Vector3f,
        alpha: Int
    ) {
        val normal = Vector3f(end).sub(start)
        if (normal.lengthSquared() < 1.0e-8f || alpha <= 0) return
        normal.normalize()
        lineVertex(consumer, start, alpha, normal)
        lineVertex(consumer, end, alpha, normal)
    }

    private fun lineVertex(consumer: VertexConsumer, point: Vector3f, alpha: Int, normal: Vector3f) {
        consumer.addVertex(point.x, point.y, point.z)
            .setColor(255, 255, 255, alpha.coerceIn(0, 255))
            .setNormal(normal.x, normal.y, normal.z)
    }

    private fun bounds(cube: GeoCube): Bounds {
        val points = cube.quads().filterNotNull().flatMap { it.vertices().map { vertex -> vertex.position() } }
        return Bounds(
            points.minOf { it.x }, points.maxOf { it.x },
            points.minOf { it.y }, points.maxOf { it.y },
            points.minOf { it.z }, points.maxOf { it.z }
        )
    }

    private fun center(poseStack: PoseStack, bounds: Bounds, y: Float): Vector3f = transform(
        poseStack,
        (bounds.minX + bounds.maxX) * 0.5f,
        y,
        (bounds.minZ + bounds.maxZ) * 0.5f
    )

    private fun transform(poseStack: PoseStack, x: Float, y: Float, z: Float): Vector3f {
        val point = poseStack.last().pose().transform(Vector4f(x, y, z, 1f))
        return Vector3f(point.x, point.y, point.z)
    }

    private fun vertex(
        consumer: VertexConsumer,
        point: Vector3f,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        consumer.addVertex(point.x, point.y, point.z)
            .setColor(red, green, blue, alpha.coerceIn(0, 255))
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

    private fun hash01(seed: Long, channel: Int): Float {
        val mixed = mix64(seed + channel * -7046029254386353131L)
        return ((mixed ushr 40) and 0xFFFFFFL).toFloat() / 0xFFFFFF.toFloat()
    }

    private fun signed(seed: Long, channel: Int): Float = hash01(seed, channel) * 2.0f - 1.0f

    private fun mix64(value: Long): Long {
        var mixed = value
        mixed = (mixed xor (mixed ushr 30)) * -4658895280553007687L
        mixed = (mixed xor (mixed ushr 27)) * -7723592293110705685L
        return mixed xor (mixed ushr 31)
    }

    private data class LimbSample(val hand: Vector3f)
    private data class VisualPhase(val glass: Float, val current: Float, val burst: Float) {
        val energy: Float get() = max(current, burst)
    }

    private data class Bounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float,
        val minZ: Float,
        val maxZ: Float
    ) {
        fun expand(amount: Float) = Bounds(
            minX - amount,
            maxX + amount,
            minY - amount,
            maxY + amount,
            minZ - amount,
            maxZ + amount
        )
    }

    private enum class Plane(
        val first: (Float) -> Vector3f,
        val second: (Float) -> Vector3f
    ) {
        XY({ Vector3f(it, 0f, 0f) }, { Vector3f(0f, it, 0f) }),
        XZ({ Vector3f(it, 0f, 0f) }, { Vector3f(0f, 0f, it) }),
        YZ({ Vector3f(0f, it, 0f) }, { Vector3f(0f, 0f, it) })
    }

    companion object {
        private const val ARC_SEGMENTS = 7
        private const val ARM_FILAMENT_COUNT = 28
        private const val FILAMENTS_PER_FACE = ARM_FILAMENT_COUNT / 4.0f
        private const val ARM_SURFACE_MARGIN = 0.022f
        private const val ELECTRIC_PATH_LIFETIME_TICKS = 3
        private const val TRANSITION_BANDS = 8
        private val HAND_GLOW_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "textures/particle/revolver/muzzle_flash_00.png"
        )
        private val FOREARM_BONES = setOf("right_forearm", "left_forearm")
        private val ARM_BONES = FOREARM_BONES
        private val BOX_FACES = arrayOf(
            intArrayOf(0, 1, 2, 3),
            intArrayOf(4, 7, 6, 5),
            intArrayOf(0, 4, 5, 1),
            intArrayOf(1, 5, 6, 2),
            intArrayOf(2, 6, 7, 3),
            intArrayOf(3, 7, 4, 0)
        )
    }
}
