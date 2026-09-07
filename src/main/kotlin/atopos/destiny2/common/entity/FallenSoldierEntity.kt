package atopos.destiny2.common.entity

import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.*
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

/** Independent testable enemy. No natural spawns or changes to the captain. */
class FallenSoldierEntity(type: EntityType<out FallenSoldierEntity>, level: Level) :
    Monster(type, level), GeoEntity {
    private val cache = GeckoLibUtil.createInstanceCache(this)
    private var attackTicks = 0
    private var attackSequence = 0
    private var visualSequence = 0

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, MeleeAttackGoal(this, 1.2, false))
        goalSelector.addGoal(6, WaterAvoidingRandomStrollGoal(this, 0.7))
        goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 10f))
        goalSelector.addGoal(8, RandomLookAroundGoal(this))
        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun doHurtTarget(target: Entity): Boolean {
        if (!level().isClientSide) level().broadcastEntityEvent(this, 61.toByte())
        return super.doHurtTarget(target)
    }

    override fun handleEntityEvent(event: Byte) {
        if (event == 61.toByte()) { attackTicks = 24; attackSequence++ }
        else super.handleEntityEvent(event)
    }

    override fun tick() {
        super.tick()
        if (attackTicks > 0) attackTicks--
    }

    override fun tickDeath() {
        deathTime++
        if (deathTime >= 48 && !level().isClientSide && !isRemoved) {
            level().broadcastEntityEvent(this, 60.toByte())
            remove(Entity.RemovalReason.KILLED)
        }
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "body", 3) { state ->
            if (visualSequence != attackSequence) {
                visualSequence = attackSequence
                state.controller.forceAnimationReset()
            }
            state.controller.transitionLength(if (isDeadOrDying || attackTicks > 0) 0 else 3)
            state.setAndContinue(when {
                isDeadOrDying -> DEATH
                hurtTime > 0 -> HURT
                attackTicks > 0 -> SLASH
                state.isMoving && target != null -> RUN
                state.isMoving -> WALK
                else -> IDLE
            })
        })
    }

    override fun getAnimatableInstanceCache() = cache

    companion object {
        fun createAttributes() = createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 24.0)
            .add(Attributes.MOVEMENT_SPEED, 0.25)
            .add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.FOLLOW_RANGE, 24.0)
        private val IDLE = RawAnimation.begin().thenLoop("animation.fallen.idle")
        private val WALK = RawAnimation.begin().thenLoop("animation.fallen.walk")
        private val RUN = RawAnimation.begin().thenLoop("animation.fallen.run")
        private val HURT = RawAnimation.begin().thenPlay("animation.fallen.hurt")
        private val SLASH = RawAnimation.begin().thenPlay("animation.fallen.slash")
        private val DEATH = RawAnimation.begin().thenPlayAndHold("animation.fallen.death")
    }
}
