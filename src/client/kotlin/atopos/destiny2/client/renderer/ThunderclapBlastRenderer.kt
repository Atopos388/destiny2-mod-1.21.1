// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.client.camera.ThunderclapCameraClient
import atopos.destiny2.common.action.ThunderclapTiming
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.MeshData
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.ladysnake.satin.api.event.ShaderEffectRenderCallback
import org.ladysnake.satin.api.managed.ShaderEffectManager
import org.slf4j.LoggerFactory
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.tan

/** Open-top Arc energy chamber erected from Thunderclap's compressed hand core. */
object ThunderclapBlastRenderer : HudRenderCallback {
    private val logger = LoggerFactory.getLogger("DestinyThunderclapVfx")
    private const val LIFETIME_TICKS = 12.0f
    private const val FIELD_START_TICK = 0.75f
    private const val CUP_HEIGHT = 3.35
    private const val CUP_RADIUS = 2.35
    private const val CUP_VERTICAL_SCALE = 0.78
    private const val CUP_VERTICAL_SEGMENTS = 9
    private const val CUP_RADIAL_SEGMENTS = 40
    private const val MAX_EFFECTS = 8
    private const val ABSTRACT_PLATE_MS = 130.0
    private val UP = Vec3(0.0, 1.0, 0.0)

    @Volatile private var volumeShader: ShaderInstance? = null
    @Volatile private var cloudShader: ShaderInstance? = null
    @Volatile private var screenShader: ShaderInstance? = null
    @Volatile private var groundShader: ShaderInstance? = null

    private val worldInkEffect by lazy {
        ShaderEffectManager.getInstance().manage(
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "shaders/post/thunderclap_world_ink.json")
        ) { logger.info("Thunderclap world ink post shader initialized") }
    }

    private data class Blast(
        val startGameTime: Double,
        val startNanos: Long,
        val core: Vec3,
        val forward: Vec3,
        val right: Vec3,
        val groundY: Double,
        val handDrawnImpact: Boolean
    )

    private data class Frame(
        val age: Float,
        val coreAlpha: Float,
        val fieldAlpha: Float,
        val fieldScale: Double,
        val topActivity: Float,
        val impact: Float
    )

    private data class ScreenImpact(
        val centerX: Float,
        val centerY: Float,
        val directionX: Float,
        val directionY: Float
    )

    private val active = ArrayDeque<Blast>()

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "thunderclap_blast_volume"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { volumeShader = it }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "thunderclap_blast_cloud"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { cloudShader = it }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "thunderclap_blast_screen"),
                DefaultVertexFormat.POSITION_TEX
            ) { screenShader = it }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "thunderclap_blast_ground"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { groundShader = it }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
        ShaderEffectRenderCallback.EVENT.register(::renderWorldInk)
        HudRenderCallback.EVENT.register(this)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            active.clear()
        }
    }

    fun activate(origin: Vec3, yawDegrees: Float, handDrawnImpact: Boolean = false) {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val forward = Vec3(-sin(yaw), 0.0, cos(yaw)).normalize()
        val right = Vec3(forward.z, 0.0, -forward.x)
        val handCore = origin.add(forward.scale(0.62)).add(0.0, 1.17, 0.0)
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        // Preserve the fractional tick at packet arrival. Integer-only time can
        // begin a newly received blast near age 1.0 and skip the flash peak.
        val gameTime = level.gameTime + client.timer.getGameTimeDeltaPartialTick(false).toDouble()
        val startNanos = System.nanoTime()
        while (active.size >= MAX_EFFECTS) active.removeFirst()
        active.addLast(
            Blast(
                gameTime,
                startNanos,
                handCore,
                forward,
                right,
                origin.y + 0.035,
                handDrawnImpact
            )
        )
        if (handDrawnImpact) {
            logger.info("Server-confirmed rare Thunderclap hand-drawn impact")
            ThunderclapCameraClient.beginImpactSequence(
                ThunderclapTiming.IMPACT_HIT_STOP_MS,
                ThunderclapTiming.IMPACT_SEQUENCE_END_MS
            )
            ThunderclapPlayerProxyClient.beginReleaseHitStop(ThunderclapTiming.IMPACT_HIT_STOP_MS)
        }
    }

    /** Vanilla HUD is omitted only while an authored monochrome plate is visible. */
    fun isHandDrawnImpactPlateActive(): Boolean {
        val client = Minecraft.getInstance()
        client.level ?: return false
        if (active.isEmpty() || client.screen != null) return false
        val nowNanos = System.nanoTime()
        return active.any { blast ->
            blast.handDrawnImpact && impactElapsedMillis(blast, nowNanos) in
                0.0..ThunderclapTiming.IMPACT_SEQUENCE_END_MS.toDouble()
        }
    }

    override fun onHudRender(graphics: GuiGraphics, tickCounter: DeltaTracker) {
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        if (active.isEmpty() || client.screen != null) return
        val camera = client.gameRenderer.mainCamera.position
        val now = level.gameTime + tickCounter.getGameTimeDeltaPartialTick(true).toDouble()
        var strength = 0.0f
        var flashStrength = 0.0f
        var inkImpact = 0.0f
        active.forEach { blast ->
            val age = (now - blast.startGameTime).toFloat()
            if (age !in 0.0f..LIFETIME_TICKS) return@forEach
            val distanceFade = (1.0 - camera.distanceTo(blast.core) / 14.0).coerceIn(0.0, 1.0).toFloat()
            val releaseGlow = pulse(age, 0.0f, 0.30f, 0.70f) * 0.42f
            // Start early enough that a low/uneven frame rate cannot skip the
            // exposure strike. It still falls away in roughly a tenth of a
            // second, before the chamber's internal structure needs to read.
            val impactFlash = pulse(age, 0.08f, 0.55f, 2.20f)
            val impactAfterglow = pulse(age, 0.72f, 1.30f, 2.80f) * 0.22f
            val chamberLight = smoothstep(0.76f, 0.96f, age) *
                (1.0f - smoothstep(5.8f, 7.2f, age)) * 0.15f
            strength = maxOf(strength, maxOf(releaseGlow, chamberLight, impactAfterglow) * distanceFade)
            flashStrength = maxOf(flashStrength, (impactFlash * 1.18f * distanceFade).coerceAtMost(1.0f))
            if (blast.handDrawnImpact) {
                // Suppress the later blue HUD exposure while the completed
                // world framebuffer is being converted to inked monochrome.
                val candidate = impactPlateStrength(impactElapsedMillis(blast, System.nanoTime())) *
                    if (distanceFade > 0.0f) 1.0f else 0.0f
                inkImpact = maxOf(inkImpact, candidate)
            }
        }
        if (
            strength <= 0.002f &&
            flashStrength <= 0.002f &&
            inkImpact <= 0.002f
        ) return

        // The rare frame has already post-processed the actual world color
        // buffer. Do not paint the normal blue HUD exposure over it afterwards.
        if (inkImpact > 0.002f) return

        val width = graphics.guiWidth().toFloat()
        val height = graphics.guiHeight().toFloat()
        if (width <= 0.0f || height <= 0.0f) return
        screenShader?.let { shader ->
            shader.getUniform("Strength")?.set(strength)
            shader.getUniform("FlashStrength")?.set(flashStrength)
            shader.getUniform("Time")?.set(now.toFloat() * 0.05f)

            RenderSystem.enableBlend()
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            )
            RenderSystem.disableDepthTest()
            RenderSystem.depthMask(false)
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
            buffer.addVertex(0f, height, 0f).setUv(0f, 1f)
            buffer.addVertex(width, height, 0f).setUv(1f, 1f)
            buffer.addVertex(width, 0f, 0f).setUv(1f, 0f)
            buffer.addVertex(0f, 0f, 0f).setUv(0f, 0f)
            RenderSystem.setShader { shader }
            BufferUploader.drawWithShader(buffer.buildOrThrow())
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.defaultBlendFunc()
            RenderSystem.disableBlend()
        }

        // Guaranteed exposure strike. Unlike the shaped shader above this uses
        // Minecraft's built-in GUI path, so shader registration, resource-pack
        // reload timing, or a missed narrow frame can no longer erase the flash.
        val exposureAlpha = (flashStrength * 178.0f + strength * 18.0f)
            .toInt()
            .coerceIn(0, 196)
        if (exposureAlpha > 0) {
            graphics.fill(
                0,
                0,
                width.toInt(),
                height.toInt(),
                (exposureAlpha shl 24) or 0x00EAF8FF
            )
        }

    }

    private fun renderWorldInk(tickDelta: Float) {
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val nowNanos = System.nanoTime()
        if (active.isEmpty() || client.screen != null) return
        val camera = client.gameRenderer.mainCamera.position
        var strength = 0.0f
        var seed = 0.0f
        var burstStrength = 0.0f
        var platePhase = 0.0f
        var phaseProgress = 0.0f
        var plateIndex = 0.0f
        var screenImpact = ScreenImpact(0.52f, 0.50f, 1.0f, 0.0f)
        active.forEach { blast ->
            if (!blast.handDrawnImpact) return@forEach
            val elapsedMs = impactElapsedMillis(blast, nowNanos)
            if (elapsedMs !in 0.0..ThunderclapTiming.IMPACT_SEQUENCE_END_MS.toDouble()) return@forEach
            if (camera.distanceTo(blast.core) > 14.0) return@forEach
            val candidate = impactPlateStrength(elapsedMs)
            burstStrength = maxOf(burstStrength, impactBurst(elapsedMs))
            if (candidate > strength) {
                strength = candidate
                seed = floor(elapsedMs / ABSTRACT_PLATE_MS + blast.startGameTime * 0.17).toFloat()
                platePhase = impactPlatePhase(elapsedMs)
                phaseProgress = ThunderclapTiming.impactPhaseProgress(elapsedMs)
                plateIndex = floor(elapsedMs / ABSTRACT_PLATE_MS).toFloat() % 4.0f
                screenImpact = screenImpact(blast, tickDelta)
            }
        }
        if (strength <= 0.002f) return
        worldInkEffect.setUniformValue("Strength", strength)
        worldInkEffect.setUniformValue("Seed", seed)
        worldInkEffect.setUniformValue("PlatePhase", platePhase)
        worldInkEffect.setUniformValue("PhaseProgress", phaseProgress)
        worldInkEffect.setUniformValue("PlateIndex", plateIndex)
        worldInkEffect.setUniformValue("ImpactCenter", screenImpact.centerX, screenImpact.centerY)
        worldInkEffect.setUniformValue("ImpactDirection", screenImpact.directionX, screenImpact.directionY)
        worldInkEffect.setUniformValue("BurstStrength", burstStrength)
        worldInkEffect.render(tickDelta)
    }

    private fun impactPlateStrength(elapsedMs: Double): Float = when {
        elapsedMs < 0.0 || elapsedMs > ThunderclapTiming.IMPACT_SEQUENCE_END_MS.toDouble() -> 0.0f
        elapsedMs <= ThunderclapTiming.IMPACT_INK_CLOSE_END_MS.toDouble() -> 1.0f
        else -> 1.0f - smoothstep(
            ThunderclapTiming.IMPACT_INK_CLOSE_END_MS.toFloat(),
            ThunderclapTiming.IMPACT_SEQUENCE_END_MS.toFloat(),
            elapsedMs.toFloat()
        )
    }

    private fun impactPlatePhase(elapsedMs: Double): Float = when (ThunderclapTiming.impactPhase(elapsedMs)) {
        ThunderclapTiming.ImpactPhase.ABSTRACT -> 0.0f
        ThunderclapTiming.ImpactPhase.LINE_ART -> 1.0f
        ThunderclapTiming.ImpactPhase.INK_CLOSE -> 2.0f
        ThunderclapTiming.ImpactPhase.RELEASE -> 3.0f
        ThunderclapTiming.ImpactPhase.COMPLETE -> 4.0f
    }

    private fun impactBurst(elapsedMs: Double): Float =
        1.0f - smoothstep(0.0f, 82.0f, elapsedMs.toFloat())

    private fun impactElapsedMillis(blast: Blast, nowNanos: Long): Double =
        (nowNanos - blast.startNanos).coerceAtLeast(0L) / 1_000_000.0

    private fun screenImpact(blast: Blast, tickDelta: Float): ScreenImpact {
        val client = Minecraft.getInstance()
        val camera = client.gameRenderer.mainCamera
        val width = client.window.width.coerceAtLeast(1)
        val height = client.window.height.coerceAtLeast(1)
        val fov = ThunderclapCameraClient.currentFov(tickDelta)
            ?: client.options.fov().get().toDouble()
        val center = projectToScreen(blast.core, camera.position, camera.yRot, camera.xRot, fov, width, height)
            ?: return ScreenImpact(0.52f, 0.50f, 1.0f, 0.0f)
        val endpoint = projectToScreen(
            blast.core.add(blast.forward.scale(1.4)),
            camera.position,
            camera.yRot,
            camera.xRot,
            fov,
            width,
            height
        )
        val dx = (endpoint?.first ?: center.first + 0.25f) - center.first
        val dy = (endpoint?.second ?: center.second) - center.second
        val length = sqrt(dx * dx + dy * dy)
        return ScreenImpact(
            center.first.coerceIn(0.10f, 0.90f),
            center.second.coerceIn(0.12f, 0.88f),
            if (length > 0.001f) dx / length else 1.0f,
            if (length > 0.001f) dy / length else 0.0f
        )
    }

    private fun projectToScreen(
        point: Vec3,
        camera: Vec3,
        yawDegrees: Float,
        pitchDegrees: Float,
        fovDegrees: Double,
        width: Int,
        height: Int
    ): Pair<Float, Float>? {
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val pitch = Math.toRadians(pitchDegrees.toDouble())
        val cosPitch = cos(pitch)
        val forward = Vec3(-sin(yaw) * cosPitch, -sin(pitch), cos(yaw) * cosPitch)
        val right = Vec3(cos(yaw), 0.0, sin(yaw))
        val up = forward.cross(right)
        val relative = point.subtract(camera)
        val depth = relative.dot(forward)
        if (depth <= 0.05) return null
        val tanHalfFov = tan(Math.toRadians(fovDegrees.coerceIn(20.0, 140.0) * 0.5))
        val aspect = width.toDouble() / height.toDouble()
        val ndcX = relative.dot(right) / (depth * tanHalfFov * aspect)
        val ndcY = relative.dot(up) / (depth * tanHalfFov)
        return (0.5 + ndcX * 0.5).toFloat() to (0.5 - ndcY * 0.5).toFloat()
    }

    private fun render(context: WorldRenderContext) {
        if (active.isEmpty()) return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val now = context.world().gameTime + partialTick.toDouble()
        val iterator = active.iterator()
        val cleanupNanos = System.nanoTime()
        while (iterator.hasNext()) {
            val blast = iterator.next()
            val worldFinished = now - blast.startGameTime > LIFETIME_TICKS
            val inkFinished = !blast.handDrawnImpact ||
                impactElapsedMillis(blast, cleanupNanos) > ThunderclapTiming.IMPACT_SEQUENCE_END_MS
            if (worldFinished && inkFinished) iterator.remove()
        }
        if (active.isEmpty()) return

        val modelView = RenderSystem.getModelViewStack()
        val worldPose = poseStack.last().pose()
        val worldTime = now.toFloat()
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

        volumeShader?.let { renderVolumes(it, camera, now, worldTime, modelView, worldPose) }
        cloudShader?.let { renderClouds(it, camera, now, worldTime, modelView, worldPose) }
        groundShader?.let { renderGroundEnergy(it, camera, now, worldTime, modelView, worldPose) }
        renderLightning(camera, now, modelView, worldPose)

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderVolumes(
        shader: ShaderInstance,
        camera: Vec3,
        now: Double,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        active.forEach { blast ->
            val frame = frame(blast, now) ?: return@forEach
            if (frame.fieldAlpha <= 0.001f) return@forEach
            // These shells communicate only a broken density boundary. They must
            // never be reused as lightning paths or endpoint sequences.
            addCupShell(buffer, blast, camera, frame, 1.00, 48)
            addCupShell(buffer, blast, camera, frame, 0.76, 36)
        }
        shader.getUniform("Time")?.set(worldTime * 0.068f)
        drawCustom(buffer.build(), shader, modelView, worldPose)
    }

    private fun addCupShell(
        buffer: BufferBuilder,
        blast: Blast,
        camera: Vec3,
        frame: Frame,
        radiusScale: Double,
        baseAlpha: Int
    ) {
        val base = blast.core.add(blast.forward.scale(0.10))
        val alpha = (baseAlpha * frame.fieldAlpha).toInt()
        repeat(CUP_VERTICAL_SEGMENTS) { verticalIndex ->
            val t0 = verticalIndex.toDouble() / CUP_VERTICAL_SEGMENTS
            val t1 = (verticalIndex + 1).toDouble() / CUP_VERTICAL_SEGMENTS
            val center0 = base.add(blast.forward.scale(CUP_HEIGHT * frame.fieldScale * t0))
            val center1 = base.add(blast.forward.scale(CUP_HEIGHT * frame.fieldScale * t1))
            val radius0 = cupRadius(t0) * radiusScale * frame.fieldScale
            val radius1 = cupRadius(t1) * radiusScale * frame.fieldScale
            repeat(CUP_RADIAL_SEGMENTS) { radialIndex ->
                val u0 = radialIndex.toFloat() / CUP_RADIAL_SEGMENTS
                val u1 = (radialIndex + 1).toFloat() / CUP_RADIAL_SEGMENTS
                val angle0 = u0 * PI * 2.0
                val angle1 = u1 * PI * 2.0
                addVolumeVertex(buffer, cupRingPoint(center0, blast, radius0, angle0), camera, t0.toFloat(), u0, alpha)
                addVolumeVertex(buffer, cupRingPoint(center0, blast, radius0, angle1), camera, t0.toFloat(), u1, alpha)
                addVolumeVertex(buffer, cupRingPoint(center1, blast, radius1, angle1), camera, t1.toFloat(), u1, alpha)
                addVolumeVertex(buffer, cupRingPoint(center1, blast, radius1, angle0), camera, t1.toFloat(), u0, alpha)
            }
        }
    }

    private fun renderClouds(
        shader: ShaderInstance,
        camera: Vec3,
        now: Double,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        active.forEach { blast ->
            val frame = frame(blast, now) ?: return@forEach
            val compressedRadius = 0.52 - smoothstep(0.0f, FIELD_START_TICK, frame.age) * 0.18
            if (frame.coreAlpha > 0.001f) {
                addCrossedCloud(buffer, blast.core, blast, camera, compressedRadius, (238 * frame.coreAlpha).toInt())
                addCrossedCloud(buffer, blast.core, blast, camera, compressedRadius * 0.40, (255 * frame.coreAlpha).toInt())
            }
            if (frame.impact > 0.001f) {
                // World-space flash remains visible even when the HUD is hidden:
                // one white-hot core bloom plus a broader, softer Arc bloom.
                val impactCenter = blast.core.add(blast.forward.scale(0.18))
                addCrossedCloud(buffer, impactCenter, blast, camera, 0.62 + frame.impact * 0.82, (230 * frame.impact).toInt())
                addCrossedCloud(buffer, impactCenter, blast, camera, 1.10 + frame.impact * 1.05, (112 * frame.impact).toInt())
            }
            if (frame.fieldAlpha > 0.001f) addCupPlasma(buffer, blast, camera, frame)
        }
        shader.getUniform("Time")?.set(worldTime * 0.082f)
        drawCustom(buffer.build(), shader, modelView, worldPose)
    }

    private fun addCupPlasma(buffer: BufferBuilder, blast: Blast, camera: Vec3, frame: Frame) {
        val base = blast.core.add(blast.forward.scale(0.10))
        val retainedFrame = (frame.age * 2.0f).toInt()
        // A turbulent, short transition joins the core and chamber. Offsets and
        // unequal layers keep it from reading as a hard pipe or paired wires.
        repeat(4) { neckLayer ->
            val key = retainedFrame * 89 + neckLayer * 43
            val center = base.add(blast.forward.scale(0.10 + neckLayer * 0.13))
                .add(blast.right.scale(signedNoise(key, 1) * 0.075))
                .add(UP.scale(signedNoise(key, 2) * 0.060))
            val width = 0.18 + neckLayer * 0.085
            addCloudPlane(buffer, center, blast.right, UP, camera, width, width * 0.74, (142 * frame.fieldAlpha).toInt())
            if (neckLayer % 2 == 0) {
                addCloudPlane(buffer, center, blast.right, blast.forward, camera, width * 0.82, 0.25, (76 * frame.fieldAlpha).toInt())
            }
        }

        // Medium-frequency emissive blobs make the arcs live inside charged
        // matter instead of floating in empty air.
        repeat(14) { knot ->
            val key = retainedFrame * 97 + knot * 131
            val axialT = 0.14 + hash01(key, 1) * 0.76
            val localRadius = cupRadius(axialT) * 0.64
            val angle = knot * PI * 0.77 + signedNoise(key, 2) * 0.42
            val centerline = base.add(blast.forward.scale(CUP_HEIGHT * frame.fieldScale * axialT))
            val center = cupRingPoint(centerline, blast, localRadius * sqrt(hash01(key, 3)), angle)
            val width = 0.28 + localRadius * 0.22
            val length = 0.34 + hash01(key, 4) * 0.56
            val pulse = 0.58 + hash01(key, 5) * 0.42
            addCloudPlane(buffer, center, blast.right, blast.forward, camera, width, length, (112 * frame.fieldAlpha * pulse).toInt())
            addCloudPlane(buffer, center, UP, blast.forward, camera, width * CUP_VERTICAL_SCALE, length, (72 * frame.fieldAlpha * pulse).toInt())
        }

        // Rolling ribbons cross several density cells. They are deliberately
        // interior-biased and do not follow the cup surface.
        repeat(5) { ribbon ->
            val key = retainedFrame * 149 + ribbon * 101
            val startT = 0.12 + hash01(key, 1) * 0.42
            repeat(4) { segment ->
                val axialT = (startT + segment * (0.08 + hash01(key, 2) * 0.035)).coerceAtMost(0.88)
                val centerline = base.add(blast.forward.scale(CUP_HEIGHT * frame.fieldScale * axialT))
                val radius = cupRadius(axialT) * (0.12 + hash01(key, 3) * 0.42)
                val angle = hash01(key, 4) * PI * 2.0 + segment * 0.34 + signedNoise(key, 20 + segment) * 0.24
                val center = cupRingPoint(centerline, blast, radius, angle)
                val alpha = (82 * frame.fieldAlpha * (1.0 - segment * 0.12)).toInt()
                addCloudPlane(buffer, center, blast.right, blast.forward, camera, 0.28 + segment * 0.04, 0.42, alpha)
            }
        }

        // The far opening boils forward instead of closing with a cap.
        repeat(6) { wisp ->
            val key = retainedFrame * 173 + wisp * 61
            val angle = wisp * PI * 2.0 / 6.0 + signedNoise(key, 1) * 0.30
            val openCenter = base.add(blast.forward.scale(CUP_HEIGHT * frame.fieldScale))
            val center = cupRingPoint(openCenter, blast, CUP_RADIUS * (0.28 + hash01(key, 2) * 0.54), angle)
                .add(blast.forward.scale(0.10 + hash01(key, 3) * 0.40))
            val width = 0.30 + hash01(key, 4) * 0.34
            val length = 0.44 + hash01(key, 5) * 0.55
            addCloudPlane(buffer, center, blast.right, blast.forward, camera, width, length, (92 * frame.topActivity).toInt())
            addCloudPlane(buffer, center, UP, blast.forward, camera, width * CUP_VERTICAL_SCALE, length, (66 * frame.topActivity).toInt())
        }
    }

    private fun renderGroundEnergy(
        shader: ShaderInstance,
        camera: Vec3,
        now: Double,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        active.forEach { blast ->
            val frame = frame(blast, now) ?: return@forEach
            if (frame.fieldAlpha <= 0.001f) return@forEach
            val center = Vec3(blast.core.x, blast.groundY, blast.core.z)
            val radius = 1.48 + pulse(frame.age, 0.52f, 0.95f, 2.10f) * 0.78
            val a = blast.right.scale(radius)
            val b = blast.forward.scale(radius)
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            addCloudVertex(buffer, center.subtract(a).subtract(b), camera, 0f, 0f, 255)
            addCloudVertex(buffer, center.add(a).subtract(b), camera, 1f, 0f, 255)
            addCloudVertex(buffer, center.add(a).add(b), camera, 1f, 1f, 255)
            addCloudVertex(buffer, center.subtract(a).add(b), camera, 0f, 1f, 255)
            shader.getUniform("Time")?.set(worldTime * 0.065f)
            shader.getUniform("Phase")?.set(((frame.age - FIELD_START_TICK) / 2.2f).coerceIn(0.0f, 1.0f))
            // Retain roughly 40% of the impact response for several ticks, then
            // let it layer out with the chamber instead of dropping to black.
            val retainedLight = frame.fieldAlpha * (0.40f + frame.impact * 0.60f)
            shader.getUniform("Opacity")?.set(retainedLight)
            drawCustom(buffer.build(), shader, modelView, worldPose)
        }
    }

    private fun renderLightning(
        camera: Vec3,
        now: Double,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        active.forEach { blast ->
            val frame = frame(blast, now) ?: return@forEach
            if (frame.fieldAlpha <= 0.001f) return@forEach
            addRandomFieldLightning(buffer, blast, frame, camera)
            addMicroDischarges(buffer, blast, frame, camera)
        }
        val mesh = buffer.build() ?: return
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        drawMesh(mesh, modelView, worldPose)
    }

    private fun addRandomFieldLightning(buffer: BufferBuilder, blast: Blast, frame: Frame, camera: Vec3) {
        // 1.5 redraws/tick is roughly a new topology every 1-3 rendered frames.
        val redraw = (frame.age * 1.5f).toInt()
        repeat(10) { bolt ->
            val key = redraw * 997 + bolt * 131
            val (from, to) = when (bolt) {
                // 50%: two unrelated points inside the density volume.
                in 0..4 -> randomInteriorPoint(blast, frame, key, 0.14, 0.88, 0.64) to
                    randomInteriorPoint(blast, frame, key + 409, 0.20, 0.92, 0.68)
                // 30%: varied points around the core to a random middle/upper cell.
                in 5..7 -> randomCorePoint(blast, key) to
                    randomInteriorPoint(blast, frame, key + 613, 0.28, 0.84, 0.62)
                // 20%: only one endpoint may touch the containment boundary.
                else -> randomInteriorPoint(blast, frame, key, 0.22, 0.82, 0.52) to
                    randomBoundaryPoint(blast, frame, key + 827)
            }
            if (from.distanceToSqr(to) < 0.11) return@repeat
            val points = jaggedPath(blast, from, to, key, 7 + (hash01(key, 90) * 3.0).toInt(), 1.0)
            val flicker = (0.68 + hash01(key, 91) * 0.32).toFloat()
            renderLightningPath(buffer, points, camera, frame.fieldAlpha * flicker, if (bolt < 5) 0.72f else 0.88f)

            // Branches are visibly subordinate: one short fork from an interior
            // main node, never another cup-spanning line.
            if (bolt < 7 && hash01(key, 92) > 0.30) {
                val branchIndex = 2 + (hash01(key, 93) * (points.size - 4)).toInt()
                val branchStart = points[branchIndex]
                val direction = blast.right.scale(signedNoise(key, 94))
                    .add(UP.scale(signedNoise(key, 95)))
                    .add(blast.forward.scale(signedNoise(key, 96) * 0.55))
                    .normalize()
                val branchEnd = branchStart.add(direction.scale(0.20 + hash01(key, 97) * 0.42))
                val branch = jaggedPath(blast, branchStart, branchEnd, key + 1237, 4, 0.48)
                renderLightningPath(buffer, branch, camera, frame.fieldAlpha * flicker * 0.62f, 0.44f)
            }
        }
    }

    private fun addMicroDischarges(buffer: BufferBuilder, blast: Blast, frame: Frame, camera: Vec3) {
        val redraw = (frame.age * 2.0f).toInt()
        repeat(14) { spark ->
            val key = redraw * 557 + spark * 89
            val start = randomInteriorPoint(blast, frame, key, 0.08, 0.94, 0.58)
            val direction = blast.right.scale(signedNoise(key, 11))
                .add(UP.scale(signedNoise(key, 12)))
                .add(blast.forward.scale(signedNoise(key, 13) * 0.42))
                .normalize()
            val end = start.add(direction.scale(0.08 + hash01(key, 14) * 0.22))
            val intensity = frame.fieldAlpha * (0.30f + hash01(key, 15).toFloat() * 0.34f)
            renderLightningPath(buffer, listOf(start, end), camera, intensity, 0.24f)
        }
    }

    private fun randomInteriorPoint(
        blast: Blast,
        frame: Frame,
        key: Int,
        minT: Double,
        maxT: Double,
        radialLimit: Double
    ): Vec3 {
        val t = minT + hash01(key, 1) * (maxT - minT)
        val center = blast.core.add(blast.forward.scale(0.10 + CUP_HEIGHT * frame.fieldScale * t))
        val radius = cupRadius(t) * radialLimit * sqrt(hash01(key, 2))
        val angle = hash01(key, 3) * PI * 2.0
        return cupRingPoint(center, blast, radius, angle)
    }

    private fun randomBoundaryPoint(blast: Blast, frame: Frame, key: Int): Vec3 {
        val t = 0.24 + hash01(key, 1) * 0.68
        val center = blast.core.add(blast.forward.scale(0.10 + CUP_HEIGHT * frame.fieldScale * t))
        val radius = cupRadius(t) * (0.88 + hash01(key, 2) * 0.08)
        return cupRingPoint(center, blast, radius, hash01(key, 3) * PI * 2.0)
    }

    private fun randomCorePoint(blast: Blast, key: Int): Vec3 {
        val angle = hash01(key, 1) * PI * 2.0
        val radius = 0.06 + hash01(key, 2) * 0.16
        return blast.core.add(blast.right.scale(cos(angle) * radius))
            .add(UP.scale(sin(angle) * radius))
            .add(blast.forward.scale(hash01(key, 3) * 0.12))
    }

    private fun jaggedPath(
        blast: Blast,
        from: Vec3,
        to: Vec3,
        key: Int,
        nodes: Int,
        jitterScale: Double
    ): List<Vec3> {
        val distance = from.distanceTo(to)
        val jitter = (0.11 + distance * 0.075).coerceAtMost(0.31) * jitterScale
        return List(nodes) { index ->
            val t = index.toDouble() / (nodes - 1)
            val envelope = sin(PI * t)
            from.scale(1.0 - t).add(to.scale(t))
                .add(blast.right.scale(signedNoise(key, 20 + index) * jitter * envelope))
                .add(UP.scale(signedNoise(key, 40 + index) * jitter * envelope))
                .add(blast.forward.scale(signedNoise(key, 60 + index) * jitter * 0.58 * envelope))
        }
    }

    private fun renderLightningPath(
        buffer: BufferBuilder,
        points: List<Vec3>,
        camera: Vec3,
        intensity: Float,
        widthScale: Float
    ) {
        for (index in 0 until points.lastIndex) {
            val midpoint = (index + 0.5) / points.lastIndex
            val envelope = sin(PI * midpoint).toFloat()
            val alpha = intensity * envelope
            emitRibbon(buffer, points[index], points[index + 1], camera, 0.062f * widthScale, 30, 92, 255, (34 * alpha).toInt())
            emitRibbon(buffer, points[index], points[index + 1], camera, 0.018f * widthScale, 80, 181, 255, (142 * alpha).toInt())
            emitRibbon(buffer, points[index], points[index + 1], camera, 0.0042f * widthScale, 244, 253, 255, (246 * alpha).toInt())
        }
    }

    private fun addCrossedCloud(
        buffer: BufferBuilder,
        center: Vec3,
        blast: Blast,
        camera: Vec3,
        radius: Double,
        alpha: Int
    ) {
        if (alpha <= 0 || radius <= 0.01) return
        addCloudPlane(buffer, center, blast.right, UP, camera, radius, radius, alpha)
        addCloudPlane(buffer, center, blast.forward, UP, camera, radius * 0.76, radius, alpha / 2)
        addCloudPlane(buffer, center, blast.right, blast.forward, camera, radius, radius * 0.76, alpha / 2)
    }

    private fun addCloudPlane(
        buffer: BufferBuilder,
        center: Vec3,
        axisA: Vec3,
        axisB: Vec3,
        camera: Vec3,
        radiusA: Double,
        radiusB: Double,
        alpha: Int
    ) {
        val a = axisA.scale(radiusA)
        val b = axisB.scale(radiusB)
        addCloudVertex(buffer, center.subtract(a).subtract(b), camera, 0f, 0f, alpha)
        addCloudVertex(buffer, center.add(a).subtract(b), camera, 1f, 0f, alpha)
        addCloudVertex(buffer, center.add(a).add(b), camera, 1f, 1f, alpha)
        addCloudVertex(buffer, center.subtract(a).add(b), camera, 0f, 1f, alpha)
    }

    private fun emitRibbon(
        buffer: BufferBuilder,
        from: Vec3,
        to: Vec3,
        camera: Vec3,
        halfWidth: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        if (alpha <= 0) return
        val direction = to.subtract(from)
        val view = camera.subtract(from.add(to).scale(0.5))
        val side = direction.cross(view).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize().scale(halfWidth.toDouble())
            else blastFallbackSide(direction, halfWidth)
        }
        addTriangle(buffer, from.add(side), from.subtract(side), to.subtract(side), camera, red, green, blue, alpha)
        addTriangle(buffer, from.add(side), to.subtract(side), to.add(side), camera, red, green, blue, alpha)
    }

    private fun blastFallbackSide(direction: Vec3, halfWidth: Float): Vec3 {
        val side = direction.cross(UP)
        return if (side.lengthSqr() > 1.0e-8) side.normalize().scale(halfWidth.toDouble())
        else Vec3(halfWidth.toDouble(), 0.0, 0.0)
    }

    private fun addTriangle(
        buffer: BufferBuilder,
        a: Vec3,
        b: Vec3,
        c: Vec3,
        camera: Vec3,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        addColorVertex(buffer, a, camera, red, green, blue, alpha)
        addColorVertex(buffer, b, camera, red, green, blue, alpha)
        addColorVertex(buffer, c, camera, red, green, blue, alpha)
    }

    private fun addVolumeVertex(
        buffer: BufferBuilder,
        point: Vec3,
        camera: Vec3,
        vertical: Float,
        angular: Float,
        alpha: Int
    ) {
        val relative = point.subtract(camera)
        buffer.addVertex(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
            .setUv(vertical, angular)
            .setColor(255, 255, 255, alpha.coerceIn(0, 255))
    }

    private fun addCloudVertex(buffer: BufferBuilder, point: Vec3, camera: Vec3, u: Float, v: Float, alpha: Int) {
        val relative = point.subtract(camera)
        buffer.addVertex(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
            .setUv(u, v)
            .setColor(255, 255, 255, alpha.coerceIn(0, 255))
    }

    private fun addColorVertex(
        buffer: BufferBuilder,
        point: Vec3,
        camera: Vec3,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val relative = point.subtract(camera)
        buffer.addVertex(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
            .setColor(red, green, blue, alpha.coerceIn(0, 255))
    }

    private fun drawCustom(
        mesh: MeshData?,
        shader: ShaderInstance,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (mesh == null) return
        RenderSystem.setShader { shader }
        drawMesh(mesh, modelView, worldPose)
    }

    private fun drawMesh(mesh: MeshData, modelView: org.joml.Matrix4fStack, worldPose: org.joml.Matrix4f) {
        modelView.pushMatrix()
        modelView.mul(worldPose)
        RenderSystem.applyModelViewMatrix()
        BufferUploader.drawWithShader(mesh)
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun frame(blast: Blast, now: Double): Frame? {
        val age = (now - blast.startGameTime).toFloat()
        if (age !in 0.0f..LIFETIME_TICKS) return null
        val initialCore = 1.0f - smoothstep(1.20f, 3.2f, age)
        val establish = smoothstep(FIELD_START_TICK, 0.94f, age)
        val fade = 1.0f - smoothstep(6.2f, LIFETIME_TICKS, age)
        val fieldAlpha = establish * fade
        val overshoot = pulse(age, 0.72f, 1.06f, 1.90f)
        val fieldScale = 0.90 + establish * 0.10 + overshoot * 0.10
        val topActivity = fieldAlpha * (0.64f + pulse(age, 0.86f, 1.45f, 3.6f) * 0.36f)
        val coreAlpha = maxOf(initialCore, fieldAlpha * 0.72f) * fade
        val impact = pulse(age, 0.08f, 0.55f, 2.20f)
        return Frame(age, coreAlpha, fieldAlpha, fieldScale, topActivity, impact)
    }

    private fun cupRadius(verticalT: Double): Double {
        val t = verticalT.coerceIn(0.0, 1.0)
        val neckRadius = 0.16
        return if (t < 0.24) {
            val flare = easeOutCubic(t / 0.24)
            neckRadius + (CUP_RADIUS * 0.78 - neckRadius) * flare
        } else {
            CUP_RADIUS * (0.78 + 0.22 * smoothstepDouble(0.24, 1.0, t))
        }
    }

    private fun cupRingPoint(center: Vec3, blast: Blast, radius: Double, angle: Double): Vec3 =
        center.add(blast.right.scale(cos(angle) * radius))
            .add(UP.scale(sin(angle) * radius * CUP_VERTICAL_SCALE))

    private fun easeOutCubic(value: Double): Double {
        val inverse = 1.0 - value.coerceIn(0.0, 1.0)
        return 1.0 - inverse * inverse * inverse
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

    private fun smoothstepDouble(edge0: Double, edge1: Double, value: Double): Double {
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }

    private fun pulse(age: Float, start: Float, peak: Float, end: Float): Float {
        return smoothstep(start, peak, age) * (1.0f - smoothstep(peak, end, age))
    }

    private fun hash01(index: Int, channel: Int): Double {
        var value = index * 73428767 xor channel * 912931
        value = value xor (value ushr 13)
        return (value and 0xFFFF) / 65535.0
    }

    private fun signedNoise(index: Int, channel: Int): Double = hash01(index, channel) * 2.0 - 1.0
}
