package atopos.destiny2.common.entity

import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.combat.DestinyExplosionRuntime
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.particle.BedrockWorldParticleBridge
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import kotlin.math.atan2
import kotlin.math.sqrt

class IncineratorSnapProjectile(
    entityType: EntityType<out ThrowableItemProjectile>,
    level: Level
) : ThrowableItemProjectile(entityType, level), GeoEntity, DestinyAbilityDamageCarrier {
    companion object {
        private val TRAIL_EFFECT = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "solar_snap__projectile_wake"
        )
        private val FLIGHT_SPARK_EFFECT = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "solar_snap__flight_spark"
        )
        private val FLIGHT_SMOKE_RING_EFFECT = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "solar_snap__flight_smoke_ring"
        )
        private val TIMED_EXPLOSION_FLASH_EFFECT = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "solar_snap__timed_explosion_flash"
        )
        private val TIMED_EXPLOSION_RING_EFFECT = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "solar_snap__timed_explosion_ring"
        )
        private val TIMED_EXPLOSION_SPARK_EFFECT = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "solar_snap__timed_explosion_sparks"
        )
        private const val TIMED_EXPLOSION_TICKS = 16
        private const val TIMED_EXPLOSION_RADIUS = 2.25
        private const val TIMED_EXPLOSION_DAMAGE = 2.0f
        private const val TIMED_EXPLOSION_SCORCH = 20
        private const val TIMED_EXPLOSION_EVENT: Byte = 72
    }

    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)
    private var exploded = false
    override val destinyAbilitySlot: AbilitySlot = AbilitySlot.MELEE

    constructor(level: Level, owner: LivingEntity) : this(DestinyEntities.INCINERATOR_SNAP_PROJECTILE, level) {
        this.owner = owner
        val forwardOffset = owner.lookAngle.scale(0.9)
        setPos(
            owner.x + forwardOffset.x,
            owner.eyeY - 0.1 + forwardOffset.y,
            owner.z + forwardOffset.z
        )
    }

    override fun getDefaultItem(): Item = Items.FIRE_CHARGE

    override fun getDefaultGravity(): Double = 0.045

    override fun tick() {
        super.tick()
        if (level().isClientSide) {
            val velocity = deltaMovement
            val horizontal = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
            val yaw = Math.toDegrees(atan2(-velocity.x, velocity.z)).toFloat()
            val pitch = Math.toDegrees(atan2(-velocity.y, horizontal)).toFloat()
            BedrockWorldParticleBridge.emit(TRAIL_EFFECT, position(), yaw, pitch)
            if (tickCount > 1 && tickCount % 3 == 0) {
                val sparkPosition = if (velocity.lengthSqr() > 1.0e-6) {
                    position().subtract(velocity.normalize().scale(0.3))
                } else {
                    position()
                }
                BedrockWorldParticleBridge.emit(FLIGHT_SPARK_EFFECT, sparkPosition, yaw, pitch)
            }
            if (tickCount == 3) {
                val tailPosition = if (velocity.lengthSqr() > 1.0e-6) {
                    position().subtract(velocity.normalize().scale(0.4))
                } else {
                    position()
                }
                BedrockWorldParticleBridge.emit(FLIGHT_SMOKE_RING_EFFECT, tailPosition, yaw, pitch)
            }
        } else if (tickCount >= TIMED_EXPLOSION_TICKS) {
            explode()
        }
    }

    override fun onHitEntity(entityHitResult: EntityHitResult) {
        super.onHitEntity(entityHitResult)
        val target = entityHitResult.entity
        if (target is LivingEntity && isEnemy(target)) {
            val damageSource = (owner as? LivingEntity)?.let { damageSources().indirectMagic(this, it) } ?: damageSources().inFire()
            val hit = target.hurt(damageSource, 4.0f)
            if (hit) {
                (owner as? net.minecraft.server.level.ServerPlayer)?.let(DestinyAspectRuntime::onPoweredMeleeHit)
            }
        }
    }

    override fun onHit(hitResult: HitResult) {
        super.onHit(hitResult)
        if (!level().isClientSide) {
            explode()
        }
    }

    override fun handleEntityEvent(id: Byte) {
        if (id == TIMED_EXPLOSION_EVENT) {
            val effectPosition = position()
            BedrockWorldParticleBridge.emit(TIMED_EXPLOSION_FLASH_EFFECT, effectPosition, yRot, xRot)
            BedrockWorldParticleBridge.emit(TIMED_EXPLOSION_RING_EFFECT, effectPosition, yRot, xRot)
            BedrockWorldParticleBridge.emit(TIMED_EXPLOSION_SPARK_EFFECT, effectPosition, yRot, xRot)
            return
        }
        super.handleEntityEvent(id)
    }

    private fun explode() {
        if (exploded) {
            return
        }
        exploded = true
        val currentLevel = level()
        val sourcePlayer = owner as? net.minecraft.server.level.ServerPlayer
        val damageSource = (owner as? LivingEntity)?.let {
            damageSources().indirectMagic(this, it)
        } ?: damageSources().inFire()
        var hitEnemy = false
        currentLevel.getEntities(
            this,
            AABB(
                x - TIMED_EXPLOSION_RADIUS,
                y - TIMED_EXPLOSION_RADIUS,
                z - TIMED_EXPLOSION_RADIUS,
                x + TIMED_EXPLOSION_RADIUS,
                y + TIMED_EXPLOSION_RADIUS,
                z + TIMED_EXPLOSION_RADIUS
            )
        ).forEach { entity ->
            if (entity is LivingEntity && isEnemy(entity)) {
                DestinyExplosionRuntime.hurtWithoutKnockback(
                    entity,
                    damageSource,
                    TIMED_EXPLOSION_DAMAGE
                )
                DestinyStatusRules.applyScorch(entity, TIMED_EXPLOSION_SCORCH, 140, sourcePlayer)
                hitEnemy = true
            }
        }
        if (hitEnemy) {
            sourcePlayer?.let(DestinyAspectRuntime::onPoweredMeleeHit)
        }
        currentLevel.broadcastEntityEvent(this, TIMED_EXPLOSION_EVENT)
        currentLevel.playSound(
            null,
            blockPosition(),
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS,
            0.8f,
            1.15f
        )
        discard()
    }

    private fun isEnemy(entity: LivingEntity): Boolean {
        val currentOwner = owner
        if (entity == currentOwner) {
            return false
        }
        return entity !is Player || currentOwner !is Player || !currentOwner.isAlliedTo(entity)
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(AnimationController(this, "controller", 0) { event ->
            event.controller.setAnimation(RawAnimation.begin().thenLoop("animation.solar_snap_fireball.fly"))
            PlayState.CONTINUE
        })
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache
}
