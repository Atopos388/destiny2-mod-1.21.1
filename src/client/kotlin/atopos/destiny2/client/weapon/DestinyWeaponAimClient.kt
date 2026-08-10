package atopos.destiny2.client.weapon

import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponAimProfile
import atopos.destiny2.client.tacz.TaczMath
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import software.bernie.geckolib.cache.`object`.GeoBone

/** Client-local ADS interpolation and GeckoLib camera-bone bridge. */
object DestinyWeaponAimClient {
    private var previousProgress = 0.0f
    private var currentProgress = 0.0f
    private var retainedProfile: WeaponAimProfile? = null
    private var aimingTimestampMs = System.currentTimeMillis()
    private var cameraPitch = 0.0f
    private var cameraYaw = 0.0f
    private var cameraRoll = 0.0f
    private var cameraOffsetX = 0.0f
    private var cameraOffsetY = 0.0f
    private var cameraOffsetZ = 0.0f
    private var lastCameraBoneUpdateNanos = Long.MIN_VALUE
    private var authoredCameraX = 0.0f
    private var authoredCameraY = 0.0f
    private var authoredCameraZ = 0.0f

    private var taczCameraPitch = 0.0f
    private var taczCameraYaw = 0.0f
    private var taczCameraRoll = 0.0f
    private var lastTaczCameraUpdateNanos = Long.MIN_VALUE

    private var constraintTranslationX = 1.0f
    private var constraintTranslationY = 1.0f
    private var constraintTranslationZ = 1.0f
    private var constraintRotationX = 1.0f
    private var constraintRotationY = 1.0f
    private var constraintRotationZ = 1.0f
    private var lastConstraintBoneUpdateNanos = Long.MIN_VALUE
    private var authoredSightX = 0.0f
    private var authoredSightY = 0.0f
    private var authoredSightZ = 0.0f
    private var authoredIdleView = Matrix4f()
    private var authoredIronView = Matrix4f()
    private var lastPositioningViewUpdateNanos = Long.MIN_VALUE

    fun tick(client: Minecraft) {
        previousProgress = currentProgress
        val heldProfile = currentHeldProfile(client)
        if (heldProfile != null) retainedProfile = heldProfile

        val wantsAim = heldProfile != null &&
            client.screen == null &&
            client.options.cameraType == CameraType.FIRST_PERSON &&
            client.options.keyUse.isDown

        val profile = heldProfile ?: retainedProfile
        val now = System.currentTimeMillis()
        val aimTime = (profile?.aimTimeSeconds ?: DEFAULT_AIM_TIME_SECONDS).coerceAtLeast(0.001f)
        // TaCZ LocalPlayerAim uses elapsed wall-clock time instead of a fixed per-tick step.
        val step = (now - aimingTimestampMs + 1L).coerceAtLeast(1L) / (aimTime * 1000.0f)
        currentProgress = if (wantsAim) {
            (currentProgress + step).coerceAtMost(1.0f)
        } else {
            (currentProgress - step).coerceAtLeast(0.0f)
        }
        aimingTimestampMs = now

        if (currentProgress <= 0.0f && heldProfile == null) retainedProfile = null
        if (heldProfile == null) {
            clearStaleCameraBone(force = true)
            clearStaleConstraintBone(force = true)
            clearStalePositioningViews(force = true)
        }
    }

    fun isAimableHeld(client: Minecraft = Minecraft.getInstance()): Boolean =
        currentHeldProfile(client) != null

    fun activeProfile(): WeaponAimProfile? = retainedProfile

    fun progress(partialTick: Float): Float {
        val linear = Mth.lerp(partialTick.coerceIn(0.0f, 1.0f), previousProgress, currentProgress)
            .coerceIn(0.0f, 1.0f)
        return linear * linear * (3.0f - 2.0f * linear)
    }

    fun applyFirstPersonTransform(poseStack: PoseStack, partialTick: Float) {
        val profile = retainedProfile ?: return
        val amount = progress(partialTick)

        if (hasFreshPositioningViews()) {
            poseStack.translate(0.0, 1.5, 0.0)
            poseStack.mulPose(interpolateView(authoredIdleView, authoredIronView, amount))
            poseStack.translate(0.0, -1.5, 0.0)
            return
        }

        // TaCZ positions first-person models with inverse idle_view/iron_view
        // matrices. This converted model has neither view bone: its Blockbench
        // camera is idle_view, while constraint supplies the iron sight X/Y.
        val usesAuthoredRig = hasFreshAuthoredRig()
        if (usesAuthoredRig) {
            val viewX = Mth.lerp(amount, authoredCameraX, authoredSightX)
            val viewY = Mth.lerp(amount, authoredCameraY, authoredSightY)
            // Preserve the authored camera distance. constraint is at the front
            // sight, not at the player's eye-relief plane.
            val viewZ = authoredCameraZ
            poseStack.translate(
                (-viewX / MODEL_PIXELS_PER_BLOCK).toDouble(),
                (-viewY / MODEL_PIXELS_PER_BLOCK).toDouble(),
                (-viewZ / MODEL_PIXELS_PER_BLOCK).toDouble()
            )
        }

        // The numeric profile transform is only a fallback for models without
        // authored camera/constraint bones; do not offset a Blockbench camera twice.
        if (amount <= 0.0f || usesAuthoredRig) return
        poseStack.translate(
            profile.modelOffsetX * amount,
            profile.modelOffsetY * amount,
            profile.modelOffsetZ * amount
        )
        poseStack.mulPose(Axis.ZP.rotationDegrees(profile.modelRotationZ * amount))
        poseStack.mulPose(Axis.YP.rotationDegrees(profile.modelRotationY * amount))
        poseStack.mulPose(Axis.XP.rotationDegrees(profile.modelRotationX * amount))
    }

    /**
     * Returns the current authored view without applying TaCZ's model-origin
     * wrapper. Independent Bedrock first-person renderers already establish
     * their own clean render origin before calling this bridge.
     */
    fun positioningView(partialTick: Float): Matrix4f? {
        if (!hasFreshPositioningViews()) return null
        return interpolateView(
            authoredIdleView,
            authoredIronView,
            progress(partialTick)
        )
    }

    fun modifyWorldFov(originalFov: Double, partialTick: Float): Double {
        val profile = retainedProfile
        return if (profile == null) {
            originalFov
        } else {
            val magnification = 1.0 + (profile.zoom.coerceAtLeast(1.0f) - 1.0) * progress(partialTick)
            TaczMath.magnificationToFov(magnification, originalFov)
        }
    }

    fun publishCameraBone(bone: GeoBone) {
        authoredCameraX = bone.pivotX
        authoredCameraY = bone.pivotY
        authoredCameraZ = bone.pivotZ
        cameraPitch = Math.toDegrees(bone.rotX.toDouble()).toFloat()
        cameraYaw = Math.toDegrees(bone.rotY.toDouble()).toFloat()
        cameraRoll = Math.toDegrees(bone.rotZ.toDouble()).toFloat()
        // Blockbench/GeckoLib translations use model pixels; Camera uses blocks.
        cameraOffsetX = bone.posX / MODEL_PIXELS_PER_BLOCK
        cameraOffsetY = bone.posY / MODEL_PIXELS_PER_BLOCK
        cameraOffsetZ = bone.posZ / MODEL_PIXELS_PER_BLOCK
        lastCameraBoneUpdateNanos = System.nanoTime()
    }

    /**
     * Standard TaCZ gun-pack positioning. These are inverse view matrices
     * produced from the complete idle_view and iron_view bone paths.
     */
    fun publishPositioningViews(idleView: Matrix4f, ironView: Matrix4f) {
        authoredIdleView = Matrix4f(idleView)
        authoredIronView = Matrix4f(ironView)
        lastPositioningViewUpdateNanos = System.nanoTime()
    }

    /**
     * TaCZ's camera node is not a geometry bone. Its authored world-box
     * rotation is consumed by both the Minecraft camera and the gun model.
     */
    fun publishTaczCamera(rotationRadians: Vector3f) {
        taczCameraPitch = Math.toDegrees(rotationRadians.x.toDouble()).toFloat()
        taczCameraYaw = Math.toDegrees(rotationRadians.y.toDouble()).toFloat()
        taczCameraRoll = -Math.toDegrees(rotationRadians.z.toDouble()).toFloat()
        lastTaczCameraUpdateNanos = System.nanoTime()
    }

    fun clearPositioningViews() {
        lastPositioningViewUpdateNanos = Long.MIN_VALUE
    }

    /**
     * TaCZ ICA convention: constraint channel values are per-axis freedoms in [0, 1].
     * Translation reads position directly. Rotation is authored as 0-1 degrees in
     * Blockbench, so GeckoLib radians are converted back to degrees here.
     */
    fun publishConstraintBone(bone: GeoBone) {
        authoredSightX = bone.pivotX
        authoredSightY = bone.pivotY
        authoredSightZ = bone.pivotZ
        constraintTranslationX = bone.posX.absoluteFreedom()
        constraintTranslationY = bone.posY.absoluteFreedom()
        constraintTranslationZ = bone.posZ.absoluteFreedom()
        constraintRotationX = Math.toDegrees(bone.rotX.toDouble()).toFloat().absoluteFreedom()
        constraintRotationY = Math.toDegrees(bone.rotY.toDouble()).toFloat().absoluteFreedom()
        constraintRotationZ = Math.toDegrees(bone.rotZ.toDouble()).toFloat().absoluteFreedom()
        lastConstraintBoneUpdateNanos = System.nanoTime()
    }

    fun cameraTransform(partialTick: Float): CameraTransform {
        clearStaleCameraBone(force = false)
        clearStaleConstraintBone(force = false)
        clearStaleTaczCamera(force = false)
        val profile = retainedProfile ?: return CameraTransform.ZERO
        val amount = progress(partialTick)
        val animationScale = Mth.lerp(
            amount,
            profile.hipCameraAnimationScale,
            profile.aimedCameraAnimationScale
        )
        fun constrained(freedom: Float): Float = Mth.lerp(amount, 1.0f, freedom)
        return CameraTransform(
            position = Vec3(
                (cameraOffsetX * animationScale * constrained(constraintTranslationX)).toDouble(),
                (cameraOffsetY * animationScale * constrained(constraintTranslationY)).toDouble(),
                (cameraOffsetZ * animationScale * constrained(constraintTranslationZ)).toDouble()
            ),
            pitch = (
                cameraPitch * constrained(constraintRotationX) +
                    taczCameraPitch
                ) * animationScale,
            yaw = (
                cameraYaw * constrained(constraintRotationY) +
                    taczCameraYaw
                ) * animationScale,
            roll = (
                cameraRoll * constrained(constraintRotationZ) +
                    taczCameraRoll
                ) * animationScale
        )
    }

    fun modelCameraRotation(partialTick: Float): Quaternionf {
        clearStaleTaczCamera(force = false)
        val profile = retainedProfile ?: return Quaternionf()
        val amount = progress(partialTick)
        val animationScale = Mth.lerp(
            amount,
            profile.hipCameraAnimationScale,
            profile.aimedCameraAnimationScale
        )
        return Quaternionf().rotationZYX(
            taczCameraRoll * animationScale * Mth.DEG_TO_RAD,
            taczCameraYaw * animationScale * Mth.DEG_TO_RAD,
            taczCameraPitch * animationScale * Mth.DEG_TO_RAD
        )
    }

    @Deprecated("Use cameraTransform so camera-bone translation and ICA constraints are retained")
    fun cameraRotation(partialTick: Float): CameraRotation {
        val transform = cameraTransform(partialTick)
        return CameraRotation(transform.pitch, transform.yaw, transform.roll)
    }

    private fun currentHeldProfile(client: Minecraft): WeaponAimProfile? {
        val player = client.player ?: return null
        val stack = player.mainHandItem
        val weapon = stack.item as? DestinyRangedWeapon ?: return null
        return weapon.aimProfile(stack).takeIf { it.enabled }
    }

    private fun clearStaleCameraBone(force: Boolean) {
        val stale = lastCameraBoneUpdateNanos == Long.MIN_VALUE ||
            System.nanoTime() - lastCameraBoneUpdateNanos > CAMERA_BONE_TIMEOUT_NANOS
        if (force || stale) {
            cameraPitch = 0.0f
            cameraYaw = 0.0f
            cameraRoll = 0.0f
            cameraOffsetX = 0.0f
            cameraOffsetY = 0.0f
            cameraOffsetZ = 0.0f
        }
    }

    private fun clearStaleConstraintBone(force: Boolean) {
        val stale = lastConstraintBoneUpdateNanos == Long.MIN_VALUE ||
            System.nanoTime() - lastConstraintBoneUpdateNanos > CAMERA_BONE_TIMEOUT_NANOS
        if (force || stale) {
            constraintTranslationX = 1.0f
            constraintTranslationY = 1.0f
            constraintTranslationZ = 1.0f
            constraintRotationX = 1.0f
            constraintRotationY = 1.0f
            constraintRotationZ = 1.0f
        }
    }

    private fun clearStaleTaczCamera(force: Boolean) {
        val stale = lastTaczCameraUpdateNanos == Long.MIN_VALUE ||
            System.nanoTime() - lastTaczCameraUpdateNanos > CAMERA_BONE_TIMEOUT_NANOS
        if (force || stale) {
            taczCameraPitch = 0.0f
            taczCameraYaw = 0.0f
            taczCameraRoll = 0.0f
            lastTaczCameraUpdateNanos = Long.MIN_VALUE
        }
    }

    private fun clearStalePositioningViews(force: Boolean) {
        val stale = lastPositioningViewUpdateNanos == Long.MIN_VALUE ||
            System.nanoTime() - lastPositioningViewUpdateNanos > CAMERA_BONE_TIMEOUT_NANOS
        if (force || stale) {
            lastPositioningViewUpdateNanos = Long.MIN_VALUE
        }
    }

    private fun hasFreshAuthoredRig(): Boolean {
        val now = System.nanoTime()
        return lastCameraBoneUpdateNanos != Long.MIN_VALUE &&
            lastConstraintBoneUpdateNanos != Long.MIN_VALUE &&
            now - lastCameraBoneUpdateNanos <= CAMERA_BONE_TIMEOUT_NANOS &&
            now - lastConstraintBoneUpdateNanos <= CAMERA_BONE_TIMEOUT_NANOS
    }

    private fun hasFreshPositioningViews(): Boolean {
        clearStalePositioningViews(force = false)
        return lastPositioningViewUpdateNanos != Long.MIN_VALUE
    }

    private fun interpolateView(idle: Matrix4f, iron: Matrix4f, amount: Float): Matrix4f {
        val translation = idle.getTranslation(Vector3f())
            .lerp(iron.getTranslation(Vector3f()), amount)
        val rotation = idle.getUnnormalizedRotation(Quaternionf())
            .normalize()
            .slerp(iron.getUnnormalizedRotation(Quaternionf()).normalize(), amount)
        return Matrix4f().translation(translation).rotate(rotation)
    }

    private fun Float.absoluteFreedom(): Float = kotlin.math.abs(this).coerceIn(0.0f, 1.0f)

    data class CameraTransform(
        val position: Vec3,
        val pitch: Float,
        val yaw: Float,
        val roll: Float
    ) {
        companion object {
            val ZERO = CameraTransform(Vec3.ZERO, 0.0f, 0.0f, 0.0f)
        }
    }

    data class CameraRotation(val pitch: Float, val yaw: Float, val roll: Float) {
        companion object {
            val ZERO = CameraRotation(0.0f, 0.0f, 0.0f)
        }
    }

    private const val CAMERA_BONE_TIMEOUT_NANOS = 250_000_000L
    private const val MODEL_PIXELS_PER_BLOCK = 16.0f
    private const val DEFAULT_AIM_TIME_SECONDS = 0.25f
}
