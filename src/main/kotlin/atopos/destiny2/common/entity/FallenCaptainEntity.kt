// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.entity

import atopos.destiny2.common.particle.BedrockParticleKeyframeBridge
import atopos.destiny2.common.sound.DestinySounds
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.EnumSet
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class FallenCaptainEntity(
    type: EntityType<out FallenCaptainEntity>,
    level: Level
) : Monster(type, level), GeoEntity {
    private val animationCache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    private var actionTicks = 0
    private var attackCooldown = 0
    private var shotgunCooldown = 0
    private var closeAttackIndex = 0
    private var comboStage = 0
    private var hasRoared = false
    private var pathUpdateCooldown = 0
    private var retreatUpdateCooldown = 0
    private var cachedRetreatPoint: Vec3? = null
    private var actionTargetId = -1
    private var visualWalkAnimationSpeed = 1.0f
    private var lastVisualActionSequence = Int.MIN_VALUE

    val combatAction: CombatAction
        get() = CombatAction.fromId(entityData.get(DATA_COMBAT_ACTION))

    override fun registerGoals() {
        goalSelector.addGoal(0, FloatGoal(this))
        goalSelector.addGoal(1, FallenCaptainCombatGoal(this))
        goalSelector.addGoal(6, WaterAvoidingRandomStrollGoal(this, 0.8))
        goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 12.0f))
        goalSelector.addGoal(8, RandomLookAroundGoal(this))

        targetSelector.addGoal(1, HurtByTargetGoal(this))
        targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
    }

    override fun customServerAiStep() {
        super.customServerAiStep()
        if (attackCooldown > 0) attackCooldown--
        if (shotgunCooldown > 0) shotgunCooldown--
        if (pathUpdateCooldown > 0) pathUpdateCooldown--
        if (retreatUpdateCooldown > 0) retreatUpdateCooldown--
        if (combatAction != CombatAction.NONE && target?.isAlive != true) {
            val committedTarget = committedActionTarget()
            committedTarget?.let(::faceTarget)
            tickAction(committedTarget)
        }
    }

    internal fun combatTick(target: LivingEntity) {
        if (combatAction != CombatAction.NONE) {
            navigation.stop()
            val committedTarget = committedActionTarget()
            committedTarget?.let(::faceTarget)
            tickAction(committedTarget)
            return
        }

        faceTarget(target)
        if (!hasRoared) {
            hasRoared = true
            beginAction(CombatAction.ROAR, target)
            return
        }

        val distance = distanceTo(target).toDouble()
        if (FallenCaptainCombatRules.shouldRetreat(health, maxHealth)) {
            retreatAndFight(target, distance)
        } else {
            pursueAndFight(target, distance)
        }
    }

    private fun pursueAndFight(target: LivingEntity, distance: Double) {
        if (canStartMelee(target) && attackCooldown <= 0) {
            when (closeAttackIndex++ % 3) {
                0 -> beginAction(CombatAction.LEFT_SLASH, target)
                1 -> beginAction(CombatAction.RIGHT_SLASH, target)
                else -> {
                    comboStage = 1
                    beginAction(CombatAction.SHOOT, target)
                }
            }
            return
        }

        if (distance in MID_RANGE_SHOT_MIN_DISTANCE..SHOTGUN_RANGE &&
            shotgunCooldown <= 0 &&
            hasLineOfSight(target)
        ) {
            beginAction(CombatAction.SHOOT, target)
            return
        }

        moveToward(target, PURSUIT_SPEED)
    }

    private fun retreatAndFight(target: LivingEntity, distance: Double) {
        if (distance < RETREAT_MIN_DISTANCE || !hasLineOfSight(target)) {
            moveAwayFrom(target)
            faceTarget(target)
        } else if (distance > RETREAT_MAX_DISTANCE) {
            moveToward(target, LOW_HEALTH_REPOSITION_SPEED)
        } else {
            navigation.stop()
            cachedRetreatPoint = null
        }

        if (distance <= SHOTGUN_RANGE && shotgunCooldown <= 0 && hasLineOfSight(target)) {
            if (canStartMelee(target)) comboStage = 1
            beginAction(CombatAction.SHOOT, target)
        }
    }

    private fun tickAction(target: LivingEntity?) {
        actionTicks++
        when (combatAction) {
            CombatAction.LEFT_SLASH -> {
                if (actionTicks == MELEE_SWING_SOUND_TICK) {
                    playMeleeSwingSound()
                }
                if (actionTicks == combatAction.impactTick) {
                    target?.let { applyMeleeSweep(it, FallenCaptainCombatRules.LEFT_MELEE_DAMAGE) }
                }
            }
            CombatAction.RIGHT_SLASH -> {
                if (actionTicks == MELEE_SWING_SOUND_TICK) {
                    playMeleeSwingSound()
                }
                if (actionTicks == combatAction.impactTick) {
                    target?.let { applyMeleeSweep(it, FallenCaptainCombatRules.RIGHT_MELEE_DAMAGE) }
                }
            }
            CombatAction.SHOOT -> {
                if (actionTicks == combatAction.impactTick) {
                    target?.let(::fireShotgun)
                }
            }
            else -> Unit
        }

        if (actionTicks >= combatAction.durationTicks) {
            finishAction()
        }
    }

    private fun applyMeleeSweep(primaryTarget: LivingEntity, damage: Float) {
        val yawRadians = Math.toRadians(yBodyRot.toDouble())
        val forwardX = -sin(yawRadians)
        val forwardZ = cos(yawRadians)
        val searchBox = AABB(
            x - MELEE_SEARCH_RADIUS,
            y - 1.5,
            z - MELEE_SEARCH_RADIUS,
            x + MELEE_SEARCH_RADIUS,
            y + 3.5,
            z + MELEE_SEARCH_RADIUS
        )
        val victims = level().getEntitiesOfClass(LivingEntity::class.java, searchBox) { candidate ->
            isValidMeleeVictim(candidate, primaryTarget) &&
                verticalGapTo(candidate) <= FallenCaptainCombatRules.MELEE_MAX_VERTICAL_GAP &&
                hasLineOfSight(candidate) &&
                FallenCaptainCombatRules.isInsideMeleeSector(
                    forwardX,
                    forwardZ,
                    candidate.x - x,
                    candidate.z - z,
                    horizontalEdgeGapTo(candidate)
                )
        }
        victims.forEach { victim ->
            if (victim.hurt(damageSources().mobAttack(this), damage)) {
                victim.knockback(0.45, x - victim.x, z - victim.z)
            }
        }
    }

    private fun isValidMeleeVictim(candidate: LivingEntity, primaryTarget: LivingEntity): Boolean {
        if (!candidate.isAlive || candidate === this || candidate is FallenCaptainEntity || isAlliedTo(candidate)) {
            return false
        }
        if (candidate is Player && (candidate.isCreative || candidate.isSpectator)) return false
        return candidate === primaryTarget || candidate is Player
    }

    private fun canStartMelee(target: LivingEntity): Boolean {
        return target.isAlive &&
            FallenCaptainCombatRules.canStartMelee(
                horizontalEdgeGapTo(target),
                verticalGapTo(target),
                hasLineOfSight(target)
            )
    }

    private fun horizontalEdgeGapTo(target: LivingEntity): Double {
        val ownBox = boundingBox
        val targetBox = target.boundingBox
        val gapX = maxOf(targetBox.minX - ownBox.maxX, ownBox.minX - targetBox.maxX, 0.0)
        val gapZ = maxOf(targetBox.minZ - ownBox.maxZ, ownBox.minZ - targetBox.maxZ, 0.0)
        return kotlin.math.sqrt(gapX * gapX + gapZ * gapZ)
    }

    private fun verticalGapTo(target: LivingEntity): Double {
        val ownBox = boundingBox
        val targetBox = target.boundingBox
        return maxOf(targetBox.minY - ownBox.maxY, ownBox.minY - targetBox.maxY, 0.0)
    }

    private fun fireShotgun(target: LivingEntity) {
        if (!target.isAlive || !hasLineOfSight(target)) return
        val aimPoint = target.eyePosition.add(target.deltaMovement.scale(0.3))
        val origin = eyePosition
        val baseDirection = aimPoint.subtract(origin).normalize()
        repeat(FallenCaptainCombatRules.PELLET_COUNT) {
            val spreadDirection = Vec3(
                baseDirection.x + random.nextGaussian() * SHOTGUN_SPREAD,
                baseDirection.y + random.nextGaussian() * SHOTGUN_SPREAD,
                baseDirection.z + random.nextGaussian() * SHOTGUN_SPREAD
            ).normalize()
            level().addFreshEntity(FallenCaptainPelletEntity(level(), this, spreadDirection))
        }
        level().playSound(
            null,
            blockPosition(),
            DestinySounds.FALLEN_SHOCK_RIFLE_FIRE,
            SoundSource.HOSTILE,
            0.24f,
            0.92f + random.nextFloat() * 0.16f
        )
    }

    private fun playMeleeSwingSound() {
        level().playSound(
            null,
            blockPosition(),
            DestinySounds.FALLEN_CAPTAIN_MELEE_SWING,
            SoundSource.HOSTILE,
            0.7f,
            0.92f + random.nextFloat() * 0.14f
        )
    }

    private fun finishAction() {
        val finished = combatAction
        val comboTarget = committedActionTarget()
        if (finished == CombatAction.SHOOT) {
            shotgunCooldown = SHOTGUN_COOLDOWN_TICKS
        }
        entityData.set(DATA_COMBAT_ACTION, CombatAction.NONE.id)
        actionTicks = 0
        actionTargetId = -1
        when {
            finished == CombatAction.SHOOT &&
                comboStage == 1 &&
                comboTarget != null &&
                canStartMelee(comboTarget) -> {
                comboStage = 2
                beginAction(CombatAction.LEFT_SLASH, comboTarget)
            }
            finished == CombatAction.LEFT_SLASH &&
                comboStage == 2 &&
                comboTarget != null &&
                canStartMelee(comboTarget) -> {
                comboStage = 0
                beginAction(CombatAction.RIGHT_SLASH, comboTarget)
            }
            else -> {
                comboStage = 0
                attackCooldown = if (finished == CombatAction.ROAR) 8 else 7
            }
        }
    }

    private fun beginAction(action: CombatAction, actionTarget: LivingEntity?) {
        navigation.stop()
        cachedRetreatPoint = null
        actionTicks = 0
        actionTargetId = actionTarget?.id ?: -1
        entityData.set(DATA_COMBAT_ACTION, action.id)
        entityData.set(DATA_ACTION_SEQUENCE, entityData.get(DATA_ACTION_SEQUENCE) + 1)
        if (action == CombatAction.ROAR) {
            level().playSound(
                null,
                blockPosition(),
                DestinySounds.FALLEN_CAPTAIN_ROAR,
                SoundSource.HOSTILE,
                0.9f,
                0.94f + random.nextFloat() * 0.1f
            )
        }
    }

    private fun committedActionTarget(): LivingEntity? {
        if (actionTargetId < 0) return null
        return level().getEntity(actionTargetId) as? LivingEntity
    }

    private fun moveToward(target: LivingEntity, speed: Double) {
        if (pathUpdateCooldown <= 0 || navigation.isDone) {
            navigation.moveTo(target, speed)
            pathUpdateCooldown = PATH_RECALCULATION_TICKS
        }
    }

    private fun moveAwayFrom(target: LivingEntity) {
        val shouldRefreshPath =
            retreatUpdateCooldown <= 0 || cachedRetreatPoint == null || navigation.isDone
        if (shouldRefreshPath) {
            cachedRetreatPoint = DefaultRandomPos.getPosAway(this, 8, 4, target.position())
                ?: fallbackRetreatPoint(target)
            retreatUpdateCooldown = RETREAT_PATH_RECALCULATION_TICKS
            cachedRetreatPoint?.let { point ->
                navigation.moveTo(point.x, point.y, point.z, RETREAT_SPEED)
            }
        }
    }

    private fun fallbackRetreatPoint(target: LivingEntity): Vec3 {
        val away = position().subtract(target.position())
        val horizontalAway = Vec3(away.x, 0.0, away.z).normalize()
        val lateralSign = if ((tickCount / RETREAT_DIRECTION_INTERVAL) % 2 == 0) 1.0 else -1.0
        val lateral = Vec3(-horizontalAway.z, 0.0, horizontalAway.x).scale(1.5 * lateralSign)
        return position().add(horizontalAway.scale(6.0)).add(lateral)
    }

    private fun faceTarget(target: LivingEntity) {
        lookControl.setLookAt(target, 45.0f, 45.0f)
        val dx = target.x - x
        val dz = target.z - z
        val facing = (atan2(dz, dx) * Mth.RAD_TO_DEG).toFloat() - 90.0f
        yRot = facing
        yBodyRot = facing
        yHeadRot = facing
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_COMBAT_ACTION, CombatAction.NONE.id)
        builder.define(DATA_ACTION_SEQUENCE, 0)
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.putBoolean("HasRoared", hasRoared)
        compound.putInt("AttackCooldown", attackCooldown)
        compound.putInt("ShotgunCooldown", shotgunCooldown)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        hasRoared = compound.getBoolean("HasRoared")
        attackCooldown = compound.getInt("AttackCooldown").coerceAtLeast(0)
        shotgunCooldown = compound.getInt("ShotgunCooldown").coerceAtLeast(0)
        comboStage = 0
        actionTicks = 0
        actionTargetId = -1
        entityData.set(DATA_COMBAT_ACTION, CombatAction.NONE.id)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "fallen_captain_controller", 0) { state ->
                val sequence = entityData.get(DATA_ACTION_SEQUENCE)
                if (sequence != lastVisualActionSequence) {
                    lastVisualActionSequence = sequence
                    state.controller.forceAnimationReset()
                }
                state.controller.transitionLength(if (combatAction == CombatAction.NONE) 3 else 0)
                val animation = when (combatAction) {
                    CombatAction.ROAR -> {
                        state.setControllerSpeed(1.0f)
                        ROAR_ANIMATION
                    }
                    CombatAction.LEFT_SLASH -> {
                        state.setControllerSpeed(1.0f)
                        LEFT_SLASH_ANIMATION
                    }
                    CombatAction.RIGHT_SLASH -> {
                        state.setControllerSpeed(1.0f)
                        RIGHT_SLASH_ANIMATION
                    }
                    CombatAction.SHOOT -> {
                        state.setControllerSpeed(1.0f)
                        SHOOT_ANIMATION
                    }
                    CombatAction.NONE -> if (state.isMoving) {
                        val targetSpeed = FallenCaptainCombatRules
                            .walkAnimationSpeed(deltaMovement.horizontalDistance())
                            .toFloat()
                        visualWalkAnimationSpeed = Mth.lerp(0.22f, visualWalkAnimationSpeed, targetSpeed)
                        state.setControllerSpeed(visualWalkAnimationSpeed)
                        WALK_ANIMATION
                    } else {
                        visualWalkAnimationSpeed = Mth.lerp(0.22f, visualWalkAnimationSpeed, 1.0f)
                        state.setControllerSpeed(1.0f)
                        IDLE_ANIMATION
                    }
                }
                state.controller.setAnimation(animation)
                PlayState.CONTINUE
            }.setParticleKeyframeHandler { event ->
                val data = event.keyframeData
                BedrockParticleKeyframeBridge.emit(data.effect, data.locator)
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = animationCache

    enum class CombatAction(
        val id: Int,
        val durationTicks: Int,
        val impactTick: Int = -1
    ) {
        NONE(0, 0),
        ROAR(1, 44),
        LEFT_SLASH(2, 16, 7),
        RIGHT_SLASH(3, 16, 7),
        SHOOT(4, 21, 4);

        companion object {
            fun fromId(id: Int): CombatAction = entries.firstOrNull { it.id == id } ?: NONE
        }
    }

    companion object {
        private val DATA_COMBAT_ACTION: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(FallenCaptainEntity::class.java, EntityDataSerializers.INT)
        private val DATA_ACTION_SEQUENCE: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(FallenCaptainEntity::class.java, EntityDataSerializers.INT)

        private const val SHOTGUN_RANGE = 18.0
        private const val MID_RANGE_SHOT_MIN_DISTANCE = 8.0
        private const val RETREAT_MIN_DISTANCE = 9.0
        private const val RETREAT_MAX_DISTANCE = 15.0
        private const val PURSUIT_SPEED = 1.25
        private const val RETREAT_SPEED = 1.05
        private const val LOW_HEALTH_REPOSITION_SPEED = 0.85
        private const val SHOTGUN_SPREAD = 0.075
        private const val PATH_RECALCULATION_TICKS = 5
        private const val RETREAT_PATH_RECALCULATION_TICKS = 10
        private const val RETREAT_DIRECTION_INTERVAL = 50
        private const val SHOTGUN_COOLDOWN_TICKS = 32
        private const val MELEE_SWING_SOUND_TICK = 4
        private const val MELEE_SEARCH_RADIUS = 4.5
        private val IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle")
        private val WALK_ANIMATION = RawAnimation.begin().thenLoop("walk")
        private val ROAR_ANIMATION = RawAnimation.begin().thenPlay("roar")
        private val LEFT_SLASH_ANIMATION = RawAnimation.begin().thenPlay("animation.single_slash")
        private val RIGHT_SLASH_ANIMATION = RawAnimation.begin().thenPlay("animation.left_slash")
        private val SHOOT_ANIMATION = RawAnimation.begin().thenPlay("animation.shoot_new")

        fun createAttributes(): AttributeSupplier.Builder {
            return createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, FallenCaptainCombatRules.MAX_HEALTH.toDouble())
                .add(Attributes.MOVEMENT_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 36.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.45)
        }
    }
}

private class FallenCaptainCombatGoal(
    private val captain: FallenCaptainEntity
) : Goal() {
    init {
        flags = EnumSet.of(Flag.MOVE, Flag.LOOK)
    }

    override fun canUse(): Boolean = captain.target?.isAlive == true

    override fun canContinueToUse(): Boolean = captain.target?.isAlive == true

    override fun tick() {
        captain.target?.takeIf(LivingEntity::isAlive)?.let(captain::combatTick)
    }

    override fun stop() {
        captain.navigation.stop()
    }
}
