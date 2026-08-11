// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.ArcPulseGrenadeEntity
import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
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
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Destiny-style presentation for the Arc Pulse Grenade.
 *
 * The entity remains the single authority for impact, damage and cadence. A
 * vanilla entity event marks each real damage pulse; this renderer turns that
 * event into a low plasma crown, broken ground illumination and short crawling
 * filaments. It deliberately avoids a permanent large orb or a hard circular
 * outline: between pulses only the tiny white-hot anchor remains.
 */
object ArcPulseGrenadeWorldRenderer {
    private const val QUERY_RANGE = 96.0
    private const val LATITUDE_SEGMENTS = 13
    private const val LONGITUDE_SEGMENTS = 20
    private const val DISC_SEGMENTS = 72
    private const val TRAIL_SEGMENTS = 7
    private const val PULSE_FILAMENTS = 3
    private const val PULSE_DEBRIS = 90
    private const val PULSE_PLASMA_TONGUES = 16

    @Volatile
    private var coreShader: ShaderInstance? = null

    @Volatile
    private var fieldShader: ShaderInstance? = null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_pulse_grenade_core"),
                DefaultVertexFormat.POSITION_COLOR
            ) { loaded -> coreShader = loaded }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_pulse_grenade_field"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { loaded -> fieldShader = loaded }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        val core = coreShader ?: return
        val field = fieldShader ?: return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val grenades = context.world().getEntitiesOfClass(
            ArcPulseGrenadeEntity::class.java,
            AABB(camera, camera).inflate(QUERY_RANGE)
        ) { it.isAlive }
        if (grenades.isEmpty()) return

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
        grenades.forEach { grenade ->
            if (grenade.isAnchored()) {
                renderAnchor(grenade, camera, partialTick, worldTime, core, field, modelView, worldPose)
            } else {
                renderProjectile(grenade, camera, partialTick, worldTime, core, modelView, worldPose)
            }
        }

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderProjectile(
        grenade: ArcPulseGrenadeEntity,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        shader: ShaderInstance,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val center = interpolatedPosition(grenade, partialTick)
        val age = grenade.tickCount + partialTick
        val breathing = 0.96f + sin(age * 0.83f) * 0.04f
        renderSphere(
            shader,
            center,
            camera,
            Vector3f(0.115f * breathing, 0.115f * breathing, 0.115f * breathing),
            0f,
            1.55f,
            worldTime,
            modelView,
            worldPose
        )
        renderSphere(
            shader,
            center,
            camera,
            Vector3f(0.23f * breathing, 0.20f * breathing, 0.23f * breathing),
            0f,
            0.32f,
            worldTime + 2.7f,
            modelView,
            worldPose
        )

        val velocity = grenade.deltaMovement
        if (velocity.lengthSqr() <= 1.0e-5) return
        val backward = velocity.normalize().scale(-1.0)
        repeat(2) { layer ->
            val inner = layer == 1
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
            )
            var previous = center.add(backward.scale(0.03))
            repeat(TRAIL_SEGMENTS) { segment ->
                val t = (segment + 1f) / TRAIL_SEGMENTS
                val phase = age * 1.15f + segment * 1.73f + grenade.id * 0.31f
                val side = Vec3(-backward.z, 0.0, backward.x)
                val current = center.add(backward.scale(0.92 * t)).add(
                    side.scale(sin(phase.toDouble()) * 0.035 * (1.0 - t))
                ).add(0.0, sin((phase * 0.71f).toDouble()) * 0.022, 0.0)
                val nearFade = 1f - segment.toFloat() / TRAIL_SEGMENTS
                val farFade = 1f - (segment + 1f) / TRAIL_SEGMENTS
                emitRibbon(
                    buffer,
                    previous,
                    current,
                    camera,
                    (if (inner) 0.015f else 0.065f) * nearFade,
                    (if (inner) 0.004f else 0.018f) * farFade,
                    if (inner) 226 else 48,
                    if (inner) 252 else 137,
                    255,
                    ((if (inner) 224f else 82f) * nearFade).toInt()
                )
                previous = current
            }
            drawPositionColor(buffer.build(), modelView, worldPose)
        }
    }

    private fun renderAnchor(
        grenade: ArcPulseGrenadeEntity,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        coreShader: ShaderInstance,
        fieldShader: ShaderInstance,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val center = interpolatedPosition(grenade, partialTick).add(0.0, 0.075, 0.0)
        val age = grenade.tickCount + partialTick
        val breathing = 0.95f + sin(age * 0.39f + grenade.id * 0.7f) * 0.05f

        // A dim, broken ground pool makes the field spatially present without
        // drawing the damage radius as a clean ring.
        renderDisc(
            fieldShader,
            center.add(0.0, -0.058, 0.0),
            camera,
            0.92f * breathing,
            0f,
            0f,
            0.22f,
            worldTime,
            modelView,
            worldPose
        )

        renderSphere(
            coreShader,
            center,
            camera,
            Vector3f(0.105f, 0.095f, 0.105f) * breathing,
            0f,
            1.72f,
            worldTime,
            modelView,
            worldPose
        )
        renderSphere(
            coreShader,
            center,
            camera,
            Vector3f(0.23f, 0.17f, 0.23f) * breathing,
            0f,
            0.37f,
            worldTime + 1.9f,
            modelView,
            worldPose
        )
        renderSphere(
            coreShader,
            center,
            camera,
            Vector3f(0.38f, 0.22f, 0.38f) * breathing,
            1f,
            0.095f,
            worldTime + 4.3f,
            modelView,
            worldPose
        )

        renderAnchorFilaments(grenade, center, camera, age, modelView, worldPose)

        val pulseAge = grenade.pulseVisualAge(partialTick)
        if (pulseAge < 0f) return
        renderPulse(
            grenade,
            center,
            camera,
            pulseAge,
            worldTime,
            coreShader,
            fieldShader,
            modelView,
            worldPose
        )
    }

    private fun renderPulse(
        grenade: ArcPulseGrenadeEntity,
        center: Vec3,
        camera: Vec3,
        pulseAge: Float,
        worldTime: Float,
        coreShader: ShaderInstance,
        fieldShader: ShaderInstance,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        // The plasma front visibly travels out from the anchor. A cubic ease
        // made the shell appear almost complete on its first readable frame;
        // smoothstep keeps the early radius small while still reaching the
        // Destiny-sized hemisphere in about two ticks. The soft shell dies
        // before the material debris and ground light, matching the reference
        // sequence instead of leaving a transparent bubble behind.
        val expansion = smoothStep01(pulseAge / 2.0f)
        val attack = smoothStep01(pulseAge / 0.55f)
        val domeEnvelope = attack * (1f - smoothStep01((pulseAge - 1.10f) / 4.65f)).pow(1.16f)
        val plasmaEnvelope = attack * (1f - smoothStep01((pulseAge - 1.45f) / 6.15f)).pow(1.08f)
        val groundEnvelope = attack * (1f - smoothStep01((pulseAge - 2.25f) / 8.25f)).pow(1.04f)
        val particleEnvelope = attack * (1f - smoothStep01((pulseAge - 6.5f) / 4.2f))
        if (particleEnvelope <= 0.002f) return
        val firstPulseBoost = if (grenade.pulseVisualSequence() <= 1) 1.35f else 1f

        // One-frame-class core punch, followed immediately by readable detail.
        val flashFade = 1f - smoothStep01(pulseAge / 0.72f)
        renderSphere(
            coreShader,
            center.add(0.0, 0.035, 0.0),
            camera,
            Vector3f(
                Mth.lerp(expansion, 0.15f, 0.78f),
                Mth.lerp(expansion, 0.13f, 0.68f),
                Mth.lerp(expansion, 0.15f, 0.78f)
            ),
            0f,
            1.35f * flashFade * firstPulseBoost,
            worldTime,
            modelView,
            worldPose
        )

        // The impact volume is an actual upper hemisphere whose equator stays
        // on the terrain. It is never an elevated full sphere or an ellipsoid.
        val radius = Mth.lerp(expansion, 0.10f, 3.55f)
        val hemisphereCenter = center.add(0.0, -0.035, 0.0)
        setAlphaBlend()
        renderSphere(
            coreShader,
            hemisphereCenter,
            camera,
            Vector3f(radius, radius, radius),
            2f,
            0.78f * domeEnvelope * firstPulseBoost,
            worldTime + grenade.pulseVisualSequence() * 2.31f,
            modelView,
            worldPose,
            hemisphere = true
        )
        renderSphere(
            coreShader,
            hemisphereCenter.add(0.0, 0.015, 0.0),
            camera,
            Vector3f(radius * 0.70f, radius * 0.70f, radius * 0.70f),
            2f,
            0.34f * domeEnvelope,
            worldTime + 5.8f,
            modelView,
            worldPose,
            hemisphere = true
        )
        setAdditiveBlend()

        renderPulsePlasmaMasses(
            grenade,
            center,
            camera,
            pulseAge,
            expansion,
            plasmaEnvelope * firstPulseBoost,
            fieldShader,
            worldTime,
            modelView,
            worldPose
        )

        // Ground response is a diffuse consequence of the blast, never the
        // main expanding silhouette and never a clean damage-radius ring.
        renderDisc(
            fieldShader,
            center.add(0.0, -0.052, 0.0),
            camera,
            3.55f * expansion.coerceAtLeast(0.04f),
            0f,
            0f,
            0.46f * groundEnvelope * firstPulseBoost,
            worldTime,
            modelView,
            worldPose
        )
        renderDisc(
            fieldShader,
            center.add(0.0, -0.037, 0.0),
            camera,
            1.35f * expansion.coerceAtLeast(0.04f),
            0f,
            0f,
            0.52f * groundEnvelope,
            worldTime + 3.7f,
            modelView,
            worldPose
        )

        renderImpactStreaks(
            grenade,
            center,
            camera,
            pulseAge,
            flashFade * firstPulseBoost,
            modelView,
            worldPose
        )

        renderPulseFilaments(
            grenade,
            center,
            camera,
            pulseAge,
            expansion,
            plasmaEnvelope,
            modelView,
            worldPose
        )
        renderBallisticDebris(
            grenade,
            center,
            camera,
            pulseAge,
            particleEnvelope * firstPulseBoost,
            fieldShader,
            worldTime,
            modelView,
            worldPose
        )
    }

    private fun renderPulsePlasmaMasses(
        grenade: ArcPulseGrenadeEntity,
        center: Vec3,
        camera: Vec3,
        pulseAge: Float,
        expansion: Float,
        envelope: Float,
        shader: ShaderInstance,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (envelope <= 0.002f) return
        val sequence = grenade.pulseVisualSequence()
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)

        // Interior point cloud: the same soft radial/noise primitive used by
        // the GPU preview, not solid POSITION_COLOR diamonds.
        repeat(72) { particle ->
            val seed = grenade.id * 463 + sequence * 2069 + particle * 127
            val heightRatio = hash01(seed + 1).pow(0.82f)
            val radialRatio = sqrt((1f - heightRatio * heightRatio).coerceAtLeast(0f))
            val angle = hash01(seed + 3) * Mth.TWO_PI
            val fill = 0.22f + hash01(seed + 5) * 0.76f
            val radius = 3.38f * fill * expansion
            val point = center.add(
                cos(angle.toDouble()) * radialRatio * radius,
                0.03 + heightRatio * radius,
                sin(angle.toDouble()) * radialRatio * radius
            )
            val size = (0.10f + hash01(seed + 7) * 0.20f) * (0.28f + expansion * 0.72f)
            emitFieldBillboard(
                buffer,
                point,
                camera,
                size * (0.78f + hash01(seed + 9) * 0.54f),
                size,
                if (hash01(seed + 11) > 0.30f) 58 else 143,
                if (hash01(seed + 11) > 0.30f) 143 else 234,
                255,
                (196f * envelope * (0.62f + hash01(seed + 13) * 0.38f)).toInt().coerceIn(0, 255)
            )
        }

        // A few larger low plasma masses make the ground edge readable
        // without radial ribbons or a ring outline.
        repeat(PULSE_PLASMA_TONGUES) { mass ->
            val seed = grenade.id * 557 + sequence * 1877 + mass * 113
            val angle = hash01(seed + 1) * Mth.TWO_PI
            val reach = (0.35f + hash01(seed + 3) * 2.75f) * expansion
            val size = (0.22f + hash01(seed + 5) * 0.42f) * (0.24f + expansion * 0.76f)
            val point = center.add(
                cos(angle.toDouble()) * reach,
                0.06 + size * (0.26 + hash01(seed + 7) * 0.22),
                sin(angle.toDouble()) * reach
            )
            emitFieldBillboard(
                buffer,
                point,
                camera,
                size * (1.10f + hash01(seed + 9) * 0.60f),
                size * (0.72f + hash01(seed + 11) * 0.48f),
                if (mass % 3 == 0) 143 else 58,
                if (mass % 3 == 0) 234 else 143,
                255,
                (218f * envelope * (0.72f + hash01(seed + 13) * 0.28f)).toInt().coerceIn(0, 255)
            )
        }
        drawFieldQuads(shader, buffer.build(), worldTime, 2.35f, modelView, worldPose)
    }

    private fun renderImpactStreaks(
        grenade: ArcPulseGrenadeEntity,
        center: Vec3,
        camera: Vec3,
        pulseAge: Float,
        intensity: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (intensity <= 0.002f) return
        repeat(2) { layer ->
            val inner = layer == 1
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
            repeat(26) { streak ->
                val seed = grenade.id * 733 + grenade.pulseVisualSequence() * 1999 + streak * 61
                val angle = hash01(seed + 1) * Mth.TWO_PI
                val lift = -0.10f + hash01(seed + 3) * 0.82f
                val direction = Vec3(cos(angle.toDouble()), lift.toDouble(), sin(angle.toDouble())).normalize()
                val start = 0.15f + hash01(seed + 5) * 0.34f
                val length = (0.68f + hash01(seed + 7) * 1.82f) * (0.28f + smoothStep01(pulseAge / 0.70f) * 0.72f)
                emitRibbon(
                    buffer,
                    center.add(0.0, 0.20, 0.0).add(direction.scale(start.toDouble())),
                    center.add(0.0, 0.20, 0.0).add(direction.scale((start + length).toDouble())),
                    camera,
                    if (inner) 0.009f else 0.052f,
                    if (inner) 0.002f else 0.010f,
                    if (inner) 236 else 54,
                    if (inner) 254 else 151,
                    255,
                    ((if (inner) 245f else 104f) * intensity).toInt().coerceIn(0, 255)
                )
            }
            drawPositionColor(buffer.build(), modelView, worldPose)
        }
    }

    private fun renderAnchorFilaments(
        grenade: ArcPulseGrenadeEntity,
        center: Vec3,
        camera: Vec3,
        age: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val timeSlice = floor(age / 2.0f).toInt()
        repeat(2) { layer ->
            val inner = layer == 1
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
            )
            repeat(5) filamentLoop@ { filament ->
                val seed = grenade.id * 97 + timeSlice * 31 + filament * 17
                if (hash01(seed) < 0.39f) return@filamentLoop
                val angle = hash01(seed + 3) * Mth.TWO_PI
                val radius = 0.16f + hash01(seed + 7) * 0.42f
                val from = center.add(
                    cos(angle.toDouble()) * radius * 0.35,
                    -0.015 + hash01(seed + 11) * 0.10,
                    sin(angle.toDouble()) * radius * 0.35
                )
                val to = center.add(
                    cos((angle + (hash01(seed + 13) - 0.5f) * 0.9f).toDouble()) * radius,
                    0.015 + hash01(seed + 19) * 0.24,
                    sin((angle + (hash01(seed + 13) - 0.5f) * 0.9f).toDouble()) * radius
                )
                emitRibbon(
                    buffer,
                    from,
                    to,
                    camera,
                    if (inner) 0.006f else 0.028f,
                    if (inner) 0.002f else 0.008f,
                    if (inner) 232 else 48,
                    if (inner) 253 else 138,
                    255,
                    if (inner) 205 else 64
                )
            }
            drawPositionColor(buffer.build(), modelView, worldPose)
        }
    }

    private fun renderPulseFilaments(
        grenade: ArcPulseGrenadeEntity,
        center: Vec3,
        camera: Vec3,
        pulseAge: Float,
        expansion: Float,
        envelope: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val sequence = grenade.pulseVisualSequence()
        val redrawSlice = floor(pulseAge / 1.25f).toInt()
        repeat(2) { layer ->
            val inner = layer == 1
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
            )
            repeat(PULSE_FILAMENTS) { filament ->
                val seed = grenade.id * 211 + sequence * 101 + redrawSlice * 83 + filament * 37
                val angle = hash01(seed) * Mth.TWO_PI
                val elevation = -0.08f + hash01(seed + 2) * 0.82f
                val horizontal = cos(elevation.toDouble())
                val direction = Vec3(
                    cos(angle.toDouble()) * horizontal,
                    sin(elevation.toDouble()),
                    sin(angle.toDouble()) * horizontal
                ).normalize()
                val tangent = Vec3(-direction.z, 0.0, direction.x)
                val startRadius = 0.18 + hash01(seed + 3) * 0.58
                val length = (0.72 + hash01(seed + 5) * 1.65) * expansion
                val segmentCount = 4 + filament % 3
                var previous = center.add(0.0, 0.42, 0.0).add(direction.scale(startRadius))
                repeat(segmentCount) { segment ->
                    val t = (segment + 1f) / segmentCount
                    val lateral = (hash01(seed + segment * 13 + 17) - 0.5f) *
                        (0.16 + length * 0.075) * sin(PI * t)
                    val lift = hash01(seed + segment * 19 + 23) * 0.12 + sin(PI * t) * 0.085
                    val current = center.add(0.0, 0.42, 0.0)
                        .add(direction.scale(startRadius + length * t))
                        .add(tangent.scale(lateral))
                        .add(0.0, lift, 0.0)
                    val widthEnvelope = sin(PI * t).toFloat().coerceAtLeast(0.16f)
                    emitRibbon(
                        buffer,
                        previous,
                        current,
                        camera,
                        (if (inner) 0.008f else 0.043f) * widthEnvelope,
                        (if (inner) 0.004f else 0.019f) * widthEnvelope,
                        if (inner) 235 else 42,
                        if (inner) 254 else 132,
                        255,
                        ((if (inner) 238f else 88f) * envelope *
                            (0.72f + sin(pulseAge * 1.9f + filament) * 0.18f)).toInt().coerceIn(0, 255)
                    )
                    previous = current
                }
            }
            drawPositionColor(buffer.build(), modelView, worldPose)
        }
    }

    private fun renderBallisticDebris(
        grenade: ArcPulseGrenadeEntity,
        center: Vec3,
        camera: Vec3,
        pulseAge: Float,
        envelope: Float,
        fieldShader: ShaderInstance,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val sequence = grenade.pulseVisualSequence()
        repeat(2) { layer ->
            val inner = layer == 1
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
            )
            repeat(PULSE_DEBRIS) debrisLoop@ { particle ->
                val seed = grenade.id * 313 + sequence * 3251 + particle * 149
                val birth = hash01(seed + 1) * 1.7f
                val life = 5.2f + hash01(seed + 3) * 5.4f
                val t = (pulseAge - birth) / life
                if (t !in 0f..1f) return@debrisLoop
                val angle = hash01(seed + 5) * Mth.TWO_PI
                val travel = 1.45f + hash01(seed + 7) * 3.15f
                val arcScale = 1.05f + hash01(seed + 11) * 1.65f
                val current = debrisPoint(center, angle, travel, arcScale, t)
                val previous = debrisPoint(center, angle, travel, arcScale, (t - 0.022f).coerceAtLeast(0f))
                val fade = 1f - smoothStep01((t - 0.72f) / 0.28f)
                emitRibbon(
                    buffer,
                    previous,
                    current,
                    camera,
                    if (inner) 0.008f else 0.026f,
                    if (inner) 0.004f else 0.012f,
                    if (inner) 150 else 42,
                    if (inner) 241 else 128,
                    255,
                    ((if (inner) 210f else 72f) * fade * envelope).toInt().coerceIn(0, 255)
                )
            }
            drawPositionColor(buffer.build(), modelView, worldPose)
        }

        val particles = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        repeat(PULSE_DEBRIS) debrisLoop@ { particle ->
            val seed = grenade.id * 313 + sequence * 3251 + particle * 149
            val birth = hash01(seed + 1) * 1.7f
            val life = 5.2f + hash01(seed + 3) * 5.4f
            val t = (pulseAge - birth) / life
            if (t !in 0f..1f) return@debrisLoop
            val angle = hash01(seed + 5) * Mth.TWO_PI
            val travel = 1.45f + hash01(seed + 7) * 3.15f
            val arcScale = 1.05f + hash01(seed + 11) * 1.65f
            val point = debrisPoint(center, angle, travel, arcScale, t)
            val fade = 1f - smoothStep01((t - 0.72f) / 0.28f)
            val heavy = hash01(seed + 23) > 0.74f
            val size = if (heavy) {
                0.105f + hash01(seed + 17) * 0.135f
            } else {
                0.040f + hash01(seed + 17) * 0.085f
            }
            emitFieldBillboard(
                particles,
                point,
                camera,
                size,
                size * (1.0f + hash01(seed + 29) * 0.72f),
                if (hash01(seed + 19) > 0.28f) 58 else 143,
                if (hash01(seed + 19) > 0.28f) 143 else 234,
                255,
                (232f * fade * envelope).toInt().coerceIn(0, 255)
            )
        }
        drawFieldQuads(fieldShader, particles.build(), worldTime + 3.1f, 2.65f, modelView, worldPose)
    }

    private fun debrisPoint(center: Vec3, angle: Float, travel: Float, arcScale: Float, progress: Float): Vec3 {
        val radial = travel * easeOutCubic(progress)
        val arc = arcScale * 4f * progress * (1f - progress)
        return center.add(
            cos(angle.toDouble()) * radial,
            0.055 + arc,
            sin(angle.toDouble()) * radial
        )
    }

    private fun renderSphere(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        scale: Vector3f,
        mode: Float,
        opacity: Float,
        time: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        hemisphere: Boolean = false
    ) {
        if (scale.x <= 0.001f || scale.y <= 0.001f || scale.z <= 0.001f || opacity <= 0.001f) return
        modelView.pushMatrix()
        modelView.mul(worldPose)
        modelView.translate(
            (center.x - camera.x).toFloat(),
            (center.y - camera.y).toFloat(),
            (center.z - camera.z).toFloat()
        )
        modelView.scale(scale.x, scale.y, scale.z)
        RenderSystem.applyModelViewMatrix()

        shader.getUniform("CameraPos")?.set(
            ((camera.x - center.x) / scale.x).toFloat(),
            ((camera.y - center.y) / scale.y).toFloat(),
            ((camera.z - center.z) / scale.z).toFloat()
        )
        shader.getUniform("Time")?.set(time * 0.055f)
        shader.getUniform("Mode")?.set(mode)
        shader.getUniform("Opacity")?.set(opacity)
        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.TRIANGLES,
            DefaultVertexFormat.POSITION_COLOR
        )
        if (hemisphere) addHemisphere(buffer) else addSphere(buffer)
        RenderSystem.setShader { shader }
        BufferUploader.drawWithShader(buffer.buildOrThrow())

        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun renderDisc(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        radius: Float,
        mode: Float,
        progress: Float,
        opacity: Float,
        time: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (radius <= 0.001f || opacity <= 0.001f) return
        val relative = center.subtract(camera)
        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.TRIANGLES,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        repeat(DISC_SEGMENTS) { segment ->
            val angle0 = segment.toFloat() / DISC_SEGMENTS * Mth.TWO_PI
            val angle1 = (segment + 1).toFloat() / DISC_SEGMENTS * Mth.TWO_PI
            buffer.addVertex(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
                .setUv(0.5f, 0.5f).setColor(255, 255, 255, 255)
            buffer.addVertex(
                (relative.x + cos(angle0.toDouble()) * radius).toFloat(),
                relative.y.toFloat(),
                (relative.z + sin(angle0.toDouble()) * radius).toFloat()
            ).setUv(0.5f + Mth.cos(angle0) * 0.5f, 0.5f + Mth.sin(angle0) * 0.5f)
                .setColor(255, 255, 255, 255)
            buffer.addVertex(
                (relative.x + cos(angle1.toDouble()) * radius).toFloat(),
                relative.y.toFloat(),
                (relative.z + sin(angle1.toDouble()) * radius).toFloat()
            ).setUv(0.5f + Mth.cos(angle1) * 0.5f, 0.5f + Mth.sin(angle1) * 0.5f)
                .setColor(255, 255, 255, 255)
        }
        shader.getUniform("Time")?.set(time * 0.055f)
        shader.getUniform("Mode")?.set(mode)
        shader.getUniform("Progress")?.set(progress)
        shader.getUniform("Opacity")?.set(opacity)
        modelView.pushMatrix()
        modelView.mul(worldPose)
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setShader { shader }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun drawPositionColor(
        mesh: com.mojang.blaze3d.vertex.MeshData?,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (mesh == null) return
        modelView.pushMatrix()
        modelView.mul(worldPose)
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        BufferUploader.drawWithShader(mesh)
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun drawFieldQuads(
        shader: ShaderInstance,
        mesh: com.mojang.blaze3d.vertex.MeshData?,
        time: Float,
        opacity: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (mesh == null) return
        shader.getUniform("Time")?.set(time * 0.055f)
        shader.getUniform("Mode")?.set(0f)
        shader.getUniform("Progress")?.set(0f)
        shader.getUniform("Opacity")?.set(opacity)
        modelView.pushMatrix()
        modelView.mul(worldPose)
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setShader { shader }
        BufferUploader.drawWithShader(mesh)
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun emitRibbon(
        buffer: BufferBuilder,
        from: Vec3,
        to: Vec3,
        camera: Vec3,
        fromHalfWidth: Float,
        toHalfWidth: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val direction = to.subtract(from)
        val midpoint = from.add(to).scale(0.5)
        val side = direction.cross(camera.subtract(midpoint)).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(1.0, 0.0, 0.0)
        }
        val fromSide = side.scale(fromHalfWidth.toDouble())
        val toSide = side.scale(toHalfWidth.toDouble())
        val a = from.subtract(camera).add(fromSide)
        val b = from.subtract(camera).subtract(fromSide)
        val c = to.subtract(camera).subtract(toSide)
        val d = to.subtract(camera).add(toSide)
        buffer.addVertex(a.x.toFloat(), a.y.toFloat(), a.z.toFloat()).setColor(red, green, blue, alpha)
        buffer.addVertex(b.x.toFloat(), b.y.toFloat(), b.z.toFloat()).setColor(red, green, blue, alpha)
        buffer.addVertex(c.x.toFloat(), c.y.toFloat(), c.z.toFloat()).setColor(red, green, blue, alpha)
        buffer.addVertex(d.x.toFloat(), d.y.toFloat(), d.z.toFloat()).setColor(red, green, blue, alpha)
    }

    private fun emitFieldBillboard(
        buffer: BufferBuilder,
        center: Vec3,
        camera: Vec3,
        halfWidth: Float,
        halfHeight: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val view = camera.subtract(center).let { if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(0.0, 0.0, 1.0) }
        val right = view.cross(Vec3(0.0, 1.0, 0.0)).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(1.0, 0.0, 0.0)
        }
        val up = right.cross(view).normalize()
        val relative = center.subtract(camera)
        val horizontal = right.scale(halfWidth.toDouble())
        val vertical = up.scale(halfHeight.toDouble())
        val topLeft = relative.subtract(horizontal).add(vertical)
        val topRight = relative.add(horizontal).add(vertical)
        val bottomRight = relative.add(horizontal).subtract(vertical)
        val bottomLeft = relative.subtract(horizontal).subtract(vertical)
        buffer.addVertex(topLeft.x.toFloat(), topLeft.y.toFloat(), topLeft.z.toFloat())
            .setUv(0f, 0f).setColor(red, green, blue, alpha)
        buffer.addVertex(topRight.x.toFloat(), topRight.y.toFloat(), topRight.z.toFloat())
            .setUv(1f, 0f).setColor(red, green, blue, alpha)
        buffer.addVertex(bottomRight.x.toFloat(), bottomRight.y.toFloat(), bottomRight.z.toFloat())
            .setUv(1f, 1f).setColor(red, green, blue, alpha)
        buffer.addVertex(bottomLeft.x.toFloat(), bottomLeft.y.toFloat(), bottomLeft.z.toFloat())
            .setUv(0f, 1f).setColor(red, green, blue, alpha)
    }

    private fun setAdditiveBlend() {
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
    }

    private fun setAlphaBlend() {
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
    }

    private fun addSphere(buffer: BufferBuilder) {
        for (latitude in 0 until LATITUDE_SEGMENTS) {
            val theta0 = (latitude.toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            val theta1 = ((latitude + 1).toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            for (longitude in 0 until LONGITUDE_SEGMENTS) {
                val phi0 = longitude.toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                val phi1 = (longitude + 1).toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                addSphereTriangle(buffer, theta0, phi0, theta1, phi0, theta1, phi1)
                addSphereTriangle(buffer, theta0, phi0, theta1, phi1, theta0, phi1)
            }
        }
    }

    private fun addHemisphere(buffer: BufferBuilder) {
        for (latitude in 0 until LATITUDE_SEGMENTS) {
            val theta0 = latitude.toFloat() / LATITUDE_SEGMENTS * Mth.HALF_PI
            val theta1 = (latitude + 1).toFloat() / LATITUDE_SEGMENTS * Mth.HALF_PI
            for (longitude in 0 until LONGITUDE_SEGMENTS) {
                val phi0 = longitude.toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                val phi1 = (longitude + 1).toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                addSphereTriangle(buffer, theta0, phi0, theta1, phi0, theta1, phi1)
                addSphereTriangle(buffer, theta0, phi0, theta1, phi1, theta0, phi1)
            }
        }
    }

    private fun addSphereTriangle(
        buffer: BufferBuilder,
        thetaA: Float,
        phiA: Float,
        thetaB: Float,
        phiB: Float,
        thetaC: Float,
        phiC: Float
    ) {
        addSphereVertex(buffer, thetaA, phiA)
        addSphereVertex(buffer, thetaB, phiB)
        addSphereVertex(buffer, thetaC, phiC)
    }

    private fun addSphereVertex(buffer: BufferBuilder, theta: Float, phi: Float) {
        val cosTheta = Mth.cos(theta)
        buffer.addVertex(
            cosTheta * Mth.cos(phi),
            Mth.sin(theta),
            cosTheta * Mth.sin(phi)
        ).setColor(255, 255, 255, 255)
    }

    private fun interpolatedPosition(entity: Entity, partialTick: Float): Vec3 = Vec3(
        Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x),
        Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y),
        Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)
    )

    private fun hash01(seed: Int): Float {
        val value = sin(seed * 12.9898 + 78.233) * 43758.5453
        return (value - floor(value)).toFloat()
    }

    private fun smoothStep01(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun easeOutCubic(value: Float): Float {
        val inverse = 1f - value.coerceIn(0f, 1f)
        return 1f - inverse * inverse * inverse
    }

    private operator fun Vector3f.times(value: Float): Vector3f = Vector3f(x * value, y * value, z * value)
}
