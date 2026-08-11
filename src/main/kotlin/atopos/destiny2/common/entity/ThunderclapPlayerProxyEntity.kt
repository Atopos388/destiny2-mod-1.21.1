// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.entity

import atopos.destiny2.common.action.ThunderclapTiming
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID

/** Client-side visual stand-in that preserves the authored segmented player hierarchy. */
class ThunderclapPlayerProxyEntity(
    entityType: EntityType<*>,
    level: Level
) : Entity(entityType, level), GeoEntity {
    enum class Phase(val animationName: String, val playbackSpeed: Double) {
        CHARGE("animation.destiny2.player.thunderclap_charge", 1.0),
        RELEASE("animation.destiny2.player.thunderclap_release", ThunderclapTiming.RELEASE_PLAYBACK_SPEED)
    }

    private val animationCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    var targetPlayerId: UUID? = null
        private set
    var phase: Phase = Phase.CHARGE
        private set
    private var remainingTicks: Int = 1
    private var phaseDurationTicks: Int = 1
    private var elapsedPhaseTicks: Int = 0
    private var lockedFacingYaw: Float = 0f
    private var visualHitStopUntilNanos: Long = 0L

    init {
        noPhysics = true
        setNoGravity(true)
    }

    fun configure(targetPlayerId: UUID, phase: Phase, durationTicks: Int, facingYaw: Float) {
        this.targetPlayerId = targetPlayerId
        this.phase = phase
        this.phaseDurationTicks = durationTicks.coerceAtLeast(1)
        this.remainingTicks = phaseDurationTicks
        this.elapsedPhaseTicks = 0
        this.lockedFacingYaw = facingYaw
        this.yRot = facingYaw
        this.yRotO = facingYaw
    }

    /** Smooth client-only phase time used by the authored hand charge render layer. */
    fun phaseProgress(partialTick: Float): Float =
        ((elapsedPhaseTicks + partialTick) / phaseDurationTicks.toFloat()).coerceIn(0.0f, 1.0f)

    fun phaseAge(partialTick: Float): Float =
        ((elapsedPhaseTicks + partialTick) * phase.playbackSpeed).toFloat()

    fun beginVisualHitStop(untilNanos: Long) {
        visualHitStopUntilNanos = maxOf(visualHitStopUntilNanos, untilNanos)
    }

    override fun tick() {
        if (level().isClientSide && System.nanoTime() < visualHitStopUntilNanos) {
            val target = targetPlayerId?.let(level()::getPlayerByUUID)
            if (target == null || target.isRemoved) {
                discard()
                return
            }
            setPos(target.x, target.y, target.z)
            yRot = lockedFacingYaw
            yRotO = lockedFacingYaw
            xRot = 0f
            xRotO = 0f
            return
        }
        super.tick()
        if (!level().isClientSide || remainingTicks-- <= 0) {
            discard()
            return
        }
        elapsedPhaseTicks++
        val target = targetPlayerId?.let(level()::getPlayerByUUID)
        if (target == null || target.isRemoved) {
            discard()
            return
        }
        setPos(target.x, target.y, target.z)
        // The pose direction belongs to the animation phase, not the freely orbiting camera.
        // CHARGE captures its starting yaw; RELEASE creates a new proxy using release-time view yaw.
        yRot = lockedFacingYaw
        yRotO = lockedFacingYaw
        xRot = 0f
        xRotO = 0f
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        val controller = AnimationController(this, "thunderclap_player", 0) { state ->
                state.controller.setAnimation(RawAnimation.begin().thenPlay(phase.animationName))
                PlayState.CONTINUE
            }
        controller.setAnimationSpeedHandler { it.phase.playbackSpeed }
        controllers.add(controller)
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animationCache
    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) = Unit
    override fun readAdditionalSaveData(compound: CompoundTag) = Unit
    override fun addAdditionalSaveData(compound: CompoundTag) = Unit
    override fun isPickable(): Boolean = false
}
