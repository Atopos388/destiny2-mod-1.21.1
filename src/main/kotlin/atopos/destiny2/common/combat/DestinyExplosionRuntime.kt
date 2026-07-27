package atopos.destiny2.common.combat

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ExplosionDamageCalculator
import net.minecraft.world.level.Level

/**
 * Shared explosion rules for Destiny-owned effects.
 *
 * Vanilla still calculates damage, exposure, particles and sound, but its
 * velocity multiplier is forced to zero. Custom area-damage effects can use
 * [hurtWithoutKnockback] to preserve the target's exact pre-hit movement.
 */
object DestinyExplosionRuntime {
    private val NO_KNOCKBACK = object : ExplosionDamageCalculator() {
        override fun getKnockbackMultiplier(entity: Entity): Float = 0.0f
    }

    fun explodeWithoutKnockback(
        level: Level,
        sourceEntity: Entity?,
        x: Double,
        y: Double,
        z: Double,
        power: Float
    ) {
        level.explode(
            sourceEntity,
            null,
            NO_KNOCKBACK,
            x,
            y,
            z,
            power,
            false,
            Level.ExplosionInteraction.NONE
        )
    }

    fun hurtWithoutKnockback(
        target: LivingEntity,
        source: DamageSource,
        amount: Float
    ): Boolean {
        val previousMotion = target.deltaMovement
        val hurt = target.hurt(source, amount)
        target.deltaMovement = previousMotion
        target.hasImpulse = true
        target.hurtMarked = true
        return hurt
    }
}
