package atopos.destiny2.common.entity

import atopos.destiny2.common.combat.DestinyExplosionRuntime
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.gear.GearPerkEffect
import atopos.destiny2.common.gear.GearPerkRuntime
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.AmmoDropSystem
import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.weapon.DestinyDamageElement
import atopos.destiny2.common.weapon.DestinyElementalDamageCarrier
import atopos.destiny2.common.weapon.DestinyWeaponDamageCarrier
import atopos.destiny2.common.tacz.TaczEntityHitbox
import atopos.destiny2.common.weapon.DamageNumberRuntime
import atopos.destiny2.common.item.MicroMissileBurstWeaponItem
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult

enum class MicroMissileBarrelEffect {
    NONE,
    PRECISION,
    HIGH_EXPLOSIVE,
    TRACKING
}

class MicroMissileProjectile : ThrowableItemProjectile, DestinyWeaponDamageCarrier {
    private var destinyDamage: Float = 6.0f
    private var destinyExplosionRadius: Float = 1.5f
    private var barrelEffect = MicroMissileBarrelEffect.NONE
    private var directHitTarget: LivingEntity? = null
    private var directHitWasPrecision = false
    private var sourceAmmoType = DestinyAmmoType.SPECIAL
    override val destinyAmmoType: DestinyAmmoType
        get() = sourceAmmoType
    override var destinyDamageElement: DestinyDamageElement = DestinyDamageElement.KINETIC
        private set
    private var directHitMultiplier = 1.0f

    constructor(entityType: EntityType<out MicroMissileProjectile>, level: Level) : super(entityType, level)

    constructor(level: Level, owner: LivingEntity) : super(DestinyEntities.MICRO_MISSILE_PROJECTILE, level) {
        this.owner = owner
        this.setPos(owner.x, owner.eyeY - 0.1, owner.z)
    }

    constructor(
        level: Level,
        owner: LivingEntity,
        damage: Float,
        explosionRadius: Float,
        barrelEffect: MicroMissileBarrelEffect = MicroMissileBarrelEffect.NONE,
        sourceAmmoType: DestinyAmmoType = DestinyAmmoType.SPECIAL,
        directHitMultiplier: Float = 1.0f,
        damageElement: DestinyDamageElement = DestinyDamageElement.KINETIC
    ) : this(level, owner) {
        destinyDamage = damage
        destinyExplosionRadius = explosionRadius
        this.barrelEffect = barrelEffect
        this.sourceAmmoType = sourceAmmoType
        this.destinyDamageElement = damageElement
        this.directHitMultiplier = directHitMultiplier.coerceAtLeast(0.0f)
    }

    override fun getDefaultItem(): Item {
        return Items.SLIME_BALL
    }

    override fun getDefaultGravity(): Double {
        return 0.01
    }

    override fun tick() {
        super.tick()
        if (!level().isClientSide && barrelEffect == MicroMissileBarrelEffect.TRACKING) {
            steerTowardNearestTarget()
        }
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.COMPOSTER, x, y, z, 0.0, 0.0, 0.0)
        }
    }

    override fun onHit(hitResult: HitResult) {
        directHitTarget = (hitResult as? EntityHitResult)?.entity as? LivingEntity
        directHitWasPrecision = directHitTarget?.let { target ->
            TaczEntityHitbox.isHeadshot(target, hitResult.location)
        } ?: false
        super.onHit(hitResult)
        if (!level().isClientSide) {
            explode()
            discard()
        }
    }

    private fun explode() {
        val radius = destinyExplosionRadius.coerceAtLeast(0.5f)
        val damage = destinyDamage.coerceAtLeast(0.0f)
        val level = level()
        val source = level.damageSources().thrown(this, owner)
        val bounds = AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius)
        val targets = level.getEntitiesOfClass(LivingEntity::class.java, bounds) { it != owner }
        val livingBeforeExplosion = targets.filter { it.isAlive }.toSet()
        targets.forEach { target ->
            val isDirectHit = target == directHitTarget
            val isDirectPrecision = isDirectHit && directHitWasPrecision
            val targetDamage = damage * (if (isDirectHit) directHitMultiplier else 1.0f)
            DamageNumberRuntime.withHit(owner as? ServerPlayer, target, targetDamage, isDirectPrecision) {
                DestinyExplosionRuntime.hurtWithoutKnockback(target, source, targetDamage)
            }
        }
        val player = owner as? ServerPlayer
        when (barrelEffect) {
            MicroMissileBarrelEffect.PRECISION -> if (directHitTarget != null) {
                player?.let { GearPerkRuntime.announcePerk(it, GearPerkEffect.PRECISION_BARREL, "精准弹头") }
            }
            MicroMissileBarrelEffect.HIGH_EXPLOSIVE -> {
                player?.let { GearPerkRuntime.announcePerk(it, GearPerkEffect.HIGH_EXPLOSIVE_BARREL, "高爆枪管") }
            }
            MicroMissileBarrelEffect.TRACKING -> {
                player?.let { GearPerkRuntime.announcePerk(it, GearPerkEffect.STABLE_LAUNCHER, "追踪弹头") }
            }
            MicroMissileBarrelEffect.NONE -> Unit
        }
        DestinyExplosionRuntime.explodeWithoutKnockback(level, this, x, y, z, radius)
        player?.let { shooter ->
            if (directHitWasPrecision) {
                ServerPlayNetworking.send(shooter, DestinyNetworking.PrecisionHitPayload(damage * directHitMultiplier))
            }
            val serverLevel = level as? ServerLevel ?: return@let
            val defeatedTargets = livingBeforeExplosion.filterNot { it.isAlive }
            defeatedTargets.forEach { defeated ->
                AmmoDropSystem.onWeaponKill(serverLevel, defeated, sourceAmmoType, shooter)
                MicroMissileBurstWeaponItem.onMicroMissileKill(shooter)
                if (barrelEffect == MicroMissileBarrelEffect.HIGH_EXPLOSIVE) {
                    val scorchStacks = if (
                        DestinyAspectRuntime.hasFragment(shooter, DestinyAspectRuntime.EMBER_OF_ASHES)
                    ) 40 else 30
                    serverLevel.getEntitiesOfClass(
                        LivingEntity::class.java,
                        defeated.boundingBox.inflate(4.0)
                    ) { target ->
                        target.isAlive && target !== shooter && target !== defeated && !target.isAlliedTo(shooter)
                    }.forEach { target ->
                        DestinyStatusRules.applyScorchExact(
                            target,
                            scorchStacks,
                            100,
                            shooter,
                            SolarDamageKind.WEAPON,
                            uuid
                        )
                    }
                }
            }
        }
    }

    private fun steerTowardNearestTarget() {
        val target = level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(12.0)) {
            it.isAlive && it != owner
        }.minByOrNull { it.distanceToSqr(this) } ?: return
        val velocity = deltaMovement
        if (velocity.lengthSqr() < 0.001) return
        val desired = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
            .subtract(position()).normalize().scale(velocity.length())
        deltaMovement = velocity.scale(0.80).add(desired.scale(0.20))
        hasImpulse = true
    }
}
