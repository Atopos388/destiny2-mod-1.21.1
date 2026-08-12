package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.FirespriteEntity
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
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/** True volumetric Firesprite renderer. No camera-facing flame quad is used. */
object FirespriteWorldRenderer {
    private const val QUERY_RANGE = 96.0
    private const val LATITUDE_SEGMENTS = 14
    private const val LONGITUDE_SEGMENTS = 20

    @Volatile private var volumeShader: ShaderInstance? = null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "firesprite_volume"),
                DefaultVertexFormat.POSITION_COLOR
            ) { shader -> volumeShader = shader }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        val shader = volumeShader ?: return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val firesprites = context.world().getEntitiesOfClass(
            FirespriteEntity::class.java,
            AABB(camera, camera).inflate(QUERY_RANGE)
        ) { it.isAlive }
        if (firesprites.isEmpty()) return

        RenderSystem.enableBlend()
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()

        val modelView = RenderSystem.getModelViewStack()
        val worldPose = poseStack.last().pose()
        val worldTime = context.world().gameTime + partialTick
        firesprites.forEach { entity ->
            renderFiresprite(entity, camera, partialTick, worldTime, shader, modelView, worldPose)
        }

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderFiresprite(
        entity: FirespriteEntity,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        shader: ShaderInstance,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val age = entity.tickCount + partialTick
        val formation = Mth.clamp(age / 7f, 0f, 1f).let { 1f - (1f - it).pow(3) }
        val pickup = if (entity.pickupAnimationTicks > 0) {
            Mth.clamp((entity.pickupAnimationTicks + partialTick) / FirespriteEntity.PICKUP_ANIMATION_TICKS, 0f, 1f)
        } else 0f
        val compression = 1f - pickup * 0.72f
        val yScale = 0.27f * formation * (1f - pickup * 0.42f)
        val widthPulse = 1f + sin(age * 0.19f + entity.id * 0.73f) * 0.045f
        val xScale = 0.185f * widthPulse * compression
        val zScale = 0.175f * (2f - widthPulse) * compression
        val base = interpolatedPosition(entity, partialTick).add(0.0, 0.10 + pickup * 0.18, 0.0)
        val center = base.add(
            sin(age * 0.13f + entity.id).toDouble() * 0.012,
            yScale.toDouble(),
            cos(age * 0.11f + entity.id * 0.37f).toDouble() * 0.010
        )
        val seedTime = worldTime + entity.id * 1.73f

        // The broad base is intentionally faint. Six offset volumes make the
        // visible silhouette a changing clump instead of one glowing egg.
        renderVolume(shader, center, camera, Vector3f(xScale, yScale, zScale), 0.25f, 0.34f, seedTime, 0f, modelView, worldPose)
        repeat(6) { lobe ->
            val phase = age * (0.075f + lobe * 0.006f) + entity.id * 0.41f + lobe * 2.399963f
            val tier = (lobe % 3 - 1) * 0.075f
            val radial = xScale * (0.23f + (lobe % 2) * 0.10f)
            val lobeCenter = center.add(
                cos(phase.toDouble()) * radial,
                (tier + sin((phase * 1.31f).toDouble()) * 0.025f) * formation,
                sin(phase.toDouble()) * radial
            )
            val lobeWidth = xScale * (0.48f + (lobe * 7 % 3) * 0.08f)
            val lobeHeight = yScale * (0.56f + (lobe * 5 % 4) * 0.075f)
            renderVolume(
                shader, lobeCenter, camera,
                Vector3f(lobeWidth, lobeHeight, lobeWidth * (0.88f + (lobe % 2) * 0.12f)),
                0.42f + (lobe % 3) * 0.18f, 0.40f,
                seedTime + lobe * 2.17f, 0f, modelView, worldPose
            )
        }
        renderVolume(shader, center.add(0.012, -0.014, -0.008), camera,
            Vector3f(xScale * 0.58f, yScale * 0.76f, zScale * 0.60f), 1.25f, 0.62f,
            seedTime + 4.2f, 0f, modelView, worldPose)
        renderVolume(shader, center.add(-0.006, -yScale * 0.06, 0.004), camera,
            Vector3f(xScale * 0.14f, yScale * 0.42f, zScale * 0.16f), 2.75f, 0.68f,
            seedTime + 8.7f, 0f, modelView, worldPose)

        // Sparse embers are tiny 3-D volumes, never an orbiting billboard halo.
        repeat(3) { index ->
            val cycle = positiveFraction(age * (0.028f + index * 0.004f) + hash(entity.id * 31 + index * 79))
            val x = (hash(entity.id * 13 + index * 41 + floor(age / 30f).toInt()) - 0.5f) * 0.23f
            val z = (hash(entity.id * 17 + index * 61) - 0.5f) * 0.17f
            val emberCenter = base.add(x.toDouble(), 0.12 + cycle * 0.40, z.toDouble())
            val size = (0.010f + hash(index * 53 + entity.id) * 0.009f) * (1f - cycle * 0.55f)
            renderVolume(shader, emberCenter, camera, Vector3f(size, size * 1.35f, size), 1.8f,
                (1f - cycle) * 0.72f, seedTime + index * 3.9f, 0f, modelView, worldPose)
        }

        if (pickup > 0f) {
            val wave = sin(Mth.PI * pickup).coerceAtLeast(0f)
            val radius = 0.22f + pickup * 0.38f
            renderVolume(shader, center, camera, Vector3f(radius, radius, radius), 0.7f,
                wave * 0.52f, seedTime, 1f, modelView, worldPose)
        }
    }

    private fun renderVolume(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        scale: Vector3f,
        heat: Float,
        opacity: Float,
        time: Float,
        shell: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (scale.x <= 0.001f || scale.y <= 0.001f || scale.z <= 0.001f || opacity <= 0.001f) return
        modelView.pushMatrix()
        modelView.mul(worldPose)
        modelView.translate((center.x - camera.x).toFloat(), (center.y - camera.y).toFloat(), (center.z - camera.z).toFloat())
        modelView.scale(scale.x, scale.y, scale.z)
        RenderSystem.applyModelViewMatrix()
        val cameraObject = Vector3f(
            ((camera.x - center.x) / scale.x).toFloat(),
            ((camera.y - center.y) / scale.y).toFloat(),
            ((camera.z - center.z) / scale.z).toFloat()
        )
        shader.getUniform("CameraPos")?.set(cameraObject.x, cameraObject.y, cameraObject.z)
        shader.getUniform("Time")?.set(time * 0.055f)
        shader.getUniform("Heat")?.set(heat)
        shader.getUniform("Opacity")?.set(opacity)
        shader.getUniform("Shell")?.set(shell)
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        addSphere(buffer)
        RenderSystem.setShader { shader }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun addSphere(buffer: com.mojang.blaze3d.vertex.BufferBuilder) {
        for (latitude in 0 until LATITUDE_SEGMENTS) {
            val t0 = (latitude.toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            val t1 = ((latitude + 1f) / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            for (longitude in 0 until LONGITUDE_SEGMENTS) {
                val p0 = longitude.toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                val p1 = (longitude + 1f) / LONGITUDE_SEGMENTS * Mth.TWO_PI
                vertex(buffer, t0, p0); vertex(buffer, t1, p0); vertex(buffer, t1, p1)
                vertex(buffer, t0, p0); vertex(buffer, t1, p1); vertex(buffer, t0, p1)
            }
        }
    }

    private fun vertex(buffer: com.mojang.blaze3d.vertex.BufferBuilder, theta: Float, phi: Float) {
        val c = Mth.cos(theta)
        buffer.addVertex(c * Mth.cos(phi), Mth.sin(theta), c * Mth.sin(phi)).setColor(255, 255, 255, 255)
    }

    private fun interpolatedPosition(entity: Entity, partialTick: Float): Vec3 = Vec3(
        Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x),
        Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y),
        Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)
    )
    private fun hash(seed: Int): Float { val value = sin(seed * 12.9898f) * 43758.5453f; return value - floor(value) }
    private fun positiveFraction(value: Float): Float = value - floor(value)
}
