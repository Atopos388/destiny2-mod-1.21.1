package atopos.destiny2.client.renderer

import atopos.destiny2.common.entity.WellOfRadianceEntity
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
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Client-only light volume for the Well of Radiance.
 *
 * The orb follows ShaderTest's black-hole technique: a real sphere mesh is rendered
 * with camera position in object space, then the fragment shader shades the ray/sphere
 * intersection procedurally. The result is bright and translucent rather than a flat
 * billboard. Ribbons are generated independently so they can track players in the well.
 */
object WellOfRadianceWorldRenderer {
    private const val QUERY_RANGE = 96.0
    private const val ORB_HEIGHT = 4.15
    private const val ORB_RADIUS = 0.52f
    private const val RADIAL_RAY_COUNT = 14
    private const val PLAYER_LINK_RADIUS = 8.0
    private const val PLAYER_LINK_STRANDS = 3
    private const val GROUND_RADIUS = 8.0f
    private const val GROUND_EXPAND_TICKS = 28.0f
    private const val GROUND_ANGULAR_SEGMENTS = 64
    private const val BLADE_AURA_HEIGHT = 3.82
    private const val BLADE_AURA_PLANES = 3
    private const val BLADE_AURA_SEGMENTS = 12
    private const val EDGE_MIST_SEGMENTS = 96
    private const val EDGE_MIST_SHELLS = 2
    private const val RISING_MOTE_COUNT = 192
    private const val LATITUDE_SEGMENTS = 20
    private const val LONGITUDE_SEGMENTS = 32

    @Volatile
    private var orbShader: ShaderInstance? = null
    @Volatile
    private var groundShader: ShaderInstance? = null
    @Volatile
    private var edgeMistShader: ShaderInstance? = null

    fun register() {
        CoreShaderRegistrationCallback.EVENT.register { context ->
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "well_radiance_orb"),
                DefaultVertexFormat.POSITION_COLOR
            ) { shader -> orbShader = shader }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "well_radiance_ground"),
                DefaultVertexFormat.POSITION_COLOR
            ) { shader -> groundShader = shader }
            context.register(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "well_radiance_edge_mist"),
                DefaultVertexFormat.POSITION_TEX_COLOR
            ) { shader -> edgeMistShader = shader }
        }
        WorldRenderEvents.AFTER_TRANSLUCENT.register(::renderOrbs)
    }

    private fun visibleWells(context: WorldRenderContext): List<WellOfRadianceEntity> {
        val camera = context.camera().position
        return context.world().getEntitiesOfClass(
            WellOfRadianceEntity::class.java,
            AABB(camera, camera).inflate(QUERY_RANGE)
        ) { it.isAlive }
    }

    private fun addRadialShaft(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        start: Vec3,
        direction: Vec3,
        length: Double,
        camera: Vec3,
    ) {
        val segments = 7
        for (segment in 0 until segments) {
            val t0 = segment.toDouble() / segments
            val t1 = (segment + 1).toDouble() / segments
            val p0 = start.add(direction.scale(length * t0))
            val p1 = start.add(direction.scale(length * t1))
            val width0 = Mth.lerp(t0.toFloat(), 0.004f, 0.030f)
            val width1 = Mth.lerp(t1.toFloat(), 0.004f, 0.030f)
            val midpoint = (t0 + t1) * 0.5
            val fade = sin(PI * midpoint).toFloat()
            emitRibbon(buffer, p0, p1, camera, width0, width1, 255, 214, 142, (8f * fade).toInt())
            emitRibbon(
                buffer,
                p0,
                p1,
                camera,
                width0 * 0.24f,
                width1 * 0.24f,
                255,
                251,
                230,
                (26f * fade).toInt()
            )
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
        alpha: Int
    ) {
        val direction = to.subtract(from)
        val midpoint = from.add(to).scale(0.5)
        val view = camera.subtract(midpoint)
        val sideUnit = direction.cross(view).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize()
            else Vec3(1.0, 0.0, 0.0)
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

    private fun renderOrbs(context: WorldRenderContext) {
        val shader = orbShader ?: return
        val poseStack = context.matrixStack() ?: return
        val camera = context.camera().position
        val partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true)
        val wells = visibleWells(context)
        if (wells.isEmpty()) return

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
        renderGroundAndBladeLight(
            wells,
            camera,
            partialTick,
            modelView,
            poseStack.last().pose(),
            context.world().gameTime + partialTick
        )
        edgeMistShader?.let { shader ->
            renderEdgeMist(
                wells,
                camera,
                partialTick,
                modelView,
                poseStack.last().pose(),
                context.world().gameTime + partialTick,
                shader
            )
        }
        renderTyndallRays(
            wells,
            context.world().players(),
            camera,
            partialTick,
            modelView,
            poseStack.last().pose()
        )

        wells.forEach { well ->
            val center = interpolatedPosition(well, partialTick).add(0.0, ORB_HEIGHT, 0.0)
            val fadeIn = Mth.clamp((well.tickCount + partialTick) / 12f, 0f, 1f)
            val scale = ORB_RADIUS * fadeIn
            if (scale <= 0.001f) return@forEach

            modelView.pushMatrix()
            modelView.mul(poseStack.last().pose())
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
            shader.getUniform("Time")?.set((context.world().gameTime + partialTick) * 0.035f)
            shader.getUniform("Pulse")?.set(0.82f + sin((well.tickCount + partialTick) * 0.09f) * 0.12f)
            val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
            addSphere(buffer)
            RenderSystem.setShader { shader }
            BufferUploader.drawWithShader(buffer.buildOrThrow())

            modelView.popMatrix()
            RenderSystem.applyModelViewMatrix()
        }

        RenderSystem.enableCull()
        RenderSystem.depthMask(true)
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableBlend()
    }

    private fun renderGroundAndBladeLight(
        wells: List<WellOfRadianceEntity>,
        camera: Vec3,
        partialTick: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        worldTime: Float
    ) {
        renderFluidGround(wells, camera, partialTick, modelView, worldPose, worldTime)

        val auraBuffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.POSITION_COLOR
        )
        wells.forEach { well ->
            val origin = interpolatedPosition(well, partialTick)
            val age = well.tickCount + partialTick
            addBladeAura(
                auraBuffer,
                origin,
                camera,
                age
            )
        }
        drawPositionColorBuffer(auraBuffer.build(), modelView, worldPose)
    }

    /**
     * The ground uses the same procedural-noise language as the orb. Curved
     * contour filaments are advected away from the centre in the fragment
     * shader, producing a continuous water-current texture instead of rings.
     */
    private fun renderFluidGround(
        wells: List<WellOfRadianceEntity>,
        camera: Vec3,
        partialTick: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        worldTime: Float
    ) {
        val shader = groundShader ?: return
        wells.forEach { well ->
            val age = well.tickCount + partialTick
            val linearProgress = Mth.clamp(age / GROUND_EXPAND_TICKS, 0f, 1f)
            val inverse = 1f - linearProgress
            val expansion = 1f - inverse * inverse * inverse
            val radius = GROUND_RADIUS * expansion
            if (radius <= 0.01f) return@forEach

            val center = interpolatedPosition(well, partialTick)
            val intensity = Mth.clamp(age / 14f, 0f, 1f) *
                (0.92f + sin(age * 0.055f) * 0.08f)
            modelView.pushMatrix()
            modelView.mul(worldPose)
            modelView.translate(
                (center.x - camera.x).toFloat(),
                (center.y + 0.035 - camera.y).toFloat(),
                (center.z - camera.z).toFloat()
            )
            modelView.scale(radius, 1f, radius)
            RenderSystem.applyModelViewMatrix()

            shader.getUniform("Time")?.set(worldTime * 0.045f)
            shader.getUniform("Intensity")?.set(intensity)
            shader.getUniform("Radius")?.set(radius)
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR
            )
            addGroundDisk(buffer)
            RenderSystem.setShader { shader }
            BufferUploader.drawWithShader(buffer.buildOrThrow())

            modelView.popMatrix()
            RenderSystem.applyModelViewMatrix()
        }
    }

    private fun addGroundDisk(buffer: com.mojang.blaze3d.vertex.BufferBuilder) {
        for (segment in 0 until GROUND_ANGULAR_SEGMENTS) {
            val angle0 = segment * Mth.TWO_PI / GROUND_ANGULAR_SEGMENTS
            val angle1 = (segment + 1) * Mth.TWO_PI / GROUND_ANGULAR_SEGMENTS
            buffer.addVertex(0f, 0f, 0f).setColor(255, 255, 255, 255)
            buffer.addVertex(Mth.cos(angle0), 0f, Mth.sin(angle0)).setColor(255, 255, 255, 255)
            buffer.addVertex(Mth.cos(angle1), 0f, Mth.sin(angle1)).setColor(255, 255, 255, 255)
        }
    }

    /**
     * Crossed tapered planes form a small additive envelope around the real
     * sword geometry. This is a halo only: the textured emissive sword and its
     * authored outline remain rendered by WellOfRadianceRenderer.
     */
    private fun addBladeAura(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        origin: Vec3,
        camera: Vec3,
        age: Float
    ) {
        val fadeIn = Mth.clamp((age - 4f) / 10f, 0f, 1f)
        if (fadeIn <= 0f) return
        val pulse = 0.88f + sin(age * 0.075f) * 0.12f

        repeat(BLADE_AURA_PLANES) { plane ->
            val angle = plane * Mth.PI / BLADE_AURA_PLANES
            val side = Vec3(cos(angle.toDouble()), 0.0, sin(angle.toDouble()))
            for (segment in 0 until BLADE_AURA_SEGMENTS) {
                val t0 = segment.toFloat() / BLADE_AURA_SEGMENTS
                val t1 = (segment + 1).toFloat() / BLADE_AURA_SEGMENTS
                val envelope0 = sin(PI * t0).toFloat().coerceAtLeast(0f)
                val envelope1 = sin(PI * t1).toFloat().coerceAtLeast(0f)
                val width0 = 0.045 + envelope0 * 0.18
                val width1 = 0.045 + envelope1 * 0.18
                val y0 = origin.y + 0.03 + BLADE_AURA_HEIGHT * t0
                val y1 = origin.y + 0.03 + BLADE_AURA_HEIGHT * t1
                val center0 = Vec3(origin.x, y0, origin.z)
                val center1 = Vec3(origin.x, y1, origin.z)
                val p0 = center0.subtract(side.scale(width0))
                val p1 = center0.add(side.scale(width0))
                val p2 = center1.add(side.scale(width1))
                val p3 = center1.subtract(side.scale(width1))
                val alpha0 = ((4f + envelope0 * 7f) * fadeIn * pulse).toInt()
                val alpha1 = ((4f + envelope1 * 7f) * fadeIn * pulse).toInt()
                addLightVertex(buffer, p0, camera, 255, 220, 142, alpha0)
                addLightVertex(buffer, p1, camera, 255, 220, 142, alpha0)
                addLightVertex(buffer, p2, camera, 255, 250, 225, alpha1)
                addLightVertex(buffer, p3, camera, 255, 250, 225, alpha1)
            }
        }
    }

    /** Two continuous cylindrical shells form a fully connected rising rim. */
    private fun renderEdgeMist(
        wells: List<WellOfRadianceEntity>,
        camera: Vec3,
        partialTick: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f,
        worldTime: Float,
        shader: ShaderInstance
    ) {
        wells.forEach { well ->
            val age = well.tickCount + partialTick
            val linearProgress = Mth.clamp(age / GROUND_EXPAND_TICKS, 0f, 1f)
            val inverse = 1f - linearProgress
            val radius = GROUND_RADIUS * (1f - inverse * inverse * inverse)
            val fadeIn = Mth.clamp(age / 16f, 0f, 1f)
            if (radius <= 0.1f || fadeIn <= 0f) return@forEach

            val origin = interpolatedPosition(well, partialTick)
            val buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX_COLOR
            )
            repeat(EDGE_MIST_SHELLS) { shell ->
                val shellRadius = radius * (0.945 + shell * 0.035)
                val shellAlpha = 220 - shell * 48
                val rotation = age * (0.00042f + shell * 0.00016f)
                for (segment in 0 until EDGE_MIST_SEGMENTS) {
                    val u0 = segment.toFloat() / EDGE_MIST_SEGMENTS
                    val u1 = (segment + 1).toFloat() / EDGE_MIST_SEGMENTS
                    val angle0 = u0 * Mth.TWO_PI + rotation
                    val angle1 = u1 * Mth.TWO_PI + rotation

                    fun topHeight(angle: Float): Double {
                        val broad = sin((angle * 5.0f - age * 0.012f + shell).toDouble()) * 0.16
                        val detail = sin((angle * 11.0f + age * 0.008f + shell * 1.7f).toDouble()) * 0.08
                        return 1.18 + broad + detail + shell * 0.16
                    }

                    fun point(angle: Float, height: Double, top: Boolean): Vec3 {
                        val topOffset = if (top) {
                            sin((angle * 7.0f - age * 0.010f).toDouble()) * 0.055 - 0.06
                        } else {
                            0.0
                        }
                        val pointRadius = shellRadius + topOffset
                        return origin.add(
                            cos(angle.toDouble()) * pointRadius,
                            height,
                            sin(angle.toDouble()) * pointRadius
                        )
                    }

                    val bottom0 = point(angle0, 0.035, false)
                    val bottom1 = point(angle1, 0.035, false)
                    val top0 = point(angle0, topHeight(angle0), true)
                    val top1 = point(angle1, topHeight(angle1), true)
                    addEdgeMistVertex(buffer, bottom0, camera, u0, 1f, shellAlpha)
                    addEdgeMistVertex(buffer, bottom1, camera, u1, 1f, shellAlpha)
                    addEdgeMistVertex(buffer, top1, camera, u1, 0f, shellAlpha)
                    addEdgeMistVertex(buffer, top0, camera, u0, 0f, shellAlpha)
                }
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

    private fun addEdgeMistVertex(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        position: Vec3,
        camera: Vec3,
        u: Float,
        v: Float,
        alpha: Int
    ) {
        val relative = position.subtract(camera)
        buffer.addVertex(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat())
            .setUv(u, v)
            .setColor(255, 242, 196, alpha)
    }

    private fun addLightVertex(
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

    private fun renderTyndallRays(
        wells: List<WellOfRadianceEntity>,
        players: List<Player>,
        camera: Vec3,
        partialTick: Float,
        modelView: org.joml.Matrix4fStack,
        worldPose: org.joml.Matrix4f
    ) {
        val buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)

        wells.forEach { well ->
            val wellPosition = interpolatedPosition(well, partialTick)
            val orb = wellPosition.add(0.0, ORB_HEIGHT, 0.0)
            val age = well.tickCount + partialTick
            repeat(RADIAL_RAY_COUNT) { ray ->
                val angle = ray * Mth.TWO_PI / RADIAL_RAY_COUNT + age * 0.0015f
                val elevation = when (ray % 4) {
                    0 -> -0.48
                    1 -> -0.22
                    2 -> 0.04
                    else -> 0.27
                }
                val horizontal = cos(elevation)
                val direction = Vec3(
                    cos(angle.toDouble()) * horizontal,
                    sin(elevation),
                    sin(angle.toDouble()) * horizontal
                ).normalize()
                val start = orb.add(direction.scale(ORB_RADIUS * 0.76))
                val length = 2.4 + (ray * 37 % 7) * 0.24
                addRadialShaft(buffer, start, direction, length, camera)
            }

            val linearProgress = Mth.clamp(age / GROUND_EXPAND_TICKS, 0f, 1f)
            val inverse = 1f - linearProgress
            val moteRadius = GROUND_RADIUS * (1f - inverse * inverse * inverse)
            addRisingMotes(
                buffer,
                wellPosition,
                camera,
                moteRadius,
                age,
                Mth.clamp(age / 14f, 0f, 1f)
            )

            players.asSequence()
                .filter { player ->
                    player.isAlive &&
                        !player.isSpectator &&
                        player.distanceToSqr(well) <= PLAYER_LINK_RADIUS * PLAYER_LINK_RADIUS
                }
                .forEach { player ->
                    val target = interpolatedPosition(player, partialTick)
                        .add(0.0, player.bbHeight * 0.62, 0.0)
                    addPlayerLink(
                        buffer,
                        orb,
                        target,
                        camera,
                        age,
                        player.id
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

    /** Tiny white-gold motes rise roughly 1.5 blocks across the active well. */
    private fun addRisingMotes(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        center: Vec3,
        camera: Vec3,
        radius: Float,
        age: Float,
        fadeIn: Float
    ) {
        if (radius <= 0.05f) return
        repeat(RISING_MOTE_COUNT) { mote ->
            val phase = (age * 0.012f + mote * 0.6180339f) % 1f
            val radialSeed =
                ((mote * 53 + 17) % RISING_MOTE_COUNT + 0.5) / RISING_MOTE_COUNT
            val pointRadius = kotlin.math.sqrt(radialSeed) *
                (radius - 0.24f).coerceAtLeast(0f)
            val angle =
                mote * 2.399963f +
                sin(age * 0.0045f + mote * 0.71f) * 0.085f
            val height = 0.05 + phase * 1.5
            val envelope = sin((phase * Mth.PI).toDouble())
                .toFloat()
                .coerceAtLeast(0f)
            val twinkle = 0.70f + sin(age * 0.15f + mote * 1.83f) * 0.30f
            val outerAlpha = (36f * fadeIn * envelope * twinkle).toInt()
            val coreAlpha = (72f * fadeIn * envelope * twinkle).toInt()
            if (coreAlpha <= 0) return@repeat

            val point = center.add(
                cos(angle.toDouble()) * pointRadius,
                height,
                sin(angle.toDouble()) * pointRadius
            )
            val sway = sin(age * 0.052f + mote * 1.29f) * 0.014
            val top = point.add(sway, 0.030, -sway)
            emitRibbon(
                buffer,
                point,
                top,
                camera,
                0.016f,
                0.010f,
                255,
                210,
                132,
                outerAlpha
            )
            emitRibbon(
                buffer,
                point,
                top,
                camera,
                0.007f,
                0.0045f,
                255,
                252,
                232,
                coreAlpha
            )
        }
    }

    /**
     * Three very thin curved strands connect the orb to every player inside the
     * well. Both ends are rebuilt from interpolated positions every frame, so
     * the final section remains attached while the player moves.
     */
    private fun addPlayerLink(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        orb: Vec3,
        playerTarget: Vec3,
        camera: Vec3,
        age: Float,
        playerId: Int
    ) {
        val direct = playerTarget.subtract(orb)
        if (direct.lengthSqr() <= 1.0e-6) return

        var side = direct.cross(Vec3(0.0, 1.0, 0.0))
        side = if (side.lengthSqr() > 1.0e-8) side.normalize() else Vec3(1.0, 0.0, 0.0)
        val segments = 8

        repeat(PLAYER_LINK_STRANDS) { strand ->
            val phase = age * 0.018 + playerId * 0.37 + strand * Mth.TWO_PI / PLAYER_LINK_STRANDS
            val startOffset = Vec3(
                cos(phase.toDouble()) * 0.085,
                sin((phase * 1.31f).toDouble()) * 0.055,
                sin(phase.toDouble()) * 0.085
            )
            val start = orb.add(startOffset)
            val bend = side.scale(sin((phase * 0.73f).toDouble()) * 0.11)
                .add(0.0, 0.12 + strand * 0.025, 0.0)
            val control = start.add(playerTarget).scale(0.5).add(bend)

            fun point(t: Double): Vec3 {
                val inverse = 1.0 - t
                return start.scale(inverse * inverse)
                    .add(control.scale(2.0 * inverse * t))
                    .add(playerTarget.scale(t * t))
            }

            for (segment in 0 until segments) {
                val t0 = segment.toDouble() / segments
                val t1 = (segment + 1).toDouble() / segments
                val p0 = point(t0)
                val p1 = point(t1)
                val midpoint = (t0 + t1) * 0.5
                val endAttachment = Mth.clamp(((midpoint - 0.55) / 0.45).toFloat(), 0f, 1f)
                val outerAlpha = (4f + endAttachment * 4f).toInt()
                val coreAlpha = (12f + endAttachment * 8f).toInt()
                val width = 0.004f + endAttachment * 0.002f
                emitRibbon(buffer, p0, p1, camera, width, width, 255, 221, 158, outerAlpha)
                emitRibbon(
                    buffer,
                    p0,
                    p1,
                    camera,
                    width * 0.28f,
                    width * 0.28f,
                    255,
                    252,
                    235,
                    coreAlpha
                )
            }
        }
    }

    private fun addSphere(buffer: com.mojang.blaze3d.vertex.BufferBuilder) {
        for (lat in 0 until LATITUDE_SEGMENTS) {
            val theta0 = (lat.toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            val theta1 = ((lat + 1).toFloat() / LATITUDE_SEGMENTS - 0.5f) * Mth.PI
            for (lon in 0 until LONGITUDE_SEGMENTS) {
                val phi0 = lon.toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                val phi1 = (lon + 1).toFloat() / LONGITUDE_SEGMENTS * Mth.TWO_PI
                addTriangle(buffer, theta0, phi0, theta1, phi0, theta1, phi1)
                addTriangle(buffer, theta0, phi0, theta1, phi1, theta0, phi1)
            }
        }
    }

    private fun addTriangle(
        buffer: com.mojang.blaze3d.vertex.BufferBuilder,
        thetaA: Float, phiA: Float,
        thetaB: Float, phiB: Float,
        thetaC: Float, phiC: Float
    ) {
        addSphereVertex(buffer, thetaA, phiA)
        addSphereVertex(buffer, thetaB, phiB)
        addSphereVertex(buffer, thetaC, phiC)
    }

    private fun addSphereVertex(buffer: com.mojang.blaze3d.vertex.BufferBuilder, theta: Float, phi: Float) {
        val cosTheta = Mth.cos(theta)
        buffer.addVertex(cosTheta * Mth.cos(phi), Mth.sin(theta), cosTheta * Mth.sin(phi))
            .setColor(255, 248, 224, 255)
    }

    private fun interpolatedPosition(entity: net.minecraft.world.entity.Entity, partialTick: Float): Vec3 =
        Vec3(
            Mth.lerp(partialTick.toDouble(), entity.xOld, entity.x),
            Mth.lerp(partialTick.toDouble(), entity.yOld, entity.y),
            Mth.lerp(partialTick.toDouble(), entity.zOld, entity.z)
        )
}
