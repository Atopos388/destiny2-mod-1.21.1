package atopos.destiny2.common.entity

import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.aspect.ArcTitanFragmentRuntime
import atopos.destiny2.common.aspect.ArcBoltChargeRuntime
import atopos.destiny2.common.aspect.ArcTitanAspectRules
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.effect.DestinyStatusRules
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

interface ArcGrenadeDamageEntity : DestinyAbilityDamageCarrier

class ArcPulseGrenadeEntity : ThrowableItemProjectile, ArcGrenadeDamageEntity {
    override val destinyAbilitySlot: AbilitySlot = AbilitySlot.GRENADE
    private var pulseAge = 0
    private var pulsesReleased = 0
    private var pulseLimit = TOTAL_PULSES
    private var touchOfThunder = false
    private var acceptedDamageEvents = 0

    constructor(entityType: EntityType<out ArcPulseGrenadeEntity>, level: Level) : super(entityType, level)

    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.ARC_PULSE_GRENADE, level) {
        this.owner = owner
        touchOfThunder = (owner as? net.minecraft.server.level.ServerPlayer)?.let {
            DestinyAspectRuntime.hasAspect(it, ArcTitanAspectRules.TOUCH_OF_THUNDER)
        } == true
        setPos(owner.x, owner.eyeY - 0.18, owner.z)
    }

    override fun getDefaultItem(): Item = Items.PRISMARINE_CRYSTALS

    fun isAnchored(): Boolean = entityData.get(DATA_ANCHORED)

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_ANCHORED, false)
    }

    override fun tick() {
        if (!isAnchored()) {
            super.tick()
            if (!level().isClientSide && tickCount % 2 == 0) {
                (level() as? ServerLevel)?.sendParticles(
                    ParticleTypes.ELECTRIC_SPARK,
                    x,
                    y,
                    z,
                    2,
                    0.08,
                    0.08,
                    0.08,
                    0.01
                )
            }
            if (!level().isClientSide && tickCount > 10 * 20) discard()
            return
        }

        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
        super.tick()
        if (level().isClientSide) return

        pulseAge++
        if (pulseAge == 1 || pulseAge % PULSE_INTERVAL_TICKS == 0) {
            pulse()
            pulsesReleased++
            if (pulsesReleased >= pulseLimit) {
                discard()
            }
        }
    }

    override fun onHit(hitResult: HitResult) {
        if (isAnchored()) return
        super.onHit(hitResult)
        if (level().isClientSide) return
        setPos(hitResult.location)
        deltaMovement = Vec3.ZERO
        noPhysics = true
        isNoGravity = true
        entityData.set(DATA_ANCHORED, true)
        pulseAge = 0
        pulseLimit = ArcTitanFragmentRuntime.pulseGrenadePulses(owner as? LivingEntity)
        (level() as? ServerLevel)?.playSound(
            null,
            blockPosition(),
            SoundEvents.TRIDENT_HIT_GROUND,
            SoundSource.PLAYERS,
            0.65f,
            1.45f
        )
    }

    private fun pulse() {
        val level = level() as? ServerLevel ?: return
        val currentOwner = owner as? LivingEntity
        val source = currentOwner?.let { damageSources().indirectMagic(this, it) } ?: damageSources().magic()
        level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(position(), PULSE_RADIUS * 2.0, 4.0, PULSE_RADIUS * 2.0)
        ) { target ->
            target.isAlive &&
                target !== currentOwner &&
                (currentOwner == null || target !is Player || currentOwner !is Player || !currentOwner.isAlliedTo(target)) &&
                target.position().distanceToSqr(position()) <= PULSE_RADIUS * PULSE_RADIUS
        }.forEach { target ->
            val damage = PULSE_DAMAGE * ArcTitanAspectRules.touchPulseDamageMultiplier(
                touchOfThunder,
                ArcTitanAspectRules.ArcGrenadeKind.PULSE,
                acceptedDamageEvents + 1
            )
            if (target.hurt(source, damage)) {
                acceptedDamageEvents++
                val playerOwner = currentOwner as? net.minecraft.server.level.ServerPlayer
                if (playerOwner != null && ArcTitanAspectRules.shouldCreateTouchPulseIonicTrace(
                        touchOfThunder,
                        ArcTitanAspectRules.ArcGrenadeKind.PULSE,
                        true,
                        acceptedDamageEvents
                    )
                ) {
                    ArcBoltChargeRuntime.spawnIonicTrace(playerOwner, target.x, target.y + 0.3, target.z)
                }
            }
        }

        level.sendParticles(ParticleTypes.FLASH, x, y + 0.12, z, 1, 0.0, 0.0, 0.0, 0.0)
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.18, z, 46, 1.65, 0.42, 1.65, 0.13)
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.12, z, 12, 0.85, 0.18, 0.85, 0.05)
        level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.48f, 1.55f)
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.putBoolean("ArcPulseAnchored", isAnchored())
        compound.putInt("ArcPulseAge", pulseAge)
        compound.putInt("ArcPulsesReleased", pulsesReleased)
        compound.putInt("ArcPulseLimit", pulseLimit)
        compound.putBoolean("TouchOfThunder", touchOfThunder)
        compound.putInt("AcceptedDamageEvents", acceptedDamageEvents)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        entityData.set(DATA_ANCHORED, compound.getBoolean("ArcPulseAnchored"))
        pulseAge = compound.getInt("ArcPulseAge").coerceAtLeast(0)
        pulseLimit = if (compound.contains("ArcPulseLimit")) {
            compound.getInt("ArcPulseLimit").coerceIn(TOTAL_PULSES, MAX_TOTAL_PULSES)
        } else {
            TOTAL_PULSES
        }
        pulsesReleased = compound.getInt("ArcPulsesReleased").coerceIn(0, pulseLimit)
        touchOfThunder = compound.getBoolean("TouchOfThunder")
        acceptedDamageEvents = compound.getInt("AcceptedDamageEvents").coerceAtLeast(0)
    }

    companion object {
        const val PULSE_INTERVAL_TICKS = 12
        const val TOTAL_PULSES = 7
        const val MAX_TOTAL_PULSES = 10
        const val PULSE_RADIUS = 4.0
        const val PULSE_DAMAGE = 3.5f

        private val DATA_ANCHORED: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(ArcPulseGrenadeEntity::class.java, EntityDataSerializers.BOOLEAN)
    }
}

class ArcFlashbangGrenadeEntity : ThrowableItemProjectile, ArcGrenadeDamageEntity {
    override val destinyAbilitySlot: AbilitySlot = AbilitySlot.GRENADE
    private var touchOfThunder = false
    private var bounceIndex = 0
    private var detonated = false

    constructor(type: EntityType<out ArcFlashbangGrenadeEntity>, level: Level) : super(type, level)

    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.ARC_FLASHBANG_GRENADE, level) {
        this.owner = owner
        touchOfThunder = (owner as? net.minecraft.server.level.ServerPlayer)?.let {
            DestinyAspectRuntime.hasAspect(it, ArcTitanAspectRules.TOUCH_OF_THUNDER)
        } == true
        setPos(owner.x, owner.eyeY - 0.18, owner.z)
    }

    override fun getDefaultItem(): Item = Items.GLOWSTONE_DUST

    override fun tick() {
        super.tick()
        if (!level().isClientSide && tickCount % 2 == 0) {
            (level() as ServerLevel).sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.07, 0.07, 0.07, 0.01)
        }
        if (!level().isClientSide && tickCount >= FUSE_TICKS) detonate()
    }

    override fun onHit(hit: HitResult) {
        if (level().isClientSide || detonated) return
        bounceIndex++
        if (ArcTitanAspectRules.shouldEmitTouchFlashbangBlind(
                touchOfThunder,
                ArcTitanAspectRules.ArcGrenadeKind.FLASHBANG,
                bounceIndex
            )
        ) emitBlindBurst(false)
        if (hit.type == HitResult.Type.ENTITY || bounceIndex >= 2) {
            detonate()
            return
        }
        val blockHit = hit as? BlockHitResult ?: return
        val velocity = deltaMovement
        deltaMovement = when (blockHit.direction.axis) {
            net.minecraft.core.Direction.Axis.X -> Vec3(-velocity.x * 0.58, velocity.y * 0.58 + 0.08, velocity.z * 0.58)
            net.minecraft.core.Direction.Axis.Y -> Vec3(velocity.x * 0.58, -velocity.y * 0.50 + 0.10, velocity.z * 0.58)
            net.minecraft.core.Direction.Axis.Z -> Vec3(velocity.x * 0.58, velocity.y * 0.58 + 0.08, -velocity.z * 0.58)
        }
        setPos(hit.location.add(blockHit.direction.stepX * 0.04, blockHit.direction.stepY * 0.04, blockHit.direction.stepZ * 0.04))
        hasImpulse = true
    }

    private fun detonate() {
        if (detonated) return
        detonated = true
        val level = level() as? ServerLevel ?: return
        val currentOwner = owner as? LivingEntity
        val source = currentOwner?.let { damageSources().indirectMagic(this, it) } ?: damageSources().magic()
        level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(position(), RADIUS * 2.0, RADIUS * 2.0, RADIUS * 2.0)) {
            isEnemy(currentOwner, it) && it.position().distanceToSqr(position()) <= RADIUS * RADIUS
        }.forEach { target ->
            target.hurt(source, DAMAGE)
            DestinyStatusRules.applyArcBlind(target, ArcTitanAspectRules.MINECRAFT_CALIBRATION_TOUCH_FLASHBANG_BLIND_DURATION_TICKS)
        }
        emitBlindBurst(true)
        discard()
    }

    private fun emitBlindBurst(loud: Boolean) {
        val level = level() as? ServerLevel ?: return
        val currentOwner = owner as? LivingEntity
        if (!loud) {
            level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(position(), RADIUS * 2.0, RADIUS * 2.0, RADIUS * 2.0)) {
                isEnemy(currentOwner, it)
            }.forEach { DestinyStatusRules.applyArcBlind(it, ArcTitanAspectRules.MINECRAFT_CALIBRATION_TOUCH_FLASHBANG_BLIND_DURATION_TICKS) }
        }
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0, 0.0, 0.0, 0.0)
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, if (loud) 46 else 26, 1.35, 0.75, 1.35, 0.15)
        level.playSound(null, blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, if (loud) 0.8f else 0.5f, 1.65f)
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putBoolean("TouchOfThunder", touchOfThunder)
        tag.putInt("BounceIndex", bounceIndex)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        touchOfThunder = tag.getBoolean("TouchOfThunder")
        bounceIndex = tag.getInt("BounceIndex").coerceAtLeast(0)
    }

    companion object {
        private const val FUSE_TICKS = 30
        private const val RADIUS = 5.0
        private const val DAMAGE = 4.0f
    }
}

class ArcLightningGrenadeEntity : ThrowableItemProjectile, ArcGrenadeDamageEntity {
    override val destinyAbilitySlot: AbilitySlot = AbilitySlot.GRENADE
    private var anchored = false
    private var touchOfThunder = false
    private var pulseTicks = 0
    private var pulses = 0
    private var acceptedDamageEvents = 0

    constructor(type: EntityType<out ArcLightningGrenadeEntity>, level: Level) : super(type, level)

    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.ARC_LIGHTNING_GRENADE, level) {
        this.owner = owner
        touchOfThunder = (owner as? net.minecraft.server.level.ServerPlayer)?.let {
            DestinyAspectRuntime.hasAspect(it, ArcTitanAspectRules.TOUCH_OF_THUNDER)
        } == true
        setPos(owner.x, owner.eyeY - 0.18, owner.z)
    }

    override fun getDefaultItem(): Item = Items.PRISMARINE_SHARD

    override fun tick() {
        if (!anchored) {
            super.tick()
            if (!level().isClientSide && tickCount > 200) discard()
            return
        }
        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
        super.tick()
        if (level().isClientSide) return
        pulseTicks++
        if (pulseTicks == 1 || pulseTicks % PULSE_INTERVAL == 0) {
            pulse()
            pulses++
            if (pulses >= PULSE_COUNT) discard()
        }
    }

    override fun onHit(hit: HitResult) {
        if (anchored || level().isClientSide) return
        setPos(hit.location)
        anchored = true
        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
        (level() as ServerLevel).playSound(null, blockPosition(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 0.7f, 1.7f)
    }

    private fun pulse() {
        val level = level() as? ServerLevel ?: return
        val currentOwner = owner as? LivingEntity
        val source = currentOwner?.let { damageSources().indirectMagic(this, it) } ?: damageSources().magic()
        val ownerPlayer = currentOwner as? net.minecraft.server.level.ServerPlayer
        level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(position(), RADIUS * 2.0, 4.0, RADIUS * 2.0)) {
            isEnemy(currentOwner, it) && it.position().distanceToSqr(position()) <= RADIUS * RADIUS
        }.forEach { target ->
            if (target.hurt(source, DAMAGE)) {
                acceptedDamageEvents++
                if (ownerPlayer != null && ArcTitanAspectRules.shouldApplyTouchLightningJolt(
                        touchOfThunder,
                        ArcTitanAspectRules.ArcGrenadeKind.LIGHTNING,
                        true,
                        acceptedDamageEvents
                    )
                ) ArcTitanFragmentRuntime.applyJolt(ownerPlayer, target)
            }
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 54, 1.55, 0.65, 1.55, 0.18)
        level.playSound(null, blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.55f, 1.65f)
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putBoolean("Anchored", anchored)
        tag.putBoolean("TouchOfThunder", touchOfThunder)
        tag.putInt("PulseTicks", pulseTicks)
        tag.putInt("Pulses", pulses)
        tag.putInt("AcceptedDamageEvents", acceptedDamageEvents)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        anchored = tag.getBoolean("Anchored")
        touchOfThunder = tag.getBoolean("TouchOfThunder")
        pulseTicks = tag.getInt("PulseTicks").coerceAtLeast(0)
        pulses = tag.getInt("Pulses").coerceAtLeast(0)
        acceptedDamageEvents = tag.getInt("AcceptedDamageEvents").coerceAtLeast(0)
    }

    companion object {
        private const val PULSE_INTERVAL = 15
        private const val PULSE_COUNT = 4
        private const val RADIUS = 4.5
        private const val DAMAGE = 4.0f
    }
}

class ArcStormGrenadeEntity : ThrowableItemProjectile, ArcGrenadeDamageEntity {
    override val destinyAbilitySlot: AbilitySlot = AbilitySlot.GRENADE
    private var cloud = false
    private var touchOfThunder = false
    private var cloudTicks = 0

    constructor(type: EntityType<out ArcStormGrenadeEntity>, level: Level) : super(type, level)

    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.ARC_STORM_GRENADE, level) {
        this.owner = owner
        touchOfThunder = (owner as? net.minecraft.server.level.ServerPlayer)?.let {
            DestinyAspectRuntime.hasAspect(it, ArcTitanAspectRules.TOUCH_OF_THUNDER)
        } == true
        setPos(owner.x, owner.eyeY - 0.18, owner.z)
    }

    override fun getDefaultItem(): Item = Items.HEART_OF_THE_SEA

    override fun tick() {
        if (!cloud) {
            super.tick()
            if (!level().isClientSide && tickCount > 200) discard()
            return
        }
        noPhysics = true
        isNoGravity = true
        super.tick()
        if (level().isClientSide) return
        cloudTicks++
        if (touchOfThunder) steerCloud()
        if (cloudTicks == 1 || cloudTicks % ArcTitanAspectRules.MINECRAFT_CALIBRATION_TOUCH_STORM_BOLT_INTERVAL_TICKS == 0) strike()
        val lifetime = if (touchOfThunder) ArcTitanAspectRules.MINECRAFT_CALIBRATION_TOUCH_STORM_CLOUD_DURATION_TICKS else BASE_DURATION_TICKS
        if (cloudTicks >= lifetime) discard()
    }

    override fun onHit(hit: HitResult) {
        if (cloud || level().isClientSide) return
        setPos(hit.location.add(0.0, 2.2, 0.0))
        cloud = true
        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
    }

    private fun steerCloud() {
        val level = level() as ServerLevel
        val currentOwner = owner as? LivingEntity
        val radius = ArcTitanAspectRules.MINECRAFT_CALIBRATION_TOUCH_STORM_TRACKING_RADIUS
        val target = level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(position(), radius * 2.0, radius * 2.0, radius * 2.0)) {
            isEnemy(currentOwner, it)
        }.minByOrNull { it.distanceToSqr(this) } ?: run {
            deltaMovement = deltaMovement.scale(0.75)
            return
        }
        val desired = target.position().add(0.0, target.bbHeight + 1.0, 0.0).subtract(position())
        deltaMovement = deltaMovement.scale(0.72).add(desired.normalize().scale(0.07)).let {
            if (it.length() > 0.34) it.normalize().scale(0.34) else it
        }
        hasImpulse = true
    }

    private fun strike() {
        val level = level() as? ServerLevel ?: return
        val currentOwner = owner as? LivingEntity
        val target = level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(position(), RADIUS * 2.0, 6.0, RADIUS * 2.0)) {
            isEnemy(currentOwner, it)
        }.minByOrNull { it.distanceToSqr(this) } ?: return
        val source = currentOwner?.let { damageSources().indirectMagic(this, it) } ?: damageSources().magic()
        target.hurt(source, DAMAGE)
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + target.bbHeight * 0.5, target.z, 46, 0.65, 1.1, 0.65, 0.22)
        level.sendParticles(ParticleTypes.FLASH, target.x, target.y + target.bbHeight, target.z, 1, 0.0, 0.0, 0.0, 0.0)
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.75f, 1.35f)
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putBoolean("Cloud", cloud)
        tag.putBoolean("TouchOfThunder", touchOfThunder)
        tag.putInt("CloudTicks", cloudTicks)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        cloud = tag.getBoolean("Cloud")
        touchOfThunder = tag.getBoolean("TouchOfThunder")
        cloudTicks = tag.getInt("CloudTicks").coerceAtLeast(0)
    }

    companion object {
        private const val BASE_DURATION_TICKS = 4 * 20
        private const val RADIUS = 5.5
        private const val DAMAGE = 6.0f
    }
}

private fun isEnemy(owner: LivingEntity?, target: LivingEntity): Boolean {
    if (!target.isAlive || target === owner) return false
    return target !is Player || owner !is Player || !owner.isAlliedTo(target)
}

/**
 * Short-lived invisible direct entity used so the shared damage pipeline can
 * classify non-projectile Arc hits by ability slot.
 */
class ArcAbilityDamageEntity(
    entityType: EntityType<out ArcAbilityDamageEntity>,
    level: Level
) : Entity(entityType, level), DestinyAbilityDamageCarrier {
    private var ownerUuid: UUID? = null
    private var abilitySlot = AbilitySlot.MELEE

    constructor(level: Level, position: Vec3, owner: LivingEntity, slot: AbilitySlot) :
        this(DestinyEntities.ARC_ABILITY_DAMAGE, level) {
        setPos(position)
        ownerUuid = owner.uuid
        abilitySlot = slot
    }

    override val destinyAbilitySlot: AbilitySlot
        get() = abilitySlot

    override fun tick() {
        super.tick()
        if (!level().isClientSide && tickCount > 2) discard()
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

    override fun addAdditionalSaveData(compound: CompoundTag) {
        ownerUuid?.let { compound.putUUID("ArcAbilityOwner", it) }
        compound.putString("ArcAbilitySlot", abilitySlot.key)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        ownerUuid = if (compound.hasUUID("ArcAbilityOwner")) compound.getUUID("ArcAbilityOwner") else null
        abilitySlot = AbilitySlot.entries.firstOrNull { it.key == compound.getString("ArcAbilitySlot") }
            ?: AbilitySlot.MELEE
    }
}
