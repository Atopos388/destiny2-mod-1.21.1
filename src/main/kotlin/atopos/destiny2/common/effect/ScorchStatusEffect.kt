package atopos.destiny2.common.effect

import atopos.destiny2.common.combat.DestinyExplosionRuntime
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

class ScorchStatusEffect : MobEffect(MobEffectCategory.HARMFUL, 0xFF4500) {
    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return duration % 10 == 0
    }

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int): Boolean {
        val stacks = amplifier + 1
        if (stacks >= DestinyStatusRules.SCORCH_IGNITION_STACKS) {
            triggerIgnition(entity)
            entity.removeEffect(DestinyEffects.SCORCH)
            return true
        }

        val damage = stacks / 100.0f
        entity.hurt(entity.damageSources().inFire(), damage)
        val current = entity.getEffect(DestinyEffects.SCORCH)
        if (stacks > 1 && current != null) {
            entity.removeEffect(DestinyEffects.SCORCH)
            entity.addEffect(MobEffectInstance(DestinyEffects.SCORCH, current.duration, stacks - 2))
        }

        val level = entity.level()
        if (level is ServerLevel) {
            level.sendParticles(
                ParticleTypes.FLAME,
                entity.x,
                entity.y + entity.bbHeight / 2,
                entity.z,
                5,
                0.2,
                0.5,
                0.2,
                0.05
            )
        }

        return true
    }

    private fun triggerIgnition(target: LivingEntity) {
        val level = target.level()
        if (level.isClientSide) {
            return
        }

        val explosionRadius = 4.0
        val explosionDamage = 20.0f

        val entities = level.getEntities(
            target,
            AABB(
                target.x - explosionRadius,
                target.y - explosionRadius,
                target.z - explosionRadius,
                target.x + explosionRadius,
                target.y + explosionRadius,
                target.z + explosionRadius
            )
        )

        DestinyExplosionRuntime.hurtWithoutKnockback(
            target,
            target.damageSources().explosion(null, null),
            explosionDamage
        )
        for (entity in entities) {
            if (entity is LivingEntity && entity !is Player) {
                DestinyExplosionRuntime.hurtWithoutKnockback(
                    entity,
                    entity.damageSources().explosion(null, null),
                    explosionDamage
                )
                DestinyStatusRules.applyScorch(entity, 40, 200)
            }
        }
        if (level is ServerLevel) {
            level.sendParticles(ParticleTypes.FLAME, target.x, target.y + target.bbHeight * 0.5, target.z, 48, 1.2, 1.0, 1.2, 0.12)
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.15f)
        }
    }
}
