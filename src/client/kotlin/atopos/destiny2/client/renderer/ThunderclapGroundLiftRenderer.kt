package atopos.destiny2.client.renderer

import atopos.destiny2.common.effect.ThunderclapGroundLiftShape
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Client-only block copies that briefly lift while both wings lean toward the centre.
 * World blocks are never removed or changed.
 */
object ThunderclapGroundLiftRenderer {
    private const val LOCAL_LIFETIME_TICKS = 12.0f
    private const val WAVE_TRAVEL_TICKS = 3.6f
    private const val MAX_EFFECTS = 6
    private const val SURFACE_SCAN_UP = 2
    private const val SURFACE_SCAN_DOWN = 4

    private data class LiftedBlock(
        val pos: BlockPos,
        val state: BlockState,
        val inwardX: Float,
        val inwardZ: Float,
        val delayTicks: Float,
        val maxTiltDegrees: Float,
        val maxLift: Float
    )

    private data class GroundLift(
        val startGameTime: Long,
        val blocks: List<LiftedBlock>
    )

    private val active = ArrayDeque<GroundLift>()

    fun register() {
        WorldRenderEvents.AFTER_ENTITIES.register(::render)
    }

    fun activate(origin: Vec3, yawDegrees: Float) {
        val client = Minecraft.getInstance()
        val world = client.level ?: return
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw)).normalize()
        val right = Vec3(forward.z, 0.0, -forward.x)
        val blocks = collectSurfaceBlocks(origin, forward, right)
        if (blocks.isEmpty()) return

        while (active.size >= MAX_EFFECTS) active.removeFirst()
        active.addLast(GroundLift(world.gameTime, blocks))
    }

    private fun collectSurfaceBlocks(origin: Vec3, forward: Vec3, right: Vec3): List<LiftedBlock> {
        val world = Minecraft.getInstance().level ?: return emptyList()
        val horizontalRadius = (ThunderclapGroundLiftShape.FORWARD_LENGTH +
            ThunderclapGroundLiftShape.HALF_WIDTH_AT_FRONT).toInt() + 1
        val centerX = floor(origin.x).toInt()
        val centerZ = floor(origin.z).toInt()
        val scanTopY = floor(origin.y).toInt() + SURFACE_SCAN_UP
        val scanBottomY = floor(origin.y).toInt() - SURFACE_SCAN_DOWN
        val result = ArrayList<LiftedBlock>()

        for (x in centerX - horizontalRadius..centerX + horizontalRadius) {
            for (z in centerZ - horizontalRadius..centerZ + horizontalRadius) {
                val relativeX = x + 0.5 - origin.x
                val relativeZ = z + 0.5 - origin.z
                val forwardDistance = relativeX * forward.x + relativeZ * forward.z
                val lateralDistance = relativeX * right.x + relativeZ * right.z
                if (!ThunderclapGroundLiftShape.contains(forwardDistance, lateralDistance)) continue
                if (!ThunderclapGroundLiftShape.shouldTilt(lateralDistance)) continue

                val pos = findSurfaceBlock(x, z, scanTopY, scanBottomY) ?: continue
                val state = world.getBlockState(pos)
                val hash = stableNoise(pos)
                val inwardSign = if (lateralDistance > 0.0) -1.0 else 1.0
                val sideStrength = ThunderclapGroundLiftShape.sideIntensity(lateralDistance).toFloat()
                result += LiftedBlock(
                    pos = pos,
                    state = state,
                    inwardX = (right.x * inwardSign).toFloat(),
                    inwardZ = (right.z * inwardSign).toFloat(),
                    delayTicks = (forwardDistance / ThunderclapGroundLiftShape.FORWARD_LENGTH).toFloat() *
                        WAVE_TRAVEL_TICKS + hash * 0.9f,
                    maxTiltDegrees = 8.0f + sideStrength * 42.0f + hash * 4.0f,
                    maxLift = 0.16f + sideStrength * 0.27f + hash * 0.07f
                )
            }
        }
        return result
    }

    private fun findSurfaceBlock(x: Int, z: Int, topY: Int, bottomY: Int): BlockPos? {
        val world = Minecraft.getInstance().level ?: return null
        for (y in topY downTo bottomY) {
            val pos = BlockPos(x, y, z)
            val state = world.getBlockState(pos)
            if (state.isAir || state.renderShape != RenderShape.MODEL || state.hasBlockEntity()) continue
            if (!state.fluidState.isEmpty) continue
            val above = pos.above()
            if (!world.getBlockState(above).getCollisionShape(world, above).isEmpty) continue
            return pos.immutable()
        }
        return null
    }

    private fun render(context: WorldRenderContext) {
        if (active.isEmpty()) return
        val poseStack = context.matrixStack() ?: return
        val consumers = context.consumers() ?: return
        val world = context.world()
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val now = world.gameTime + partialTick
        val dispatcher = Minecraft.getInstance().blockRenderer

        val iterator = active.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            val age = now - effect.startGameTime
            if (age > LOCAL_LIFETIME_TICKS + WAVE_TRAVEL_TICKS + 1.0f) {
                iterator.remove()
                continue
            }
            effect.blocks.forEach { block ->
                val localAge = age - block.delayTicks
                if (localAge < 0.0f || localAge > LOCAL_LIFETIME_TICKS) return@forEach
                val progress = (localAge / LOCAL_LIFETIME_TICKS).coerceIn(0.0f, 1.0f)
                val pulse = sin(progress * PI).toFloat().coerceAtLeast(0.0f)
                val lift = 0.035f + pulse * block.maxLift
                val tiltRadians = Math.toRadians((block.maxTiltDegrees * pulse).toDouble()).toFloat()
                // Rotating around the perpendicular axis makes the top face lean
                // in the requested horizontal direction: toward the centre line.
                val axisX = block.inwardZ
                val axisZ = -block.inwardX

                renderBlockCopy(
                    poseStack = poseStack,
                    camera = camera,
                    pos = block.pos,
                    state = block.state,
                    lift = lift,
                    rotation = Quaternionf().rotationAxis(tiltRadians, axisX, 0.0f, axisZ),
                    light = LevelRenderer.getLightColor(world, block.pos.above()),
                    dispatcher = dispatcher,
                    consumers = consumers
                )
            }
        }
    }

    private fun renderBlockCopy(
        poseStack: PoseStack,
        camera: Vec3,
        pos: BlockPos,
        state: BlockState,
        lift: Float,
        rotation: Quaternionf,
        light: Int,
        dispatcher: net.minecraft.client.renderer.block.BlockRenderDispatcher,
        consumers: net.minecraft.client.renderer.MultiBufferSource
    ) {
        poseStack.pushPose()
        poseStack.translate(
            pos.x + 0.5 - camera.x,
            pos.y + 0.5 + lift - camera.y,
            pos.z + 0.5 - camera.z
        )
        poseStack.mulPose(rotation)
        poseStack.translate(-0.5, -0.5, -0.5)
        dispatcher.renderSingleBlock(state, poseStack, consumers, light, OverlayTexture.NO_OVERLAY)
        poseStack.popPose()
    }

    private fun stableNoise(pos: BlockPos): Float {
        var value = pos.x * 73428767 xor pos.y * 912931 xor pos.z * 438289
        value = value xor (value ushr 13)
        return (value and 1023) / 1023.0f
    }
}
