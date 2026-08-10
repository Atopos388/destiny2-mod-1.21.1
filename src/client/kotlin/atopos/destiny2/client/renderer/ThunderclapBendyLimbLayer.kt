// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.ThunderclapPlayerProxyEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.resources.PlayerSkin
import org.joml.Vector3f
import org.joml.Vector4f
import software.bernie.geckolib.cache.`object`.BakedGeoModel
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.cache.`object`.GeoCube
import software.bernie.geckolib.renderer.GeoRenderer
import software.bernie.geckolib.renderer.layer.GeoRenderLayer

/** Continuous limb skin inspired by PlayerAnimator's BendyLib-backed BendableCuboid. */
class ThunderclapBendyLimbLayer(
    renderer: GeoRenderer<ThunderclapPlayerProxyEntity>
) : GeoRenderLayer<ThunderclapPlayerProxyEntity>(renderer) {
    private val upperSamples = mutableMapOf<String, Array<UpperSample?>>()

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
        upperSamples.clear()
        LIMBS.forEach { limb ->
            getGeoModel().getBone(limb.upperBone).ifPresent { bone ->
                bone.isHidden = true
                bone.setChildrenHidden(false)
            }
            getGeoModel().getBone(limb.lowerBone).ifPresent { bone ->
                bone.isHidden = true
                bone.setChildrenHidden(false)
            }
        }
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
        LIMBS.firstOrNull { it.upperBone == bone.name }?.let { limb ->
            val slimArm = limb.armSide != null && isSlimSkin(animatable)
            upperSamples[limb.lowerBone] = Array(2) { cubeIndex ->
                bone.cubes.getOrNull(cubeIndex)?.let { cube ->
                    val sourceBounds = bounds(cube)
                    val renderBounds = armBounds(sourceBounds, limb.armSide, slimArm)
                        .expand(OUTER_SKIN_OFFSET)
                    val sourceUv = cubeUv(cube, sourceBounds)
                    UpperSample(
                        ring(poseStack, renderBounds, renderBounds.maxY),
                        ring(poseStack, renderBounds, renderBounds.minY),
                        if (slimArm) slimArmUv(sourceUv, limb.textureU[cubeIndex]) else sourceUv
                    )
                }
            }
            return
        }

        val limb = LIMBS.firstOrNull { it.lowerBone == bone.name } ?: return
        val slimArm = limb.armSide != null && isSlimSkin(animatable)
        val upper = upperSamples.remove(limb.lowerBone) ?: return
        val limbBuffer = bufferSource.getBuffer(
            RenderType.entityCutoutNoCull(getTextureResource(animatable))
        )
        repeat(2) { cubeIndex ->
            val upperSample = upper[cubeIndex] ?: return@repeat
            val lowerCube = bone.cubes.getOrNull(cubeIndex) ?: return@repeat
            val lowerSourceBounds = bounds(lowerCube)
            val lowerRenderBounds = armBounds(lowerSourceBounds, limb.armSide, slimArm)
                .expand(OUTER_SKIN_OFFSET)
            val lowerJoint = ring(poseStack, lowerRenderBounds, lowerRenderBounds.maxY)
            val bottom = ring(poseStack, lowerRenderBounds, lowerRenderBounds.minY)
            val joint = Array(4) { index ->
                Vector3f(upperSample.joint[index]).add(lowerJoint[index]).mul(0.5f)
            }
            renderContinuousShell(
                limbBuffer,
                upperSample.top,
                joint,
                bottom,
                upperSample.uv,
                cubeUv(lowerCube, lowerSourceBounds).let { sourceUv ->
                    if (slimArm) slimArmUv(sourceUv, limb.textureU[cubeIndex]) else sourceUv
                },
                packedLight,
                packedOverlay
            )
        }
    }

    private fun renderContinuousShell(
        buffer: VertexConsumer,
        top: Array<Vector3f>,
        joint: Array<Vector3f>,
        bottom: Array<Vector3f>,
        upperUv: CubeUv,
        lowerUv: CubeUv,
        packedLight: Int,
        packedOverlay: Int
    ) {
        for (face in FACE_CORNERS.indices) {
            val (first, second) = FACE_CORNERS[face]
            upperUv.sides[face]?.let { uv ->
                emitQuad(
                    buffer,
                    arrayOf(top[first], top[second], joint[second], joint[first]),
                    uv,
                    packedLight,
                    packedOverlay
                )
            }
            lowerUv.sides[face]?.let { uv ->
                emitQuad(
                    buffer,
                    arrayOf(joint[first], joint[second], bottom[second], bottom[first]),
                    uv,
                    packedLight,
                    packedOverlay
                )
            }
        }
        upperUv.topCap?.let { emitQuad(buffer, arrayOf(top[3], top[2], top[1], top[0]), it, packedLight, packedOverlay) }
        lowerUv.bottomCap?.let { emitQuad(buffer, arrayOf(bottom[0], bottom[1], bottom[2], bottom[3]), it, packedLight, packedOverlay) }
    }

    /** Reuse GeckoLib's baked per-face UV orientation instead of guessing Minecraft skin mirroring. */
    private fun cubeUv(cube: GeoCube, bounds: Bounds): CubeUv {
        val top = localRing(bounds, bounds.maxY)
        val bottom = localRing(bounds, bounds.minY)
        return CubeUv(
            Array(4) { face ->
                val (first, second) = FACE_CORNERS[face]
                findUv(cube, arrayOf(top[first], top[second], bottom[second], bottom[first]))
            },
            findUv(cube, arrayOf(top[3], top[2], top[1], top[0])),
            findUv(cube, arrayOf(bottom[0], bottom[1], bottom[2], bottom[3]))
        )
    }

    private fun isSlimSkin(animatable: ThunderclapPlayerProxyEntity): Boolean =
        animatable.targetPlayerId
            ?.let { Minecraft.getInstance().level?.getPlayerByUUID(it) as? AbstractClientPlayer }
            ?.skin
            ?.model() == PlayerSkin.Model.SLIM

    /** Alex keeps the shoulder-side edge and removes one pixel from the outside edge. */
    private fun armBounds(bounds: Bounds, side: ArmSide?, slim: Boolean): Bounds {
        if (!slim || side == null) return bounds
        return when (side) {
            ArmSide.RIGHT -> bounds.copy(minX = bounds.minX + SLIM_ARM_REDUCTION)
            ArmSide.LEFT -> bounds.copy(maxX = bounds.maxX - SLIM_ARM_REDUCTION)
        }
    }

    /** Convert the classic 4px arm box atlas into Minecraft's native 3px Alex layout. */
    private fun slimArmUv(source: CubeUv, boxU: Float): CubeUv {
        val targetSideU = arrayOf(
            boxU + 4f to boxU + 7f,   // north/front: 3 px
            boxU to boxU + 4f,        // east: 4 px depth
            boxU + 11f to boxU + 14f, // south/back: 3 px
            boxU + 7f to boxU + 11f   // west: 4 px depth
        )
        return CubeUv(
            Array(4) { face -> source.sides[face]?.let { remapU(it, targetSideU[face]) } },
            source.topCap?.let { remapU(it, boxU + 4f to boxU + 7f) },
            source.bottomCap?.let { remapU(it, boxU + 7f to boxU + 10f) }
        )
    }

    private fun remapU(source: QuadUv, targetPixels: Pair<Float, Float>): QuadUv {
        val oldMin = source.vertices.minOf { it.u }
        val oldMax = source.vertices.maxOf { it.u }
        val oldWidth = oldMax - oldMin
        val targetMin = targetPixels.first / TEXTURE_SIZE
        val targetMax = targetPixels.second / TEXTURE_SIZE
        return QuadUv(Array(source.vertices.size) { index ->
            val original = source.vertices[index]
            val fraction = if (oldWidth <= 1.0e-8f) 0f else (original.u - oldMin) / oldWidth
            VertexUv(targetMin + (targetMax - targetMin) * fraction, original.v)
        })
    }

    private fun localRing(bounds: Bounds, y: Float): Array<Vector3f> = arrayOf(
        Vector3f(bounds.minX, y, bounds.minZ),
        Vector3f(bounds.maxX, y, bounds.minZ),
        Vector3f(bounds.maxX, y, bounds.maxZ),
        Vector3f(bounds.minX, y, bounds.maxZ)
    )

    private fun findUv(cube: GeoCube, targets: Array<Vector3f>): QuadUv? {
        for (quad in cube.quads().filterNotNull()) {
            val source = quad.vertices()
            val matched = targets.map { target ->
                source.firstOrNull { vertex -> samePosition(vertex.position(), target) }
                    ?: return@map null
            }
            if (matched.all { it != null }) {
                return QuadUv(matched.map { vertex -> VertexUv(vertex!!.texU(), vertex.texV()) }.toTypedArray())
            }
        }
        return null
    }

    private fun samePosition(first: Vector3f, second: Vector3f): Boolean =
        kotlin.math.abs(first.x - second.x) <= POSITION_EPSILON &&
            kotlin.math.abs(first.y - second.y) <= POSITION_EPSILON &&
            kotlin.math.abs(first.z - second.z) <= POSITION_EPSILON

    private fun bounds(cube: GeoCube): Bounds {
        val positions = cube.quads()
            .filterNotNull()
            .flatMap { it.vertices().map { vertex -> vertex.position() } }
        return Bounds(
            positions.minOf { it.x }, positions.maxOf { it.x },
            positions.minOf { it.y }, positions.maxOf { it.y },
            positions.minOf { it.z }, positions.maxOf { it.z }
        )
    }

    /** The pose here is GeckoLib's live recursive bone pose, so no second model-space conversion is applied. */
    private fun ring(poseStack: PoseStack, bounds: Bounds, y: Float): Array<Vector3f> = arrayOf(
        transform(poseStack, bounds.minX, y, bounds.minZ),
        transform(poseStack, bounds.maxX, y, bounds.minZ),
        transform(poseStack, bounds.maxX, y, bounds.maxZ),
        transform(poseStack, bounds.minX, y, bounds.maxZ)
    )

    private fun transform(poseStack: PoseStack, x: Float, y: Float, z: Float): Vector3f {
        val vector = poseStack.last().pose().transform(Vector4f(x, y, z, 1f))
        return Vector3f(vector.x, vector.y, vector.z)
    }

    private fun emitQuad(
        buffer: VertexConsumer,
        vertices: Array<Vector3f>,
        uv: QuadUv,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val normal = Vector3f(vertices[1]).sub(vertices[0])
            .cross(Vector3f(vertices[3]).sub(vertices[0]))
        if (normal.lengthSquared() <= 1.0e-8f) return
        normal.normalize()
        vertices.indices.forEach { index ->
            vertex(buffer, vertices[index], uv.vertices[index], normal, packedLight, packedOverlay)
        }
    }

    private fun vertex(
        buffer: VertexConsumer,
        position: Vector3f,
        uv: VertexUv,
        normal: Vector3f,
        packedLight: Int,
        packedOverlay: Int
    ) {
        buffer.addVertex(position.x, position.y, position.z)
            .setColor(255, 255, 255, 255)
            .setUv(uv.u, uv.v)
            .setOverlay(packedOverlay)
            .setLight(packedLight)
            .setNormal(normal.x, normal.y, normal.z)
    }

    private data class UpperSample(val top: Array<Vector3f>, val joint: Array<Vector3f>, val uv: CubeUv)
    private data class VertexUv(val u: Float, val v: Float)
    private data class QuadUv(val vertices: Array<VertexUv>)
    private data class CubeUv(val sides: Array<QuadUv?>, val topCap: QuadUv?, val bottomCap: QuadUv?)

    private data class Bounds(
        val minX: Float,
        val maxX: Float,
        val minY: Float,
        val maxY: Float,
        val minZ: Float,
        val maxZ: Float
    ) {
        fun expand(amount: Float) = Bounds(
            minX - amount, maxX + amount,
            minY, maxY,
            minZ - amount, maxZ + amount
        )
    }

    private data class LimbSpec(
        val upperBone: String,
        val lowerBone: String,
        val armSide: ArmSide? = null,
        val textureU: FloatArray = floatArrayOf(0f, 0f)
    )

    private enum class ArmSide { RIGHT, LEFT }

    companion object {
        private const val TEXTURE_SIZE = 64f
        private const val OUTER_SKIN_OFFSET = 0.002f
        // GeoCube vertex positions are already converted from model pixels to 1/16-block render units.
        private const val SLIM_ARM_REDUCTION = 1f / 16f
        private const val POSITION_EPSILON = 1.0e-4f
        private val FACE_CORNERS = arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 0)

        private val LIMBS = arrayOf(
            LimbSpec("right_arm", "right_forearm", ArmSide.RIGHT, floatArrayOf(40f, 40f)),
            LimbSpec("left_arm", "left_forearm", ArmSide.LEFT, floatArrayOf(32f, 48f)),
            LimbSpec("right_leg", "right_foreleg"),
            LimbSpec("left_leg", "left_foreleg")
        )
    }
}
