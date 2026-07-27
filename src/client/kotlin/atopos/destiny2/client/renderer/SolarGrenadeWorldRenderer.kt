package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.SolarFlareEntity
import atopos.destiny2.common.entity.SolarEruptionProjectileEntity
import atopos.destiny2.common.entity.SolarGrenadeEntity
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
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Real-time Solar Grenade presentation.
 *
 * The server entities remain authoritative for charge, flight, impact, lifetime
 * and damage. This renderer only converts their deterministic state into the
 * hand mist, translucent projectile, molten core and arcing eruptions.
 */
object SolarGrenadeWorldRenderer {
    private const val QUERY_RANGE = 96.0
    private const val LATITUDE_SEGMENTS = 16
    private const val LONGITUDE_SEGMENTS = 24
    private const val FIREBALL_TRAIL_SEGMENTS = 5
    private const val ERUPTION_TRAIL_DRAG = 0.99
    private const val ERUPTION_TRAIL_GRAVITY = 0.055
    private const val ERUPTION_TRAIL_STEP = 0.58
    private const val CORE_GATHER_MOTES = 16
    private const val IMPACT_BURST_TICKS = 10f
    private const val IMPACT_BURST_RAYS = 12

    @Volatile
    private var orbShader: ShaderInstance? = null
    @Volatile
    private var shellShader: ShaderInstance? = null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "solar_grenade_orb"),
                DefaultVertexFormat.POSITION_COLOR
            ) { shader -> orbShader = shader }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "solar_grenade_shell"),
                DefaultVertexFormat.POSITION_COLOR
            ) { shader -> shellShader = shader }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::render)
    }

    private fun render(context: WorldRenderContext) {
        val shader = orbShader ?: return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val bounds = AABB(camera, camera).inflate(QUERY_RANGE)
        val grenades = context.world().getEntitiesOfClass(
            SolarGrenadeEntity::class.java,
            bounds
        ) { it.isAlive }
        val flares = context.world().getEntitiesOfClass(
            SolarFlareEntity::class.java,
            bounds
        ) { it.isAlive }
        val eruptionProjectiles = context.world().getEntitiesOfClass(
            SolarEruptionProjectileEntity::class.java,
            bounds
        ) { it.isAlive }
        if (grenades.isEmpty() && flares.isEmpty() && eruptionProjectiles.isEmpty()) return

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
            val center = interpolatedPosition(grenade, partialTick)
            if (grenade.isCharging()) {
                renderChargeMist(
                    shader,
                    center,
                    camera,
                    grenade.tickCount + partialTick,
                    worldTime,
                    modelView,
                    worldPose
                )
            } else {
                val flightAge = (grenade.tickCount + partialTick - SolarGrenadeEntity.CHARGE_TICKS)
                    .coerceAtLeast(0f)
                val launchScale = Mth.clamp(flightAge / 5f, 0f, 1f)
                renderOrb(
                    shader,
                    center,
                    camera,
                    Mth.lerp(launchScale, 0.12f, 0.26f),
                    1.35f,
                    0.82f,
                    worldTime,
                    modelView,
                    worldPose
                )
            }
        }

        shellShader?.let { shell ->
            renderFlameShells(
                flares,
                camera,
                partialTick,
                worldTime,
                modelView,
                worldPose,
                shell
            )
        }

        flares.forEach { flare ->
            // The landed core has a 1.5-block radius. Its centre sits on the
            // ground plane so terrain depth leaves only the upper hemisphere.
            val center = interpolatedPosition(flare, partialTick).add(0.0, 0.02, 0.0)
            val age = flare.tickCount + partialTick
            renderImpactBurst(
                shader,
                center.add(0.0, 0.10, 0.0),
                camera,
                age,
                1.55f,
                flare.id,
                worldTime,
                modelView,
                worldPose
            )
            renderCoreFormation(
                shader,
                center,
                camera,
                age,
                worldTime,
                modelView,
                worldPose
            )
            val appear = Mth.clamp(
                (age - SolarFlareEntity.CORE_FORMATION_TICKS * 0.48f) /
                    (SolarFlareEntity.CORE_FORMATION_TICKS * 0.52f),
                0f,
                1f
            )
            val endFade = 1f - Mth.clamp((age - 126f) / 14f, 0f, 1f)
            val strength = appear * endFade
            renderOrb(
                shader,
                center,
                camera,
                1.5f * (0.96f + sin(age * 0.13f) * 0.035f) * appear,
                1.9f,
                1.18f * strength,
                worldTime,
                modelView,
                worldPose
            )
            renderOrb(
                shader,
                center.add(0.0, 0.08, 0.0),
                camera,
                0.98f * appear,
                2.75f,
                1.34f * strength,
                worldTime + 4.7f,
                modelView,
                worldPose
            )
            renderOrb(
                shader,
                center.add(0.0, 0.13, 0.0),
                camera,
                0.58f * appear,
                3.5f,
                1.48f * strength,
                worldTime + 8.2f,
                modelView,
                worldPose
            )
        }

        renderProjectileTrails(
            grenades,
            camera,
            partialTick,
            worldTime,
            modelView,
            worldPose,
            shader
        )
        renderEruptionProjectiles(
            eruptionProjectiles,
            camera,
            partialTick,
            worldTime,
            modelView,
            worldPose,
            shader
        )

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderChargeMist(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        age: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val progress = Mth.clamp(age / SolarGrenadeEntity.CHARGE_TICKS, 0f, 1f)
        val pulse = 0.86f + sin(age * 0.72f) * 0.09f
        renderOrb(
            shader,
            center,
            camera,
            (0.10f + progress * 0.12f) * pulse,
            1.2f,
            0.48f + progress * 0.32f,
            worldTime,
            modelView,
            worldPose
        )
        repeat(4) { wisp ->
            val phase = age * (0.17f + wisp * 0.012f) + wisp * Mth.TWO_PI / 4f
            val radius = 0.09 + progress * 0.13
            val offset = Vec3(
                cos(phase.toDouble()) * radius,
                sin((phase * 1.37f).toDouble()) * (0.055 + progress * 0.07),
                sin(phase.toDouble()) * radius
            )
            renderOrb(
                shader,
                center.add(offset),
                camera,
                0.07f + progress * 0.055f,
                0.55f,
                (0.20f + progress * 0.20f),
                worldTime + wisp * 3.1f,
                modelView,
                worldPose
            )
        }
    }

    private fun renderOrb(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        scale: Float,
        heat: Float,
        opacity: Float,
        time: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) = renderOrbScaled(
        shader,
        center,
        camera,
        Vector3f(scale, scale, scale),
        heat,
        opacity,
        time,
        modelView,
        worldPose
    )

    private fun renderOrbScaled(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        scale: Vector3f,
        heat: Float,
        opacity: Float,
        time: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
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

        val cameraObject = Vector3f(
            ((camera.x - center.x) / scale.x).toFloat(),
            ((camera.y - center.y) / scale.y).toFloat(),
            ((camera.z - center.z) / scale.z).toFloat()
        )
        shader.getUniform("CameraPos")?.set(cameraObject.x, cameraObject.y, cameraObject.z)
        shader.getUniform("Time")?.set(time * 0.055f)
        shader.getUniform("Heat")?.set(heat)
        shader.getUniform("Opacity")?.set(opacity)
        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.TRIANGLES,
            DefaultVertexFormat.POSITION_COLOR
        )
        addSphere(buffer)
        RenderSystem.setShader { shader }
        BufferUploader.drawWithShader(buffer.buildOrThrow())

        modelView.popMatrix()
        RenderSystem.applyModelViewMatrix()
    }

    private fun renderProjectileTrails(
        grenades: List<SolarGrenadeEntity>,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        shader: ShaderInstance
    ) {
        val flying = grenades.filter { !it.isCharging() && it.deltaMovement.lengthSqr() > 1.0e-5 }
        if (flying.isEmpty()) return
        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        )

        flying.forEach { grenade ->
            val head = interpolatedPosition(grenade, partialTick)
            val backwards = grenade.deltaMovement.normalize().scale(-1.0)
            var previous = head
            repeat(FIREBALL_TRAIL_SEGMENTS) { segment ->
                val t = (segment + 1f) / FIREBALL_TRAIL_SEGMENTS
                val distance = 1.65 * t
                val current = head.add(backwards.scale(distance))
                    .add(0.0, sin((grenade.tickCount + partialTick) * 0.7f + segment) * 0.025, 0.0)
                val nearTaper = 1f - segment.toFloat() / FIREBALL_TRAIL_SEGMENTS
                val farTaper = 1f - (segment + 1f) / FIREBALL_TRAIL_SEGMENTS
                emitRibbon(
                    buffer,
                    previous,
                    current,
                    camera,
                    0.18f * nearTaper,
                    0.18f * farTaper,
                    255,
                    154,
                    24,
                    (102f * nearTaper).toInt()
                )
                emitRibbon(
                    buffer,
                    previous,
                    current,
                    camera,
                    0.065f * nearTaper,
                    0.065f * farTaper,
                    255,
                    214,
                    83,
                    (190f * nearTaper).toInt()
                )
                previous = current
            }
        }
        drawPositionColorBuffer(buffer.build(), modelView, worldPose)

        flying.forEach { grenade ->
            val head = interpolatedPosition(grenade, partialTick)
            val backwards = grenade.deltaMovement.normalize().scale(-1.0)
            for (wisp in 1..3) {
                val t = wisp / 4.0
                val center = head.add(backwards.scale(1.55 * t))
                renderOrb(
                    shader,
                    center,
                    camera,
                    (0.18f * (1f - t.toFloat())).coerceAtLeast(0.045f),
                    0.62f,
                    0.34f * (1f - t.toFloat()),
                    worldTime - wisp * 0.8f,
                    modelView,
                    worldPose
                )
            }
        }
    }

    private fun renderFlameShells(
        flares: List<SolarFlareEntity>,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        shader: ShaderInstance
    ) {
        flares.forEach { flare ->
            val center = interpolatedPosition(flare, partialTick).add(0.0, 0.04, 0.0)
            val age = flare.tickCount + partialTick
            val appear = Mth.clamp(
                (age - SolarFlareEntity.CORE_FORMATION_TICKS * 0.58f) /
                    (SolarFlareEntity.CORE_FORMATION_TICKS * 0.42f),
                0f,
                1f
            )
            val endFade = 1f - Mth.clamp((age - 122f) / 18f, 0f, 1f)
            val opacity = appear * endFade
            if (opacity <= 0.001f) return@forEach

            val scale = (3.3f + sin(age * 0.078f) * 0.07f) * appear
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
            shader.getUniform("Time")?.set(worldTime * 0.082f)
            shader.getUniform("Opacity")?.set(opacity)
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
            )
            addSphere(buffer)
            RenderSystem.setShader { shader }
            BufferUploader.drawWithShader(buffer.buildOrThrow())

            modelView.popMatrix()
            RenderSystem.applyModelViewMatrix()
        }
    }

    private fun renderCoreFormation(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        age: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (age >= SolarFlareEntity.CORE_FORMATION_TICKS) return
        val progress = Mth.clamp(age / SolarFlareEntity.CORE_FORMATION_TICKS, 0f, 1f)
        val envelope = sin(progress * Mth.PI).coerceAtLeast(0f)
        val flameBuffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        )
        repeat(CORE_GATHER_MOTES) { mote ->
            val seed = mote.toFloat()
            val startRadius = 2.5 + (mote * 37 % 8) * 0.16
            val radius = Mth.lerp(progress, startRadius.toFloat(), 0.34f)
            val angle = seed * 2.399963f + (1f - progress) * 1.7f + age * 0.045f
            val startHeight = 0.38f + (mote * 19 % 7) * 0.11f
            val height = Mth.lerp(progress, startHeight, 0.24f)
            val moteCenter = center.add(
                cos(angle.toDouble()) * radius,
                (height + sin(age * 0.19f + seed) * 0.055f).toDouble(),
                sin(angle.toDouble()) * radius
            )
            renderOrb(
                shader,
                moteCenter,
                camera,
                (0.075f + progress * 0.085f) * (0.72f + envelope * 0.28f),
                1.7f + progress * 0.8f,
                (0.38f + envelope * 0.52f) * (1f - progress * 0.34f),
                worldTime + seed * 0.73f,
                modelView,
                worldPose
            )
            val outerAngle = angle - 0.20f - sin(age * 0.11f + seed) * 0.08f
            val outerRadius = radius + 0.52f + (mote % 3) * 0.11f
            val flameTail = center.add(
                cos(outerAngle.toDouble()) * outerRadius,
                (height + 0.18f + sin(age * 0.23f + seed * 1.7f) * 0.13f).toDouble(),
                sin(outerAngle.toDouble()) * outerRadius
            )
            val middleAngle = outerAngle + 0.13f + sin(age * 0.16f + seed) * 0.055f
            val middleRadius = (outerRadius + radius) * 0.5f
            val flameMiddle = center.add(
                cos(middleAngle.toDouble()) * middleRadius,
                (height + 0.10f + sin(age * 0.27f + seed * 1.31f) * 0.10f).toDouble(),
                sin(middleAngle.toDouble()) * middleRadius
            )
            emitRibbon(
                flameBuffer,
                flameTail,
                flameMiddle,
                camera,
                0.064f * (0.55f + envelope * 0.45f),
                0.042f,
                255,
                154,
                28,
                (92f + envelope * 62f).toInt()
            )
            emitRibbon(
                flameBuffer,
                flameMiddle,
                moteCenter,
                camera,
                0.042f,
                0.014f,
                255,
                176,
                48,
                (112f + envelope * 70f).toInt()
            )
            emitRibbon(
                flameBuffer,
                flameTail.add(0.0, 0.026, 0.0),
                flameMiddle.add(0.0, 0.018, 0.0),
                camera,
                0.022f * (0.62f + envelope * 0.38f),
                0.014f,
                255,
                234,
                148,
                (164f + envelope * 48f).toInt()
            )
            emitRibbon(
                flameBuffer,
                flameMiddle.add(0.0, 0.018, 0.0),
                moteCenter.add(0.0, 0.012, 0.0),
                camera,
                0.014f,
                0.004f,
                255,
                242,
                176,
                (184f + envelope * 54f).toInt()
            )
        }
        drawPositionColorBuffer(flameBuffer.build(), modelView, worldPose)
    }

    private fun renderEruptionProjectiles(
        projectiles: List<SolarEruptionProjectileEntity>,
        camera: Vec3,
        partialTick: Float,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        shader: ShaderInstance
    ) {
        if (projectiles.isEmpty()) return
        val flying = projectiles.filter { !it.isImpacted() && it.deltaMovement.lengthSqr() > 1.0e-5 }
        if (flying.isNotEmpty()) {
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
            )
            flying.forEach { projectile ->
                val head = interpolatedPosition(projectile, partialTick)
                var previous = head
                var reverseVelocity = projectile.deltaMovement
                repeat(FIREBALL_TRAIL_SEGMENTS) { segment ->
                    val t0 = segment.toFloat() / FIREBALL_TRAIL_SEGMENTS
                    val t1 = (segment + 1f) / FIREBALL_TRAIL_SEGMENTS
                    reverseVelocity = Vec3(
                        reverseVelocity.x / ERUPTION_TRAIL_DRAG,
                        (reverseVelocity.y + ERUPTION_TRAIL_GRAVITY) / ERUPTION_TRAIL_DRAG,
                        reverseVelocity.z / ERUPTION_TRAIL_DRAG
                    )
                    val current = previous.subtract(reverseVelocity.scale(ERUPTION_TRAIL_STEP))
                    emitRibbon(
                        buffer,
                        previous,
                        current,
                        camera,
                        0.078f * (1f - t0),
                        0.078f * (1f - t1),
                        255,
                        145,
                        24,
                        (118f * (1f - t0)).toInt()
                    )
                    emitRibbon(
                        buffer,
                        previous,
                        current,
                        camera,
                        0.026f * (1f - t0),
                        0.026f * (1f - t1),
                        255,
                        239,
                        166,
                        (226f * (1f - t0)).toInt()
                    )
                    previous = current
                }
            }
            drawPositionColorBuffer(buffer.build(), modelView, worldPose)
        }

        flying.forEach { projectile ->
            val head = interpolatedPosition(projectile, partialTick)
            renderOrb(
                shader,
                head,
                camera,
                0.145f,
                2.25f,
                1.08f,
                worldTime + projectile.id * 0.31f,
                modelView,
                worldPose
            )
            renderOrb(
                shader,
                head,
                camera,
                0.072f,
                3.45f,
                1.38f,
                worldTime + projectile.id * 0.31f + 2.4f,
                modelView,
                worldPose
            )
        }

        projectiles.filter { it.isImpacted() }.forEach { projectile ->
            val remaining = projectile.getAfterglowTicks()
            val fade = Mth.clamp(remaining.toFloat() / 10f, 0f, 1f)
            // Keep the complete flattened splash above the hit face. The old
            // offset left its lower half inside the block where depth testing
            // hid most of it.
            val center = projectile.getImpactPosition().add(0.0, 0.095, 0.0)
            val impactAge =
                SolarEruptionProjectileEntity.AFTERGLOW_TICKS - remaining + partialTick
            renderGroundImpactSplash(
                shader,
                center,
                camera,
                impactAge,
                projectile.id,
                modelView,
                worldPose
            )
            renderGroundFire(
                shader,
                center,
                camera,
                fade,
                projectile.id,
                modelView,
                worldPose
            )
        }
    }

    private fun renderGroundImpactSplash(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        age: Float,
        seed: Int,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (age < 0f || age >= 6f) return
        val progress = Mth.clamp(age / 6f, 0f, 1f)
        val fade = (1f - progress) * (1f - progress)
        renderOrbScaled(
            shader,
            center.add(0.0, 0.018, 0.0),
            camera,
            Vector3f(
                Mth.lerp(progress, 0.18f, 0.92f),
                Mth.lerp(progress, 0.10f, 0.035f),
                Mth.lerp(progress, 0.16f, 0.78f)
            ),
            1.25f,
            1.55f * fade,
            seed * 0.19f,
            modelView,
            worldPose
        )

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        )
        repeat(14) { ray ->
            val angle = seed * 0.527f + ray * 2.399963f
            val horizontal = Vec3(cos(angle.toDouble()), 0.0, sin(angle.toDouble()))
            val lift = 0.025 + (ray * 11 % 5) * 0.025
            val start = center.add(horizontal.scale(0.10 + progress * 0.18))
                .add(0.0, 0.018, 0.0)
            val end = center.add(horizontal.scale(0.52 + progress * (0.48 + ray % 3 * 0.11)))
                .add(0.0, lift + progress * lift, 0.0)
            emitRibbon(
                buffer,
                start,
                end,
                camera,
                0.052f * fade,
                0.008f * fade,
                255,
                112,
                18,
                (242f * fade).toInt()
            )
        }
        repeat(4) { jet ->
            val angle = seed * 0.311f + jet * Mth.TWO_PI / 4f
            val foot = center.add(
                cos(angle.toDouble()) * (0.08 + jet * 0.025),
                0.025,
                sin(angle.toDouble()) * (0.08 + jet * 0.025)
            )
            val tip = foot.add(
                cos((angle + 0.7f).toDouble()) * 0.10,
                0.28 + jet * 0.075 + progress * 0.22,
                sin((angle + 0.7f).toDouble()) * 0.10
            )
            emitRibbon(
                buffer,
                foot,
                tip,
                camera,
                0.042f * fade,
                0.006f,
                255,
                184,
                48,
                (228f * fade).toInt()
            )
        }
        drawPositionColorBuffer(buffer.build(), modelView, worldPose)
    }

    private fun renderGroundFire(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        fade: Float,
        seed: Int,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        // Ground fire is a stable molten scorch, not a crawling liquid blob.
        // Its irregular silhouette varies per projectile but does not animate.
        val flicker = 1.0f
        renderOrbScaled(
            shader,
            center.add(0.0, 0.010, 0.0),
            camera,
            Vector3f(0.62f * flicker, 0.052f, 0.49f * flicker),
            0.85f,
            1.30f * fade,
            seed * 0.73f,
            modelView,
            worldPose
        )
        renderOrbScaled(
            shader,
            center.add(0.035, 0.028, -0.025),
            camera,
            Vector3f(0.38f * flicker, 0.036f, 0.32f * flicker),
            1.35f,
            1.58f * fade,
            seed * 0.73f + 2.2f,
            modelView,
            worldPose
        )
        repeat(7) { lobe ->
            val angle = seed * 0.413f + lobe * 2.399963f
            val radial = 0.20 + (lobe * 5 % 4) * 0.055
            val lobeCenter = center.add(
                cos(angle.toDouble()) * radial,
                0.012 + (lobe % 2) * 0.008,
                sin(angle.toDouble()) * radial
            )
            val longAxis = 0.28f + (lobe * 7 % 4) * 0.035f
            val shortAxis = 0.18f + (lobe * 3 % 3) * 0.028f
            renderOrbScaled(
                shader,
                lobeCenter,
                camera,
                if (lobe % 2 == 0) {
                    Vector3f(longAxis * flicker, 0.030f, shortAxis * flicker)
                } else {
                    Vector3f(shortAxis * flicker, 0.030f, longAxis * flicker)
                },
                0.65f + (lobe % 3) * 0.16f,
                (0.82f + (lobe % 2) * 0.16f) * fade,
                seed * 0.41f + lobe * 1.73f,
                modelView,
                worldPose
            )
        }
    }

    private fun renderImpactBurst(
        shader: ShaderInstance,
        center: Vec3,
        camera: Vec3,
        age: Float,
        size: Float,
        seed: Int,
        worldTime: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        if (age < 0f || age >= IMPACT_BURST_TICKS) return
        val progress = Mth.clamp(age / IMPACT_BURST_TICKS, 0f, 1f)
        val fade = 1f - progress
        renderOrb(
            shader,
            center,
            camera,
            Mth.lerp(progress, size * 0.18f, size),
            2.25f,
            1.05f * fade,
            worldTime + seed * 0.17f,
            modelView,
            worldPose
        )
        renderOrb(
            shader,
            center,
            camera,
            Mth.lerp(progress, size * 0.11f, size * 0.48f),
            3.4f,
            1.52f * fade,
            worldTime + seed * 0.17f + 3.1f,
            modelView,
            worldPose
        )

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        )
        repeat(IMPACT_BURST_RAYS) { ray ->
            val angle = seed * 0.371f + ray * 2.399963f
            val direction = Vec3(
                cos(angle.toDouble()),
                0.10 + (ray * 7 % 5) * 0.075,
                sin(angle.toDouble())
            ).normalize()
            val start = center.add(direction.scale(size * (0.08 + progress * 0.16)))
            val end = center.add(direction.scale(size * (0.48 + progress * 0.78)))
            emitRibbon(
                buffer,
                start,
                end,
                camera,
                size * 0.055f * fade,
                size * 0.012f * fade,
                255,
                204,
                78,
                (246f * fade).toInt()
            )
        }
        drawPositionColorBuffer(buffer.build(), modelView, worldPose)
    }

    private fun drawPositionColorBuffer(
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
        alpha: Int
    ) {
        val direction = to.subtract(from)
        val midpoint = from.add(to).scale(0.5)
        val sideUnit = direction.cross(camera.subtract(midpoint)).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(1.0, 0.0, 0.0)
        }
        val fromSide = sideUnit.scale(fromHalfWidth.toDouble())
        val toSide = sideUnit.scale(toHalfWidth.toDouble())
        val a = from.subtract(camera).add(fromSide)
        val b = from.subtract(camera).subtract(fromSide)
        val c = to.subtract(camera).subtract(toSide)
        val d = to.subtract(camera).add(toSide)
        buffer.addVertex(a.x.toFloat(), a.y.toFloat(), a.z.toFloat()).setColor(red, green, blue, alpha)
        buffer.addVertex(b.x.toFloat(), b.y.toFloat(), b.z.toFloat()).setColor(red, green, blue, alpha)
        buffer.addVertex(c.x.toFloat(), c.y.toFloat(), c.z.toFloat()).setColor(red, green, blue, alpha)
        buffer.addVertex(d.x.toFloat(), d.y.toFloat(), d.z.toFloat()).setColor(red, green, blue, alpha)
    }

    private fun addSphere(buffer: com.mojang.blaze3d.vertex.BufferBuilder) {
        for (latitude in 0 until LATITUDE_SEGMENTS) {
            val theta0 = (latitude.toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            val theta1 = ((latitude + 1).toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            for (longitude in 0 until LONGITUDE_SEGMENTS) {
                val phi0 = longitude.toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                val phi1 = (longitude + 1).toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                addTriangle(buffer, theta0, phi0, theta1, phi0, theta1, phi1)
                addTriangle(buffer, theta0, phi0, theta1, phi1, theta0, phi1)
            }
        }
    }

    private fun addTriangle(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
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

    private fun addSphereVertex(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        theta: Float,
        phi: Float
    ) {
        val cosTheta = Mth.cos(theta)
        buffer.addVertex(
            cosTheta * Mth.cos(phi),
            Mth.sin(theta),
            cosTheta * Mth.sin(phi)
        ).setColor(255, 255, 255, 255)
    }

    private fun interpolatedPosition(entity: Entity, partialTick: Float): Vec3 =
        Vec3(
            Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x),
            Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y),
            Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)
        )
}
