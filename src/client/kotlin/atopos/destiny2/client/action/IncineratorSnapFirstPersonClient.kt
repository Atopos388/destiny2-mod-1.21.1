package atopos.destiny2.client.action

import atopos.destiny2.client.model.IncineratorSnapArmModel
import atopos.destiny2.client.renderer.IncineratorSnapArmRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * First-person GeckoLib overlay for Incinerator Snap.
 *
 * This renders the exported Blockbench bone hierarchy directly. In particular,
 * `lefthand` keeps its authored pivot and `lefthand_pos` remains its child,
 * rather than replaying child-bone values around vanilla's camera-space arm.
 */
object IncineratorSnapFirstPersonClient : GeoAnimatable {
    private const val DURATION_SECONDS = 1.2833f
    private const val TICKS_PER_SECOND = 20.0f
    private const val INSTANCE_ID = 0L
    private val animation = RawAnimation.begin().thenPlay("xiangzhi")
    private val cache = GeckoLibUtil.createInstanceCache(this)
    private val model = IncineratorSnapArmModel()
    private val renderer = IncineratorSnapArmRenderer(model)
    private var startedAtPlayerTick = Int.MIN_VALUE

    fun play() {
        val player = Minecraft.getInstance().player ?: return
        startedAtPlayerTick = player.tickCount
        val manager = cache.getManagerForId<IncineratorSnapFirstPersonClient>(INSTANCE_ID)
        manager.animationControllers["snap_controller"]?.forceAnimationReset()
        manager.tryTriggerAnimation("snap_controller", "snap")
    }

    fun isActive(player: AbstractClientPlayer): Boolean =
        elapsedSeconds(player, 0.0f) in 0.0f..DURATION_SECONDS

    fun render(
        player: AbstractClientPlayer,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        combinedLight: Int
    ) {
        if (!isActive(player)) return

        val texture = player.skin.texture()
        val renderType = RenderType.entityTranslucent(texture)
        poseStack.pushPose()
        try {
            // GeoObjectRenderer adds (0.5, 0.51, 0.5) for block-style
            // objects. Cancel that convention, then place the authored camera
            // bone at the first-person eye origin.
            val cameraBone = model.getBone("camera").orElse(null)
            if (cameraBone != null) {
                poseStack.translate(
                    (-cameraBone.pivotX / MODEL_PIXELS_PER_BLOCK).toDouble(),
                    (-cameraBone.pivotY / MODEL_PIXELS_PER_BLOCK).toDouble(),
                    (-cameraBone.pivotZ / MODEL_PIXELS_PER_BLOCK).toDouble()
                )
            }
            poseStack.translate(-0.5, -0.51, -0.5)
            renderer.render(
                poseStack,
                this,
                bufferSource,
                renderType,
                bufferSource.getBuffer(renderType),
                combinedLight,
                partialTick
            )
        } finally {
            poseStack.popPose()
        }
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "snap_controller", 0) { PlayState.CONTINUE }
                .triggerableAnim("snap", animation)
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getTick(context: Any): Double =
        Minecraft.getInstance().player?.tickCount?.toDouble() ?: 0.0

    private fun elapsedSeconds(player: AbstractClientPlayer, partialTick: Float): Float {
        if (startedAtPlayerTick == Int.MIN_VALUE) return -1.0f
        return (player.tickCount - startedAtPlayerTick + partialTick) / TICKS_PER_SECOND
    }

    private const val MODEL_PIXELS_PER_BLOCK = 16.0f
}
