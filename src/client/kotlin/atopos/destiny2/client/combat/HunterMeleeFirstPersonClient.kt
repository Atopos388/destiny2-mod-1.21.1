package atopos.destiny2.client.combat

import atopos.destiny2.client.model.HunterMeleeFirstPersonModel
import atopos.destiny2.client.renderer.HunterMeleeFirstPersonRenderer
import atopos.destiny2.client.cinematic.GeckoLibCameraTrackSampler
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import atopos.destiny2.common.network.DestinyNetworking
import software.bernie.geckolib.animatable.GeoAnimatable
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

/** Authored first-person knife-and-player-arms overlay for Hunter uncharged melee. */
object HunterMeleeFirstPersonClient : GeoAnimatable {
    private const val MELEE_DURATION_SECONDS = 1.0833f
    private const val GRENADE_DURATION_SECONDS = 0.65f
    private const val CHARGED_MELEE_DURATION_SECONDS = 0.70f
    private const val TICKS_PER_SECOND = 20.0f
    private const val INSTANCE_ID = 0L
    private const val MODEL_PIXELS_PER_BLOCK = 16.0f
    private val meleeAnimation = RawAnimation.begin().thenPlay("jinzhan")
    private val grenadeAnimation = RawAnimation.begin().thenPlay("hunter_grenade_throw")
    private val chargedMeleeAnimation = RawAnimation.begin().thenPlay("hunter_charged_melee")
    private val cache = GeckoLibUtil.createInstanceCache(this)
    private val model = HunterMeleeFirstPersonModel()
    private val renderer = HunterMeleeFirstPersonRenderer(model)
    private var startedAtPlayerTick = Int.MIN_VALUE
    private var cameraTrack: GeckoLibCameraTrackSampler? = null
    private var activeAction = Action.NONE
    private var throwReleaseSent = false
    private var throwLocatorWorldPosition: Vec3? = null

    fun play() {
        val player = Minecraft.getInstance().player ?: return
        activeAction = Action.MELEE
        startedAtPlayerTick = player.tickCount
        cameraTrack = loadCameraTrack()
        val manager = cache.getManagerForId<HunterMeleeFirstPersonClient>(INSTANCE_ID)
        manager.animationControllers["hunter_melee_controller"]?.forceAnimationReset()
        manager.tryTriggerAnimation("hunter_melee_controller", "melee")
    }

    fun playGrenadeThrow() {
        val player = Minecraft.getInstance().player ?: return
        activeAction = Action.GRENADE
        throwReleaseSent = false
        throwLocatorWorldPosition = null
        startedAtPlayerTick = player.tickCount
        cameraTrack = null
        val manager = cache.getManagerForId<HunterMeleeFirstPersonClient>(INSTANCE_ID)
        manager.animationControllers["hunter_melee_controller"]?.forceAnimationReset()
        manager.tryTriggerAnimation("hunter_melee_controller", "grenade")
    }

    fun playChargedMelee() {
        val player = Minecraft.getInstance().player ?: return
        activeAction = Action.CHARGED_MELEE
        throwReleaseSent = false
        throwLocatorWorldPosition = null
        startedAtPlayerTick = player.tickCount
        cameraTrack = null
        val manager = cache.getManagerForId<HunterMeleeFirstPersonClient>(INSTANCE_ID)
        manager.animationControllers["hunter_melee_controller"]?.forceAnimationReset()
        manager.tryTriggerAnimation("hunter_melee_controller", "charged_melee")
    }

    fun isActive(player: AbstractClientPlayer): Boolean =
        elapsedSeconds(player, 0.0f) in 0.0f..activeDurationSeconds()

    fun isGrenadeThrowActive(): Boolean = activeAction == Action.GRENADE

    fun isChargedMeleeActive(): Boolean = activeAction == Action.CHARGED_MELEE

    fun isThrowingHandActionActive(): Boolean =
        activeAction == Action.GRENADE || activeAction == Action.CHARGED_MELEE

    fun updateThrowLocator(player: AbstractClientPlayer, partialTick: Float, worldPosition: Vec3) {
        if (!isThrowingHandActionActive() || throwReleaseSent) return
        throwLocatorWorldPosition = worldPosition
        val releaseSeconds = when (activeAction) {
            Action.GRENADE -> GRENADE_RELEASE_SECONDS
            Action.CHARGED_MELEE -> CHARGED_MELEE_RELEASE_SECONDS
            else -> return
        }
        if (elapsedSeconds(player, partialTick) < releaseSeconds) return
        throwReleaseSent = true
        when (activeAction) {
            Action.GRENADE -> ClientPlayNetworking.send(
                DestinyNetworking.ReleaseHunterGrenadePayload(
                    worldPosition.x,
                    worldPosition.y,
                    worldPosition.z
                )
            )
            Action.CHARGED_MELEE -> ClientPlayNetworking.send(
                DestinyNetworking.ReleaseHunterChargedMeleePayload(
                    worldPosition.x,
                    worldPosition.y,
                    worldPosition.z
                )
            )
            else -> Unit
        }
    }

    fun heldGrenadePosition(player: AbstractClientPlayer, partialTick: Float): Vec3? {
        if (activeAction != Action.GRENADE) return null
        if (elapsedSeconds(player, partialTick) !in 0.0f..<GRENADE_RELEASE_SECONDS) return null
        return throwLocatorWorldPosition
    }

    /**
     * Samples the authored top-level camera bone from GeckoLib's baked clip.
     * This uses the same easing evaluator as the model, including Catmull-Rom
     * segments, so the game camera and hand animation remain on one timeline.
     */
    fun cameraTransform(partialTick: Float): GeckoLibCameraTrackSampler.LocalPose? {
        val player = Minecraft.getInstance().player ?: return null
        if (!isActive(player) || activeAction != Action.MELEE) return null
        val track = cameraTrack ?: loadCameraTrack()?.also { cameraTrack = it } ?: return null
        val animationTick = player.tickCount - startedAtPlayerTick + partialTick
        return track.sample(animationTick.toDouble())
    }

    fun render(
        player: AbstractClientPlayer,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        combinedLight: Int
    ) {
        if (!isActive(player)) return

        val renderType = RenderType.entityTranslucent(HunterMeleeFirstPersonModel.TEXTURE)
        poseStack.pushPose()
        try {
            /*
             * The Blockbench source contains two different objects named
             * "camera": a bone used only for the authored rotation track, and
             * the actual first-person camera object. GeckoLib exports the bone
             * but drops the camera object, so cameraBone.pivot is not the view
             * origin. Keep the real camera object's authored position here in
             * GeckoLib coordinates (Blockbench X is mirrored on export).
             */
            val blockbenchViewCameraX = -0.25f
            val blockbenchViewCameraY = 11.2875f
            val blockbenchViewCameraZ = 18.0f
            poseStack.translate(
                (-blockbenchViewCameraX / MODEL_PIXELS_PER_BLOCK).toDouble(),
                (-blockbenchViewCameraY / MODEL_PIXELS_PER_BLOCK).toDouble(),
                (-blockbenchViewCameraZ / MODEL_PIXELS_PER_BLOCK).toDouble()
            )
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
            AnimationController(this, "hunter_melee_controller", 0) { PlayState.CONTINUE }
                .triggerableAnim("melee", meleeAnimation)
                .triggerableAnim("grenade", grenadeAnimation)
                .triggerableAnim("charged_melee", chargedMeleeAnimation)
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun getTick(context: Any): Double =
        Minecraft.getInstance().player?.tickCount?.toDouble() ?: 0.0

    private fun elapsedSeconds(player: AbstractClientPlayer, partialTick: Float): Float {
        if (startedAtPlayerTick == Int.MIN_VALUE) return -1.0f
        return (player.tickCount - startedAtPlayerTick + partialTick) / TICKS_PER_SECOND
    }

    private fun activeDurationSeconds(): Float = when (activeAction) {
        Action.MELEE -> MELEE_DURATION_SECONDS
        Action.GRENADE -> GRENADE_DURATION_SECONDS
        Action.CHARGED_MELEE -> CHARGED_MELEE_DURATION_SECONDS
        Action.NONE -> -1.0f
    }

    private fun loadCameraTrack(): GeckoLibCameraTrackSampler? =
        GeckoLibCameraTrackSampler.load(ANIMATION_RESOURCE, "jinzhan")

    private val ANIMATION_RESOURCE: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "animations/hunter_melee_first_person.animation.json"
        )

    private enum class Action {
        NONE,
        MELEE,
        GRENADE,
        CHARGED_MELEE
    }

    private const val GRENADE_RELEASE_SECONDS = 0.30f
    private const val CHARGED_MELEE_RELEASE_SECONDS = 0.30f
}
