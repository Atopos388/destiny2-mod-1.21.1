// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.entity

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.tacz.TaczEntityHitbox
import atopos.destiny2.common.weapon.AmmoDropSystem
import atopos.destiny2.common.weapon.DamageNumberRuntime
import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.weapon.DestinyDamageElement
import atopos.destiny2.common.weapon.DestinyElementalDamageCarrier
import atopos.destiny2.common.weapon.DestinyWeaponDamageCarrier
import atopos.destiny2.common.weapon.AscWeaponRuntime
import atopos.destiny2.common.item.GenericGunPackItem
import atopos.destiny2.common.weapon.ForgottenNameExoticRules
import atopos.destiny2.common.weapon.ForgottenNameExoticRuntime
import atopos.destiny2.common.weapon.MonteCarloExoticRuntime
import atopos.destiny2.common.weapon.WeaponCombatProfile
import atopos.destiny2.common.weapon.WeaponDistanceDamage
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * TaCZ-style kinetic bullet: a real tracked entity with per-tick swept block/entity collision.
 * The implementation is adapted from TaCZ Refabricated's EntityKineticBullet (GPL-3.0).
 */
class ForgottenNameBulletEntity : Projectile, DestinyWeaponDamageCarrier {
    private var damage = 10.0f
    private var precisionMultiplier = 1.7f
    private var ammoType = DestinyAmmoType.PRIMARY
    override val destinyAmmoType: DestinyAmmoType
        get() = ammoType
    override var destinyDamageElement: DestinyDamageElement = DestinyDamageElement.KINETIC
        private set
    private var gravity = 0.0f
    private var friction = 0.01f
    private var lifeTicks = 40
    private var remainingPierce = 1
    private var maxRange = 128.0
    private var entityTolerance = 0.08
    private var showBulletImpact = true
    private var forgottenTraitsEnabled = true
    private var sourceWeaponId: ResourceLocation? = null
    private var distanceDamage: List<WeaponDistanceDamage> = emptyList()
    private var startPosition = Vec3.ZERO
    private val hitEntityIds = HashSet<Int>()

    constructor(type: EntityType<out ForgottenNameBulletEntity>, level: Level) : super(type, level)

    constructor(
        level: Level,
        owner: LivingEntity,
        profile: WeaponCombatProfile,
        direction: Vec3,
        forgottenTraitsEnabled: Boolean = true,
        sourceWeaponId: ResourceLocation? = null
    ) :
        super(DestinyEntities.FORGOTTEN_NAME_BULLET, level) {
        this.owner = owner
        this.forgottenTraitsEnabled = forgottenTraitsEnabled
        this.sourceWeaponId = sourceWeaponId
        damage = profile.baseDamage
        precisionMultiplier = profile.precisionMultiplier
        ammoType = profile.ammoType
        destinyDamageElement = profile.damageElement
        gravity = profile.ballistics.gravity.coerceAtLeast(0.0f)
        friction = profile.ballistics.friction.coerceIn(0.0f, 1.0f)
        lifeTicks = profile.ballistics.lifeTicks.coerceAtLeast(1)
        remainingPierce = profile.ballistics.pierce.coerceAtLeast(1)
        maxRange = profile.ballistics.range.coerceAtLeast(0.0)
        entityTolerance = profile.ballistics.entityTolerance.coerceAtLeast(0.0)
        showBulletImpact = profile.ballistics.showBulletImpact
        distanceDamage = profile.ballistics.distanceDamage.sortedBy(WeaponDistanceDamage::distance)
        setPos(owner.x, owner.eyeY - 0.12, owner.z)
        startPosition = position()
        entityData.set(DATA_GRAVITY, gravity)
        entityData.set(DATA_FRICTION, friction)
        val inheritedVelocity = owner.deltaMovement
        deltaMovement = direction.normalize().scale(profile.ballistics.speed.coerceAtLeast(0.01f).toDouble()).add(
            inheritedVelocity.x,
            if (owner.onGround()) 0.0 else inheritedVelocity.y,
            inheritedVelocity.z
        )
        updateRotation(deltaMovement)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_GRAVITY, 0.0f)
        builder.define(DATA_FRICTION, 0.01f)
    }

    override fun tick() {
        super.tick()
        val motion = deltaMovement
        if (motion.lengthSqr() <= 1.0E-8) {
            (owner as? ServerPlayer)?.let { AscWeaponRuntime.onMiss(it, sourceWeaponId) }
            discard()
            return
        }

        val from = position()
        val requestedEnd = clampedEnd(from, from.add(motion))
        if (!level().isClientSide && level() is ServerLevel) {
            processServerCollisions(level() as ServerLevel, from, requestedEnd)
            if (isRemoved) return
        }

        setPos(requestedEnd.x, requestedEnd.y, requestedEnd.z)
        updateRotation(motion)
        val activeFriction = if (level().isClientSide) entityData.get(DATA_FRICTION) else friction
        val activeGravity = if (level().isClientSide) entityData.get(DATA_GRAVITY) else gravity
        val drag = (1.0f - activeFriction).coerceIn(0.0f, 1.0f).toDouble()
        deltaMovement = motion.scale(drag).add(0.0, -activeGravity.toDouble(), 0.0)
        if (!level().isClientSide && (tickCount >= lifeTicks || startPosition.distanceToSqr(position()) >= maxRange * maxRange)) {
            if (hitEntityIds.isEmpty()) {
                (owner as? ServerPlayer)?.let { AscWeaponRuntime.onMiss(it, sourceWeaponId) }
            }
            discard()
        }
    }

    private fun clampedEnd(from: Vec3, requestedEnd: Vec3): Vec3 {
        if (startPosition == Vec3.ZERO || maxRange <= 0.0) return requestedEnd
        val travelled = startPosition.distanceTo(from)
        val remaining = (maxRange - travelled).coerceAtLeast(0.0)
        val step = requestedEnd.subtract(from)
        return if (step.length() <= remaining) requestedEnd else from.add(step.normalize().scale(remaining))
    }

    private fun processServerCollisions(level: ServerLevel, from: Vec3, requestedEnd: Vec3) {
        val blockHit = level.clip(
            ClipContext(from, requestedEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)
        )
        val collisionEnd = if (blockHit.type == HitResult.Type.MISS) requestedEnd else blockHit.location
        val impacts = level.getEntities(
            this,
            AABB(from, collisionEnd).inflate(1.0)
        ) { candidate ->
            candidate !== owner && candidate.id !in hitEntityIds && candidate.isPickable &&
                !candidate.isSpectator && candidate is LivingEntity && candidate.isAlive
        }.mapNotNull { candidate ->
            val living = candidate as LivingEntity
            val location = living.boundingBox.inflate(entityTolerance).clip(from, collisionEnd).orElse(null)
                ?: return@mapNotNull null
            EntityImpact(living, location, from.distanceToSqr(location))
        }.sortedBy(EntityImpact::distanceSquared)

        for (impact in impacts) {
            hitEntityIds += impact.target.id
            applyEntityImpact(level, impact.target, impact.location)
            remainingPierce--
            if (remainingPierce <= 0) {
                discard()
                return
            }
        }

        if (blockHit.type != HitResult.Type.MISS) {
            (owner as? ServerPlayer)?.let { player ->
                AscWeaponRuntime.onBlockImpact(level, player, sourceWeaponId, blockHit.location)
            }
            sendFeedback(level, collisionEnd, IMPACT_BLOCK, hit = false, precision = false, killed = false)
            discard()
        }
    }

    private fun applyEntityImpact(level: ServerLevel, target: LivingEntity, impact: Vec3) {
        val shooter = owner as? ServerPlayer
        val precisionHit = TaczEntityHitbox.isHeadshot(target, impact)
        val echoStacksBefore = if (forgottenTraitsEnabled) {
            shooter?.let(ForgottenNameExoticRuntime::stacks) ?: 0
        } else {
            0
        }
        val precisionBonus = if (forgottenTraitsEnabled && precisionHit) {
            ForgottenNameExoticRules.precisionBonusMultiplier(echoStacksBefore)
        } else {
            1.0f
        }
        val wasMarked = forgottenTraitsEnabled &&
            shooter?.let { ForgottenNameExoticRuntime.isMarked(it, target) } == true
        val markedBonus = if (forgottenTraitsEnabled) {
            shooter?.let { ForgottenNameExoticRuntime.markedDamageMultiplier(it, target) } ?: 1.0f
        } else {
            1.0f
        }
        val baseAtDistance = damageAtDistance(startPosition.distanceTo(impact))
        val sourceStack = shooter?.let { player ->
            listOf(player.mainHandItem, player.offhandItem).firstOrNull {
                it.item is GenericGunPackItem && GenericGunPackItem.id(it) == sourceWeaponId
            }
        }
        val ascDamageMultiplier = if (
            shooter != null && sourceStack != null && sourceWeaponId == AscWeaponRuntime.OUTBREAK
        ) {
            AscWeaponRuntime.outgoingDamageMultiplier(shooter, sourceStack, target)
        } else {
            1.0f
        }
        val finalDamage = baseAtDistance *
            (if (precisionHit) precisionMultiplier.coerceAtLeast(0.0f) else 1.0f) *
            precisionBonus * markedBonus * ascDamageMultiplier
        val wasAlive = target.isAlive
        target.invulnerableTime = 0
        val accepted = DamageNumberRuntime.withHit(shooter, target, finalDamage, precisionHit) {
            target.hurt(level.damageSources().thrown(this, owner), finalDamage)
        }
        if (!accepted) {
            sendFeedback(level, impact, IMPACT_ENTITY, hit = false, precision = precisionHit, killed = false)
            return
        }

        val killed = wasAlive && !target.isAlive
        if (shooter != null && sourceStack != null) {
            AscWeaponRuntime.onHit(level, shooter, sourceStack, target, impact, precisionHit, killed)
        }
        if (sourceWeaponId == MonteCarloExoticRuntime.ID) {
            shooter?.let { MonteCarloExoticRuntime.onWeaponHit(it, killed) }
        }
        val namelessTriggered = forgottenTraitsEnabled &&
            ForgottenNameExoticRules.shouldTriggerNameless(echoStacksBefore, precisionHit, killed)
        if (precisionHit) {
            level.sendParticles(ParticleTypes.CRIT, impact.x, impact.y, impact.z, 8, 0.12, 0.12, 0.12, 0.08)
            shooter?.let { player ->
                if (forgottenTraitsEnabled) {
                    if (namelessTriggered) {
                        ForgottenNameExoticRuntime.triggerNameless(player, target)
                    } else {
                        ForgottenNameExoticRuntime.onPrecisionHit(player, target)
                    }
                }
                ServerPlayNetworking.send(player, DestinyNetworking.PrecisionHitPayload(finalDamage))
            }
        }
        if (killed) {
            if (wasMarked && !namelessTriggered) {
                ForgottenNameExoticRuntime.onMarkedTargetKilled(shooter, target)
            }
            AmmoDropSystem.onWeaponKill(level, target, ammoType, shooter)
        }
        sendFeedback(level, impact, IMPACT_ENTITY, hit = true, precision = precisionHit, killed = killed)
    }

    private fun damageAtDistance(distance: Double): Float {
        if (distanceDamage.isEmpty()) return damage
        return distanceDamage.firstOrNull { distance < it.distance }?.damage ?: 0.0f
    }

    private fun sendFeedback(
        level: ServerLevel,
        impact: Vec3,
        impactKind: Int,
        hit: Boolean,
        precision: Boolean,
        killed: Boolean
    ) {
        val shooter = owner as? ServerPlayer ?: return
        val payload = DestinyNetworking.WeaponShotFeedbackPayload(
            shooter.uuid,
            impact.x, impact.y, impact.z,
            impact.x, impact.y, impact.z,
            impactKind, hit, precision, killed,
            false,
            0.0f, 0.0f, 0, 0, 1.0f,
            0.0,
            showBulletImpact
        )
        PlayerLookup.world(level).forEach { ServerPlayNetworking.send(it, payload) }
    }

    private fun updateRotation(motion: Vec3) {
        val horizontal = sqrt(motion.x * motion.x + motion.z * motion.z)
        yRot = (atan2(motion.x, motion.z) * Mth.RAD_TO_DEG).toFloat()
        xRot = (atan2(motion.y, horizontal) * Mth.RAD_TO_DEG).toFloat()
    }

    private data class EntityImpact(
        val target: LivingEntity,
        val location: Vec3,
        val distanceSquared: Double
    )

    companion object {
        private val DATA_GRAVITY: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(ForgottenNameBulletEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_FRICTION: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(ForgottenNameBulletEntity::class.java, EntityDataSerializers.FLOAT)
        const val IMPACT_MISS = 0
        const val IMPACT_BLOCK = 1
        const val IMPACT_ENTITY = 2
    }
}
