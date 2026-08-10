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
import net.minecraft.world.entity.player.Player
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
            val impact = if (hitResult is BlockHitResult) {
                hitResult.location.add(Vec3.atLowerCornerOf(hitResult.direction.normal).scale(0.035))
            } else {
                hitResult.location
            }
            (this.level() as? ServerLevel)?.playSound(
                null,
                impact.x,
                impact.y,
                impact.z,
                DestinySounds.VOID_GRENADE_IMPACT,
                SoundSource.PLAYERS,
                1.0f,
                1.0f
            )
            val vortex = VoidVortexEntity(this.level(), impact.x, impact.y, impact.z, this.owner as? LivingEntity)
            this.level().addFreshEntity(vortex)
            this.discard()
        }
    }
}

/**
 * 陷阱炸弹 (Snare Bomb)
 * 落地后部署为贴地地雷并让附近友军隐身；武装完成后由目标踩踏触发，
 * 爆发烟雾并对其中的敌人持续造成递增伤害与虚弱。
 */
class SnareBombEntity : ThrowableItemProjectile, VoidAbilityDamageCarrier {
    private var ownerCanTrigger = false
    private val smokeExposureTicks = mutableMapOf<UUID, Int>()

    override val voidAbilitySource: VoidAbilitySource
        get() = VoidAbilitySource.MELEE
    
    constructor(entityType: EntityType<out SnareBombEntity>, level: Level) : super(entityType, level)
    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.SNARE_BOMB, level) {
        this.owner = owner
        this.setPos(owner.x, owner.eyeY - 0.1, owner.z)
    }

    override fun getDefaultItem(): Item = Items.SLIME_BALL // Placeholder

    fun state(): Int = entityData.get(DATA_STATE)

    fun isDeployed(): Boolean = state() >= STATE_DEPLOYED

    fun isTriggered(): Boolean = state() == STATE_TRIGGERED

    fun stateAge(partialTick: Float = 0f): Float =
        (tickCount - entityData.get(DATA_STATE_START_TICK)).toFloat() + partialTick

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_STATE, STATE_FLYING)
        builder.define(DATA_STATE_START_TICK, 0)
    }

    override fun tick() {
        if (state() == STATE_FLYING) {
            super.tick()
            if (!level().isClientSide && tickCount >= MAX_FLIGHT_TICKS) {
                discard()
            }
            return
        }

        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
        super.tick()
        if (level().isClientSide) {
            return
        }

        when (state()) {
            STATE_DEPLOYED -> {
                val age = stateAge()
                if (age >= MAX_DEPLOYED_TICKS) {
                    discard()
                    return
                }
                if (age >= ARMING_TICKS) {
                    updateOwnerTriggerPermission()
                    if (findTriggerTarget() != null) {
                        trigger()
                    }
                }
            }

            STATE_TRIGGERED -> {
                if (stateAge() >= SMOKE_DURATION_TICKS) {
                    discard()
                    return
                }
                tickSmokeEffects()
            }
        }
    }

    override fun onHit(hitResult: HitResult) {
        if (state() != STATE_FLYING) {
            return
        }
        if (hitResult !is BlockHitResult) {
            return
        }
        if (hitResult.direction != net.minecraft.core.Direction.UP) {
            bounceOffSurface(hitResult)
            return
        }
        super.onHit(hitResult)
        if (level().isClientSide) {
            return
        }
        deploy(hitResult)
    }

    private fun bounceOffSurface(hitResult: BlockHitResult) {
        val normal = Vec3.atLowerCornerOf(hitResult.direction.normal)
        val velocity = deltaMovement
        val reflected = velocity.subtract(normal.scale(2.0 * velocity.dot(normal))).scale(0.42)
        setPos(hitResult.location.add(normal.scale(0.045)))
        deltaMovement = reflected
    }

    private fun deploy(hitResult: HitResult) {
        val normalOffset = if (hitResult is BlockHitResult) {
            Vec3.atLowerCornerOf(hitResult.direction.normal).scale(0.035)
        } else {
            Vec3(0.0, 0.035, 0.0)
        }
        val impact = hitResult.location.add(normalOffset)
        setPos(impact)
        deltaMovement = Vec3.ZERO
        noPhysics = true
        isNoGravity = true
        setState(STATE_DEPLOYED)
        grantDeploymentInvisibility()

        (level() as? ServerLevel)?.playSound(
            null,
            blockPosition(),
            DestinySounds.SNARE_BOMB_DEPLOY,
            SoundSource.PLAYERS,
            0.7f,
            1.0f
        )
    }

    private fun findTriggerTarget(): LivingEntity? {
        val currentOwner = owner as? LivingEntity
        return level().getEntitiesOfClass(
            LivingEntity::class.java,
            boundingBox.inflate(TRIGGER_HORIZONTAL_RADIUS, TRIGGER_HEIGHT, TRIGGER_HORIZONTAL_RADIUS)
        ) { entity ->
            val dx = entity.x - x
            val dz = entity.z - z
            val playerCanTrigger = entity is Player && (entity != currentOwner || ownerCanTrigger)
            val hostileCanTrigger = entity !is Player &&
                entity != currentOwner &&
                (currentOwner == null || !entity.isAlliedTo(currentOwner))
            entity.isAlive &&
                (playerCanTrigger || hostileCanTrigger) &&
                dx * dx + dz * dz <= TRIGGER_HORIZONTAL_RADIUS * TRIGGER_HORIZONTAL_RADIUS &&
                entity.boundingBox.minY <= y + TRIGGER_HEIGHT &&
                entity.boundingBox.maxY >= y - 0.12
        }.minByOrNull { entity -> entity.distanceToSqr(this) }
    }

    private fun updateOwnerTriggerPermission() {
        if (ownerCanTrigger) {
            return
        }
        val currentOwner = owner as? LivingEntity ?: run {
            ownerCanTrigger = true
            return
        }
        val dx = currentOwner.x - x
        val dz = currentOwner.z - z
        val insideHorizontal = dx * dx + dz * dz <= TRIGGER_HORIZONTAL_RADIUS * TRIGGER_HORIZONTAL_RADIUS
        val insideVertical = currentOwner.boundingBox.minY <= y + TRIGGER_HEIGHT &&
            currentOwner.boundingBox.maxY >= y - 0.12
        if (!insideHorizontal || !insideVertical) {
            ownerCanTrigger = true
        }
    }

    private fun trigger() {
        if (state() != STATE_DEPLOYED) {
            return
        }
        setState(STATE_TRIGGERED)
        beginSmoke()
    }

    private fun beginSmoke() {
        val level = level()
        (level as? ServerLevel)?.let { serverLevel ->
            serverLevel.playSound(null, this.blockPosition(), DestinySounds.SNARE_BOMB_TRIGGER, SoundSource.PLAYERS, 0.7f, 1.0f)
        }
        tickSmokeEffects()
    }

    private fun grantDeploymentInvisibility() {
        val currentOwner = owner as? LivingEntity ?: return
        level().getEntitiesOfClass(
            LivingEntity::class.java,
            boundingBox.inflate(EFFECT_RADIUS)
        ) { entity ->
            entity.isAlive &&
                (entity === currentOwner || entity.isAlliedTo(currentOwner)) &&
                position().distanceToSqr(entity.position()) <= EFFECT_RADIUS * EFFECT_RADIUS
        }.forEach { ally ->
            DestinyStatusRules.applyVoidInvisibility(ally, INVISIBILITY_DURATION_TICKS)
        }
    }

    private fun tickSmokeEffects() {
        val currentOwner = owner as? LivingEntity
        val targets = level().getEntitiesOfClass(
            LivingEntity::class.java,
            boundingBox.inflate(EFFECT_RADIUS)
        ) { entity ->
            entity.isAlive &&
                entity !== currentOwner &&
                (currentOwner == null || !entity.isAlliedTo(currentOwner)) &&
                position().distanceToSqr(entity.position()) <= EFFECT_RADIUS * EFFECT_RADIUS
        }
        val activeTargets = targets.mapTo(mutableSetOf()) { it.uuid }
        smokeExposureTicks.keys.retainAll(activeTargets)

        for (entity in targets) {
            val exposureTicks = (smokeExposureTicks[entity.uuid] ?: 0) + 1
            smokeExposureTicks[entity.uuid] = exposureTicks
            val shouldPulse = exposureTicks == 1 || exposureTicks % SMOKE_DAMAGE_INTERVAL_TICKS == 0
            if (!shouldPulse) {
                continue
            }

            val damageSource = owner?.let { level().damageSources().indirectMagic(this, it) }
                ?: level().damageSources().magic()
            entity.hurt(damageSource, SnareBombRules.damageForExposure(exposureTicks))
            DestinyStatusRules.applyWeaken(entity, WEAKEN_DURATION_TICKS)
            if (entity is Player) {
                entity.addEffect(
                    MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.BLINDNESS,
                        PLAYER_DISORIENT_DURATION_TICKS,
                        0,
                        false,
                        false,
                        false
                    )
                )
            }
        }
    }

    private fun setState(newState: Int) {
        entityData.set(DATA_STATE, newState)
        entityData.set(DATA_STATE_START_TICK, tickCount)
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.putInt(STATE_TAG, state())
        compound.putInt(STATE_AGE_TAG, stateAge().toInt().coerceAtLeast(0))
        compound.putBoolean(OWNER_CAN_TRIGGER_TAG, ownerCanTrigger)
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        val restoredState = if (compound.contains(STATE_TAG)) compound.getInt(STATE_TAG) else STATE_FLYING
        val restoredAge = if (compound.contains(STATE_AGE_TAG)) compound.getInt(STATE_AGE_TAG) else 0
        ownerCanTrigger = compound.getBoolean(OWNER_CAN_TRIGGER_TAG)
        val normalizedState = restoredState.coerceIn(STATE_FLYING, STATE_TRIGGERED)
        entityData.set(DATA_STATE, normalizedState)
        entityData.set(DATA_STATE_START_TICK, tickCount - restoredAge.coerceAtLeast(0))
        if (normalizedState != STATE_FLYING) {
            noPhysics = true
            isNoGravity = true
            deltaMovement = Vec3.ZERO
        }
    }

    companion object {
        const val STATE_FLYING = 0
        const val STATE_DEPLOYED = 1
        const val STATE_TRIGGERED = 2
        const val ARMING_TICKS = 10
        // The confirmed trigger recording is exactly 7.2 seconds at pitch 1.0.
        const val SMOKE_DURATION_TICKS = 144
        const val MAX_DEPLOYED_TICKS = 30 * 20
        private const val MAX_FLIGHT_TICKS = 10 * 20
        private const val TRIGGER_HORIZONTAL_RADIUS = 2.25
        private const val TRIGGER_HEIGHT = 1.25
        private const val EFFECT_RADIUS = 4.0
        private const val INVISIBILITY_DURATION_TICKS = 5 * 20
        private const val WEAKEN_DURATION_TICKS = 8 * 20
        private const val PLAYER_DISORIENT_DURATION_TICKS = 30
        private const val SMOKE_DAMAGE_INTERVAL_TICKS = 20
        private const val STATE_TAG = "DestinySnareState"
        private const val STATE_AGE_TAG = "DestinySnareStateAge"
        private const val OWNER_CAN_TRIGGER_TAG = "DestinySnareOwnerCanTrigger"

        private val DATA_STATE: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(SnareBombEntity::class.java, EntityDataSerializers.INT)
        private val DATA_STATE_START_TICK: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(SnareBombEntity::class.java, EntityDataSerializers.INT)
    }
}

object SnareBombRules {
    private const val BASE_DAMAGE = 0.5f
    private const val DAMAGE_GAIN_PER_SECOND = 0.1f
    private const val MAX_DAMAGE_PER_PULSE = 1.2f

    fun damageForExposure(exposureTicks: Int): Float {
        val completedSeconds = (exposureTicks.coerceAtLeast(1) / 20).toFloat()
        return (BASE_DAMAGE + completedSeconds * DAMAGE_GAIN_PER_SECOND)
            .coerceAtMost(MAX_DAMAGE_PER_PULSE)
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
                DestinyStatusRules.applySuppression(entity, 40, owner)
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
