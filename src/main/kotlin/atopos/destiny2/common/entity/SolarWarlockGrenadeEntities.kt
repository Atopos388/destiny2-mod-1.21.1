package atopos.destiny2.common.entity

import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.aspect.SolarWarlockAspectRules
import atopos.destiny2.common.combat.DestinyExplosionRuntime
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.sqrt

/** Shared authoritative state for the three additional Solar grenade projectiles. */
abstract class SolarWarlockThrownGrenadeEntity(
    entityType: EntityType<out ThrowableItemProjectile>,
    level: Level
) : ThrowableItemProjectile(entityType, level), DestinyAbilityDamageCarrier {
    protected var touchOfFlame: Boolean = false
    override val destinyAbilitySlot: AbilitySlot = AbilitySlot.GRENADE

    protected fun initializeCast(owner: LivingEntity) {
        this.owner = owner
        touchOfFlame = DestinyAspectRuntime.hasTouchOfFlame(owner)
    }

    override fun tick() {
        super.tick()
        if (!level().isClientSide && tickCount >= SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_MAX_FLIGHT_TICKS) {
            discard()
        }
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.putBoolean(TOUCH_OF_FLAME_TAG, touchOfFlame)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        touchOfFlame = compound.getBoolean(TOUCH_OF_FLAME_TAG)
    }

    companion object {
        private const val TOUCH_OF_FLAME_TAG = "touch_of_flame"
    }
}

class HealingGrenadeEntity(
    entityType: EntityType<out HealingGrenadeEntity>,
    level: Level
) : SolarWarlockThrownGrenadeEntity(entityType, level) {
    constructor(level: Level, owner: LivingEntity) : this(DestinyEntities.HEALING_GRENADE, level) {
        initializeCast(owner)
    }

    override fun getDefaultItem(): Item = Items.GLISTERING_MELON_SLICE

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        val level = level() as? ServerLevel ?: return
        val currentOwner = owner as? LivingEntity ?: run {
            discard()
            return
        }
        val center = hitResult.location
        setPos(center)
        val profile = SolarWarlockAspectRules.healingGrenadeProfile(touchOfFlame)
        val bounds = centeredBounds(center, profile.impactRadius)
        level.getEntitiesOfClass(LivingEntity::class.java, bounds) { candidate ->
            candidate.isAlive && SolarGrenadeTargeting.isAlly(currentOwner, candidate) &&
                candidate.distanceToSqr(center) <= profile.impactRadius * profile.impactRadius
        }.forEach { ally ->
            DestinyStatusRules.applyCure(ally, profile.cureHealth, currentOwner as? ServerPlayer)
        }

        level.addFreshEntity(
            HealingGrenadeOrbEntity(level, center, currentOwner, touchOfFlame)
        )
        level.sendParticles(
            ParticleTypes.INSTANT_EFFECT,
            center.x,
            center.y + MINECRAFT_CALIBRATION_IMPACT_PARTICLE_Y_OFFSET,
            center.z,
            MINECRAFT_CALIBRATION_IMPACT_PARTICLE_COUNT,
            profile.impactRadius * MINECRAFT_CALIBRATION_IMPACT_PARTICLE_SPREAD_MULTIPLIER,
            MINECRAFT_CALIBRATION_IMPACT_PARTICLE_VERTICAL_SPREAD,
            profile.impactRadius * MINECRAFT_CALIBRATION_IMPACT_PARTICLE_SPREAD_MULTIPLIER,
            MINECRAFT_CALIBRATION_IMPACT_PARTICLE_SPEED
        )
        level.playSound(
            null,
            blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS,
            MINECRAFT_CALIBRATION_IMPACT_SOUND_VOLUME,
            MINECRAFT_CALIBRATION_IMPACT_SOUND_PITCH
        )
        discard()
    }

    companion object {
        const val MINECRAFT_CALIBRATION_IMPACT_PARTICLE_Y_OFFSET = 0.35
        const val MINECRAFT_CALIBRATION_IMPACT_PARTICLE_COUNT = 36
        const val MINECRAFT_CALIBRATION_IMPACT_PARTICLE_SPREAD_MULTIPLIER = 0.22
        const val MINECRAFT_CALIBRATION_IMPACT_PARTICLE_VERTICAL_SPREAD = 0.35
        const val MINECRAFT_CALIBRATION_IMPACT_PARTICLE_SPEED = 0.06
        const val MINECRAFT_CALIBRATION_IMPACT_SOUND_VOLUME = 0.75f
        const val MINECRAFT_CALIBRATION_IMPACT_SOUND_PITCH = 1.45f
    }
}

class HealingGrenadeOrbEntity(
    entityType: EntityType<out HealingGrenadeOrbEntity>,
    level: Level
) : Entity(entityType, level) {
    private var ownerId: UUID? = null
    private var touchOfFlame: Boolean = false
    private var remainingTicks: Int = SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEALING_ORB_LIFETIME_TICKS

    constructor(
        level: Level,
        position: Vec3,
        owner: LivingEntity,
        touchOfFlame: Boolean
    ) : this(DestinyEntities.HEALING_GRENADE_ORB, level) {
        setPos(position.x, position.y, position.z)
        ownerId = owner.uuid
        this.touchOfFlame = touchOfFlame
    }

    override fun tick() {
        super.tick()
        val level = level() as? ServerLevel ?: return
        if (remainingTicks-- <= 0) {
            discard()
            return
        }

        if (tickCount % MINECRAFT_CALIBRATION_ORB_PARTICLE_INTERVAL_TICKS == 0) {
            level.sendParticles(
                ParticleTypes.END_ROD,
                x,
                y + MINECRAFT_CALIBRATION_ORB_PARTICLE_Y_OFFSET,
                z,
                MINECRAFT_CALIBRATION_ORB_PARTICLE_COUNT,
                MINECRAFT_CALIBRATION_ORB_PARTICLE_SPREAD,
                MINECRAFT_CALIBRATION_ORB_PARTICLE_SPREAD,
                MINECRAFT_CALIBRATION_ORB_PARTICLE_SPREAD,
                MINECRAFT_CALIBRATION_ORB_PARTICLE_SPEED
            )
        }

        val currentOwner = ownerId?.let(level::getEntity) as? LivingEntity ?: run {
            discard()
            return
        }
        val profile = SolarWarlockAspectRules.healingGrenadeProfile(touchOfFlame)
        val pickupBounds = centeredBounds(position(), profile.orbPickupRadius)
        val recipient = level.getEntitiesOfClass(LivingEntity::class.java, pickupBounds) { candidate ->
            candidate.isAlive && SolarGrenadeTargeting.isAlly(currentOwner, candidate) &&
                candidate.distanceToSqr(this) <= profile.orbPickupRadius * profile.orbPickupRadius
        }.minByOrNull { it.distanceToSqr(this) } ?: return

        DestinyStatusRules.applyRestoration(
            recipient,
            profile.restorationDurationTicks,
            profile.restorationLevel,
            currentOwner as? ServerPlayer
        )
        level.playSound(
            null,
            recipient.blockPosition(),
            SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS,
            MINECRAFT_CALIBRATION_PICKUP_SOUND_VOLUME,
            MINECRAFT_CALIBRATION_PICKUP_SOUND_PITCH
        )
        discard()
    }

    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) = Unit

    override fun addAdditionalSaveData(compound: CompoundTag) {
        ownerId?.let { compound.putUUID(OWNER_TAG, it) }
        compound.putBoolean(TOUCH_OF_FLAME_TAG, touchOfFlame)
        compound.putInt(REMAINING_TICKS_TAG, remainingTicks)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        ownerId = if (compound.hasUUID(OWNER_TAG)) compound.getUUID(OWNER_TAG) else null
        touchOfFlame = compound.getBoolean(TOUCH_OF_FLAME_TAG)
        remainingTicks = compound.getInt(REMAINING_TICKS_TAG).takeIf { it > 0 }
            ?: SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEALING_ORB_LIFETIME_TICKS
    }

    companion object {
        private const val OWNER_TAG = "owner"
        private const val TOUCH_OF_FLAME_TAG = "touch_of_flame"
        private const val REMAINING_TICKS_TAG = "remaining_ticks"
        const val MINECRAFT_CALIBRATION_ORB_PARTICLE_INTERVAL_TICKS = 5
        const val MINECRAFT_CALIBRATION_ORB_PARTICLE_Y_OFFSET = 0.35
        const val MINECRAFT_CALIBRATION_ORB_PARTICLE_COUNT = 3
        const val MINECRAFT_CALIBRATION_ORB_PARTICLE_SPREAD = 0.12
        const val MINECRAFT_CALIBRATION_ORB_PARTICLE_SPEED = 0.015
        const val MINECRAFT_CALIBRATION_PICKUP_SOUND_VOLUME = 0.6f
        const val MINECRAFT_CALIBRATION_PICKUP_SOUND_PITCH = 1.5f
    }
}

class FireboltGrenadeEntity(
    entityType: EntityType<out FireboltGrenadeEntity>,
    level: Level
) : SolarWarlockThrownGrenadeEntity(entityType, level) {
    constructor(level: Level, owner: LivingEntity) : this(DestinyEntities.FIREBOLT_GRENADE, level) {
        initializeCast(owner)
    }

    override fun getDefaultItem(): Item = Items.BLAZE_POWDER

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        val level = level() as? ServerLevel ?: return
        val currentOwner = owner as? LivingEntity ?: run {
            discard()
            return
        }
        val center = hitResult.location
        setPos(center)
        val profile = SolarWarlockAspectRules.fireboltGrenadeProfile(touchOfFlame)
        val bounds = centeredBounds(center, profile.targetSearchRadius)
        val targets = level.getEntitiesOfClass(LivingEntity::class.java, bounds) { candidate ->
            candidate.isAlive && SolarGrenadeTargeting.isEnemy(currentOwner, candidate) &&
                candidate.distanceToSqr(center) <= profile.targetSearchRadius * profile.targetSearchRadius &&
                hasClearPath(level, center, candidate.eyePosition)
        }.sortedWith(compareBy<LivingEntity> { it.distanceToSqr(center) }.thenBy { it.uuid.toString() })
            .take(profile.maximumTargets)

        val damageSource = level.damageSources().indirectMagic(this, currentOwner)
        targets.forEach { target ->
            val damaged = DestinyExplosionRuntime.hurtWithoutKnockback(target, damageSource, profile.damage)
            if (damaged) {
                DestinyStatusRules.applyScorch(
                    target,
                    profile.scorchStacks,
                    profile.scorchDurationTicks,
                    currentOwner as? ServerPlayer,
                    SolarDamageKind.GRENADE,
                    uuid
                )
            }
            sendBoltTrail(level, center, target.eyePosition)
        }

        level.playSound(
            null,
            blockPosition(),
            SoundEvents.FIRECHARGE_USE,
            SoundSource.PLAYERS,
            MINECRAFT_CALIBRATION_FIREBOLT_SOUND_VOLUME,
            MINECRAFT_CALIBRATION_FIREBOLT_SOUND_PITCH
        )
        discard()
    }

    private fun hasClearPath(level: ServerLevel, from: Vec3, to: Vec3): Boolean =
        level.clip(
            ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)
        ).type == HitResult.Type.MISS

    private fun sendBoltTrail(level: ServerLevel, from: Vec3, to: Vec3) {
        repeat(MINECRAFT_CALIBRATION_FIREBOLT_TRAIL_STEPS) { index ->
            val progress = (index + 1.0) / MINECRAFT_CALIBRATION_FIREBOLT_TRAIL_STEPS
            val point = from.lerp(to, progress)
            level.sendParticles(
                ParticleTypes.FLAME,
                point.x,
                point.y,
                point.z,
                MINECRAFT_CALIBRATION_FIREBOLT_PARTICLES_PER_STEP,
                MINECRAFT_CALIBRATION_FIREBOLT_PARTICLE_SPREAD,
                MINECRAFT_CALIBRATION_FIREBOLT_PARTICLE_SPREAD,
                MINECRAFT_CALIBRATION_FIREBOLT_PARTICLE_SPREAD,
                MINECRAFT_CALIBRATION_FIREBOLT_PARTICLE_SPEED
            )
        }
    }

    companion object {
        const val MINECRAFT_CALIBRATION_FIREBOLT_TRAIL_STEPS = 10
        const val MINECRAFT_CALIBRATION_FIREBOLT_PARTICLES_PER_STEP = 1
        const val MINECRAFT_CALIBRATION_FIREBOLT_PARTICLE_SPREAD = 0.025
        const val MINECRAFT_CALIBRATION_FIREBOLT_PARTICLE_SPEED = 0.01
        const val MINECRAFT_CALIBRATION_FIREBOLT_SOUND_VOLUME = 0.7f
        const val MINECRAFT_CALIBRATION_FIREBOLT_SOUND_PITCH = 1.25f
    }
}

class FusionGrenadeEntity(
    entityType: EntityType<out FusionGrenadeEntity>,
    level: Level
) : SolarWarlockThrownGrenadeEntity(entityType, level) {
    private var stuck = false
    private var attachedTargetId: UUID? = null
    private var fuseTicksRemaining = SolarWarlockAspectRules.MINECRAFT_CALIBRATION_FUSION_GRENADE_FUSE_TICKS
    private var explosionsRemaining = 1

    constructor(level: Level, owner: LivingEntity) : this(DestinyEntities.FUSION_GRENADE, level) {
        initializeCast(owner)
    }

    override fun getDefaultItem(): Item = Items.FIRE_CHARGE

    override fun tick() {
        super.tick()
        val level = level() as? ServerLevel ?: return
        if (!stuck || isRemoved) return

        val attached = attachedTargetId?.let(level::getEntity) as? LivingEntity
        if (attached?.isAlive == true) {
            setPos(attached.x, attached.y + attached.bbHeight * MINECRAFT_CALIBRATION_ATTACHED_HEIGHT_FRACTION, attached.z)
        } else {
            attachedTargetId = null
        }

        fuseTicksRemaining--
        if (fuseTicksRemaining <= 0) detonate(level)
    }

    override fun onHitEntity(hitResult: EntityHitResult) {
        super.onHitEntity(hitResult)
        if (level().isClientSide || stuck) return
        val currentOwner = owner as? LivingEntity ?: return
        val target = hitResult.entity as? LivingEntity ?: return
        if (!SolarGrenadeTargeting.isEnemy(currentOwner, target)) return
        attachedTargetId = target.uuid
        stickAt(hitResult.location)
    }

    override fun onHitBlock(hitResult: BlockHitResult) {
        super.onHitBlock(hitResult)
        if (level().isClientSide || stuck) return
        stickAt(hitResult.location)
    }

    private fun stickAt(impact: Vec3) {
        val profile = SolarWarlockAspectRules.fusionGrenadeProfile(touchOfFlame)
        setPos(impact)
        deltaMovement = Vec3.ZERO
        isNoGravity = true
        noPhysics = true
        stuck = true
        fuseTicksRemaining = profile.fuseTicks
        explosionsRemaining = profile.explosionCount
    }

    private fun detonate(level: ServerLevel) {
        val currentOwner = owner as? LivingEntity ?: run {
            discard()
            return
        }
        val profile = SolarWarlockAspectRules.fusionGrenadeProfile(touchOfFlame)
        val center = position()
        val bounds = centeredBounds(center, profile.explosionRadius)
        val damageSource = level.damageSources().indirectMagic(this, currentOwner)
        level.getEntitiesOfClass(LivingEntity::class.java, bounds) { target ->
            SolarGrenadeTargeting.isEnemy(currentOwner, target)
        }.forEach { target ->
            val distance = sqrt(target.distanceToSqr(center))
            if (distance <= profile.explosionRadius + target.bbWidth * MINECRAFT_CALIBRATION_TARGET_WIDTH_ALLOWANCE) {
                val falloff = (
                    1.0 - distance / profile.explosionRadius * MINECRAFT_CALIBRATION_FUSION_DAMAGE_FALLOFF_FACTOR
                    ).coerceIn(MINECRAFT_CALIBRATION_FUSION_MIN_DAMAGE_FALLOFF, 1.0).toFloat()
                val damaged = DestinyExplosionRuntime.hurtWithoutKnockback(
                    target,
                    damageSource,
                    profile.explosionDamage * falloff
                )
                if (damaged) {
                    DestinyStatusRules.applyScorch(
                        target,
                        (profile.scorchStacks * falloff).toInt().coerceAtLeast(1),
                        profile.scorchDurationTicks,
                        currentOwner as? ServerPlayer,
                        SolarDamageKind.GRENADE,
                        uuid
                    )
                }
            }
        }
        level.playSound(
            null,
            blockPosition(),
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS,
            MINECRAFT_CALIBRATION_FUSION_SOUND_VOLUME,
            if (explosionsRemaining > 1) {
                MINECRAFT_CALIBRATION_FUSION_FIRST_EXPLOSION_PITCH
            } else {
                MINECRAFT_CALIBRATION_FUSION_FINAL_EXPLOSION_PITCH
            }
        )
        level.sendParticles(
            ParticleTypes.FLAME,
            x,
            y,
            z,
            MINECRAFT_CALIBRATION_FUSION_PARTICLE_COUNT,
            profile.explosionRadius * MINECRAFT_CALIBRATION_FUSION_PARTICLE_SPREAD_MULTIPLIER,
            MINECRAFT_CALIBRATION_FUSION_PARTICLE_VERTICAL_SPREAD,
            profile.explosionRadius * MINECRAFT_CALIBRATION_FUSION_PARTICLE_SPREAD_MULTIPLIER,
            MINECRAFT_CALIBRATION_FUSION_PARTICLE_SPEED
        )

        explosionsRemaining--
        if (explosionsRemaining <= 0) {
            discard()
        } else {
            fuseTicksRemaining = profile.repeatExplosionDelayTicks
        }
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.putBoolean(STUCK_TAG, stuck)
        attachedTargetId?.let { compound.putUUID(ATTACHED_TARGET_TAG, it) }
        compound.putInt(FUSE_TICKS_TAG, fuseTicksRemaining)
        compound.putInt(EXPLOSIONS_REMAINING_TAG, explosionsRemaining)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        stuck = compound.getBoolean(STUCK_TAG)
        attachedTargetId = if (compound.hasUUID(ATTACHED_TARGET_TAG)) {
            compound.getUUID(ATTACHED_TARGET_TAG)
        } else {
            null
        }
        fuseTicksRemaining = compound.getInt(FUSE_TICKS_TAG).takeIf { it > 0 }
            ?: SolarWarlockAspectRules.MINECRAFT_CALIBRATION_FUSION_GRENADE_FUSE_TICKS
        explosionsRemaining = compound.getInt(EXPLOSIONS_REMAINING_TAG).coerceAtLeast(1)
        if (stuck) {
            isNoGravity = true
            noPhysics = true
            deltaMovement = Vec3.ZERO
        }
    }

    companion object {
        private const val STUCK_TAG = "stuck"
        private const val ATTACHED_TARGET_TAG = "attached_target"
        private const val FUSE_TICKS_TAG = "fuse_ticks"
        private const val EXPLOSIONS_REMAINING_TAG = "explosions_remaining"
        const val MINECRAFT_CALIBRATION_ATTACHED_HEIGHT_FRACTION = 0.5
        const val MINECRAFT_CALIBRATION_FUSION_SOUND_VOLUME = 0.8f
        const val MINECRAFT_CALIBRATION_FUSION_FIRST_EXPLOSION_PITCH = 1.1f
        const val MINECRAFT_CALIBRATION_FUSION_FINAL_EXPLOSION_PITCH = 0.9f
        const val MINECRAFT_CALIBRATION_FUSION_PARTICLE_COUNT = 32
        const val MINECRAFT_CALIBRATION_FUSION_PARTICLE_SPREAD_MULTIPLIER = 0.2
        const val MINECRAFT_CALIBRATION_FUSION_PARTICLE_VERTICAL_SPREAD = 0.35
        const val MINECRAFT_CALIBRATION_FUSION_PARTICLE_SPEED = 0.05
        const val MINECRAFT_CALIBRATION_FUSION_DAMAGE_FALLOFF_FACTOR = 0.35
        const val MINECRAFT_CALIBRATION_FUSION_MIN_DAMAGE_FALLOFF = 0.65
        const val MINECRAFT_CALIBRATION_TARGET_WIDTH_ALLOWANCE = 0.5
    }
}

private object SolarGrenadeTargeting {
    fun isAlly(owner: LivingEntity, target: LivingEntity): Boolean =
        target === owner || owner.isAlliedTo(target)

    fun isEnemy(owner: LivingEntity, target: LivingEntity): Boolean =
        target !== owner && target.isAlive && !owner.isAlliedTo(target) &&
            (target !is Player || !target.isSpectator)
}

private fun centeredBounds(center: Vec3, radius: Double): AABB = AABB(
    center.x - radius,
    center.y - radius,
    center.z - radius,
    center.x + radius,
    center.y + radius,
    center.z + radius
)
