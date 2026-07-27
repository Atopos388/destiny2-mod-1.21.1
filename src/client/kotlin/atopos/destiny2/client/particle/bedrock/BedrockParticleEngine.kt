package atopos.destiny2.client.particle.bedrock

import atopos.destiny2.common.item.ForgottenNameItem
import atopos.destiny2.common.weapon.WeaponAmmoState
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import de.tomalbrc.sandstorm.Sandstorm
import de.tomalbrc.sandstorm.component.ParticleComponentHolder
import de.tomalbrc.sandstorm.component.ParticleComponentMap
import de.tomalbrc.sandstorm.component.ParticleComponents
import de.tomalbrc.sandstorm.component.emitter.*
import de.tomalbrc.sandstorm.component.particle.*
import de.tomalbrc.sandstorm.io.ParticleEffectFile
import de.tomalbrc.sandstorm.util.EmitterDirection
import gg.moonflower.molangcompiler.api.MolangExpression
import gg.moonflower.molangcompiler.api.MolangRuntime
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import org.slf4j.LoggerFactory
import software.bernie.geckolib.animatable.GeoItem
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*
import kotlin.random.Random

/** Client-native runtime for Snowstorm/Bedrock particle JSON files. */
object BedrockParticleEngine {
    private val logger = LoggerFactory.getLogger("DestinyBedrockParticleEngine")
    private val worldEmitters = mutableListOf<Emitter>()
    private val pendingWorldEmitters = mutableListOf<PendingWorldEmitter>()
    private val attachedEmitters = ConcurrentHashMap<String, MutableList<Emitter>>()
    private val lastAttachedKeyframeAtNanos = ConcurrentHashMap<AttachedKeyframe, Long>()
    private val renderContext = ThreadLocal<AttachmentContext?>()
    @Volatile private var recentAttachmentContext: TimedAttachmentContext? = null
    private const val DT = 1f / 20f
    private const val MAX_PARTICLES_PER_EMITTER = 20_000
    private const val ATTACHMENT_CONTEXT_TIMEOUT_NANOS = 500_000_000L
    private const val KEYFRAME_DUPLICATE_WINDOW_NANOS = 100_000_000L
    private const val EVENT_HISTORY_NANOS = 30_000_000_000L
    private const val RELOAD_EFFECT_PATH = "ancient_city_weapon_echo"

    data class AttachmentContext(val key: String, val model: ResourceLocation)

    fun register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(BedrockParticleResources)
        ClientTickEvents.END_CLIENT_TICK.register {
            if (it.level == null) {
                worldEmitters.clear(); pendingWorldEmitters.clear(); attachedEmitters.clear()
                lastAttachedKeyframeAtNanos.clear()
                recentAttachmentContext = null
            } else {
                val pending = pendingWorldEmitters.iterator()
                while (pending.hasNext()) {
                    val item = pending.next()
                    item.remainingTicks--
                    if (item.remainingTicks <= 0) {
                        playWorld(item.effect, item.position, item.yaw, item.pitch)
                        pending.remove()
                    }
                }
                tickList(worldEmitters)
                attachedEmitters.entries.removeIf { (_, list) -> tickList(list); list.isEmpty() }
            }
        }
        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            val pose = context.matrixStack() ?: return@register
            val buffers = context.consumers() ?: return@register
            val camera = context.camera()
            val partial = context.tickCounter().getGameTimeDeltaPartialTick(true)
            worldEmitters.forEach { emitter -> renderEmitter(emitter, pose, buffers, camera.position, camera.rotation(), partial, false) }
        }
    }

    private fun tickList(list: MutableList<Emitter>) {
        val iterator = list.iterator()
        while (iterator.hasNext()) if (!iterator.next().tick(DT)) iterator.remove()
    }

    fun <T> withAttachmentContext(key: String, model: ResourceLocation, action: () -> T): T {
        val previous = renderContext.get()
        val context = AttachmentContext(key, model)
        renderContext.set(context)
        recentAttachmentContext = TimedAttachmentContext(context, System.nanoTime())
        return try { action() } finally { renderContext.set(previous) }
    }

    fun handleKeyframe(effect: String, locator: String) {
        val context = renderContext.get() ?: recentAttachmentContext
            ?.takeIf { System.nanoTime() - it.updatedAtNanos <= ATTACHMENT_CONTEXT_TIMEOUT_NANOS }
            ?.context
            ?: run {
                logger.debug("Dropped particle keyframe {} because no recently rendered weapon is attached", effect)
                return
            }
        val id = parseEffectId(effect) ?: return
        val reloadAction = currentReloadAction(id)
        val keyframe = AttachedKeyframe(context.key, context.model, id, locator, reloadAction)
        val now = System.nanoTime()
        if (reloadAction != null) {
            if (lastAttachedKeyframeAtNanos.putIfAbsent(keyframe, now) != null) return
            lastAttachedKeyframeAtNanos.entries.removeIf { now - it.value > EVENT_HISTORY_NANOS }
        } else {
            val previous = lastAttachedKeyframeAtNanos.put(keyframe, now)
            if (previous != null && now - previous < KEYFRAME_DUPLICATE_WINDOW_NANOS) return
        }
        playAttached(context.key, context.model, id, locator)
    }

    fun playAttached(key: String, model: ResourceLocation, effect: ResourceLocation, locator: String) {
        val definition = BedrockParticleResources.effect(effect)
        if (definition == null) {
            logger.warn("Unknown Bedrock particle effect {}", effect)
            return
        }
        val emitter = Emitter(definition, Vec3.ZERO, true, model, locator)
        attachedEmitters.computeIfAbsent(key) { mutableListOf() }.add(emitter)
    }

    fun playWorld(effect: ResourceLocation, position: Vec3) {
        playWorld(effect, position, 0f, 0f)
    }

    fun playWorld(effect: ResourceLocation, position: Vec3, yaw: Float, pitch: Float) {
        val yawRadians = Math.toRadians(yaw.toDouble()).toFloat()
        val pitchRadians = Math.toRadians(pitch.toDouble()).toFloat()
        val cosPitch = cos(pitchRadians)
        val forward = Vector3f(
            -sin(yawRadians) * cosPitch,
            -sin(pitchRadians),
            cos(yawRadians) * cosPitch
        ).normalizeSafe()
        BedrockParticleResources.effect(effect)?.let {
            worldEmitters.add(Emitter(it, position, false, null, "", forward))
        }
            ?: logger.warn("Unknown Bedrock particle effect {}", effect)
    }

    fun queueWorld(
        effect: ResourceLocation,
        position: Vec3,
        yaw: Float,
        pitch: Float,
        delayTicks: Int
    ) {
        if (effect.path == "solar_snap__ignition_flash") {
            logger.info(
                "Queued solar snap VFX at [{}, {}, {}], yaw={}, pitch={}",
                position.x,
                position.y,
                position.z,
                yaw,
                pitch
            )
        }
        if (delayTicks <= 0) {
            playWorld(effect, position, yaw, pitch)
            return
        }
        pendingWorldEmitters.add(
            PendingWorldEmitter(
                effect = effect,
                position = position,
                yaw = yaw,
                pitch = pitch,
                remainingTicks = delayTicks
            )
        )
    }

    fun renderAttached(
        key: String,
        model: ResourceLocation,
        parentBone: String,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        partialTick: Float
    ) {
        val list = attachedEmitters[key] ?: return
        for (emitter in list) {
            if (emitter.model != model) continue
            val locator = BedrockParticleResources.locator(model, emitter.locator)
            // TaCZ functional position groups are bones rather than Bedrock
            // locators. When a keyframe targets muzzle_flash or shell directly,
            // the current recursive bone pose is already the desired origin.
            if (locator == null && emitter.locator != parentBone) continue
            if (locator != null && locator.parentBone != parentBone) continue
            poseStack.pushPose()
            if (locator != null) {
                poseStack.translate(locator.x, locator.y, locator.z)
                if (locator.rotationZ != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(locator.rotationZ))
                if (locator.rotationY != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(locator.rotationY))
                if (locator.rotationX != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(locator.rotationX))
            }
            renderEmitter(emitter, poseStack, buffers, Vec3.ZERO, Quaternionf(), partialTick, true)
            poseStack.popPose()
        }
    }

    private fun parseEffectId(value: String): ResourceLocation? = runCatching {
        if (':' in value) ResourceLocation.parse(value)
        else ResourceLocation.fromNamespaceAndPath("destiny2-mod", value)
    }.getOrNull()

    private fun currentReloadAction(effect: ResourceLocation): ReloadAction? {
        if (effect.path != RELOAD_EFFECT_PATH) return null
        val client = Minecraft.getInstance()
        val level = client.level ?: return null
        val stack = client.player?.mainHandItem ?: return null
        val item = stack.item as? ForgottenNameItem ?: return null
        val state = WeaponAmmoState.read(stack, item.combatProfile(stack).magazineSize)
        if (state.reloadSequence <= 0) return null
        return ReloadAction(System.identityHashCode(level), GeoItem.getId(stack), state.reloadSequence)
    }

    private data class TimedAttachmentContext(
        val context: AttachmentContext,
        val updatedAtNanos: Long
    )

    private data class AttachedKeyframe(
        val attachmentKey: String,
        val model: ResourceLocation,
        val effect: ResourceLocation,
        val locator: String,
        val reloadAction: ReloadAction?
    )

    private data class ReloadAction(
        val levelIdentity: Int,
        val stackId: Long,
        val reloadSequence: Int
    )

    private data class PendingWorldEmitter(
        val effect: ResourceLocation,
        val position: Vec3,
        val yaw: Float,
        val pitch: Float,
        var remainingTicks: Int
    )

    private class Emitter(
        val file: ParticleEffectFile,
        val origin: Vec3,
        val attached: Boolean,
        val model: ResourceLocation?,
        val locator: String,
        val worldForward: Vector3f? = null
    ) : ParticleComponentHolder {
        val effectId: ResourceLocation = file.effect.description.identifier
        private val componentMap = ParticleComponentMap()
        val runtime: MolangRuntime = MolangRuntime.runtime().create()
        val particles = mutableListOf<Particle>()
        var age = 0f
        var emissionAccumulator = 0f
        var emitting = true
        private var emittedInstant = false

        init {
            initComponents(file.effect.components)
            val edit = runtime.edit()
            repeat(4) { edit.setVariable("emitter_random_${it + 1}", Random.nextFloat()) }
            file.effect.curves.forEach { (name, curve) -> edit.setVariable(name.substringAfter('.')) { env -> curve.evaluate(env) } }
            get(ParticleComponents.EMITTER_INITIALIZATION)?.creationExpression?.let(::resolve)
            // Bedrock evaluates instant/manual emission at emitter creation. Waiting for the
            // first 20 Hz client tick would already expire very short muzzle-flash emitters.
            emitParticles(0f)
        }

        override fun components(): ParticleComponentMap = componentMap

        fun tick(dt: Float): Boolean {
            age += dt
            runtime.edit().setVariable("emitter_age", age)
            get(ParticleComponents.EMITTER_INITIALIZATION)?.perUpdateExpression?.let(::resolve)
            val once = get(ParticleComponents.EMITTER_LIFETIME_ONCE)
            val looping = get(ParticleComponents.EMITTER_LIFETIME_LOOPING)
            when {
                once != null -> {
                    val lifetime = resolve(once.activeTime).coerceAtLeast(0f)
                    runtime.edit().setVariable("emitter_lifetime", lifetime)
                    emitting = age <= lifetime
                }
                looping != null -> {
                    val active = resolve(looping.activeTime).coerceAtLeast(0.0001f)
                    val sleep = resolve(looping.sleepTime).coerceAtLeast(0f)
                    val cycle = active + sleep
                    runtime.edit().setVariable("emitter_lifetime", active)
                    emitting = age % cycle <= active
                }
                else -> {
                    val life = get(ParticleComponents.EMITTER_LIFETIME_EXPRESSION)
                    if (life != null) emitting = resolve(life.activationExpression) != 0f && resolve(life.expirationExpression) == 0f
                }
            }
            emitParticles(dt)
            particles.removeIf { !it.tick(this, dt) }
            return emitting || particles.isNotEmpty()
        }

        private fun emitParticles(dt: Float) {
            if (!emitting || particles.size >= MAX_PARTICLES_PER_EMITTER) return
            val instant = get(ParticleComponents.EMITTER_RATE_INSTANT)
            if (instant != null && !emittedInstant) {
                repeat(resolve(instant.numParticles).roundToInt().coerceIn(0, MAX_PARTICLES_PER_EMITTER)) { emit() }
                emittedInstant = true
                return
            }
            val steady = get(ParticleComponents.EMITTER_RATE_STEADY)
            if (steady != null) {
                val max = resolve(steady.maxParticles).roundToInt().coerceIn(0, MAX_PARTICLES_PER_EMITTER)
                emissionAccumulator += resolve(steady.spawnRate).coerceAtLeast(0f) * dt
                while (emissionAccumulator >= 1f && particles.size < max) { emit(); emissionAccumulator-- }
            }
            val manual = get(ParticleComponents.EMITTER_RATE_MANUAL)
            if (manual != null && !emittedInstant) {
                repeat(resolve(manual.maxParticles).roundToInt().coerceIn(0, MAX_PARTICLES_PER_EMITTER)) { emit() }
                emittedInstant = true
            }
        }

        private fun emit() {
            val (localOffset, localDirection) = initialShape(this)
            val offset = orientLocal(localOffset)
            val direction = orientLocal(localDirection).normalizeSafe()
            val particle = Particle(offset, direction)
            val speed = get(ParticleComponents.PARTICLE_INITIAL_SPEED)?.value()
            if (!speed.isNullOrEmpty()) {
                val sx = resolve(speed[0]); val sy = if (speed.size > 1) resolve(speed[1]) else sx; val sz = if (speed.size > 2) resolve(speed[2]) else sx
                particle.velocity.set(direction.x * sx, direction.y * sy, direction.z * sz)
            }
            get(ParticleComponents.PARTICLE_INITIAL_SPIN)?.let {
                particle.rotation = resolve(it.rotation); particle.rotationRate = resolve(it.rotationRate)
            }
            get(ParticleComponents.PARTICLE_LIFETIME_EXPRESSION)?.let {
                particle.lifetime = resolve(it.maxLifetime).coerceAtLeast(DT)
            }
            particles.add(particle)
        }

        private fun orientLocal(vector: Vector3f): Vector3f {
            val forward = worldForward ?: return vector
            var right = Vector3f(forward).cross(0f, 1f, 0f)
            if (right.lengthSquared() <= 1e-8f) {
                right = Vector3f(-1f, 0f, 0f)
            } else {
                right.normalize()
            }
            val up = Vector3f(right).cross(forward).normalizeSafe()
            return Vector3f(right).mul(vector.x)
                .add(Vector3f(up).mul(vector.y))
                .add(Vector3f(forward).mul(vector.z))
        }

        fun resolve(expression: MolangExpression?): Float =
            if (expression == null) 0f else runCatching { runtime.resolve(expression) }.getOrDefault(0f)
    }

    private class Particle(offset: Vector3f, direction: Vector3f) {
        val position = Vector3f(offset)
        val previous = Vector3f(offset)
        val direction = Vector3f(direction)
        val velocity = Vector3f()
        val acceleration = Vector3f()
        val random = FloatArray(4) { Random.nextFloat() }
        var age = 0f
        var lifetime = 1f
        var rotation = 0f
        var rotationRate = 0f

        fun tick(emitter: Emitter, dt: Float): Boolean {
            previous.set(position)
            age += dt
            val edit = emitter.runtime.edit()
            repeat(4) { edit.setVariable("particle_random_${it + 1}", random[it]) }
            edit.setVariable("particle_age", age).setVariable("particle_lifetime", lifetime)
            val life = emitter.get(ParticleComponents.PARTICLE_LIFETIME_EXPRESSION)
            if (age >= lifetime || (life != null && emitter.resolve(life.expirationExpression) != 0f)) return false
            emitter.get(ParticleComponents.PARTICLE_MOTION_PARAMETRIC)?.let {
                position.set(emitter.resolve(it.relativePosition[0]), emitter.resolve(it.relativePosition[1]), emitter.resolve(it.relativePosition[2]))
                if (it.direction.size >= 3) velocity.set(emitter.resolve(it.direction[0]), emitter.resolve(it.direction[1]), emitter.resolve(it.direction[2]))
                rotation = emitter.resolve(it.rotation)
            }
            emitter.get(ParticleComponents.PARTICLE_MOTION_DYNAMIC)?.let {
                acceleration.set(emitter.resolve(it.linearAcceleration[0]), emitter.resolve(it.linearAcceleration[1]), emitter.resolve(it.linearAcceleration[2]))
                val drag = emitter.resolve(it.linearDragCoefficient)
                acceleration.add(-velocity.x * drag, -velocity.y * drag, -velocity.z * drag)
                velocity.fma(dt, acceleration)
                position.fma(dt, velocity)
                val angularAcceleration = emitter.resolve(it.rotationAcceleration) - emitter.resolve(it.rotationDragCoefficient) * rotationRate
                rotationRate += angularAcceleration * dt
                rotation += rotationRate * dt
            }
            return true
        }
    }

    private fun initialShape(emitter: Emitter): Pair<Vector3f, Vector3f> {
        fun expr3(a: Array<MolangExpression>) = Vector3f(emitter.resolve(a.getOrNull(0)), emitter.resolve(a.getOrNull(1)), emitter.resolve(a.getOrNull(2)))
        emitter.get(ParticleComponents.EMITTER_SHAPE_POINT)?.let { return expr3(it.offset) to expr3(it.direction).normalizeSafe() }
        emitter.get(ParticleComponents.EMITTER_SHAPE_CUSTOM)?.let { return expr3(it.offset) to expr3(it.direction).normalizeSafe() }
        emitter.get(ParticleComponents.EMITTER_SHAPE_BOX)?.let {
            val center = expr3(it.offset); val half = expr3(it.halfDimensions)
            val p = Vector3f((Random.nextFloat()*2-1)*half.x, (Random.nextFloat()*2-1)*half.y, (Random.nextFloat()*2-1)*half.z)
            if (it.surfaceOnly) when (Random.nextInt(3)) { 0 -> p.x = Math.copySign(half.x, p.x); 1 -> p.y = Math.copySign(half.y, p.y); else -> p.z = Math.copySign(half.z, p.z) }
            return center.add(p) to p.normalizeSafe()
        }
        emitter.get(ParticleComponents.EMITTER_SHAPE_SPHERE)?.let {
            val center = expr3(it.offset); val dir = randomUnit(); val radius = emitter.resolve(it.radius) * if (it.surfaceOnly) 1f else Random.nextFloat().pow(1f/3f)
            return center.add(Vector3f(dir).mul(radius)) to dir
        }
        emitter.get(ParticleComponents.EMITTER_SHAPE_DISC)?.let {
            val center = expr3(it.offset); val normal = expr3(it.planeNormal).normalizeSafe(); val tangent = Vector3f(normal).cross(if (abs(normal.y) < .9f) Vector3f(0f,1f,0f) else Vector3f(1f,0f,0f)).normalizeSafe()
            val bitangent = Vector3f(normal).cross(tangent); val angle = Random.nextFloat() * (Math.PI * 2).toFloat(); val r = emitter.resolve(it.radius) * if (it.surfaceOnly) 1f else sqrt(Random.nextFloat())
            val p = tangent.mul(cos(angle)*r).add(bitangent.mul(sin(angle)*r))
            val explicitDirection = it.directionList
                ?.takeIf { expressions -> expressions.size >= 3 }
                ?.let(::expr3)
                ?.normalizeSafe()
            val radialDirection = Vector3f(p).normalizeSafe().let { direction ->
                if (it.direction == EmitterDirection.INWARDS) direction.negate() else direction
            }
            return center.add(p) to (explicitDirection ?: radialDirection)
        }
        return Vector3f() to Vector3f()
    }

    private fun randomUnit(): Vector3f {
        val z = Random.nextFloat() * 2 - 1; val a = Random.nextFloat() * (Math.PI * 2).toFloat(); val r = sqrt(1-z*z)
        return Vector3f(r*cos(a), z, r*sin(a))
    }

    private fun Vector3f.normalizeSafe(): Vector3f = if (lengthSquared() > 1e-8f) normalize() else this

    private fun renderEmitter(emitter: Emitter, pose: PoseStack, buffers: MultiBufferSource, cameraPos: Vec3, cameraRotation: Quaternionf, partial: Float, attached: Boolean) {
        val billboard = emitter.get(ParticleComponents.PARTICLE_APPEARANCE_BILLBOARD) ?: return
        val texture = textureLocation(emitter.file) ?: return
        val material = emitter.file.effect.description.renderParameters?.get("material") ?: "particles_blend"
        val renderType = when (material) {
            "particles_opaque" -> RenderType.entityCutoutNoCull(texture)
            "particles_add" -> RenderType.entityTranslucentEmissive(texture)
            else -> RenderType.entityTranslucent(texture)
        }
        val consumer = buffers.getBuffer(renderType)
        for (particle in emitter.particles) {
            updateParticleRuntime(emitter, particle, partial)
            val x = Mth.lerp(partial, particle.previous.x, particle.position.x)
            val y = Mth.lerp(partial, particle.previous.y, particle.position.y)
            val z = Mth.lerp(partial, particle.previous.z, particle.position.z)
            pose.pushPose()
            if (attached) pose.translate(x.toDouble(), y.toDouble(), z.toDouble())
            else pose.translate(emitter.origin.x + x - cameraPos.x, emitter.origin.y + y - cameraPos.y, emitter.origin.z + z - cameraPos.z)
            when (billboard.cameraMode) {
                ParticleAppearanceBillboard.CameraMode.LOOKAT_XYZ, ParticleAppearanceBillboard.CameraMode.ROTATE_XYZ,
                ParticleAppearanceBillboard.CameraMode.LOOKAT_Y, ParticleAppearanceBillboard.CameraMode.ROTATE_Y -> pose.mulPose(cameraRotation)
                else -> {}
            }
            if (particle.rotation != 0f) pose.mulPose(Axis.ZP.rotationDegrees(particle.rotation))
            val width = emitter.resolve(billboard.size.getOrNull(0)).takeIf(Float::isFinite) ?: 0f
            val height = emitter.resolve(billboard.size.getOrNull(1)).takeIf(Float::isFinite) ?: width
            val uv = calculateUv(emitter, particle, billboard)
            val color = calculateColor(emitter)
            val light = if (emitter.has(ParticleComponents.PARTICLE_APPEARANCE_LIGHTING)) 0x00F000F0 else LightTexture.FULL_BRIGHT
            drawQuad(consumer, pose.last(), billboard.cameraMode, width, height, uv, color, light)
            pose.popPose()
        }
    }

    private fun updateParticleRuntime(emitter: Emitter, p: Particle, partial: Float) {
        val age = p.age + partial * DT
        val edit = emitter.runtime.edit().setVariable("particle_age", age).setVariable("particle_lifetime", p.lifetime)
        repeat(4) { edit.setVariable("particle_random_${it + 1}", p.random[it]) }
    }

    private fun textureLocation(file: ParticleEffectFile): ResourceLocation? {
        val raw = file.effect.description.renderParameters?.get("texture") ?: return null
        val id = if (':' in raw) ResourceLocation.parse(raw) else ResourceLocation.fromNamespaceAndPath(file.effect.description.identifier.namespace, raw)
        return ResourceLocation.fromNamespaceAndPath(id.namespace, if (id.path.endsWith(".png")) id.path else "${id.path}.png")
    }

    private data class Uv(val u0: Float, val v0: Float, val u1: Float, val v1: Float)
    private fun calculateUv(emitter: Emitter, p: Particle, billboard: ParticleAppearanceBillboard): Uv {
        val config = billboard.uv ?: return Uv(0f,0f,1f,1f)
        val tw = config.textureWidth.coerceAtLeast(1).toFloat(); val th = config.textureHeight.coerceAtLeast(1).toFloat()
        config.flipbook?.let { f ->
            val max = emitter.resolve(f.max_frame).roundToInt().coerceAtLeast(1)
            val frame = if (f.stretch_to_lifetime) floor((p.age / p.lifetime).coerceIn(0f,.999999f) * max).toInt()
                else floor(p.age * f.frames_per_second).toInt().let { if (f.loop) it.mod(max) else it.coerceAtMost(max-1) }
            val bu = emitter.resolve(f.base_UV.getOrNull(0)); val bv = emitter.resolve(f.base_UV.getOrNull(1))
            val u = bu + f.step_UV.getOrElse(0){0f} * frame; val v = bv + f.step_UV.getOrElse(1){0f} * frame
            return Uv(u/tw, v/th, (u+f.size_UV.getOrElse(0){tw})/tw, (v+f.size_UV.getOrElse(1){th})/th)
        }
        val u = emitter.resolve(config.uv?.getOrNull(0)); val v = emitter.resolve(config.uv?.getOrNull(1))
        val w = emitter.resolve(config.uvSize?.getOrNull(0)).takeIf { it != 0f } ?: tw; val h = emitter.resolve(config.uvSize?.getOrNull(1)).takeIf { it != 0f } ?: th
        return Uv(u/tw,v/th,(u+w)/tw,(v+h)/th)
    }

    private fun calculateColor(emitter: Emitter): Int {
        val tint = emitter.get(ParticleComponents.PARTICLE_APPEARANCE_TINTING) ?: return -1
        return runCatching { if (tint.isRGBA) tint.rgba(emitter.runtime) else tint.color.color(emitter.runtime) }.getOrDefault(-1)
    }

    private fun drawQuad(c: com.mojang.blaze3d.vertex.VertexConsumer, pose: PoseStack.Pose, mode: ParticleAppearanceBillboard.CameraMode?, w: Float, h: Float, uv: Uv, color: Int, light: Int) {
        val a=(color ushr 24) and 255; val r=(color ushr 16) and 255; val g=(color ushr 8) and 255; val b=color and 255
        fun vertex(x:Float,y:Float,z:Float,u:Float,v:Float,nx:Float,ny:Float,nz:Float){ c.addVertex(pose,x,y,z).setColor(r,g,b,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,nx,ny,nz) }
        when(mode) {
            ParticleAppearanceBillboard.CameraMode.EMITTER_TRANSFORM_YZ -> { vertex(0f,-h,-w,uv.u0,uv.v1,1f,0f,0f); vertex(0f,h,-w,uv.u0,uv.v0,1f,0f,0f); vertex(0f,h,w,uv.u1,uv.v0,1f,0f,0f); vertex(0f,-h,w,uv.u1,uv.v1,1f,0f,0f) }
            ParticleAppearanceBillboard.CameraMode.EMITTER_TRANSFORM_XZ -> { vertex(-w,0f,-h,uv.u0,uv.v1,0f,1f,0f); vertex(w,0f,-h,uv.u1,uv.v1,0f,1f,0f); vertex(w,0f,h,uv.u1,uv.v0,0f,1f,0f); vertex(-w,0f,h,uv.u0,uv.v0,0f,1f,0f) }
            else -> { vertex(-w,-h,0f,uv.u0,uv.v1,0f,0f,1f); vertex(w,-h,0f,uv.u1,uv.v1,0f,0f,1f); vertex(w,h,0f,uv.u1,uv.v0,0f,0f,1f); vertex(-w,h,0f,uv.u0,uv.v0,0f,0f,1f) }
        }
    }
}
