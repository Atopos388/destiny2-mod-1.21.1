// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.entity

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

class FallenCaptainPelletEntity : Projectile {
    private var startPosition = Vec3.ZERO
    private var lifeTicks = 20

    constructor(type: EntityType<out FallenCaptainPelletEntity>, level: Level) : super(type, level)

    constructor(level: Level, owner: FallenCaptainEntity, direction: Vec3) :
        super(DestinyEntities.FALLEN_CAPTAIN_PELLET, level) {
        this.owner = owner
        val normalized = direction.normalize()
        val muzzle = owner.eyePosition.add(normalized.scale(0.85)).add(0.0, -0.28, 0.0)
        setPos(muzzle.x, muzzle.y, muzzle.z)
        startPosition = muzzle
        deltaMovement = normalized.scale(2.2)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {}

    override fun tick() {
        super.tick()
        if (startPosition == Vec3.ZERO) {
            startPosition = position()
        }

        val motion = deltaMovement
        if (motion.lengthSqr() <= 1.0E-8) {
            discard()
            return
        }

        val from = position()
        val requestedEnd = from.add(motion)
        if (!level().isClientSide && level() is ServerLevel) {
            processServerCollision(level() as ServerLevel, from, requestedEnd)
            if (isRemoved) return
        }

        setPos(requestedEnd.x, requestedEnd.y, requestedEnd.z)
        if (level().isClientSide) {
            level().addParticle(
                ParticleTypes.ELECTRIC_SPARK,
                x, y, z,
                -motion.x * 0.025, -motion.y * 0.025, -motion.z * 0.025
            )
        } else if (tickCount >= lifeTicks || startPosition.distanceToSqr(position()) >= MAX_RANGE * MAX_RANGE) {
            discard()
        }
    }

    private fun processServerCollision(level: ServerLevel, from: Vec3, requestedEnd: Vec3) {
        val blockHit = level.clip(
            ClipContext(from, requestedEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)
        )
        val collisionEnd = if (blockHit.type == HitResult.Type.MISS) requestedEnd else blockHit.location
        val target = level.getEntities(
            this,
            AABB(from, collisionEnd).inflate(0.45)
        ) { candidate ->
            candidate !== owner &&
                candidate !is FallenCaptainEntity &&
                candidate is LivingEntity &&
                candidate.isAlive &&
                candidate.isPickable &&
                !candidate.isSpectator
        }.mapNotNull { candidate ->
            val living = candidate as LivingEntity
            val impact = living.boundingBox.inflate(0.18).clip(from, collisionEnd).orElse(null)
                ?: return@mapNotNull null
            PelletImpact(living, impact, from.distanceToSqr(impact))
        }.minByOrNull(PelletImpact::distanceSquared)

        if (target != null) {
            val damage = FallenCaptainCombatRules.pelletDamage(startPosition.distanceTo(target.location))
            target.target.invulnerableTime = 0
            target.target.hurt(level.damageSources().thrown(this, owner), damage)
            level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                target.location.x, target.location.y, target.location.z,
                3, 0.08, 0.08, 0.08, 0.04
            )
            discard()
            return
        }

        if (blockHit.type != HitResult.Type.MISS) {
            level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                collisionEnd.x, collisionEnd.y, collisionEnd.z,
                2, 0.05, 0.05, 0.05, 0.02
            )
            discard()
        }
    }

    private data class PelletImpact(
        val target: LivingEntity,
        val location: Vec3,
        val distanceSquared: Double
    )

    private companion object {
        const val MAX_RANGE = 24.0
    }
}
