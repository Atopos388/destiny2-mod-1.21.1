package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.VoidGrenadeEntity
import atopos.destiny2.common.entity.VoidVortexEntity
import atopos.destiny2.client.combat.HunterMeleeFirstPersonClient
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
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Video-matched presentation for the Void Hunter vortex grenade.
 *
 * The persistent effect is a complete sphere whose centre intersects the
 * ground. Terrain depth naturally hides its lower portion. Three procedural
 * sphere passes provide the dark volume, broad internal flow and broken
 * surface highlights. Loose fast-moving fog orbits outside that shell while
 * the white-violet singularity remains deliberately small.
 */
object VoidGrenadeWorldRenderer {
    private const val QUERY_RANGE = 96.0
    private const val STABLE_RADIUS = 2.85f
    private const val LATITUDE_SEGMENTS = 18
    private const val LONGITUDE_SEGMENTS = 28
    private const val FORMATION_START_TICKS = 5f
    private const val FORMATION_END_TICKS = 12f
    private const val IMPACT_FLASH_TICKS = 2.4f
    private const val IMPACT_BURST_TICKS = 7f
    private const val IMPACT_MOTES = 52
    private const val STABLE_MOTES = 72
    private const val CORONA_PLUMES = 11
    private const val CORONA_MOTES_PER_PLUME = 13
    private const val ACCRETION_DISC_SEGMENTS = 96
    private const val ACCRETION_DISC_LAYERS = 7
    private const val ACCRETION_SPARKS = 14
    private const val GROUND_COVER_MOTES = 88
    private const val COLLAPSE_TICKS = 5f

    @Volatile
    private var sphereShader: ShaderInstance? = null
    @Volatile
    private var moteShader: ShaderInstance? = null
    @Volatile
    private var ringShader: ShaderInstance? = null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_vortex_sphere"),
                DefaultVertexFormat.POSITION_COLOR
            ) { shader -> sphereShader = shader }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_vortex_mote"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { shader -> moteShader = shader }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_vortex_ring"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { shader -> ringShader = shader }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        val shader = sphereShader ?: return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val bounds = AABB(camera, camera).inflate(QUERY_RANGE)
        val grenades = context.world().getEntitiesOfClass(
            VoidGrenadeEntity::class.java,
            bounds
        ) { it.isAlive }
        val vortices = context.world().getEntitiesOfClass(
            VoidVortexEntity::class.java,
            bounds
        ) { it.isAlive }
        val heldGrenade = Minecraft.getInstance().player?.let { player ->
            HunterMeleeFirstPersonClient.heldGrenadePosition(player, partialTick)
        }
        if (grenades.isEmpty() && vortices.isEmpty() && heldGrenade == null) return

        RenderSystem.enableBlend()
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()

        val modelView = RenderSystem.getModelViewStack()
        val worldPose = poseStack.last().pose()
        val worldTime = context.world().gameTime + partialTick

        // Dark and misty passes must retain background contrast.
        RenderSystem.defaultBlendFunc()
        vortices.forEach { renderVortexVolume(it, camera, partialTick, worldTime, modelView, worldPose, shader) }

        // Only the singularity, broken shell highlights and impact debris glow additively.
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        grenades.forEach { renderProjectile(it, camera, partialTick, worldTime, modelView, worldPose, shader) }
        heldGrenade?.let { center ->
            val pulse = 0.96f + sin(worldTime * 0.72f) * 0.04f
            renderSphere(shader, center, camera, 0.16f * pulse, 3f, 1.0f, worldTime, modelView, worldPose)
            renderSphere(shader, center, camera, 0.27f * pulse, 2f, 0.30f, worldTime + 2.1f, modelView, worldPose)
        }
        vortices.forEach { renderVortexLight(it, camera, partialTick, worldTime, modelView, worldPose, shader) }

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderProjectile(
        grenade: VoidGrenadeEntity,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        shader: ShaderInstance
    ) {
        val center = interpolatedPosition(grenade, partialTick)
        val age = grenade.tickCount + partialTick
        val pulse = 0.94f + sin(age * 0.72f) * 0.06f
        renderSphere(shader, center, camera, 0.20f * pulse, 3f, 1.0f, worldTime, modelView, worldPose)
        renderSphere(shader, center, camera, 0.34f * pulse, 2f, 0.34f, worldTime + 2.1f, modelView, worldPose)

        val velocity = grenade.deltaMovement
        if (velocity.lengthSqr() <= 1.0e-5) return
        val backwards = velocity.normalize().scale(-1.0)
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        var previous = center
        repeat(6) { segment ->
            val t = (segment + 1f) / 6f
            val current = center.add(backwards.scale(0.95 * t)).add(
                0.0,
                sin(age * 0.84f - segment * 0.77f) * 0.025,
                0.0
            )
            emitRibbon(
                buffer,
                previous,
                current,
                camera,
                0.075f * (1f - segment / 6f),
                0.075f * (1f - (segment + 1f) / 6f),
                130,
                112,
                255,
                (150f * (1f - t * 0.82f)).toInt()
            )
            previous = current
        }
        drawPositionColor(buffer.build(), modelView, worldPose)
    }

    private fun renderVortexVolume(
        vortex: VoidVortexEntity,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        shader: ShaderInstance
    ) {
        val age = vortex.tickCount + partialTick
        val total = vortex.visualDuration().toFloat().coerceAtLeast(1f)
        val life = lifeEnvelope(age, total)
        val formation = formationScale(age)
        val scale = STABLE_RADIUS * formation * life.scale
        if (scale <= 0.01f) return

        val landing = interpolatedPosition(vortex, partialTick).add(0.0, 0.10, 0.0)
        val center = interpolatedPosition(vortex, partialTick).add(0.0, STABLE_RADIUS * 0.055, 0.0)
        renderSphere(shader, center, camera, scale * 0.94f, 0f, 0.98f * life.opacity, worldTime, modelView, worldPose)
        renderSphere(shader, center, camera, scale * 0.985f, 1f, 0.86f * life.opacity, worldTime + 5.7f, modelView, worldPose)
        moteShader?.let {
            renderCoronaFog(it, center, camera, scale, age, life.opacity, modelView, worldPose)
            renderGroundCover(it, landing, camera, scale, age, life.opacity, modelView, worldPose)
        }
    }

    private fun renderVortexLight(
        vortex: VoidVortexEntity,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        shader: ShaderInstance
    ) {
        val age = vortex.tickCount + partialTick
        val total = vortex.visualDuration().toFloat().coerceAtLeast(1f)
        val life = lifeEnvelope(age, total)
        val formation = formationScale(age)
        val radius = STABLE_RADIUS * formation * life.scale
        if (radius <= 0.01f) return

        val impact = interpolatedPosition(vortex, partialTick).add(0.0, 0.07, 0.0)
        val sphereCenter = impact.add(0.0, STABLE_RADIUS * 0.055, 0.0)

        // One continuous violet accretion ring, with no segmented or spiral
        // breakup. The original bright singularity remains unchanged.
        RenderSystem.defaultBlendFunc()
        ringShader?.let { continuousRingShader ->
            renderAccretionDisc(
                continuousRingShader,
                sphereCenter,
                camera,
                radius,
                age,
                life.opacity,
                modelView,
                worldPose
            )
        }

        // Keep layer three saturated purple. Standard alpha retains its colour;
        // additive blending made the previous version wash out toward white.
        renderSphere(shader, sphereCenter, camera, radius * 1.018f, 2f, 1.42f * life.opacity, worldTime, modelView, worldPose)

        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )

        // Restore the bright white-violet core and its two glow envelopes.
        val corePulse = 0.94f + sin(age * 0.24f) * 0.06f
        renderSphere(shader, sphereCenter, camera, radius * 0.19f * corePulse, 3f, 1.72f * life.opacity, worldTime + 1.2f, modelView, worldPose)
        renderSphere(shader, sphereCenter, camera, radius * 0.38f * corePulse, 3f, 0.38f * life.opacity, worldTime + 3.8f, modelView, worldPose)
        renderSphere(shader, sphereCenter, camera, radius * 0.56f * corePulse, 3f, 0.105f * life.opacity, worldTime + 6.4f, modelView, worldPose)

        moteShader?.let { softShader ->
            renderAccretionSparks(
                softShader,
                sphereCenter,
                camera,
                radius,
                age,
                life.opacity,
                modelView,
                worldPose
            )
            renderStableMotes(
                softShader,
                sphereCenter,
                sphereCenter,
                camera,
                radius,
                age,
                life.opacity,
                modelView,
                worldPose
            )
            renderImpact(shader, softShader, impact, camera, age, worldTime, modelView, worldPose)
        } ?: renderImpact(shader, null, impact, camera, age, worldTime, modelView, worldPose)
    }

    private fun renderImpact(
        shader: ShaderInstance,
        softShader: ShaderInstance?,
        center: Vec3,
        camera: Vec3,
        age: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (age >= IMPACT_BURST_TICKS) return
        val progress = Mth.clamp(age / IMPACT_BURST_TICKS, 0f, 1f)
        val fade = 1f - progress

        if (age < IMPACT_FLASH_TICKS) {
            val flash = 1f - age / IMPACT_FLASH_TICKS
            renderSphere(shader, center, camera, Mth.lerp(1f - flash, 0.18f, 1.15f), 3f, flash * 1.8f, worldTime, modelView, worldPose)
        }

        // The large translucent shock sphere exists only during formation.
        val shockEnvelope = sin(progress * Mth.PI).coerceAtLeast(0f)
        renderSphere(
            shader,
            center.add(0.0, STABLE_RADIUS * 0.055, 0.0),
            camera,
            Mth.lerp(progress, STABLE_RADIUS * 0.20f, STABLE_RADIUS * 1.35f),
            4f,
            shockEnvelope * 0.72f,
            worldTime,
            modelView,
            worldPose
        )

        if (softShader != null) {
            renderImpactMotes(
                softShader,
                center,
                camera,
                progress,
                fade,
                modelView,
                worldPose
            )
        }
    }

    /**
     * Soft round motes replace the previous pointed radial ribbons. Their
     * positions form a volume rather than a crisp shell, so the impact has no
     * readable polygonal edge or spike silhouette.
     */
    private fun renderImpactMotes(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        progress: Float,
        fade: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        val eased = 1f - (1f - progress) * (1f - progress)
        repeat(IMPACT_MOTES) { mote ->
            val ySeed = ((mote * 37 + 11) % IMPACT_MOTES + 0.5f) / IMPACT_MOTES
            val y = -0.08 + ySeed * 1.08
            val horizontal = kotlin.math.sqrt((1.0 - y * y).coerceAtLeast(0.0))
            val angle = mote * 2.399963f + vortexSeed(mote)
            val direction = Vec3(
                cos(angle.toDouble()) * horizontal,
                y,
                sin(angle.toDouble()) * horizontal
            ).normalize()
            val distance = (0.18 + (2.15 + (mote % 7) * 0.19) * eased)
            val position = center.add(direction.scale(distance))
                .add(0.0, -progress * progress * 0.16, 0.0)
            val size = (0.055f + (mote % 6) * 0.011f) * (0.55f + fade * 0.45f)
            val brightness = 142 + (mote % 4) * 18
            addSoftMote(
                buffer,
                position,
                camera,
                size,
                brightness,
                102 + (mote % 3) * 20,
                255,
                (218f * fade).toInt()
            )
        }
        drawMoteBuffer(buffer.build(), shader, modelView, worldPose)
    }

    /**
     * A complete broad violet accretion ring around the original bright core.
     * Seven horizontal annuli form a shallow lens: the middle is dense and
     * broad while the upper and lower faces taper softly. Every layer remains
     * continuously filled; only its internal light moves.
     */
    private fun renderAccretionDisc(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        radius: Float,
        age: Float,
        lifeOpacity: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val formation = Mth.clamp((age - 7f) / 5f, 0f, 1f)
        val opacity = formation * lifeOpacity
        if (opacity <= 0.001f) return

        // Fixed world-horizontal X/Z plane. It is a true circle in world space;
        // camera perspective alone determines how elliptical it looks.
        val axisMajor = Vec3(1.0, 0.0, 0.0)
        val axisMinor = Vec3(0.0, 0.0, 1.0)
        val middleLayer = (ACCRETION_DISC_LAYERS - 1) * 0.5f
        val layerIndices = if (camera.y >= center.y) {
            0 until ACCRETION_DISC_LAYERS
        } else {
            (ACCRETION_DISC_LAYERS - 1 downTo 0)
        }

        for (layer in layerIndices) {
            val vertical = (layer - middleLayer) / middleLayer
            val profile = 1f - vertical * vertical
            val layerCenter = center.add(0.0, (vertical * radius * 0.09f).toDouble(), 0.0)
            val innerRadius = radius * (0.22f - profile * 0.015f)
            val outerRadius = radius * (1.07f + profile * 0.05f)
            val layerOpacity = opacity * (0.13f + profile * 0.20f)
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR
            )

            repeat(ACCRETION_DISC_SEGMENTS) { segment ->
                val u0 = segment.toFloat() / ACCRETION_DISC_SEGMENTS
                val u1 = (segment + 1f) / ACCRETION_DISC_SEGMENTS
                val angle0 = u0 * Mth.PI * 2f
                val angle1 = u1 * Mth.PI * 2f
                val inner0 = ellipsePoint(layerCenter, axisMajor, axisMinor, innerRadius, innerRadius, angle0)
                val outer0 = ellipsePoint(layerCenter, axisMajor, axisMinor, outerRadius, outerRadius, angle0)
                val inner1 = ellipsePoint(layerCenter, axisMajor, axisMinor, innerRadius, innerRadius, angle1)
                val outer1 = ellipsePoint(layerCenter, axisMajor, axisMinor, outerRadius, outerRadius, angle1)

                addMoteVertex(buffer, inner0, camera, u0, 0f, 255, 255, 255, 255)
                addMoteVertex(buffer, outer0, camera, u0, 1f, 255, 255, 255, 255)
                addMoteVertex(buffer, outer1, camera, u1, 1f, 255, 255, 255, 255)
                addMoteVertex(buffer, inner1, camera, u1, 0f, 255, 255, 255, 255)
            }

            shader.getUniform("Time")?.set(age * 0.05f + layer * 0.011f)
            shader.getUniform("Opacity")?.set(layerOpacity)
            drawMoteBuffer(buffer.build(), shader, modelView, worldPose)
        }
    }

    /**
     * Rare white-violet fragments orbit inside the horizontal disc. Each mote
     * has a narrow visibility window, so only a few hot fragments flare at any
     * one time instead of forming a permanent dotted ring.
     */
    private fun renderAccretionSparks(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        radius: Float,
        age: Float,
        lifeOpacity: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val formation = Mth.clamp((age - 9f) / 5f, 0f, 1f)
        val opacity = formation * lifeOpacity
        if (opacity <= 0.001f) return

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        repeat(ACCRETION_SPARKS) { spark ->
            val cycle = 17f + (spark % 5) * 3.1f
            val phase = ((age + spark * 6.73f) % cycle) / cycle
            val pulse = sin(phase * Mth.PI).coerceAtLeast(0f)
            val flare = pulse * pulse * pulse * pulse * pulse * pulse
            if (flare <= 0.015f) return@repeat

            val radialSeed = ((spark * 43 + 17) % 97) / 96f
            val orbitRadius = radius * (0.30f + radialSeed * 0.76f)
            val orbitSpeed = 0.105f + (spark % 4) * 0.011f
            val angle = spark * 2.399963f + age * orbitSpeed
            val vertical = ((spark * 29 % 11) - 5) / 5f
            val position = center.add(
                cos(angle.toDouble()) * orbitRadius,
                (vertical * radius * 0.045f).toDouble(),
                sin(angle.toDouble()) * orbitRadius
            )
            val glowSize = radius * (0.025f + (spark % 3) * 0.0035f)
            val alpha = (235f * flare * opacity).toInt()

            addSoftMote(buffer, position, camera, glowSize * 2.5f, 151, 92, 255, alpha / 3)
            addSoftMote(buffer, position, camera, glowSize, 255, 246, 255, alpha)
        }
        drawMoteBuffer(buffer.build(), shader, modelView, worldPose)
    }

    private fun ellipsePoint(
        center: Vec3,
        majorAxis: Vec3,
        minorAxis: Vec3,
        majorRadius: Float,
        minorRadius: Float,
        angle: Float
    ): Vec3 = center
        .add(majorAxis.scale(cos(angle.toDouble()) * majorRadius.toDouble()))
        .add(minorAxis.scale(sin(angle.toDouble()) * minorRadius.toDouble()))

    /**
     * Dense soft violet motes sit above the collision surface so the landing
     * point remains visibly filled instead of being depth-clipped into blocks.
     */
    private fun renderGroundCover(
        shader: ShaderInstance,
        landing: Vec3,
        camera: Vec3,
        radius: Float,
        age: Float,
        lifeOpacity: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val formation = Mth.clamp((age - 5f) / 6f, 0f, 1f)
        val opacity = formation * lifeOpacity
        if (opacity <= 0.001f) return

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        repeat(GROUND_COVER_MOTES) { mote ->
            val radialSeed = ((mote * 47 + 9) % GROUND_COVER_MOTES + 0.5f) / GROUND_COVER_MOTES
            val distance = kotlin.math.sqrt(radialSeed) * radius * 0.91f
            val direction = if (mote % 4 == 0) -1f else 1f
            val angle = mote * 2.399963f + age * (0.006f + (mote % 5) * 0.0008f) * direction
            val height = 0.035 + (mote % 5) * 0.014 +
                sin(age * 0.12f + mote * 0.79f) * 0.008
            val position = landing.add(
                cos(angle.toDouble()) * distance,
                height,
                sin(angle.toDouble()) * distance
            )
            val pulse = 0.72f + sin(age * 0.17f + mote * 1.31f) * 0.22f
            val halfSize = 0.045f + (mote % 7) * 0.009f
            addSoftMote(
                buffer,
                position,
                camera,
                halfSize,
                91 + (mote % 4) * 17,
                28 + (mote % 3) * 13,
                218 + (mote % 3) * 12,
                (142f * pulse * opacity).toInt()
            )
        }
        drawMoteBuffer(buffer.build(), shader, modelView, worldPose)
    }

    /**
     * Broad overlapping soft motes trace short magnetic arches that leave and
     * re-enter layer three. The arches rotate rapidly around the sphere like a
     * turbulent violet corona, without ribbon edges or a fourth spherical shell.
     */
    private fun renderCoronaFog(
        shader: ShaderInstance,
        sphereCenter: Vec3,
        camera: Vec3,
        radius: Float,
        age: Float,
        lifeOpacity: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val formation = Mth.clamp((age - IMPACT_BURST_TICKS + 1f) / 4f, 0f, 1f)
        val opacity = formation * lifeOpacity
        if (opacity <= 0.001f) return

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        repeat(CORONA_PLUMES) { plume ->
            val ySeed = ((plume * 7 + 3) % CORONA_PLUMES + 0.5f) / CORONA_PLUMES
            val cycle = 19f + (plume % 5) * 3.2f
            val lifePhase = ((age + plume * 5.37f) % cycle) / cycle
            val eruption = sin(lifePhase * Mth.PI).coerceAtLeast(0f)
            val latitudeSway = sin(age * 0.018f + plume * 1.17f) * 0.08f
            val latitude = (Mth.lerp(ySeed, -0.78f, 0.86f) + latitudeSway).coerceIn(-0.91f, 0.91f)
            val horizontal = kotlin.math.sqrt((1f - latitude * latitude).coerceAtLeast(0f))
            val directionSign = if (plume % 3 == 0) -1f else 1f
            val rotation = plume * 2.399963f +
                age * (0.009f + (plume % 4) * 0.0015f) * directionSign +
                sin(age * 0.026f + plume * 0.83f) * 0.13f
            val normal = Vec3(
                cos(rotation.toDouble()) * horizontal,
                latitude.toDouble(),
                sin(rotation.toDouble()) * horizontal
            ).normalize()
            val azimuthTangent = Vec3(-normal.z, 0.0, normal.x).normalize()
            val tangentTwist = sin(lifePhase * Mth.PI * 2f + plume * 0.91f) * 0.36f
            val tiltedTangent = azimuthTangent
                .add(Vec3(0.0, 0.28 + (plume % 4) * 0.07 + tangentTwist, 0.0))
                .normalize()
            val crossTangent = normal.cross(tiltedTangent).normalize()
            val archSpan = radius * (0.18 + (plume % 5) * 0.038 + eruption * 0.18)
            val archHeight = radius * (0.055 + eruption * (0.17 + (plume % 4) * 0.038))
            val flowPhase = age * (0.31f + (plume % 3) * 0.044f) + plume * 1.61f

            repeat(CORONA_MOTES_PER_PLUME) { node ->
                val t = node.toFloat() / (CORONA_MOTES_PER_PLUME - 1)
                val along = (t - 0.5f) * 2f
                val archBase = sin(t * Mth.PI).coerceAtLeast(0f)
                val breathing = 0.72f + sin(flowPhase + t * 5.3f) * 0.21f +
                    sin(flowPhase * 0.47f - t * 8.1f) * 0.12f
                val arch = (archBase * breathing).coerceAtLeast(0f)
                val alongWarp = along + sin(flowPhase * 0.82f + t * 4.4f) * 0.12f * archBase
                val sideways = sin(t * Mth.PI * 2.35f + flowPhase * 1.18f) *
                    radius * (0.031f + archBase * 0.038f) * eruption
                val radialFlutter = sin(flowPhase * 1.53f - t * 7.7f) *
                    radius * 0.035f * archBase * eruption
                val base = sphereCenter
                    .add(normal.scale(radius * 1.015))
                    .add(tiltedTangent.scale(alongWarp * archSpan))
                val position = base
                    .add(normal.scale(archHeight * arch + radialFlutter))
                    .add(crossTangent.scale(sideways.toDouble()))
                val growFront = Mth.clamp(lifePhase * 2.6f - t * 0.82f, 0f, 1f)
                val collapseTail = Mth.clamp((1f - lifePhase) * 3.1f - (1f - t) * 0.48f, 0f, 1f)
                val nodeEnvelope = archBase * growFront * collapseTail * eruption
                val travellingLight = 0.62f + sin(flowPhase * 2.4f - t * 11.6f) * 0.31f
                val halfSize = radius * (
                    0.064f + archBase * eruption * 0.042f +
                        sin(flowPhase * 0.73f + t * 6.8f) * 0.007f +
                        (plume % 3) * 0.005f
                    )
                val purple = 104 + (plume % 4) * 12
                addSoftMote(
                    buffer,
                    position,
                    camera,
                    halfSize,
                    purple,
                    24 + (plume % 3) * 9,
                    214 + (plume % 3) * 11,
                    (61f * nodeEnvelope * travellingLight * opacity).toInt()
                )
            }
        }
        drawMoteBuffer(buffer.build(), shader, modelView, worldPose)
    }

    /**
     * Dense particles repeatedly spawn between the flowing inner sphere and
     * the broken outer shell, move quickly on curved paths, and disappear into
     * the ground-level singularity before recycling.
     */
    private fun renderStableMotes(
        shader: ShaderInstance,
        sphereCenter: Vec3,
        singularity: Vec3,
        camera: Vec3,
        radius: Float,
        age: Float,
        lifeOpacity: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val formationFade = Mth.clamp((age - 8f) / 6f, 0f, 1f)
        val opacity = formationFade * lifeOpacity
        if (opacity <= 0.001f) return

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        repeat(STABLE_MOTES) { mote ->
            val cycle = 14f + (mote % 9) * 1.35f
            val phase = ((age + (mote * 13 % STABLE_MOTES) * 0.37f) % cycle) / cycle
            val ySeed = ((mote * 29 + 7) % STABLE_MOTES + 0.5f) / STABLE_MOTES
            val y = ySeed * 2.0 - 1.0
            val horizontal = kotlin.math.sqrt((1.0 - y * y).coerceAtLeast(0.0))
            val angle = mote * 2.399963f + age * (0.012f + (mote % 5) * 0.0013f)
            val direction = Vec3(
                cos(angle.toDouble()) * horizontal,
                y,
                sin(angle.toDouble()) * horizontal
            )
            val shellRadius = radius * (0.988f + (mote % 7) * 0.0045f)
            val start = sphereCenter.add(direction.scale(shellRadius.toDouble()))
            val tangent = Vec3(-direction.z, 0.18 + (mote % 4) * 0.04, direction.x).normalize()
            val inward = phase * phase * (3f - 2f * phase)
            val curved = start.scale(1.0 - inward)
                .add(singularity.scale(inward.toDouble()))
                .add(tangent.scale(radius * sin(phase * Mth.PI) * (0.10 + (mote % 4) * 0.018)))
            val envelope = sin(phase * Mth.PI).coerceAtLeast(0f)
            val size = 0.035f + (mote % 6) * 0.008f
            addSoftMote(
                buffer,
                curved,
                camera,
                size,
                132 + (mote % 4) * 15,
                82 + (mote % 5) * 13,
                255,
                (196f * envelope * opacity).toInt()
            )
        }
        drawMoteBuffer(buffer.build(), shader, modelView, worldPose)
    }

    private fun renderSphere(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        scale: Float,
        mode: Float,
        opacity: Float,
        time: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (scale <= 0.001f || opacity <= 0.001f) return
        modelView.pushMatrix()
        modelView.mul(worldPose)
        modelView.translate(
            (center.x - camera.x).toFloat(),
            (center.y - camera.y).toFloat(),
            (center.z - camera.z).toFloat()
        )
        modelView.scale(scale, scale, scale)
        RenderSystem.applyModelViewMatrix()

        val cameraObject = Vector3f(
            ((camera.x - center.x) / scale).toFloat(),
            ((camera.y - center.y) / scale).toFloat(),
            ((camera.z - center.z) / scale).toFloat()
        )
        shader.getUniform("CameraPos")?.set(cameraObject.x, cameraObject.y, cameraObject.z)
        shader.getUniform("Time")?.set(time * 0.05f)
        shader.getUniform("Mode")?.set(mode)
        shader.getUniform("Opacity")?.set(opacity)
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        addSphere(buffer)
        RenderSystem.setShader { shader }
        BufferUploader.drawWithShader(buffer.buildOrThrow())

        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun formationScale(age: Float): Float {
        if (age < FORMATION_START_TICKS) return 0.20f
        val progress = Mth.clamp(
            (age - FORMATION_START_TICKS) / (FORMATION_END_TICKS - FORMATION_START_TICKS),
            0f,
            1f
        )
        val smooth = progress * progress * (3f - 2f * progress)
        return Mth.lerp(smooth, 0.65f, 1f)
    }

    private fun lifeEnvelope(age: Float, total: Float): LifeEnvelope {
        val remaining = total - age
        if (remaining >= COLLAPSE_TICKS) return LifeEnvelope(1f, 1f)
        val progress = Mth.clamp(remaining / COLLAPSE_TICKS, 0f, 1f)
        return LifeEnvelope(
            scale = 0.12f + progress * 0.88f,
            opacity = progress * progress
        )
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

    private fun drawMoteBuffer(
        mesh: com.mojang.blaze3d.vertex.MeshData?,
        shader: ShaderInstance,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (mesh == null) return
        modelView.pushMatrix()
        modelView.mul(worldPose)
        RenderSystem.applyModelViewMatrix()
        RenderSystem.setShader { shader }
        BufferUploader.drawWithShader(mesh)
        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun addSoftMote(
        buffer: BufferBuilder,
        center: Vec3,
        camera: Vec3,
        halfSize: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val view = camera.subtract(center).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(0.0, 0.0, 1.0)
        }
        val right = Vec3(0.0, 1.0, 0.0).cross(view).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(1.0, 0.0, 0.0)
        }.scale(halfSize.toDouble())
        val up = view.cross(right.normalize()).normalize().scale(halfSize.toDouble())
        addMoteVertex(buffer, center.subtract(right).subtract(up), camera, 0f, 1f, red, green, blue, alpha)
        addMoteVertex(buffer, center.add(right).subtract(up), camera, 1f, 1f, red, green, blue, alpha)
        addMoteVertex(buffer, center.add(right).add(up), camera, 1f, 0f, red, green, blue, alpha)
        addMoteVertex(buffer, center.subtract(right).add(up), camera, 0f, 0f, red, green, blue, alpha)
    }

    private fun addMoteVertex(
        buffer: BufferBuilder,
        position: Vec3,
        camera: Vec3,
        u: Float,
        v: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val relative = position.subtract(camera)
        buffer.addVertex(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
            .setUv(u, v)
            .setColor(red, green, blue, alpha.coerceIn(0, 255))
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
        addVertex(buffer, from.add(fromSide), camera, red, green, blue, alpha)
        addVertex(buffer, from.subtract(fromSide), camera, red, green, blue, alpha)
        addVertex(buffer, to.subtract(toSide), camera, red, green, blue, 0)
        addVertex(buffer, to.add(toSide), camera, red, green, blue, 0)
    }

    private fun addVertex(
        buffer: BufferBuilder,
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

    private fun addSphere(buffer: BufferBuilder) {
        repeat(LATITUDE_SEGMENTS) { latitude ->
            val theta0 = (latitude.toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            val theta1 = ((latitude + 1).toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            repeat(LONGITUDE_SEGMENTS) { longitude ->
                val phi0 = longitude.toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                val phi1 = (longitude + 1).toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                addTriangle(buffer, theta0, phi0, theta1, phi0, theta1, phi1)
                addTriangle(buffer, theta0, phi0, theta1, phi1, theta0, phi1)
            }
        }
    }

    private fun addTriangle(
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
        buffer.addVertex(cosTheta * Mth.cos(phi), Mth.sin(theta), cosTheta * Mth.sin(phi))
            .setColor(255, 255, 255, 255)
    }

    private fun polar(center: Vec3, angle: Float, radius: Float, height: Float): Vec3 =
        center.add(cos(angle.toDouble()) * radius, height.toDouble(), sin(angle.toDouble()) * radius)

    private fun interpolatedPosition(entity: Entity, partialTick: Float): Vec3 =
        Vec3(
            Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x),
            Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y),
            Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)
        )

    private fun vortexSeed(ray: Int): Float = ((ray * 37 % 19) - 9) * 0.071f

    private data class LifeEnvelope(val scale: Float, val opacity: Float)
}
