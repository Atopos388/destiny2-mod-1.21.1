package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.SnareBombEntity
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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renderer-only presentation for the Void Hunter Snare Bomb.
 *
 * The server entity owns flight, deployment, arming, trigger detection,
 * combat effects and lifetime. The client reconstructs the impact ripple,
 * low-profile mine and soft nebula volume from synchronized state.
 */
object SnareBombWorldRenderer {
    private const val QUERY_RANGE = 64.0
    private const val DEVICE_LATITUDES = 10
    private const val DEVICE_LONGITUDES = 16
    private const val CLOUD_EMITTERS_NEAR = 72
    private const val CLOUD_EMITTERS_MID = 48
    private const val CLOUD_MOTES_NEAR = 64
    private const val CLOUD_MOTES_MID = 40
    private const val IMPACT_RIPPLE_TICKS = 9f
    private const val TRIGGER_FLASH_TICKS = 7f
    private const val CLOUD_FINAL_FADE_TICKS = 30f
    private const val CLOUD_TOP_HEIGHT = 5.0f
    private const val GOLDEN_ANGLE = 2.399963f

    @Volatile
    private var cloudShader: ShaderInstance? = null

    @Volatile
    private var ringShader: ShaderInstance? = null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "snare_bomb_cloud"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { shader -> cloudShader = shader }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "snare_bomb_ring"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { shader -> ringShader = shader }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val bombs = context.world().getEntitiesOfClass(
            SnareBombEntity::class.java,
            AABB(camera, camera).inflate(QUERY_RANGE)
        ) { it.isAlive }.sortedByDescending { it.position().distanceToSqr(camera) }
        if (bombs.isEmpty()) return

        val modelView = RenderSystem.getModelViewStack()
        val worldPose = poseStack.last().pose()
        val worldTime = context.world().gameTime + partialTick

        RenderSystem.enableBlend()
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()
        RenderSystem.defaultBlendFunc()

        bombs.forEach { bomb ->
            val center = interpolatedPosition(bomb, partialTick)
            when (bomb.state()) {
                SnareBombEntity.STATE_FLYING -> {
                    renderDevice(center, camera, 1f, modelView, worldPose)
                }

                SnareBombEntity.STATE_DEPLOYED -> {
                    val age = bomb.stateAge(partialTick)
                    renderDevice(center, camera, deployedScale(age), modelView, worldPose)
                    ringShader?.let { shader ->
                        renderDeploymentRipples(shader, center, camera, age, worldTime, modelView, worldPose)
                        if (age >= SnareBombEntity.ARMING_TICKS) {
                            renderArmedScan(shader, center, camera, age, worldTime, modelView, worldPose)
                        }
                    }
                }

                SnareBombEntity.STATE_TRIGGERED -> {
                    val age = bomb.stateAge(partialTick)
                    if (age < 4f) {
                        renderDevice(center, camera, (1f - age / 4f).coerceAtLeast(0f), modelView, worldPose)
                    }
                    cloudShader?.let { shader ->
                        renderNebula(shader, bomb.id, center, camera, age, worldTime, modelView, worldPose)
                    }
                    ringShader?.let { shader ->
                        renderTriggerRipple(shader, center, camera, age, worldTime, modelView, worldPose)
                    }
                }
            }
        }

        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        )
        cloudShader?.let { shader ->
            bombs.filter { it.isTriggered() }.forEach { bomb ->
                renderTriggerGlow(
                    shader,
                    bomb.id,
                    interpolatedPosition(bomb, partialTick),
                    camera,
                    bomb.stateAge(partialTick),
                    worldTime,
                    modelView,
                    worldPose
                )
            }
        }

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderDeploymentRipples(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        age: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (age !in 0f..IMPACT_RIPPLE_TICKS) return
        val progress = (age / IMPACT_RIPPLE_TICKS).coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        val opacity = sin(Mth.PI * progress).coerceAtLeast(0f) * 0.30f
        renderGroundRing(
            shader,
            center.add(0.0, 0.018, 0.0),
            camera,
            2.45f,
            eased,
            0.105f + progress * 0.045f,
            opacity,
            worldTime,
            modelView,
            worldPose
        )
    }

    private fun renderArmedScan(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        age: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val phase = ((age - SnareBombEntity.ARMING_TICKS) % 24f) / 24f
        val opacity = sin(Mth.PI * phase).coerceAtLeast(0f) * 0.085f
        renderGroundRing(
            shader,
            center.add(0.0, 0.022, 0.0),
            camera,
            0.72f,
            0.18f + phase * 0.62f,
            0.12f,
            opacity,
            worldTime,
            modelView,
            worldPose
        )
    }

    private fun renderTriggerRipple(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        age: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (age >= 8f) return
        val progress = (age / 8f).coerceIn(0f, 1f)
        val eased = 1f - (1f - progress) * (1f - progress) * (1f - progress)
        renderGroundRing(
            shader,
            center.add(0.0, 0.025, 0.0),
            camera,
            3.45f,
            eased,
            0.14f + progress * 0.05f,
            sin(Mth.PI * progress).coerceAtLeast(0f) * 0.34f,
            worldTime + 6.2f,
            modelView,
            worldPose
        )
    }

    private fun renderGroundRing(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        radius: Float,
        progress: Float,
        thickness: Float,
        opacity: Float,
        time: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (opacity <= 0.001f) return
        val relative = center.subtract(camera)
        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        buffer.addVertex((relative.x - radius).toFloat(), relative.y.toFloat(), (relative.z - radius).toFloat())
            .setUv(0f, 0f).setColor(255, 255, 255, 255)
        buffer.addVertex((relative.x + radius).toFloat(), relative.y.toFloat(), (relative.z - radius).toFloat())
            .setUv(1f, 0f).setColor(255, 255, 255, 255)
        buffer.addVertex((relative.x + radius).toFloat(), relative.y.toFloat(), (relative.z + radius).toFloat())
            .setUv(1f, 1f).setColor(255, 255, 255, 255)
        buffer.addVertex((relative.x - radius).toFloat(), relative.y.toFloat(), (relative.z + radius).toFloat())
            .setUv(0f, 1f).setColor(255, 255, 255, 255)

        shader.getUniform("Time")?.set(time * 0.05f)
        shader.getUniform("Progress")?.set(progress)
        shader.getUniform("Thickness")?.set(thickness)
        shader.getUniform("Opacity")?.set(opacity)
        drawCustom(buffer.build(), shader, modelView, worldPose)
    }

    private fun renderNebula(
        shader: ShaderInstance,
        entityId: Int,
        center: Vec3,
        camera: Vec3,
        age: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val total = SnareBombEntity.SMOKE_DURATION_TICKS.toFloat()
        val globalFadeIn = smoothStep01(age / 4f)
        val globalFadeOut = 1f - smoothStep01(
            (age - (total - CLOUD_FINAL_FADE_TICKS)) / CLOUD_FINAL_FADE_TICKS
        )
        val globalLife = globalFadeIn * globalFadeOut
        if (globalLife <= 0.001f) return

        val distanceSqr = camera.distanceToSqr(center)
        val emitterCount = when {
            distanceSqr > 48.0 * 48.0 -> 0
            distanceSqr > 24.0 * 24.0 -> CLOUD_EMITTERS_MID
            else -> CLOUD_EMITTERS_NEAR
        }
        if (emitterCount == 0) return

        val initialBurstCount = if (emitterCount == CLOUD_EMITTERS_NEAR) 18 else 12
        val staggeredCount = (emitterCount - initialBurstCount).coerceAtLeast(1)
        val staggeredStartWindow = 20f
        val lobes = ArrayList<CloudLobe>(emitterCount)
        repeat(emitterCount) { lobe ->
            val seedA = hash01(entityId, lobe, 17)
            val seedB = hash01(entityId, lobe, 43)
            val seedC = hash01(entityId, lobe, 79)
            val firstBirthTick = if (lobe < initialBurstCount) {
                (lobe % 4) * 0.8f
            } else {
                4f + (lobe - initialBurstCount) * (staggeredStartWindow / staggeredCount)
            }
            val elapsed = age - firstBirthTick
            if (elapsed < 0f) return@repeat
            // A new plume starts before the previous one has finished fading.
            // Keeping two generations alive removes the visible empty beat that
            // appeared when emission interval and plume lifetime were identical.
            val emissionInterval = 28f + seedB * 10f
            val plumeLifetime = emissionInterval + 24f + seedC * 8f
            val cycleIndex = (elapsed / emissionInterval).toInt()
            repeat(2) { generation ->
                val generationAge = elapsed - (cycleIndex - generation) * emissionInterval
                if (generationAge < 0f || generationAge >= plumeLifetime) return@repeat
                val generationSeedA = hash01(entityId, lobe * 257 + cycleIndex - generation, 131)
                val generationSeedB = hash01(entityId, lobe * 257 + cycleIndex - generation, 167)
                val localProgress = (generationAge / plumeLifetime).coerceIn(0f, 1f)
                val localFadeIn = smoothStep01(generationAge / 3.2f)
                val localFadeOut = 1f - smoothStep01(
                    (generationAge - (plumeLifetime - 18f)) / 18f
                )
                val localLife = localFadeIn * localFadeOut * globalLife
                if (localLife <= 0.001f) return@repeat
                val outwardProgress = smoothStep01(localProgress)

                val angle = lobe * GOLDEN_ANGLE + entityId * 0.071f +
                    generationSeedA * 0.34f +
                    sin(generationAge * 0.051f + lobe * 1.19f) * (0.045f + seedC * 0.070f)
                val radialTarget = when (lobe % 3) {
                    0 -> 0.45f + generationSeedA * 1.25f
                    1 -> 1.55f + generationSeedA * 1.60f
                    else -> 3.00f + generationSeedA * 1.65f
                }
                val radialBoil = sin(generationAge * (0.075f + seedB * 0.035f) + lobe) *
                    (0.045f + seedC * 0.09f) * outwardProgress
                val radial = (radialTarget * outwardProgress + radialBoil).coerceAtLeast(0f)
                val targetHeight = 0.24f + generationSeedB * 1.65f +
                    (1f - (radialTarget / 4.65f).coerceIn(0f, 1f)) * (0.25f + seedC * 0.45f)
                val desiredHeight = 0.08f + (targetHeight - 0.08f) * outwardProgress +
                    localProgress * (0.85f + seedC * 1.15f) +
                    sin(generationAge * 0.083f + lobe * 0.73f) * 0.075f * outwardProgress
                val height = desiredHeight.coerceAtMost(CLOUD_TOP_HEIGHT - 0.35f)
                val position = center.add(
                    cos(angle.toDouble()) * radial,
                    height.toDouble(),
                    sin(angle.toDouble()) * radial
                )
                val expansion = 0.28f + smoothStep01(localProgress / 0.42f) * 0.72f
                val breathing = 0.94f + sin(generationAge * 0.105f + lobe * 1.61f) * 0.06f
                val halfWidth = (0.70f + seedB * 0.78f) * expansion * breathing
                val rawHalfHeight = halfWidth * (0.68f + seedC * 0.27f)
                val halfHeight = minOf(
                    rawHalfHeight,
                    (CLOUD_TOP_HEIGHT - height).coerceAtLeast(0.18f)
                )
                val radialRatio = (radial / 4.65f).coerceIn(0f, 1f)
                val outerDarkening = smoothStep01((radialRatio - 0.36f) / 0.56f)
                val density = (142f + seedA * 58f + outerDarkening * 26f) * localLife
                val innerRed = 104f + seedC * 39f
                val innerGreen = 45f + seedA * 28f
                val innerBlue = 184f + seedB * 54f
                val outerRed = 25f + seedC * 20f
                val outerGreen = 8f + seedA * 14f
                val outerBlue = 64f + seedB * 40f
                val red = (innerRed + (outerRed - innerRed) * outerDarkening).toInt()
                val green = (innerGreen + (outerGreen - innerGreen) * outerDarkening).toInt()
                val blue = (innerBlue + (outerBlue - innerBlue) * outerDarkening).toInt()
                lobes += CloudLobe(
                    position = position,
                    halfWidth = halfWidth,
                    halfHeight = halfHeight,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = density.toInt(),
                    roll = generationSeedB * Mth.TWO_PI +
                        sin(generationAge * 0.018f + lobe) * 0.10f
                )
            }
        }
        if (age in 3f..13f) {
            val veilLife = sin(Mth.PI * ((age - 3f) / 10f)).coerceAtLeast(0f)
            lobes += CloudLobe(
                position = center.add(0.0, 0.72, 0.0),
                halfWidth = 0.17f + age * 0.007f,
                halfHeight = 0.72f + age * 0.025f,
                red = 19,
                green = 10,
                blue = 55,
                alpha = (112f * veilLife).toInt(),
                roll = sin(age * 0.08f) * 0.08f
            )
        }

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        lobes.sortedByDescending { it.position.distanceToSqr(camera) }.forEach { lobe ->
            addBillboard(
                buffer,
                lobe.position,
                camera,
                lobe.halfWidth,
                lobe.halfHeight,
                lobe.red,
                lobe.green,
                lobe.blue,
                lobe.alpha,
                lobe.roll
            )
        }
        shader.getUniform("Time")?.set(worldTime * 0.025f + entityId * 0.137f)
        drawCustom(buffer.build(), shader, modelView, worldPose)
    }

    private fun renderTriggerGlow(
        shader: ShaderInstance,
        entityId: Int,
        center: Vec3,
        camera: Vec3,
        age: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (age < 5f) {
            val fade = 1f - smoothStep01(age / TRIGGER_FLASH_TICKS)
            val flashBuffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR
            )
            addBillboard(
                flashBuffer,
                center.add(0.0, 0.82, 0.0),
                camera,
                0.15f + age * 0.018f,
                0.34f + age * 0.045f,
                126,
                91,
                255,
                (118f * fade).toInt()
            )
            shader.getUniform("Time")?.set(worldTime * 0.04f + entityId)
            drawCustom(flashBuffer.build(), shader, modelView, worldPose)
        }

        val total = SnareBombEntity.SMOKE_DURATION_TICKS.toFloat()
        val fadeOut = 1f - smoothStep01(
            (age - (total - CLOUD_FINAL_FADE_TICKS)) / CLOUD_FINAL_FADE_TICKS
        )
        if (fadeOut <= 0.001f) return
        val distanceSqr = camera.distanceToSqr(center)
        val moteCount = when {
            distanceSqr > 48.0 * 48.0 -> 0
            distanceSqr > 24.0 * 24.0 -> CLOUD_MOTES_MID
            else -> CLOUD_MOTES_NEAR
        }
        if (moteCount == 0) return

        val glowBuffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_TEX_COLOR
        )
        val burstEnergy = 1f - smoothStep01((age - 5f) / 9f)
        val innerEnergy = 0.62f + burstEnergy * 0.38f
        // Keep the previous version's readable purple interior, but split it into
        // moving pockets so it never becomes one uniformly glowing sphere.
        repeat(7) { core ->
            val coreSeed = hash01(entityId, core, 269)
            val coreSeedB = hash01(entityId, core, 307)
            val coreAngle = core * GOLDEN_ANGLE + age * (0.004f + coreSeed * 0.004f) +
                sin(age * 0.047f + core * 0.91f) * 0.11f
            val coreRadius = 0.14f + sqrt(coreSeed.toDouble()).toFloat() * 1.08f
            val corePulse = 0.72f + smoothStep01(
                sin(age * 0.075f + core * 1.7f) * 0.5f + 0.5f
            ) * 0.28f
            addBillboard(
                glowBuffer,
                center.add(
                    cos(coreAngle.toDouble()) * coreRadius,
                    (0.20f + coreSeedB * 0.88f +
                        sin(age * 0.039f + core) * 0.08f).toDouble(),
                    sin(coreAngle.toDouble()) * coreRadius
                ),
                camera,
                (0.25f + coreSeed * 0.31f) * corePulse,
                (0.22f + coreSeedB * 0.34f) * corePulse,
                127 + (coreSeedB * 37f).toInt(),
                67 + (coreSeed * 31f).toInt(),
                255,
                ((28f + coreSeedB * 26f) * innerEnergy * fadeOut).toInt(),
                coreSeed * Mth.TWO_PI
            )
        }
        addBillboard(
            glowBuffer,
            center.add(0.0, 0.17, 0.0),
            camera,
            0.13f,
            0.18f,
            116,
            82,
            255,
            (38f * fadeOut).toInt()
        )
        repeat(moteCount) { mote ->
            val seedA = hash01(entityId, mote, 113)
            val seedB = hash01(entityId, mote, 157)
            val seedC = hash01(entityId, mote, 211)
            val cycle = 24f + seedB * 20f
            val progress = ((age + seedA * cycle) % cycle) / cycle
            val particleLife = sin(Mth.PI * progress).coerceAtLeast(0f)
            val angle = mote * GOLDEN_ANGLE + entityId * 0.11f +
                age * (0.006f + seedC * 0.010f) +
                sin(age * 0.025f + mote * 1.37f) * 0.16f
            val radialLimit = 0.70f + sqrt(seedA.toDouble()).toFloat() * 4.00f
            val radius = radialLimit * (0.12f + progress * 0.88f)
            val lateralWander = sin(age * 0.031f + mote * 2.11f) * (0.06f + seedB * 0.10f)
            val moteHeight = (
                0.20f + seedB * 0.62f + progress * (1.20f + seedC * 3.00f) +
                    sin(age * 0.020f + mote) * 0.07f
                ).coerceIn(0.10f, CLOUD_TOP_HEIGHT - 0.10f)
            val position = center.add(
                cos(angle.toDouble()) * radius + cos((angle + Mth.HALF_PI).toDouble()) * lateralWander,
                moteHeight.toDouble(),
                sin(angle.toDouble()) * radius + sin((angle + Mth.HALF_PI).toDouble()) * lateralWander
            )
            val size = 0.026f + seedC * 0.052f
            val brightness = 175 + (seedB * 72f).toInt()
            addBillboard(
                glowBuffer,
                position,
                camera,
                size,
                size * (0.78f + seedA * 0.54f),
                126 + (seedA * 46f).toInt(),
                76 + (seedC * 61f).toInt(),
                brightness.coerceAtMost(255),
                ((22f + seedB * 38f) * particleLife * fadeOut).toInt(),
                seedC * Mth.TWO_PI
            )
        }
        shader.getUniform("Time")?.set(worldTime * 0.035f + entityId * 0.19f)
        drawCustom(glowBuffer.build(), shader, modelView, worldPose)
    }

    private fun renderDevice(
        center: Vec3,
        camera: Vec3,
        scale: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (scale <= 0.001f) return
        val base = center.add(0.0, 0.045, 0.0)
        renderEllipsoid(base, camera, 0.18f * scale, 0.065f * scale, 0.18f * scale, 18, 16, 27, 255, modelView, worldPose)
        renderEllipsoid(base.add(0.0, 0.045 * scale, 0.0), camera, 0.125f * scale, 0.035f * scale, 0.125f * scale, 192, 192, 218, 240, modelView, worldPose)
        renderEllipsoid(base.add(0.0, 0.062 * scale, 0.0), camera, 0.088f * scale, 0.027f * scale, 0.088f * scale, 11, 10, 24, 255, modelView, worldPose)
        renderEllipsoid(base.add(0.0, 0.078 * scale, 0.0), camera, 0.042f * scale, 0.017f * scale, 0.042f * scale, 154, 127, 255, 245, modelView, worldPose)
    }

    private fun renderEllipsoid(
        center: Vec3,
        camera: Vec3,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        modelView.pushMatrix()
        modelView.mul(worldPose)
        modelView.translate(
            (center.x - camera.x).toFloat(),
            (center.y - camera.y).toFloat(),
            (center.z - camera.z).toFloat()
        )
        modelView.scale(scaleX, scaleY, scaleZ)
        RenderSystem.applyModelViewMatrix()

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.TRIANGLES,
            DefaultVertexFormat.POSITION_COLOR
        )
        addSphere(buffer, red, green, blue, alpha)
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        BufferUploader.drawWithShader(buffer.buildOrThrow())

        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun addSphere(buffer: BufferBuilder, red: Int, green: Int, blue: Int, alpha: Int) {
        for (latitude in 0 until DEVICE_LATITUDES) {
            val theta0 = (latitude.toFloat() / DEVICE_LATITUDES - 0.5f) * Mth.PI
            val theta1 = ((latitude + 1).toFloat() / DEVICE_LATITUDES - 0.5f) * Mth.PI
            for (longitude in 0 until DEVICE_LONGITUDES) {
                val phi0 = longitude.toFloat() / DEVICE_LONGITUDES * Mth.TWO_PI
                val phi1 = (longitude + 1).toFloat() / DEVICE_LONGITUDES * Mth.TWO_PI
                addSphereTriangle(buffer, theta0, phi0, theta1, phi0, theta1, phi1, red, green, blue, alpha)
                addSphereTriangle(buffer, theta0, phi0, theta1, phi1, theta0, phi1, red, green, blue, alpha)
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
        phiC: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        addSphereVertex(buffer, thetaA, phiA, red, green, blue, alpha)
        addSphereVertex(buffer, thetaB, phiB, red, green, blue, alpha)
        addSphereVertex(buffer, thetaC, phiC, red, green, blue, alpha)
    }

    private fun addSphereVertex(
        buffer: BufferBuilder,
        theta: Float,
        phi: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int
    ) {
        val cosTheta = Mth.cos(theta)
        buffer.addVertex(
            cosTheta * Mth.cos(phi),
            Mth.sin(theta),
            cosTheta * Mth.sin(phi)
        ).setColor(red, green, blue, alpha)
    }

    private fun addBillboard(
        buffer: BufferBuilder,
        center: Vec3,
        camera: Vec3,
        halfWidth: Float,
        halfHeight: Float,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
        roll: Float = 0f
    ) {
        val view = camera.subtract(center).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(0.0, 0.0, 1.0)
        }
        val rightUnit = Vec3(0.0, 1.0, 0.0).cross(view).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(1.0, 0.0, 0.0)
        }
        val upUnit = view.cross(rightUnit).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(0.0, 1.0, 0.0)
        }
        val cosRoll = cos(roll.toDouble())
        val sinRoll = sin(roll.toDouble())
        val rolledRight = rightUnit.scale(cosRoll).add(upUnit.scale(sinRoll))
        val rolledUp = upUnit.scale(cosRoll).subtract(rightUnit.scale(sinRoll))
        val right = rolledRight.scale(halfWidth.toDouble())
        val up = rolledUp.scale(halfHeight.toDouble())
        addCloudVertex(buffer, center.subtract(right).subtract(up), camera, 0f, 1f, red, green, blue, alpha)
        addCloudVertex(buffer, center.add(right).subtract(up), camera, 1f, 1f, red, green, blue, alpha)
        addCloudVertex(buffer, center.add(right).add(up), camera, 1f, 0f, red, green, blue, alpha)
        addCloudVertex(buffer, center.subtract(right).add(up), camera, 0f, 0f, red, green, blue, alpha)
    }

    private fun addCloudVertex(
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

    private fun drawCustom(
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

    private fun interpolatedPosition(entity: SnareBombEntity, partialTick: Float): Vec3 = Vec3(
        Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x),
        Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y),
        Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)
    )

    private fun deployedScale(age: Float): Float {
        val progress = smoothStep01(age / SnareBombEntity.ARMING_TICKS)
        return 0.36f + progress * 0.64f + sin(age * 0.38f) * 0.018f * progress
    }

    private fun smoothStep01(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun hash01(entityId: Int, index: Int, salt: Int): Float {
        var value = entityId * 73428767 xor index * 912931 xor salt * 19349663
        value = value xor (value ushr 13)
        value *= 1274126177
        value = value xor (value ushr 16)
        return (value and 0x7fffffff) / Int.MAX_VALUE.toFloat()
    }

    private data class CloudLobe(
        val position: Vec3,
        val halfWidth: Float,
        val halfHeight: Float,
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
        val roll: Float
    )
}
