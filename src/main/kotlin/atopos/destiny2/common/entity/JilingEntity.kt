// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.entity

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * Small floating Ghost model imported from the multi-texture Blockbench export.
 *
 * Ordinary spawned Ghosts stay stationary and track nearby players. During the
 * first resurrection the server temporarily turns one instance into a synced,
 * non-interactive cinematic actor and starts the authored awakening animation.
 */
class JilingEntity(
    type: EntityType<out JilingEntity>,
    level: Level
) : PathfinderMob(type, level), GeoEntity {
    private val animationCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    init {
        setNoGravity(true)
    }

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, LookAtPlayerGoal(this, Player::class.java, 8.0f))
        goalSelector.addGoal(2, RandomLookAroundGoal(this))
    }

    override fun tick() {
        super.tick()
        setNoGravity(true)
        deltaMovement = if (isAwakeningActor()) {
            net.minecraft.world.phys.Vec3.ZERO
        } else {
            deltaMovement.multiply(0.75, 0.75, 0.75)
        }
    }

    override fun removeWhenFarAway(distanceToClosestPlayer: Double): Boolean = false

    override fun shouldBeSaved(): Boolean = !isAwakeningActor() && super.shouldBeSaved()

    fun prepareAwakeningActor() {
        entityData.set(DATA_AWAKENING_ACTOR, true)
        entityData.set(DATA_AWAKENING_STARTED, false)
        isInvisible = true
        isInvulnerable = true
        isNoAi = true
        setNoGravity(true)
        deltaMovement = net.minecraft.world.phys.Vec3.ZERO
    }

    fun startAwakeningAnimation() {
        if (!isAwakeningActor()) return
        entityData.set(DATA_AWAKENING_STARTED, true)
        isInvisible = false
    }

    fun isAwakeningActor(): Boolean = entityData.get(DATA_AWAKENING_ACTOR)

    fun isAwakeningStarted(): Boolean = entityData.get(DATA_AWAKENING_STARTED)

    override fun isPushable(): Boolean = !isAwakeningActor() && super.isPushable()

    override fun isPickable(): Boolean = !isAwakeningActor() && super.isPickable()

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_AWAKENING_ACTOR, false)
        builder.define(DATA_AWAKENING_STARTED, false)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "awakening_controller", 0) { state ->
                if (!isAwakeningStarted()) return@AnimationController PlayState.STOP
                state.setAndContinue(AWAKENING_ANIMATION)
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animationCache

    companion object {
        private val DATA_AWAKENING_ACTOR: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(JilingEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val DATA_AWAKENING_STARTED: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(JilingEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val AWAKENING_ANIMATION = RawAnimation.begin()
            .thenPlayAndHold("animation.destiny2.jiling.awakening")

        fun createAttributes(): AttributeSupplier.Builder =
            createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.FOLLOW_RANGE, 8.0)
    }
}
