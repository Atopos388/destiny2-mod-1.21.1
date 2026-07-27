package atopos.destiny2.client.cinematic

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animation.EasingType
import software.bernie.geckolib.animation.keyframe.AnimationPoint
import software.bernie.geckolib.animation.keyframe.BoneAnimation
import software.bernie.geckolib.animation.keyframe.Keyframe
import software.bernie.geckolib.animation.keyframe.KeyframeStack
import software.bernie.geckolib.cache.GeckoLibCache
import software.bernie.geckolib.loading.math.MathValue
import kotlin.math.ceil

/**
 * Reads the same baked GeckoLib keyframes used by normal model animation, but
 * evaluates only a top-level `camera` bone without requiring a rendered entity.
 */
class GeckoLibCameraTrackSampler private constructor(
    private val cameraBone: BoneAnimation,
    val durationTicks: Int
) {
    data class LocalPose(
        val positionPixels: Vec3,
        val pitch: Float,
        val yaw: Float,
        val roll: Float
    )

    fun sample(animationTick: Double): LocalPose {
        val tick = animationTick.coerceIn(0.0, durationTicks.toDouble())
        val position = cameraBone.positionKeyFrames()
        val rotation = cameraBone.rotationKeyFrames()
        return LocalPose(
            positionPixels = Vec3(
                sampleAxis(position.xKeyframes(), tick),
                sampleAxis(position.yKeyframes(), tick),
                sampleAxis(position.zKeyframes(), tick)
            ),
            // GeckoLib's baked rotation channels are already converted to radians
            // with the same Blockbench axis signs used by the weapon camera bridge.
            pitch = Math.toDegrees(sampleAxis(rotation.xKeyframes(), tick)).toFloat(),
            yaw = Math.toDegrees(sampleAxis(rotation.yKeyframes(), tick)).toFloat(),
            roll = Math.toDegrees(sampleAxis(rotation.zKeyframes(), tick)).toFloat()
        )
    }

    private fun sampleAxis(frames: List<Keyframe<MathValue>>, tick: Double): Double {
        if (frames.isEmpty()) return 0.0
        var segmentStart = 0.0
        for (frame in frames) {
            val segmentEnd = segmentStart + frame.length()
            if (segmentEnd > tick) {
                return evaluate(frame, tick - segmentStart)
            }
            segmentStart = segmentEnd
        }
        val last = frames.last()
        return evaluate(last, last.length())
    }

    private fun evaluate(frame: Keyframe<MathValue>, localTick: Double): Double =
        EasingType.lerpWithOverride(
            AnimationPoint(
                frame,
                localTick.coerceAtLeast(0.0),
                frame.length(),
                frame.startValue().get(),
                frame.endValue().get()
            ),
            null
        )

    companion object {
        fun load(
            animationResource: ResourceLocation,
            animationName: String,
            cameraBoneName: String = "camera"
        ): GeckoLibCameraTrackSampler? {
            val animation = GeckoLibCache.getBakedAnimations()[animationResource]
                ?.getAnimation(animationName)
                ?: return null
            val cameraBone = animation.boneAnimations().firstOrNull { it.boneName() == cameraBoneName }
                ?: return null
            val duration = ceil(animation.length()).toInt().coerceIn(1, MAX_DURATION_TICKS)
            return GeckoLibCameraTrackSampler(cameraBone, duration)
        }

        private const val MAX_DURATION_TICKS = 20 * 60
    }
}
