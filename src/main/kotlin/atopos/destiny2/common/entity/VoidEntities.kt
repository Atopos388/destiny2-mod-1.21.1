package atopos.destiny2.common.entity

import atopos.destiny2.common.aspect.VoidAbilityDamageCarrier
import atopos.destiny2.common.aspect.VoidAbilitySource
import atopos.destiny2.common.aspect.VoidHunterAspectRules
import atopos.destiny2.common.aspect.VoidHunterAspectRuntime
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.sound.DestinySounds
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * 粘性闪光手雷 (Void Grenade)
 * 吸附在表面，爆炸后产生持续伤害区域。
 */
class VoidGrenadeEntity : ThrowableItemProjectile {
    
    constructor(entityType: EntityType<out VoidGrenadeEntity>, level: Level) : super(entityType, level)
    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.VOID_GRENADE, level) {
        this.owner = owner
        this.setPos(owner.x, owner.eyeY - 0.1, owner.z)
    }

    override fun getDefaultItem(): Item = Items.FIREWORK_STAR // Placeholder

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        if (!this.level().isClientSide) {
            (this.level() as? ServerLevel)?.let { level ->
                level.playSound(null, this.blockPosition(), DestinySounds.VOID_GRENADE_IMPACT, SoundSource.PLAYERS, 0.75f, 1.0f)
            }
            val impact = if (hitResult is BlockHitResult) {
                hitResult.location.add(Vec3.atLowerCornerOf(hitResult.direction.normal).scale(0.035))
            } else {
                hitResult.location
            }
            val vortex = VoidVortexEntity(this.level(), impact.x, impact.y, impact.z, this.owner as? LivingEntity)
            this.level().addFreshEntity(vortex)
            this.discard()
        }
    }
}

/**
 * 陷阱炸弹 (Snare Bomb)
 * 吸附在敌人身上或地面，爆炸后致盲+虚弱。
 */
class SnareBombEntity : ThrowableItemProjectile {
    
    constructor(entityType: EntityType<out SnareBombEntity>, level: Level) : super(entityType, level)
    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.SNARE_BOMB, level) {
        this.owner = owner
        this.setPos(owner.x, owner.eyeY - 0.1, owner.z)
    }

    override fun getDefaultItem(): Item = Items.SLIME_BALL // Placeholder

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        if (!this.level().isClientSide) {
            // Immediate explosion for now (can upgrade to trap later)
            explode()
            this.discard()
        }
    }

    private fun explode() {
        val radius = 4.0
        val level = this.level()
        val entities = level.getEntities(this, this.boundingBox.inflate(radius))

        (level as? ServerLevel)?.let { serverLevel ->
            serverLevel.sendParticles(ParticleTypes.SQUID_INK, this.x, this.y + 0.1, this.z, 24, 0.8, 0.35, 0.8, 0.05)
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, this.x, this.y + 0.1, this.z, 18, 0.8, 0.25, 0.8, 0.08)
            serverLevel.playSound(null, this.blockPosition(), DestinySounds.SNARE_BOMB_IMPACT, SoundSource.PLAYERS, 0.7f, 1.0f)
        }

        for (entity in entities) {
            if (entity is LivingEntity && entity != owner) {
                val damageSource = owner?.let { level.damageSources().indirectMagic(this, it) }
                    ?: level.damageSources().magic()
                entity.hurt(damageSource, 2.0f)
                entity.addEffect(MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 100, 0))
                DestinyStatusRules.applyWeaken(entity, 8 * 20)
                entity.addEffect(MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 8 * 20, 1))
            }
        }
    }
}

/**
 * 虚空漩涡 (Void Vortex) - 持续伤害区域
 */
class VoidVortexEntity(
    entityType: EntityType<*>,
    level: Level
) : net.minecraft.world.entity.Entity(entityType, level), VoidAbilityDamageCarrier {
    
    constructor(level: Level, x: Double, y: Double, z: Double, owner: LivingEntity?) : this(DestinyEntities.VOID_VORTEX, level) {
        this.setPos(x, y, z)
        this.ownerUuid = owner?.uuid
        this.duration = VoidHunterAspectRuntime.vortexDuration(owner as? ServerPlayer)
        this.entityData.set(DATA_TOTAL_DURATION, this.duration)
    }

    override val voidAbilitySource: VoidAbilitySource = VoidAbilitySource.GRENADE
    private var duration = VoidHunterAspectRules.BASE_VORTEX_DURATION_TICKS
    private var ownerUuid: UUID? = null
    private var damageApplied = false

    fun visualDuration(): Int = entityData.get(DATA_TOTAL_DURATION)

    override fun tick() {
        super.tick()
        if (this.level().isClientSide) {
            return
        }

        if (duration-- <= 0) {
            this.discard()
            return
        }

        val owner = resolveOwner()
        val targets = this.level().getEntitiesOfClass(
            LivingEntity::class.java,
            this.boundingBox.inflate(VORTEX_RADIUS)
        ) { entity ->
            entity.isAlive &&
                entity != owner &&
                (owner == null || !entity.isAlliedTo(owner)) &&
                entity.position().distanceToSqr(this.position()) <= VORTEX_RADIUS * VORTEX_RADIUS
        }

        if (!damageApplied) {
            damageApplied = true
            val source = owner?.let { this.damageSources().indirectMagic(this, it) }
                ?: this.damageSources().magic()
            for (entity in targets) {
                entity.hurt(source, VORTEX_DAMAGE)
                if (VoidHunterAspectRuntime.shouldApplyGrenadeWeaken(owner)) {
                    DestinyStatusRules.applyWeaken(entity, 8 * 20)
                }
            }
        }

        for (entity in targets) {
            pullTowardCenter(entity, VORTEX_PULL_STRENGTH)
        }
    }

    private fun pullTowardCenter(entity: LivingEntity, strength: Double) {
        val delta = Vec3(this.x - entity.x, this.y - entity.y, this.z - entity.z)
        if (delta.lengthSqr() < 0.25) {
            return
        }
        val pull = delta.normalize().scale(strength)
        entity.push(pull.x, pull.y * 0.25, pull.z)
        entity.hurtMarked = true
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_TOTAL_DURATION, VoidHunterAspectRules.BASE_VORTEX_DURATION_TICKS)
    }
    override fun readAdditionalSaveData(compound: CompoundTag) {
        ownerUuid = if (compound.hasUUID(OWNER_TAG)) compound.getUUID(OWNER_TAG) else null
        if (compound.contains(DURATION_TAG)) {
            duration = compound.getInt(DURATION_TAG).coerceAtLeast(0)
            entityData.set(DATA_TOTAL_DURATION, duration)
        }
        damageApplied = compound.getBoolean(DAMAGE_APPLIED_TAG)
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        ownerUuid?.let { compound.putUUID(OWNER_TAG, it) }
        compound.putInt(DURATION_TAG, duration)
        compound.putBoolean(DAMAGE_APPLIED_TAG, damageApplied)
    }

    private fun resolveOwner(): ServerPlayer? {
        val ownerId = ownerUuid ?: return null
        return (level() as? ServerLevel)?.server?.playerList?.getPlayer(ownerId)
    }

    private companion object {
        val DATA_TOTAL_DURATION: EntityDataAccessor<Int> = SynchedEntityData.defineId(
            VoidVortexEntity::class.java,
            EntityDataSerializers.INT
        )
        const val OWNER_TAG = "DestinyOwner"
        const val DURATION_TAG = "DestinyDuration"
        const val DAMAGE_APPLIED_TAG = "DestinyDamageApplied"
        const val VORTEX_RADIUS = 4.0
        const val VORTEX_DAMAGE = 10.0f
        const val VORTEX_PULL_STRENGTH = 0.075
    }
}

/**
 * 狩猎陷阱锚点 (Shadowshot Anchor)
 */
class ShadowshotAnchorEntity : ThrowableItemProjectile {
    
    constructor(entityType: EntityType<out ShadowshotAnchorEntity>, level: Level) : super(entityType, level)
    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.SHADOWSHOT_ANCHOR, level) {
        this.owner = owner
        this.setPos(owner.x, owner.eyeY - 0.1, owner.z)
    }

    override fun getDefaultItem(): Item = Items.AMETHYST_SHARD // Placeholder

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        if (!this.level().isClientSide) {
            val target = (hitResult as? EntityHitResult)?.entity as? LivingEntity
            if (target != null && target != owner) {
                val source = owner?.let { damageSources().indirectMagic(this, it) } ?: damageSources().magic()
                target.hurt(source, 12.0f)
            }
            (this.level() as? ServerLevel)?.let { level ->
                level.sendParticles(ParticleTypes.DRAGON_BREATH, this.x, this.y, this.z, 48, 0.35, 0.2, 0.35, 0.12)
                level.playSound(null, this.blockPosition(), DestinySounds.SHADOWSHOT_IMPACT, SoundSource.PLAYERS, 0.9f, 1.0f)
            }
            val tether = VoidTetherEntity(this.level(), this.x, this.y, this.z, this.owner as? LivingEntity)
            this.level().addFreshEntity(tether)
            this.discard()
        }
    }
}

/**
 * 虚空连线 (Void Tether) - 压制与牵引
 */
class VoidTetherEntity(
    entityType: EntityType<*>,
    level: Level
) : net.minecraft.world.entity.Entity(entityType, level), VoidAbilityDamageCarrier {

    constructor(level: Level, x: Double, y: Double, z: Double, owner: LivingEntity?) : this(DestinyEntities.VOID_TETHER, level) {
        this.setPos(x, y, z)
        this.ownerUuid = owner?.uuid
    }

    override val voidAbilitySource: VoidAbilitySource = VoidAbilitySource.SUPER
    private var duration = 12 * 20
    private var ownerUuid: UUID? = null

    override fun tick() {
        super.tick()
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.PORTAL, this.x, this.y + 1, this.z, 0.0, 0.0, 0.0)
            this.level().addParticle(ParticleTypes.REVERSE_PORTAL, this.x, this.y + 0.2, this.z, 0.0, 0.02, 0.0)
            return
        }

        if (duration-- <= 0) {
            this.discard()
            return
        }

        val radius = 12.0
        val owner = resolveOwner()
        val entities = this.level().getEntities(this, this.boundingBox.inflate(radius))
        var tethered = 0
        
        for (entity in entities) {
            if (entity is LivingEntity && entity != owner && (owner == null || !entity.isAlliedTo(owner))) {
                DestinyStatusRules.applySuppression(entity, 40)
                DestinyStatusRules.applyStrongWeaken(entity, 12 * 20)

                val dx = this.x - entity.x
                val dy = this.y - entity.y
                val dz = this.z - entity.z
                val distSq = dx*dx + dy*dy + dz*dz
                
                if (distSq > 1.0) {
                    val pullStrength = 0.05
                    entity.push(dx * pullStrength, dy * pullStrength, dz * pullStrength)
                    entity.hurtMarked = true // Force update
                }

                tethered += 1
            }
        }

        if (tethered > 0 && this.tickCount % 10 == 0) {
            (this.level() as? ServerLevel)?.sendParticles(
                ParticleTypes.DRAGON_BREATH,
                this.x,
                this.y + 0.8,
                this.z,
                6 + tethered * 2,
                1.0,
                0.35,
                1.0,
                0.03
            )
        }
    }

    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) {}
    override fun readAdditionalSaveData(compound: CompoundTag) {
        ownerUuid = if (compound.hasUUID(OWNER_TAG)) compound.getUUID(OWNER_TAG) else null
        if (compound.contains(DURATION_TAG)) {
            duration = compound.getInt(DURATION_TAG).coerceAtLeast(0)
        }
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        ownerUuid?.let { compound.putUUID(OWNER_TAG, it) }
        compound.putInt(DURATION_TAG, duration)
    }

    private fun resolveOwner(): ServerPlayer? {
        val ownerId = ownerUuid ?: return null
        return (level() as? ServerLevel)?.server?.playerList?.getPlayer(ownerId)
    }

    private companion object {
        const val OWNER_TAG = "DestinyOwner"
        const val DURATION_TAG = "DestinyDuration"
    }
}
