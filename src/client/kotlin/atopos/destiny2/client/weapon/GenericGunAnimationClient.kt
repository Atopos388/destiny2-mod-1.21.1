package atopos.destiny2.client.weapon

import atopos.destiny2.client.gui.DestinyWeaponHUDState
import atopos.destiny2.client.model.tacz.TaczBedrockGunModel
import atopos.destiny2.common.item.GenericGunPackItem
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.sound.WeaponSoundKeyframeBridge
import atopos.destiny2.common.weapon.TaczWeaponAnimationContract
import atopos.destiny2.common.weapon.WeaponThirdPersonAction
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * TaCZ-style Bedrock animation tracks for the generic gun-pack model.
 *
 * The exported channels are relative transforms. Every frame starts clean,
 * applies static_idle first, then lets the active action overwrite only the
 * channels it owns. That is the same base-track/main-track ordering used by
 * TaCZ's default state machine.
 */
object GenericGunAnimationClient {
    private data class ActionState(
        val gunId: ResourceLocation,
        val animations: List<String>,
        var animationIndex: Int = 0,
        var startedAtNanos: Long,
        var lastSoundTimeSeconds: Float = -SOUND_KEYFRAME_EPSILON_SECONDS
    ) {
        val animation: String
            get() = animations[animationIndex]

        fun advance(completedDurationSeconds: Float): Boolean {
            if (animationIndex >= animations.lastIndex) return false
            animationIndex += 1
            startedAtNanos += (completedDurationSeconds * NANOS_PER_SECOND).toLong()
            lastSoundTimeSeconds = -SOUND_KEYFRAME_EPSILON_SECONDS
            return true
        }
    }

    private data class ShootTrack(
        val gunId: ResourceLocation,
        val startedAtNanos: Long,
        var lastSoundTimeSeconds: Float = -SOUND_KEYFRAME_EPSILON_SECONDS,
        var sampled: Boolean = false
    )

    private data class CycleState(
        val gunId: ResourceLocation,
        val animation: String,
        val startedAtNanos: Long,
        var lastSoundTimeSeconds: Float = -SOUND_KEYFRAME_EPSILON_SECONDS,
        var sampled: Boolean = false,
        var pumpSampled: Boolean = false
    )

    private data class CachedAnimations(
        val resourceHash: Int,
        val clips: Map<String, Clip>
    )

    private data class Clip(
        val name: String,
        val endTimeSeconds: Float,
        val bones: Map<String, BoneTrack>,
        val soundEffects: List<SoundKeyframe>
    ) {
        fun apply(model: TaczBedrockGunModel, timeSeconds: Float) {
            bones.forEach { (boneName, track) ->
                model.applyAnimation(
                    boneName,
                    track.position?.evaluate(timeSeconds),
                    track.rotation?.evaluate(timeSeconds),
                    track.scale?.evaluate(timeSeconds)
                )
            }
        }

        /** TaCZ blending tracks contain deltas authored around zero/identity. */
        fun blend(model: TaczBedrockGunModel, timeSeconds: Float) {
            bones.forEach { (boneName, track) ->
                model.blendAnimation(
                    boneName,
                    track.position?.evaluate(timeSeconds),
                    track.rotation?.evaluate(timeSeconds),
                    track.scale?.evaluate(timeSeconds)
                )
            }
        }

        /** Compatibility for gun packs not yet migrated to TaCZ additive shoot data. */
        fun blendLegacy(model: TaczBedrockGunModel, timeSeconds: Float, idle: Clip?) {
            bones.forEach { (boneName, track) ->
                val idleTrack = idle?.bones?.get(boneName)
                val position = track.position?.evaluate(timeSeconds)?.sub(
                    idleTrack?.position?.evaluate(0.0f) ?: Vector3f()
                )
                val rotation = track.rotation?.evaluate(timeSeconds)?.sub(
                    idleTrack?.rotation?.evaluate(0.0f) ?: Vector3f()
                )
                val scale = track.scale?.evaluate(timeSeconds)?.let { animated ->
                    val base = idleTrack?.scale?.evaluate(0.0f) ?: Vector3f(1.0f, 1.0f, 1.0f)
                    Vector3f(
                        safeScaleRatio(animated.x, base.x),
                        safeScaleRatio(animated.y, base.y),
                        safeScaleRatio(animated.z, base.z)
                    )
                }
                model.blendAnimation(boneName, position, rotation, scale)
            }
        }

        fun cameraRotation(timeSeconds: Float): Vector3f? =
            bones[CAMERA_NODE]?.rotation?.evaluate(timeSeconds)

        fun additiveBoundaryViolations(): List<String> = buildList {
            bones.forEach { (boneName, track) ->
                if (!track.position.isNeutralAt(0.0f, endTimeSeconds, Vector3f())) {
                    add("$boneName.position")
                }
                if (!track.rotation.isNeutralAt(0.0f, endTimeSeconds, Vector3f())) {
                    add("$boneName.rotation")
                }
                if (!track.scale.isNeutralAt(0.0f, endTimeSeconds, Vector3f(1.0f, 1.0f, 1.0f))) {
                    add("$boneName.scale")
                }
            }
        }

        fun baseBoundaryViolations(base: Clip?): List<String> = buildList {
            bones.forEach { (boneName, track) ->
                val baseTrack = base?.bones?.get(boneName)
                val basePosition = baseTrack?.position?.evaluate(0.0f) ?: Vector3f()
                val baseRotation = baseTrack?.rotation?.evaluate(0.0f) ?: Vector3f()
                val baseScale = baseTrack?.scale?.evaluate(0.0f) ?: Vector3f(1.0f, 1.0f, 1.0f)
                if (!track.position.isNeutralAt(0.0f, endTimeSeconds, basePosition)) {
                    add("$boneName.position")
                }
                if (!track.rotation.isNeutralAt(0.0f, endTimeSeconds, baseRotation)) {
                    add("$boneName.rotation")
                }
                if (!track.scale.isNeutralAt(0.0f, endTimeSeconds, baseScale)) {
                    add("$boneName.scale")
                }
            }
        }

        fun emitSounds(
            fromExclusive: Float,
            toInclusive: Float,
            excludedEffects: Set<String> = emptySet()
        ) {
            soundEffects.asSequence()
                .filter { it.timeSeconds > fromExclusive && it.timeSeconds <= toInclusive + SOUND_KEYFRAME_EPSILON_SECONDS }
                .filterNot { it.effect in excludedEffects }
                .forEach { WeaponSoundKeyframeBridge.emit(it.effect) }
        }
    }

    private data class BoneTrack(
        val position: Channel?,
        val rotation: Channel?,
        val scale: Channel?
    )

    private data class SoundKeyframe(
        val timeSeconds: Float,
        val effect: String
    )

    private data class Channel(
        val frames: List<Keyframe>,
        val kind: ChannelKind
    ) {
        val endTimeSeconds: Float
            get() = frames.lastOrNull()?.timeSeconds ?: 0.0f

        fun evaluate(timeSeconds: Float): Vector3f {
            if (frames.isEmpty()) return kind.defaultValue()
            if (frames.size == 1) return frames[0].value(usePost = timeSeconds > frames[0].timeSeconds)
            if (timeSeconds <= frames.first().timeSeconds) return frames.first().value(usePost = false)
            if (timeSeconds >= frames.last().timeSeconds) return frames.last().value(usePost = true)

            var fromIndex = frames.binarySearchBy(timeSeconds) { it.timeSeconds }
            if (fromIndex >= 0) return frames[fromIndex].value(usePost = true)
            fromIndex = (-fromIndex - 2).coerceIn(0, frames.lastIndex)
            val toIndex = (fromIndex + 1).coerceAtMost(frames.lastIndex)
            val from = frames[fromIndex]
            val to = frames[toIndex]
            val span = (to.timeSeconds - from.timeSeconds).coerceAtLeast(0.000001f)
            val alpha = ((timeSeconds - from.timeSeconds) / span).coerceIn(0.0f, 1.0f)
            return if (from.lerpMode == LerpMode.CATMULLROM || to.lerpMode == LerpMode.CATMULLROM) {
                catmullRom(fromIndex, toIndex, alpha)
            } else {
                from.value(usePost = true).lerp(to.value(usePost = false), alpha, Vector3f())
            }
        }

        private fun catmullRom(fromIndex: Int, toIndex: Int, alpha: Float): Vector3f {
            val previous = frames[(fromIndex - 1).coerceAtLeast(0)].value(usePost = true)
            val from = frames[fromIndex].value(usePost = false)
            val to = frames[toIndex].value(usePost = false)
            val next = frames[(toIndex + 1).coerceAtMost(frames.lastIndex)].value(usePost = false)
            return Vector3f(
                spline(previous.x, from.x, to.x, next.x, alpha),
                spline(previous.y, from.y, to.y, next.y, alpha),
                spline(previous.z, from.z, to.z, next.z, alpha)
            )
        }

        /**
         * THREE.SplineCurve-style cubic interpolation used by TaCZ to match
         * Blockbench's catmullrom channel appearance.
         */
        private fun spline(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
            val t2 = t * t
            val t3 = t2 * t
            val v0 = (p2 - p0) * 0.5f
            val v1 = (p3 - p1) * 0.5f
            return (2f * p1 - 2f * p2 + v0 + v1) * t3 +
                (-3f * p1 + 3f * p2 - 2f * v0 - v1) * t2 +
                v0 * t + p1
        }
    }

    private data class Keyframe(
        val timeSeconds: Float,
        val pre: Vector3f?,
        val post: Vector3f?,
        val data: Vector3f?,
        val lerpMode: LerpMode
    ) {
        fun value(usePost: Boolean): Vector3f =
            Vector3f(
                if (usePost) post ?: pre ?: data ?: Vector3f() else pre ?: post ?: data ?: Vector3f()
            )
    }

    private enum class ChannelKind {
        POSITION,
        ROTATION,
        SCALE;

        fun defaultValue(): Vector3f =
            if (this == SCALE) Vector3f(1.0f, 1.0f, 1.0f) else Vector3f()
    }

    private enum class LerpMode {
        LINEAR,
        CATMULLROM
    }

    private val cache = ConcurrentHashMap<ResourceLocation, CachedAnimations>()
    private val logger = LoggerFactory.getLogger("DestinyTaCZAnimationRunner")
    private var action: ActionState? = null
    private var cycle: CycleState? = null
    private val shootTracks = ArrayList<ShootTrack>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
    }

    fun onAction(payload: DestinyNetworking.WeaponThirdPersonActionPayload) {
        val player = Minecraft.getInstance().player ?: return
        if (payload.playerId != player.uuid) return
        val stack = player.mainHandItem
        if (stack.item !is GenericGunPackItem) return

        val gunId = GenericGunPackItem.id(stack)
        if (payload.weaponId != null && payload.weaponId != gunId) return
        val animation = when (payload.action) {
            WeaponThirdPersonAction.RELOAD ->
                if (DestinyWeaponHUDState.snapshot?.chamberEmpty == true) {
                    TaczWeaponAnimationContract.RELOAD_EMPTY
                } else {
                    TaczWeaponAnimationContract.RELOAD_TACTICAL
                }
            WeaponThirdPersonAction.INSPECT -> TaczWeaponAnimationContract.INSPECT
            WeaponThirdPersonAction.SHOOT -> TaczWeaponAnimationContract.SHOOT
            WeaponThirdPersonAction.DRAW -> TaczWeaponAnimationContract.DRAW
            WeaponThirdPersonAction.PUT_AWAY -> TaczWeaponAnimationContract.PUT_AWAY
        }
        val now = System.nanoTime()
        if (animation == TaczWeaponAnimationContract.SHOOT) {
            triggerShoot(gunId, now)
        } else {
            val animations = if (payload.action == WeaponThirdPersonAction.RELOAD && gunId == THE_DEICIDE_ID) {
                theDeicideReloadSequence()
            } else {
                listOf(animation)
            }
            action = ActionState(gunId, animations, startedAtNanos = now)
            cycle = null
            shootTracks.removeAll { it.gunId == gunId }
        }
    }

    /**
     * The shot-feedback packet is the first-person authority for recoil. Use
     * the same confirmed packet to start the authored gun animation, while
     * retaining the action packet as a deduplicated compatibility trigger.
     */
    fun onLocalShotFeedback() {
        val player = Minecraft.getInstance().player ?: return
        val stack = player.mainHandItem
        if (stack.item !is GenericGunPackItem) return
        triggerShoot(GenericGunPackItem.id(stack), System.nanoTime())
    }

    private fun triggerShoot(gunId: ResourceLocation, now: Long) {
        action = null
        val definition = TaczGunPackResources.definition(gunId)
        val duplicate = shootTracks.any { track ->
            track.gunId == gunId &&
                now - track.startedAtNanos <= SHOOT_TRIGGER_DEDUPLICATION_NANOS
        }
        if (!duplicate) {
            if (definition?.mergeCycleIntoShoot == true) {
                // The server keeps the weapon locked until the pump is back in
                // battery, so a completed legacy shot can be replaced safely.
                shootTracks.removeAll { it.gunId == gunId }
                cycle = null
            }
            shootTracks += ShootTrack(gunId, now)
            logger.info("Triggered first-person shoot track for {}", gunId)
            definition?.cycleAnimation
                ?.takeUnless { definition.mergeCycleIntoShoot }
                ?.let { animation ->
                // Mechanical cycling is a single non-blending track. A new
                // shot replaces it instead of accumulating hand/pump poses.
                cycle = CycleState(gunId, animation, now)
            }
        }
    }

    /**
     * Advances TaCZ-style animation runners independently from model drawing.
     * Sound keyframes therefore have one clock and cannot disappear when a
     * first-person render pass is skipped or replaced by an ADS overlay.
     */
    private fun tick() {
        val player = Minecraft.getInstance().player ?: return
        val stack = player.mainHandItem
        if (stack.item !is GenericGunPackItem) return
        val gunId = GenericGunPackItem.id(stack)
        val definition = TaczGunPackResources.definition(gunId) ?: return
        val clips = load(gunId, definition.animation) ?: return
        val now = System.nanoTime()

        advanceAction(gunId, clips, now)
        advanceCycle(gunId, clips, now)
        advanceShootTracks(gunId, clips, now)
    }

    private fun advanceAction(
        gunId: ResourceLocation,
        clips: Map<String, Clip>,
        now: Long
    ) {
        val current = action?.takeIf { it.gunId == gunId } ?: return
        var transitionsRemaining = current.animations.size
        while (transitionsRemaining-- > 0) {
            val clip = clips[current.animation]
            if (clip == null) {
                action = null
                return
            }
            val elapsed = secondsSince(now, current.startedAtNanos)
            val sampleTime = elapsed.coerceAtMost(clip.endTimeSeconds)
            if (sampleTime >= current.lastSoundTimeSeconds) {
                clip.emitSounds(current.lastSoundTimeSeconds, sampleTime)
                current.lastSoundTimeSeconds = sampleTime
            }
            val finalClip = current.animationIndex >= current.animations.lastIndex
            val endThreshold = clip.endTimeSeconds +
                if (finalClip) ACTION_END_EPSILON_SECONDS else 0.0f
            if (elapsed <= endThreshold) return
            if (!current.advance(clip.endTimeSeconds)) {
                action = null
                return
            }
        }
    }

    private fun advanceCycle(
        gunId: ResourceLocation,
        clips: Map<String, Clip>,
        now: Long
    ) {
        val current = cycle?.takeIf { it.gunId == gunId } ?: return
        val clip = clips[current.animation]
        if (clip == null) {
            cycle = null
            return
        }
        val elapsed = secondsSince(now, current.startedAtNanos)
        val sampleTime = elapsed.coerceAtMost(clip.endTimeSeconds)
        if (sampleTime >= current.lastSoundTimeSeconds) {
            clip.emitSounds(current.lastSoundTimeSeconds, sampleTime)
            current.lastSoundTimeSeconds = sampleTime
        }
        if (elapsed > clip.endTimeSeconds + ACTION_END_EPSILON_SECONDS) {
            cycle = null
        }
    }

    private fun advanceShootTracks(
        gunId: ResourceLocation,
        clips: Map<String, Clip>,
        now: Long
    ) {
        val shoot = clips[TaczWeaponAnimationContract.SHOOT]
        val definition = TaczGunPackResources.definition(gunId)
        val mechanical = definition
            ?.takeIf { it.mergeCycleIntoShoot }
            ?.cycleAnimation
            ?.let(clips::get)
        val iterator = shootTracks.iterator()
        while (iterator.hasNext()) {
            val track = iterator.next()
            val elapsed = secondsSince(now, track.startedAtNanos)
            if (track.gunId != gunId) {
                if (elapsed > STALE_SHOOT_TRACK_SECONDS) iterator.remove()
                continue
            }
            if (shoot == null) {
                logger.error("Shoot track for {} was triggered, but the loaded animation has no shoot clip", gunId)
                iterator.remove()
                continue
            }
            val endTime = maxOf(shoot.endTimeSeconds, mechanical?.endTimeSeconds ?: 0.0f)
            val sampleTime = elapsed.coerceAtMost(endTime)
            if (sampleTime >= track.lastSoundTimeSeconds) {
                shoot.emitSounds(
                    track.lastSoundTimeSeconds,
                    sampleTime.coerceAtMost(shoot.endTimeSeconds),
                    SERVER_AUTHORITATIVE_SHOOT_EFFECTS
                )
                mechanical?.emitSounds(
                    track.lastSoundTimeSeconds,
                    sampleTime.coerceAtMost(mechanical.endTimeSeconds),
                    SERVER_AUTHORITATIVE_SHOOT_EFFECTS
                )
                track.lastSoundTimeSeconds = sampleTime
            }
            if (elapsed > endTime + ACTION_END_EPSILON_SECONDS) {
                iterator.remove()
            }
        }
    }

    fun apply(
        gunId: ResourceLocation,
        animationResource: ResourceLocation,
        model: TaczBedrockGunModel
    ) {
        val clips = load(gunId, animationResource) ?: return
        model.resetAnimation()

        val now = System.nanoTime()
        var authoredCameraRotation = Vector3f()
        val idleClip = clips[TaczWeaponAnimationContract.STATIC_IDLE]
        idleClip?.let { idle ->
            val idleTime = if (idle.endTimeSeconds > MIN_CLIP_LENGTH_SECONDS) {
                ((now / NANOS_PER_SECOND.toDouble()) % idle.endTimeSeconds).toFloat()
            } else {
                0.0f
            }
            idle.apply(model, idleTime)
            idle.cameraRotation(idleTime)?.let(authoredCameraRotation::set)
        }

        action?.takeIf { it.gunId == gunId }?.let { current ->
            clips[current.animation]?.let { clip ->
                val sampleTime = secondsSince(now, current.startedAtNanos)
                    .coerceAtMost(clip.endTimeSeconds)
                clip.apply(model, sampleTime)
                clip.cameraRotation(sampleTime)?.let(authoredCameraRotation::set)
            }
        } ?: cycle?.takeIf { it.gunId == gunId }?.let { current ->
            clips[current.animation]?.let { clip ->
                val sampleTime = secondsSince(now, current.startedAtNanos)
                    .coerceAtMost(clip.endTimeSeconds)
                if (!current.sampled) {
                    current.sampled = true
                    logger.info(
                        "Sampled first-person cycle clip {} for {} at {}s (duration={}s)",
                        current.animation,
                        gunId,
                        sampleTime,
                        clip.endTimeSeconds
                    )
                }
                if (!current.pumpSampled && sampleTime >= THE_DEICIDE_PUMP_SAMPLE_SECONDS) {
                    current.pumpSampled = true
                    val pumpPosition = clip.bones[THE_DEICIDE_PUMP_BONE]
                        ?.position
                        ?.evaluate(sampleTime)
                    logger.info(
                        "Cycle {} for {} sampled {} position={} at {}s",
                        current.animation,
                        gunId,
                        THE_DEICIDE_PUMP_BONE,
                        pumpPosition,
                        sampleTime
                    )
                }
                clip.apply(model, sampleTime)
                clip.cameraRotation(sampleTime)?.let(authoredCameraRotation::set)
            }
        }

        val definition = TaczGunPackResources.definition(gunId)
        val shoot = clips[TaczWeaponAnimationContract.SHOOT]
        val mechanical = definition
            ?.takeIf { it.mergeCycleIntoShoot }
            ?.cycleAnimation
            ?.let(clips::get)
        val additiveShoot = definition?.additiveShoot == true
        for (track in shootTracks) {
            if (track.gunId != gunId) {
                continue
            }
            if (shoot == null) {
                continue
            }
            val elapsed = secondsSince(now, track.startedAtNanos)
            val endTime = maxOf(shoot.endTimeSeconds, mechanical?.endTimeSeconds ?: 0.0f)
            if (elapsed <= endTime + ACTION_END_EPSILON_SECONDS) {
                val shootTime = elapsed.coerceAtMost(shoot.endTimeSeconds)
                if (!track.sampled) {
                    track.sampled = true
                    logger.info(
                        "Sampled first-person shoot clip for {} at {}s (duration={}s, additive={})",
                        gunId,
                        shootTime,
                        endTime,
                        additiveShoot
                    )
                }
                if (additiveShoot) shoot.blend(model, shootTime)
                else shoot.blendLegacy(model, shootTime, idleClip)
                shoot.cameraRotation(shootTime)?.let(authoredCameraRotation::add)
                mechanical?.let { clip ->
                    val mechanicalTime = elapsed.coerceAtMost(clip.endTimeSeconds)
                    clip.blendLegacy(model, mechanicalTime, idleClip)
                    clip.cameraRotation(mechanicalTime)?.let(authoredCameraRotation::add)
                }
            }
        }
        DestinyWeaponAimClient.publishTaczCamera(authoredCameraRotation)
    }

    /**
     * Applies a deterministic bind/idle pose for inventory and other GUI previews.
     * Gameplay action tracks are deliberately ignored so a reload, inspect, or shot
     * cannot move an icon outside its slot.
     */
    fun applyStatic(
        gunId: ResourceLocation,
        animationResource: ResourceLocation,
        model: TaczBedrockGunModel
    ) {
        val clips = load(gunId, animationResource) ?: return
        model.resetAnimation()
        clips[TaczWeaponAnimationContract.STATIC_IDLE]?.apply(model, 0.0f)
    }

    fun reset(model: TaczBedrockGunModel) {
        model.resetAnimation()
    }

    private fun theDeicideReloadSequence(): List<String> {
        val snapshot = DestinyWeaponHUDState.snapshot
        val missing = snapshot?.let { (it.capacity - it.magazine).coerceAtLeast(1) } ?: 1
        val available = snapshot?.reserve?.coerceAtLeast(1) ?: missing
        val inserts = minOf(missing, available).coerceIn(1, THE_DEICIDE_MAGAZINE_SIZE)
        return buildList {
            add(THE_DEICIDE_RELOAD_START)
            repeat(inserts) { add(THE_DEICIDE_RELOAD_INSERT) }
            add(THE_DEICIDE_RELOAD_GRIP)
            add(THE_DEICIDE_RELOAD_PUMP)
            add(THE_DEICIDE_RELOAD_END)
        }
    }

    private fun load(
        gunId: ResourceLocation,
        animationResource: ResourceLocation
    ): Map<String, Clip>? {
        val bytes = TaczGunPackResources.resourceBytes(animationResource) ?: return null
        val hash = bytes.contentHashCode()
        cache[gunId]?.takeIf { it.resourceHash == hash }?.let { return it.clips }

        val root = bytes.inputStream().reader(Charsets.UTF_8).use(JsonParser::parseReader).asJsonObject
        val aliases = TaczGunPackResources.definition(gunId)?.animationBoneAliases.orEmpty()
        val clips = root.getAsJsonObject("animations")
            ?.entrySet()
            ?.associate { (name, element) -> name to parseClip(name, element.asJsonObject, aliases) }
            .orEmpty()
            .toMutableMap()
        if (TaczWeaponAnimationContract.SHOOT !in clips) {
            clips.entries.firstOrNull { (name, _) ->
                name.equals(TaczWeaponAnimationContract.SHOOT, ignoreCase = true)
            }?.let { (_, clip) -> clips[TaczWeaponAnimationContract.SHOOT] = clip }
        }
        if (TaczGunPackResources.definition(gunId)?.additiveShoot == true) {
            clips[TaczWeaponAnimationContract.SHOOT]?.let { shoot ->
                val violations = shoot.additiveBoundaryViolations()
                if (violations.isNotEmpty()) {
                    logger.error(
                        "Rejecting non-additive shoot animation for {}: non-neutral boundaries={}",
                        gunId,
                        violations.joinToString()
                    )
                    clips.remove(TaczWeaponAnimationContract.SHOOT)
                }
            }
        }
        val definition = TaczGunPackResources.definition(gunId)
        definition?.cycleAnimation?.let { cycleName ->
            val cycleClip = clips[cycleName]
            if (cycleClip == null) {
                logger.error("Missing configured cycle animation {} for {}", cycleName, gunId)
            } else {
                val violations = cycleClip.baseBoundaryViolations(
                    clips[TaczWeaponAnimationContract.STATIC_IDLE]
                )
                if (violations.isNotEmpty()) {
                    logger.error(
                        "Rejecting cycle animation {} for {}: boundaries do not match static_idle={}",
                        cycleName,
                        gunId,
                        violations.joinToString()
                    )
                    clips.remove(cycleName)
                }
            }
        }
        cache[gunId] = CachedAnimations(hash, clips)
        return clips
    }

    private fun parseClip(name: String, root: JsonObject, aliases: Map<String, String>): Clip {
        val bones = root.getAsJsonObject("bones")
            ?.entrySet()
            ?.associate { (boneName, element) ->
                val bone = element.asJsonObject
                (aliases[boneName] ?: boneName) to BoneTrack(
                    position = bone.get("position")?.let { parseChannel(it, ChannelKind.POSITION) },
                    rotation = bone.get("rotation")?.let { parseChannel(it, ChannelKind.ROTATION) },
                    scale = bone.get("scale")?.let { parseChannel(it, ChannelKind.SCALE) }
                )
            }
            .orEmpty()
        val soundEffects = root.getAsJsonObject("sound_effects")
            ?.entrySet()
            ?.mapNotNull { (timeText, element) ->
                val time = timeText.toFloatOrNull() ?: return@mapNotNull null
                val effect = when {
                    element.isJsonPrimitive -> runCatching { element.asString }.getOrNull()
                    element.isJsonObject -> element.asJsonObject.get("effect")
                        ?.takeIf(JsonElement::isJsonPrimitive)
                        ?.let { runCatching { it.asString }.getOrNull() }
                    else -> null
                }?.trim().orEmpty()
                effect.takeIf(String::isNotEmpty)?.let { SoundKeyframe(time, it) }
            }
            ?.sortedBy(SoundKeyframe::timeSeconds)
            .orEmpty()
        // TaCZ's ObjectAnimation duration is the final channel key, not the
        // animation_length declaration.
        val boneEndTime = bones.values.maxOfOrNull { track ->
            maxOf(
                track.position?.endTimeSeconds ?: 0.0f,
                track.rotation?.endTimeSeconds ?: 0.0f,
                track.scale?.endTimeSeconds ?: 0.0f
            )
        } ?: 0.0f
        val endTime = maxOf(boneEndTime, soundEffects.lastOrNull()?.timeSeconds ?: 0.0f)
        return Clip(name, endTime, bones, soundEffects)
    }

    private fun parseChannel(element: JsonElement, kind: ChannelKind): Channel {
        val frames = when {
            element.isJsonPrimitive -> {
                val value = element.takeIf { it.asJsonPrimitive.isNumber }?.asFloat ?: 0.0f
                listOf(Keyframe(0.0f, null, null, Vector3f(value), LerpMode.LINEAR))
            }
            element.isJsonArray -> {
                listOf(Keyframe(0.0f, null, null, readVector(element), LerpMode.LINEAR))
            }
            element.isJsonObject -> {
                val channel = element.asJsonObject
                val staticVector = channel.get("vector")
                    ?.let(::readVectorOrNull)
                if (staticVector != null) {
                    listOf(Keyframe(0.0f, null, null, staticVector, LerpMode.LINEAR))
                } else {
                    channel.entrySet().mapNotNull { (timeText, value) ->
                        val time = timeText.toFloatOrNull() ?: return@mapNotNull null
                        when {
                            value.isJsonArray ->
                                Keyframe(time, null, null, readVector(value), LerpMode.LINEAR)
                            value.isJsonObject -> {
                                val frame = value.asJsonObject
                                Keyframe(
                                    timeSeconds = time,
                                    pre = frame.get("pre")?.let(::readVectorOrNull),
                                    post = frame.get("post")?.let(::readVectorOrNull),
                                    data = frame.get("vector")?.let(::readVectorOrNull),
                                    lerpMode = if (frame.get("lerp_mode")?.asString?.lowercase() == "catmullrom") {
                                        LerpMode.CATMULLROM
                                    } else {
                                        LerpMode.LINEAR
                                    }
                                )
                            }
                            else -> null
                        }
                    }.sortedBy(Keyframe::timeSeconds)
                }
            }
            else -> emptyList()
        }.map { frame ->
            if (kind == ChannelKind.ROTATION) {
                frame.copy(
                    pre = frame.pre?.mul(DEGREES_TO_RADIANS),
                    post = frame.post?.mul(DEGREES_TO_RADIANS),
                    data = frame.data?.mul(DEGREES_TO_RADIANS)
                )
            } else {
                frame
            }
        }
        return Channel(frames, kind)
    }

    private fun readVector(element: JsonElement): Vector3f {
        val array = element.asJsonArray
        fun component(index: Int): Float =
            array.elementOrNull(index)
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asFloat
                ?: 0.0f
        return Vector3f(component(0), component(1), component(2))
    }

    private fun readVectorOrNull(element: JsonElement): Vector3f? = when {
        element.isJsonArray -> readVector(element)
        element.isJsonObject -> element.asJsonObject.get("vector")?.let(::readVectorOrNull)
        else -> null
    }

    private fun com.google.gson.JsonArray.elementOrNull(index: Int): JsonElement? =
        if (index in 0 until size()) get(index) else null

    private fun secondsSince(nowNanos: Long, startedAtNanos: Long): Float =
        ((nowNanos - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_SECOND.toDouble()).toFloat()

    private fun Channel?.isNeutralAt(start: Float, end: Float, expected: Vector3f): Boolean {
        if (this == null) return true
        return evaluate(start).isCloseTo(expected) && evaluate(end).isCloseTo(expected)
    }

    private fun Vector3f.isCloseTo(expected: Vector3f): Boolean =
        kotlin.math.abs(x - expected.x) <= ADDITIVE_BOUNDARY_EPSILON &&
            kotlin.math.abs(y - expected.y) <= ADDITIVE_BOUNDARY_EPSILON &&
            kotlin.math.abs(z - expected.z) <= ADDITIVE_BOUNDARY_EPSILON

    private fun safeScaleRatio(animated: Float, base: Float): Float =
        if (kotlin.math.abs(base) <= MIN_SCALE_DIVISOR) 1.0f else animated / base

    private const val NANOS_PER_SECOND = 1_000_000_000L
    private const val MIN_CLIP_LENGTH_SECONDS = 0.001f
    private const val ACTION_END_EPSILON_SECONDS = 0.05f
    private const val STALE_SHOOT_TRACK_SECONDS = 5.0f
    private const val SHOOT_TRIGGER_DEDUPLICATION_NANOS = 120_000_000L
    private const val THE_DEICIDE_PUMP_SAMPLE_SECONDS = 0.5f
    private const val THE_DEICIDE_PUMP_BONE = "bone3"
    private const val SOUND_KEYFRAME_EPSILON_SECONDS = 0.0001f
    private const val ADDITIVE_BOUNDARY_EPSILON = 0.0005f
    private const val MIN_SCALE_DIVISOR = 0.0001f
    private val SERVER_AUTHORITATIVE_SHOOT_EFFECTS = setOf("the_deicide_fire")
    private val THE_DEICIDE_ID = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "the_deicide")
    private const val THE_DEICIDE_MAGAZINE_SIZE = 7
    private const val THE_DEICIDE_RELOAD_START = "reload_start"
    private const val THE_DEICIDE_RELOAD_INSERT = "reload_insert"
    private const val THE_DEICIDE_RELOAD_GRIP = "reload_grip"
    private const val THE_DEICIDE_RELOAD_PUMP = "reload_pump"
    private const val THE_DEICIDE_RELOAD_END = "reload_end"
    private const val DEGREES_TO_RADIANS = (Math.PI / 180.0).toFloat()
    private const val CAMERA_NODE = "camera"
}
