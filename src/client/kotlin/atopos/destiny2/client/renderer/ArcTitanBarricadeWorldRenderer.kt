// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.ArcTitanBarricadeEntity
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Curved, translucent Arc material used by the Titan's Towering Barricade. */
object ArcTitanBarricadeWorldRenderer {
    private const val QUERY_RANGE = 96.0
    private const val HALF_WIDTH = 2.18
    private const val BASE_HEIGHT = 2.30
    private const val HORIZONTAL_SEGMENTS = 20
    private const val VERTICAL_SEGMENTS = 9
    private const val FORM_TICKS = 8f
    private const val FADE_TICKS = 18f

    @Volatile
    private var shader: ShaderInstance? = null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_titan_barricade"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { loaded -> shader = loaded }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        val activeShader = shader ?: return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val barriers = context.world().getEntitiesOfClass(
            ArcTitanBarricadeEntity::class.java,
            AABB(camera, camera).inflate(QUERY_RANGE)
        ) { it.isAlive }.sortedByDescending { it.position().distanceToSqr(camera) }
        if (barriers.isEmpty()) return

        RenderSystem.enableBlend()
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()

        val modelView = RenderSystem.getModelViewStack()
        val worldPose = poseStack.last().pose()
        barriers.forEach { barrier ->
            renderBarrier(barrier, camera, partialTick, activeShader, modelView, worldPose)
        }

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderBarrier(
        barrier: ArcTitanBarricadeEntity,
        camera: Vec3,
        partialTick: Float,
        activeShader: ShaderInstance,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val age = barrier.tickCount + partialTick
        val formation = smoothStep(age / FORM_TICKS)
        val remaining = ArcTitanBarricadeEntity.LIFETIME_TICKS - age
        val fade = smoothStep(remaining / FADE_TICKS)
        val intensity = formation * fade
        if (intensity <= 0.002f) return

        val center = Vec3(
            Mth.lerp(partialTick.toDouble(), barrier.xOld, barrier.x),
            Mth.lerp(partialTick.toDouble(), barrier.yOld, barrier.y),
            Mth.lerp(partialTick.toDouble(), barrier.zOld, barrier.z)
        )
        val yaw = Mth.rotLerp(partialTick, barrier.yRotO, barrier.yRot) * PI.toFloat() / 180f
        val normal = Vec3(-sin(yaw.toDouble()), 0.0, cos(yaw.toDouble()))
        val right = Vec3(normal.z, 0.0, -normal.x)
        val relativeCenter = center.subtract(camera)

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        // Two close surfaces give the original barricade its thick, milky
        // energy-volume appearance without turning it into an opaque wall.
        floatArrayOf(-0.045f, 0.045f).forEach { normalOffset ->
            repeat(HORIZONTAL_SEGMENTS) { column ->
                val u0 = column.toFloat() / HORIZONTAL_SEGMENTS
                val u1 = (column + 1).toFloat() / HORIZONTAL_SEGMENTS
                repeat(VERTICAL_SEGMENTS) { row ->
                    val v0 = row.toFloat() / VERTICAL_SEGMENTS
                    val v1 = (row + 1).toFloat() / VERTICAL_SEGMENTS
                    addVertex(buffer, relativeCenter, right, normal, u0, v0, formation, normalOffset)
                    addVertex(buffer, relativeCenter, right, normal, u1, v0, formation, normalOffset)
                    addVertex(buffer, relativeCenter, right, normal, u1, v1, formation, normalOffset)
                    addVertex(buffer, relativeCenter, right, normal, u0, v1, formation, normalOffset)
                }
            }
        }

        activeShader.getUniform("Time")?.set((contextTime(barrier, partialTick)) * 0.05f)
        activeShader.getUniform("Intensity")?.set(intensity)
        activeShader.getUniform("Formation")?.set(formation)
        activeShader.getUniform("Seed")?.set((barrier.id and 1023) / 1023f)

        modelView.pushMatrix()
        modelView.mul(worldPose)
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setShader { activeShader }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()

    }

    private fun addVertex(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        center: Vec3,
        right: Vec3,
        normal: Vec3,
        u: Float,
        v: Float,
        formation: Float,
        normalOffset: Float
    ) {
        val point = barrierPoint(center, right, normal, u, v, formation, normalOffset)
        buffer.addVertex(point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
            .setUv(u, v)
            .setColor(255, 255, 255, 255)
    }

    private fun barrierPoint(
        center: Vec3,
        right: Vec3,
        normal: Vec3,
        u: Float,
        v: Float,
        formation: Float,
        normalOffset: Float
    ): Vec3 {
        val normalizedX = u * 2f - 1f
        val roundedCorners = 0.935f + 0.065f * sin((v * PI).toFloat()).coerceAtLeast(0f).pow(0.42f)
        val widthScale = (0.91f + formation * 0.09f) * roundedCorners
        val localX = normalizedX * HALF_WIDTH.toFloat() * widthScale
        // The reference has raised shoulders and a subtly lower centre, not a ragged arch.
        val raisedShoulders = 0.14f * normalizedX.pow(4)
        val softTopRipple = sin(u * 12.0f + 0.35f) * 0.018f
        val height = (BASE_HEIGHT.toFloat() + raisedShoulders + softTopRipple) * formation
        val curve = 0.15f * (1f - normalizedX * normalizedX) + normalOffset
        return center
            .add(right.scale(localX.toDouble()))
            .add(normal.scale(curve.toDouble()))
            .add(0.0, (height * v).toDouble(), 0.0)
    }

    private fun contextTime(entity: ArcTitanBarricadeEntity, partialTick: Float): Float =
        entity.level().gameTime + partialTick

    private fun smoothStep(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
