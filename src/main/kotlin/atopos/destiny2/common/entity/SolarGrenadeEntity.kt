package atopos.destiny2.common.entity

import atopos.destiny2.common.ability.DestinyGrenadeThrow
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.sound.DestinySounds
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
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
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

class SolarGrenadeEntity : ThrowableItemProjectile, GeoEntity {
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    constructor(entityType: EntityType<out ThrowableItemProjectile>, level: Level) : super(entityType, level)

    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.SOLAR_GRENADE, level) {
        this.owner = owner
        this.setPos(chargingPosition(owner))
        touchOfFlame = DestinyAspectRuntime.hasTouchOfFlame(owner)
    }

    private var touchOfFlame = false

    override fun getDefaultItem(): Item = Items.FIRE_CHARGE

    fun isCharging(): Boolean = entityData.get(DATA_CHARGING)

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(DATA_CHARGING, true)
    }

    override fun tick() {
        if (!isCharging()) {
            super.tick()
            return
        }

        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
        super.tick()

        val currentOwner = owner as? LivingEntity
        if (!level().isClientSide) {
            if (currentOwner == null || !currentOwner.isAlive) {
                discard()
                return
            }

            setPos(chargingPosition(currentOwner))
            if (tickCount >= CHARGE_TICKS) {
                release(currentOwner)
            }
        }
    }

    private fun release(currentOwner: LivingEntity) {
        entityData.set(DATA_CHARGING, false)
        noPhysics = false
        isNoGravity = false
        setPos(chargingPosition(currentOwner))
        DestinyGrenadeThrow.launch(this, currentOwner, DestinyGrenadeThrow.Profile.AREA)
    }

    override fun onHit(hitResult: HitResult) {
        if (isCharging()) {
            return
        }
        super.onHit(hitResult)
        if (level().isClientSide) {
            return
        }

        val impact = hitResult.location
        setPos(impact)
        SolarExplosionEffect.detonate(
            sourceEntity = this,
            owner = owner as? LivingEntity,
            radius = 4.0,
            damage = 7.0f,
            scorchStacks = 30,
            scorchDuration = 130,
            soundVolume = 0.72f,
            soundPitch = 0.92f
        )
        val flare = SolarFlareEntity(
            level(),
            impact.x,
            impact.y,
            impact.z,
            owner as? LivingEntity,
            touchOfFlame
        )
        level().addFreshEntity(flare)
        level().playSound(
            null,
            blockPosition(),
            DestinySounds.SOLAR_GRENADE_IMPACT,
            SoundSource.PLAYERS,
            0.55f,
            1.0f
        )
        discard()
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "solar_grenade_controller", 0) { state ->
            state.controller.setAnimation(RawAnimation.begin().thenLoop("animation.solar_grenade.idle"))
            PlayState.CONTINUE
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    override fun addAdditionalSaveData(compound: CompoundTag) {
        super.addAdditionalSaveData(compound)
        compound.putBoolean("touch_of_flame", touchOfFlame)
        compound.putBoolean("charging", isCharging())
    }

    override fun readAdditionalSaveData(compound: CompoundTag) {
        super.readAdditionalSaveData(compound)
        touchOfFlame = compound.getBoolean("touch_of_flame")
        entityData.set(DATA_CHARGING, compound.getBoolean("charging"))
    }

    companion object {
        const val CHARGE_TICKS = 12
        private val DATA_CHARGING: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(SolarGrenadeEntity::class.java, EntityDataSerializers.BOOLEAN)

        private fun chargingPosition(owner: LivingEntity): Vec3 {
            val look = owner.lookAngle.normalize()
            val right = look.cross(Vec3(0.0, 1.0, 0.0)).let {
                if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(-1.0, 0.0, 0.0)
            }
            return owner.eyePosition
                .add(look.scale(0.42))
                .add(right.scale(0.28))
                .add(0.0, -0.32, 0.0)
        }
    }
}

class SolarFlareEntity(
    entityType: EntityType<*>,
    level: Level
) : Entity(entityType, level), GeoEntity, DestinyAbilityDamageCarrier {
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    constructor(level: Level, x: Double, y: Double, z: Double, owner: LivingEntity?, touchOfFlame: Boolean = false) : this(DestinyEntities.SOLAR_FLARE, level) {
        setPos(x, y, z)
        this.owner = owner
        this.touchOfFlame = touchOfFlame
        this.duration = if (touchOfFlame) TOUCH_OF_FLAME_DURATION_TICKS else BASE_DURATION_TICKS
    }

    private var duration = BASE_DURATION_TICKS
    private var touchOfFlame = false
    private var eruptionsLaunched = 0
    private var eruptionVolleysLaunched = 0
    private var eruptionCooldown = FIRST_ERUPTION_TICK
    private var doubleVolleyIndex = -1
    var owner: LivingEntity? = null
    override val destinyAbilitySlot: AbilitySlot = AbilitySlot.GRENADE

    override fun tick() {
        super.tick()
        if (level().isClientSide) {
            return
        }

        if (duration-- <= 0) {
            discard()
            return
        }

        if (touchOfFlame && eruptionsLaunched < ERUPTION_PROJECTILE_COUNT) {
            eruptionCooldown--
            if (eruptionCooldown <= 0) {
                if (doubleVolleyIndex < 0) {
                    val availableVolleys =
                        (ERUPTION_VOLLEY_COUNT - eruptionVolleysLaunched).coerceAtLeast(1)
                    doubleVolleyIndex =
                        eruptionVolleysLaunched + random.nextInt(availableVolleys)
                }
                val remaining = ERUPTION_PROJECTILE_COUNT - eruptionsLaunched
                val volleySize = if (eruptionVolleysLaunched == doubleVolleyIndex) {
                    minOf(2, remaining)
                } else {
                    1
                }
                repeat(volleySize) {
                    launchEruptionProjectile()
                    eruptionsLaunched++
                }
                eruptionVolleysLaunched++
                eruptionCooldown = ERUPTION_INTERVAL_TICKS
            }
        }

        if (tickCount % 10 == 0) {
            burnPulse()
        }
    }

    private fun launchEruptionProjectile() {
        val angle = random.nextDouble() * Mth.TWO_PI
        val direction = Vec3(
            kotlin.math.cos(angle),
            0.0,
            kotlin.math.sin(angle)
        )
        val spawn = Vec3(
            x + direction.x * ERUPTION_SPAWN_RADIUS,
            y + ERUPTION_SPAWN_HEIGHT,
            z + direction.z * ERUPTION_SPAWN_RADIUS
        )
        val targetX = x + direction.x * ERUPTION_LANDING_RADIUS
        val targetZ = z + direction.z * ERUPTION_LANDING_RADIUS
        val groundHit = level().clip(
            ClipContext(
                Vec3(targetX, y + 4.0, targetZ),
                Vec3(targetX, y - 4.0, targetZ),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
            )
        )
        val targetY = if (groundHit.type == HitResult.Type.BLOCK) groundHit.location.y else y
        val target = Vec3(targetX, targetY, targetZ)
        val projectile = SolarEruptionProjectileEntity(level(), owner, touchOfFlame)
        projectile.setPos(spawn)
        projectile.deltaMovement = ballisticVelocity(spawn, target)
        projectile.hasImpulse = true
        level().addFreshEntity(projectile)
    }

    private fun ballisticVelocity(from: Vec3, target: Vec3): Vec3 {
        var dragFactor = 1.0
        var dragSum = 0.0
        repeat(ERUPTION_FLIGHT_TICKS) {
            dragSum += dragFactor
            dragFactor *= ERUPTION_DRAG
        }
        val gravityLoss = ERUPTION_GRAVITY / (1.0 - ERUPTION_DRAG) *
            (ERUPTION_FLIGHT_TICKS - dragSum)
        val delta = target.subtract(from)
        return Vec3(
            delta.x / dragSum,
            (delta.y + gravityLoss) / dragSum,
            delta.z / dragSum
        )
    }

    private fun burnPulse() {
        val radius = 4.0
        val entities = level().getEntities(
            this,
            AABB(x - radius, y - 1.0, z - radius, x + radius, y + 2.5, z + radius)
        )

        entities.forEach { entity ->
            if (entity is LivingEntity && isEnemy(entity)) {
                val source = owner as? net.minecraft.server.level.ServerPlayer
                DestinyStatusRules.applyScorch(
                    entity,
                    18,
                    if (touchOfFlame) 150 else 120,
                    source,
                    SolarDamageKind.GRENADE,
                    uuid
                )
                val damageSource = owner?.let { damageSources().indirectMagic(this, it) } ?: damageSources().inFire()
                entity.hurt(damageSource, 2.5f)
            }
        }

    }

    private fun isEnemy(entity: LivingEntity): Boolean {
        val currentOwner = owner
        if (entity == currentOwner) {
            return false
        }
        return entity !is Player || currentOwner !is Player || !currentOwner.isAlliedTo(entity)
    }

    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) {}
    override fun readAdditionalSaveData(compound: CompoundTag) {
        duration = compound.getInt("duration").takeIf { it > 0 }
            ?: if (compound.getBoolean("touch_of_flame")) TOUCH_OF_FLAME_DURATION_TICKS else BASE_DURATION_TICKS
        touchOfFlame = compound.getBoolean("touch_of_flame")
        eruptionsLaunched = if (compound.contains("eruptions_launched")) {
            compound.getInt("eruptions_launched").coerceIn(0, ERUPTION_PROJECTILE_COUNT)
        } else {
            0
        }
        eruptionVolleysLaunched = if (compound.contains("eruption_volleys_launched")) {
            compound.getInt("eruption_volleys_launched").coerceIn(0, ERUPTION_VOLLEY_COUNT)
        } else {
            eruptionsLaunched.coerceAtMost(ERUPTION_VOLLEY_COUNT)
        }
        eruptionCooldown = if (compound.contains("eruption_cooldown")) {
            compound.getInt("eruption_cooldown").coerceAtLeast(1)
        } else {
            FIRST_ERUPTION_TICK
        }
        doubleVolleyIndex = if (compound.contains("double_volley_index")) {
            compound.getInt("double_volley_index").coerceIn(-1, ERUPTION_VOLLEY_COUNT - 1)
        } else {
            -1
        }
    }
    override fun addAdditionalSaveData(compound: CompoundTag) {
        compound.putInt("duration", duration)
        compound.putBoolean("touch_of_flame", touchOfFlame)
        compound.putInt("eruptions_launched", eruptionsLaunched)
        compound.putInt("eruption_volleys_launched", eruptionVolleysLaunched)
        compound.putInt("eruption_cooldown", eruptionCooldown)
        compound.putInt("double_volley_index", doubleVolleyIndex)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "solar_flare_controller", 0) { state ->
            state.controller.setAnimation(RawAnimation.begin().thenLoop("animation.solar_flare.idle"))
            PlayState.CONTINUE
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    companion object {
        const val CORE_FORMATION_TICKS = 16
        const val BASE_DURATION_TICKS = 4 * 20
        const val TOUCH_OF_FLAME_DURATION_TICKS = 6 * 20
        const val FIRST_ERUPTION_TICK = 22
        const val ERUPTION_INTERVAL_TICKS = 15
        const val ERUPTION_PROJECTILE_COUNT = 9
        private const val ERUPTION_VOLLEY_COUNT = 8
        private const val ERUPTION_LANDING_RADIUS = 3.5
        private const val ERUPTION_SPAWN_RADIUS = 0.82
        private const val ERUPTION_SPAWN_HEIGHT = 0.72
        private const val ERUPTION_FLIGHT_TICKS = 24
        private const val ERUPTION_DRAG = 0.99
        private const val ERUPTION_GRAVITY = 0.055
    }
}
