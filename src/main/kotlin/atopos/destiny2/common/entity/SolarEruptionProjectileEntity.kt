package atopos.destiny2.common.entity

import atopos.destiny2.common.combat.DestinyExplosionRuntime
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
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
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

class SolarEruptionProjectileEntity : ThrowableItemProjectile, DestinyAbilityDamageCarrier {
    private var touchOfFlame = false
    private var afterglowTicks = AFTERGLOW_TICKS
    override val destinyAbilitySlot: AbilitySlot = AbilitySlot.GRENADE

    constructor(
        entityType: EntityType<out SolarEruptionProjectileEntity>,
        level: Level
    ) : super(entityType, level)

    constructor(
        level: Level,
        owner: LivingEntity?,
        touchOfFlame: Boolean
    ) : super(DestinyEntities.SOLAR_ERUPTION_PROJECTILE, level) {
        this.owner = owner
        this.touchOfFlame = touchOfFlame
    }

    override fun getDefaultItem(): Item = Items.FIRE_CHARGE

    override fun getDefaultGravity(): Double = 0.055

    fun isImpacted(): Boolean = entityData.get(DATA_IMPACTED)

    fun getAfterglowTicks(): Int = entityData.get(DATA_AFTERGLOW_TICKS)

    /**
     * The client projectile can continue simulating briefly before the server's
     * impacted flag arrives. Keep the authoritative hit point in synced data so
     * the afterglow is never rendered at that predicted (often underground)
     * client position.
     */
    fun getImpactPosition(): Vec3 =
        if (isImpacted()) {
            Vec3(
                entityData.get(DATA_IMPACT_X).toDouble(),
                entityData.get(DATA_IMPACT_Y).toDouble(),
                entityData.get(DATA_IMPACT_Z).toDouble()
            )
        } else {
            position()
        }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_IMPACTED, false)
        builder.define(DATA_AFTERGLOW_TICKS, 0)
        builder.define(DATA_IMPACT_X, 0f)
        builder.define(DATA_IMPACT_Y, 0f)
        builder.define(DATA_IMPACT_Z, 0f)
    }

    override fun tick() {
        if (isImpacted()) {
            noPhysics = true
            isNoGravity = true
            deltaMovement = Vec3.ZERO
            super.tick()
            if (!level().isClientSide) {
                afterglowTicks = (afterglowTicks - 1).coerceAtLeast(0)
                entityData.set(DATA_AFTERGLOW_TICKS, afterglowTicks)
                if (afterglowTicks <= 0) {
                    discard()
                }
            }
            return
        }

        super.tick()
        if (!level().isClientSide && tickCount >= MAX_LIFETIME_TICKS) {
            detonate()
        }
    }

    override fun onHit(hitResult: HitResult) {
        if (isImpacted()) return
        super.onHit(hitResult)
        if (!level().isClientSide) {
            setPos(hitResult.location)
            detonate()
        }
    }

    private fun detonate() {
        if (!isAlive || isImpacted()) return
        SolarExplosionEffect.detonate(
            sourceEntity = this,
            owner = owner as? LivingEntity,
            radius = 1.5,
            damage = 4.0f,
            scorchStacks = 20,
            scorchDuration = 120,
            soundVolume = 0.42f,
            soundPitch = 1.25f
        )
        entityData.set(DATA_IMPACT_X, x.toFloat())
        entityData.set(DATA_IMPACT_Y, y.toFloat())
        entityData.set(DATA_IMPACT_Z, z.toFloat())
        entityData.set(DATA_IMPACTED, true)
        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
        afterglowTicks = AFTERGLOW_TICKS
        entityData.set(DATA_AFTERGLOW_TICKS, afterglowTicks)
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.putBoolean("touch_of_flame", touchOfFlame)
        compound.putBoolean("impacted", isImpacted())
        compound.putInt("afterglow_ticks", afterglowTicks)
        val impactPosition = getImpactPosition()
        compound.putDouble("impact_x", impactPosition.x)
        compound.putDouble("impact_y", impactPosition.y)
        compound.putDouble("impact_z", impactPosition.z)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        touchOfFlame = compound.getBoolean("touch_of_flame")
        entityData.set(DATA_IMPACT_X, compound.getDouble("impact_x").toFloat())
        entityData.set(DATA_IMPACT_Y, compound.getDouble("impact_y").toFloat())
        entityData.set(DATA_IMPACT_Z, compound.getDouble("impact_z").toFloat())
        entityData.set(DATA_IMPACTED, compound.getBoolean("impacted"))
        afterglowTicks = compound.getInt("afterglow_ticks").takeIf { it > 0 } ?: AFTERGLOW_TICKS
        entityData.set(DATA_AFTERGLOW_TICKS, if (isImpacted()) afterglowTicks else 0)
    }

    companion object {
        private const val MAX_LIFETIME_TICKS = 80
        const val AFTERGLOW_TICKS = 32
        private val DATA_IMPACTED: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(
                SolarEruptionProjectileEntity::class.java,
                EntityDataSerializers.BOOLEAN
            )
        private val DATA_AFTERGLOW_TICKS: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(
                SolarEruptionProjectileEntity::class.java,
                EntityDataSerializers.INT
            )
        private val DATA_IMPACT_X: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(
                SolarEruptionProjectileEntity::class.java,
                EntityDataSerializers.FLOAT
            )
        private val DATA_IMPACT_Y: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(
                SolarEruptionProjectileEntity::class.java,
                EntityDataSerializers.FLOAT
            )
        private val DATA_IMPACT_Z: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(
                SolarEruptionProjectileEntity::class.java,
                EntityDataSerializers.FLOAT
            )
    }
}

object SolarExplosionEffect {
    fun detonate(
        sourceEntity: Entity,
        owner: LivingEntity?,
        radius: Double,
        damage: Float,
        scorchStacks: Int,
        scorchDuration: Int,
        soundVolume: Float,
        soundPitch: Float
    ) {
        val level = sourceEntity.level() as? ServerLevel ?: return
        val center = sourceEntity.position()
        val bounds = AABB(
            center.x - radius,
            center.y - radius,
            center.z - radius,
            center.x + radius,
            center.y + radius,
            center.z + radius
        )
        val damageSource = owner?.let {
            level.damageSources().indirectMagic(sourceEntity, it)
        } ?: level.damageSources().inFire()
        val scorchSource = owner as? ServerPlayer

        level.getEntitiesOfClass(LivingEntity::class.java, bounds) { target ->
            target.isAlive && isEnemy(owner, target)
        }.forEach { target ->
            val distance = sqrt(target.distanceToSqr(center))
            if (distance > radius + target.bbWidth * 0.5) return@forEach
            val falloff = (1.0 - distance / radius * 0.35).coerceIn(0.65, 1.0).toFloat()
            DestinyExplosionRuntime.hurtWithoutKnockback(target, damageSource, damage * falloff)
            DestinyStatusRules.applyScorch(
                target,
                (scorchStacks * falloff).toInt().coerceAtLeast(1),
                scorchDuration,
                scorchSource,
                SolarDamageKind.GRENADE,
                sourceEntity.uuid
            )
        }

        level.playSound(
            null,
            BlockPos.containing(center),
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS,
            soundVolume,
            soundPitch
        )
    }

    private fun isEnemy(owner: LivingEntity?, target: LivingEntity): Boolean {
        if (target == owner) return false
        return target !is Player || owner !is Player || !owner.isAlliedTo(target)
    }
}
