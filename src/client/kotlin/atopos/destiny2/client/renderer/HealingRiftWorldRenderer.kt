package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.HealingRiftEntity
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** Procedural world-space visual for the Warlock healing rift. */
object HealingRiftWorldRenderer {
    private const val QUERY_RANGE = 72.0
    private const val RIFT_RADIUS = 6.0f
    private const val EXPAND_TICKS = 16.0f
    private const val DISK_SEGMENTS = 80
    private const val VERTICAL_STRANDS = 12
    private const val RISING_MOTES = 84
    private const val RIM_SEGMENTS = 96
    private const val RIM_SHELLS = 3

    @Volatile
    private var groundShader: ShaderInstance? = null
    @Volatile
    private var vortexShader: ShaderInstance? = null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "healing_rift_ground"),
                DefaultVertexFormat.POSITION_COLOR
            ) { shader -> groundShader = shader }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "healing_rift_vortex"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { shader -> vortexShader = shader }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        val shader = groundShader ?: return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val rifts = context.world().getEntitiesOfClass(
            HealingRiftEntity::class.java,
            AABB(camera, camera).inflate(QUERY_RANGE)
        ) { it.isAlive }
        if (rifts.isEmpty()) return

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
        renderGround(rifts, camera, partialTick, modelView, worldPose, shader)
        renderVerticalLight(rifts, camera, partialTick, modelView, worldPose)
        vortexShader?.let { renderVortex(rifts, camera, partialTick, modelView, worldPose, worldTime, it) }

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderGround(
        rifts: List<HealingRiftEntity>,
        camera: Vec3,
        partialTick: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        shader: ShaderInstance
    ) {
        rifts.forEach { rift ->
            val age = rift.tickCount + partialTick
            val progress = Mth.clamp(age / EXPAND_TICKS, 0f, 1f)
            val expansion = 1f - (1f - progress) * (1f - progress) * (1f - progress)
            val radius = RIFT_RADIUS * expansion
            if (radius <= 0.01f) return@forEach

            val center = interpolatedPosition(rift, partialTick)
            val intensity = Mth.clamp(age / 10f, 0f, 1f) *
                (0.94f + sin(age * 0.07f) * 0.06f)

            val layerHeights = floatArrayOf(0.028f, 0.13f, 0.28f, 0.45f)
            val layerScales = floatArrayOf(1.0f, 0.975f, 0.94f, 0.88f)
            val layerStrengths = floatArrayOf(1.0f, 0.58f, 0.36f, 0.20f)
            for (layer in layerHeights.indices) {
                val layerRadius = radius * layerScales[layer]
                modelView.pushMatrix()
                modelView.mul(worldPose)
                modelView.translate(
                    (center.x - camera.x).toFloat(),
                    (center.y + layerHeights[layer] - camera.y).toFloat(),
                    (center.z - camera.z).toFloat()
                )
                modelView.scale(layerRadius, 1f, layerRadius)
                RenderSystem.applyModelViewMatrix()

                shader.getUniform("Time")?.set(age * 0.045f + layer * 0.21f)
                shader.getUniform("Intensity")?.set(intensity * layerStrengths[layer])
                shader.getUniform("Radius")?.set(layerRadius)
                shader.getUniform("Layer")?.set(layer.toFloat() / (layerHeights.size - 1))
                val buffer = Tesselator.getInstance().begin(
                    VertexFormat.Mode.TRIANGLES,
                    DefaultVertexFormat.POSITION_COLOR
                )
                addDisk(buffer)
                RenderSystem.setShader { shader }
                BufferUploader.drawWithShader(buffer.buildOrThrow())

                modelView.popMatrix()
                RenderSystem.applyModelViewMatrix()
            }
        }
    }

    private fun renderVerticalLight(
        rifts: List<HealingRiftEntity>,
        camera: Vec3,
        partialTick: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        )
        rifts.forEach { rift ->
            val age = rift.tickCount + partialTick
            val fadeIn = Mth.clamp(age / 12f, 0f, 1f)
            if (fadeIn <= 0f) return@forEach
            val center = interpolatedPosition(rift, partialTick)
            val progress = Mth.clamp(age / EXPAND_TICKS, 0f, 1f)
            val expansion = 1f - (1f - progress) * (1f - progress) * (1f - progress)
            val currentRadius = RIFT_RADIUS * expansion

            addMistRim(buffer, center, camera, currentRadius, age, fadeIn)
            addRisingMotes(buffer, center, camera, currentRadius, age, fadeIn)

            repeat(VERTICAL_STRANDS) { strand ->
                val angle = strand * Mth.TWO_PI / VERTICAL_STRANDS + age * 0.0025f
                val radius = (1.0 + (strand % 4) * 1.25) * expansion
                val base = center.add(
                    cos(angle.toDouble()) * radius,
                    0.04,
                    sin(angle.toDouble()) * radius
                )
                val height = 0.58 + (strand % 3) * 0.28
                val sway = sin(age * 0.045f + strand * 1.7f) * 0.055
                val top = base.add(
                    cos(angle.toDouble()) * sway,
                    height,
                    sin(angle.toDouble()) * sway
                )
                val pulse = 0.74f + sin(age * 0.055f + strand) * 0.26f
                emitRibbon(
                    buffer,
                    base,
                    top,
                    camera,
                    0.020f,
                    0.006f,
                    218,
                    239,
                    255,
                    (9f * fadeIn * pulse).toInt()
                )
            }

        }

        val mesh = buffer.build() ?: return
        modelView.pushMatrix()
        modelView.mul(worldPose)
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        BufferUploader.drawWithShader(mesh)
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    /** Dense, tiny white motes rise about 1.5 blocks before softly recycling. */
    private fun addRisingMotes(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        center: Vec3,
        camera: Vec3,
        radius: Float,
        age: Float,
        fadeIn: Float
    ) {
        if (radius <= 0.05f) return
        repeat(RISING_MOTES) { mote ->
            val phase = (age * 0.014f + mote * 0.6180339f) % 1f
            val radialSeed = ((mote * 47 + 13) % RISING_MOTES + 0.5) / RISING_MOTES
            val moteRadius = kotlin.math.sqrt(radialSeed) * (radius - 0.28f).coerceAtLeast(0f)
            val angle = mote * 2.399963f + sin(age * 0.004f + mote * 0.73f) * 0.075f
            val height = 0.04 + phase * 1.5
            val envelope = sin((phase * Mth.PI).toDouble()).toFloat().coerceAtLeast(0f)
            val twinkle = 0.72f + sin(age * 0.16f + mote * 1.91f) * 0.28f
            val alpha = (34f * fadeIn * envelope * twinkle).toInt()
            if (alpha <= 0) return@repeat

            val point = center.add(
                cos(angle.toDouble()) * moteRadius,
                height,
                sin(angle.toDouble()) * moteRadius
            )
            val sway = sin(age * 0.055f + mote * 1.37f) * 0.012
            val top = point.add(sway, 0.032, -sway)
            emitRibbon(
                buffer,
                point,
                top,
                camera,
                0.011f,
                0.008f,
                248,
                253,
                255,
                alpha,
                alpha
            )
        }
    }

    /**
     * Several close, inward-leaning shells form the raised foamy rim visible
     * from a low camera angle. Independent top heights prevent a perfect wall.
     */
    private fun addMistRim(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        center: Vec3,
        camera: Vec3,
        radius: Float,
        age: Float,
        fadeIn: Float
    ) {
        if (radius <= 0.05f) return
        repeat(RIM_SHELLS) { shell ->
            val shellRadius = radius - shell * 0.11f
            val shellAlpha = (31f - shell * 6f) * fadeIn
            for (segment in 0 until RIM_SEGMENTS) {
                val angle0 = segment * Mth.TWO_PI / RIM_SEGMENTS
                val angle1 = (segment + 1) * Mth.TWO_PI / RIM_SEGMENTS

                fun height(angle: Float): Double {
                    val broad = sin((angle * 5.0f + age * 0.018f + shell).toDouble()) * 0.055
                    val detail = sin((angle * 13.0f - age * 0.011f + shell * 1.7f).toDouble()) * 0.028
                    return 0.16 + shell * 0.035 + broad + detail
                }

                val topInset = 0.10 + shell * 0.025
                val bottom0 = center.add(
                    cos(angle0.toDouble()) * shellRadius,
                    0.025 + shell * 0.018,
                    sin(angle0.toDouble()) * shellRadius
                )
                val bottom1 = center.add(
                    cos(angle1.toDouble()) * shellRadius,
                    0.025 + shell * 0.018,
                    sin(angle1.toDouble()) * shellRadius
                )
                val top0 = center.add(
                    cos(angle0.toDouble()) * (shellRadius - topInset),
                    height(angle0),
                    sin(angle0.toDouble()) * (shellRadius - topInset)
                )
                val top1 = center.add(
                    cos(angle1.toDouble()) * (shellRadius - topInset),
                    height(angle1),
                    sin(angle1.toDouble()) * (shellRadius - topInset)
                )
                addVertex(buffer, bottom0, camera, 205, 232, 255, shellAlpha.toInt())
                addVertex(buffer, bottom1, camera, 205, 232, 255, shellAlpha.toInt())
                addVertex(buffer, top1, camera, 240, 250, 255, (shellAlpha * 0.24f).toInt())
                addVertex(buffer, top0, camera, 240, 250, 255, (shellAlpha * 0.24f).toInt())
            }
        }
    }

    /**
     * Three crossed procedural mist sheets suggest a rotating updraft without
     * exposing a literal spiral mesh. Their soft masks overlap into a small
     * translucent volume at the centre of the rift.
     */
    private fun renderVortex(
        rifts: List<HealingRiftEntity>,
        camera: Vec3,
        partialTick: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        worldTime: Float,
        shader: ShaderInstance
    ) {
        rifts.forEach { rift ->
            val age = rift.tickCount + partialTick
            val fadeIn = Mth.clamp(age / 14f, 0f, 1f)
            if (fadeIn <= 0f) return@forEach
            val center = interpolatedPosition(rift, partialTick)
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR
            )

            repeat(3) { plane ->
                val angle = plane * Mth.PI / 3f + age * 0.0018f
                val side = Vec3(cos(angle.toDouble()), 0.0, sin(angle.toDouble()))
                val bottomHalfWidth = 0.22
                val topHalfWidth = 0.68
                val bottom = center.add(0.0, 0.035, 0.0)
                val top = center.add(0.0, 1.48, 0.0)
                addVortexVertex(buffer, bottom.subtract(side.scale(bottomHalfWidth)), camera, 0f, 1f)
                addVortexVertex(buffer, bottom.add(side.scale(bottomHalfWidth)), camera, 1f, 1f)
                addVortexVertex(buffer, top.add(side.scale(topHalfWidth)), camera, 1f, 0f)
                addVortexVertex(buffer, top.subtract(side.scale(topHalfWidth)), camera, 0f, 0f)
            }

            modelView.pushMatrix()
            modelView.mul(worldPose)
            RenderSystem.applyModelViewMatrix()
            shader.getUniform("Time")?.set(worldTime * 0.045f)
            shader.getUniform("Intensity")?.set(fadeIn)
            RenderSystem.setShader { shader }
            BufferUploader.drawWithShader(buffer.buildOrThrow())
            modelView.popMatrix()
            RenderSystem.applyModelViewMatrix()
        }
    }

    private fun addVortexVertex(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        position: Vec3,
        camera: Vec3,
        u: Float,
        v: Float
    ) {
        val relative = position.subtract(camera)
        buffer.addVertex(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
            .setUv(u, v)
            .setColor(238, 249, 255, 255)
    }

    private fun addDisk(buffer: com.mojang.blaze3d.vertex.BufferBuilder) {
        for (segment in 0 until DISK_SEGMENTS) {
            val angle0 = segment * Mth.TWO_PI / DISK_SEGMENTS
            val angle1 = (segment + 1) * Mth.TWO_PI / DISK_SEGMENTS
            buffer.addVertex(0f, 0f, 0f).setColor(255, 255, 255, 255)
            buffer.addVertex(Mth.cos(angle0), 0f, Mth.sin(angle0)).setColor(255, 255, 255, 255)
            buffer.addVertex(Mth.cos(angle1), 0f, Mth.sin(angle1)).setColor(255, 255, 255, 255)
        }
    }

    private fun emitRibbon(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        from: Vec3,
        to: Vec3,
        camera: Vec3,
        fromHalfWidth: Float,
        toHalfWidth: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
        toAlpha: Int = 0
    ) {
        val direction = to.subtract(from)
        val midpoint = from.add(to).scale(0.5)
        val side = direction.cross(camera.subtract(midpoint)).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(1.0, 0.0, 0.0)
        }
        val fromSide = side.scale(fromHalfWidth.toDouble())
        val toSide = side.scale(toHalfWidth.toDouble())
        addVertex(buffer, from.add(fromSide), camera, red, green, blue, alpha)
        addVertex(buffer, from.subtract(fromSide), camera, red, green, blue, alpha)
        addVertex(buffer, to.subtract(toSide), camera, red, green, blue, toAlpha)
        addVertex(buffer, to.add(toSide), camera, red, green, blue, toAlpha)
    }

    private fun addVertex(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        position: Vec3,
        camera: Vec3,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val relative = position.subtract(camera)
        buffer.addVertex(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
            .setColor(red, green, blue, alpha.coerceIn(0, 255))
    }

    private fun interpolatedPosition(entity: HealingRiftEntity, partialTick: Float): Vec3 =
        Vec3(
            Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x),
            Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y),
            Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)
        )
}
