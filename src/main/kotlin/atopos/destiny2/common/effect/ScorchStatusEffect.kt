package atopos.destiny2.common.effect

import atopos.destiny2.common.aspect.SolarWarlockFragmentRuntime
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity

class ScorchStatusEffect : MobEffect(MobEffectCategory.HARMFUL, 0xFF4500) {
    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return duration % 10 == 0
    }

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int): Boolean {
        val stacks = amplifier + 1
        if (stacks >= DestinyStatusRules.SCORCH_IGNITION_STACKS) {
            entity.removeEffect(DestinyEffects.SCORCH)
            SolarIgnitionRuntime.ignite(entity, (entity as SolarScorchCarrier).destiny2modScorchContext())
            (entity as SolarScorchCarrier).destiny2modSetScorchContext(null)
            return true
        }

        val damage = stacks / 100.0f
        val context = (entity as SolarScorchCarrier).destiny2modScorchContext()
        val level = entity.level() as? ServerLevel
        val sourcePlayer = context?.let { level?.server?.playerList?.getPlayer(it.sourcePlayerId) }
        val damageSource = if (sourcePlayer != null) {
            entity.damageSources().indirectMagic(entity, sourcePlayer)
        } else {
            entity.damageSources().inFire()
        }
        val wasAlive = entity.isAlive
        if (context != null) {
            SolarIgnitionRuntime.withAttributedSolarDamage(entity, context, ignition = false) {
                entity.hurt(damageSource, damage)
            }
        } else {
            entity.hurt(damageSource, damage)
        }
        if (wasAlive && !entity.isAlive && sourcePlayer != null && context != null) {
            SolarWarlockFragmentRuntime.onScorchFinalBlow(sourcePlayer, entity, context)
        }
        val current = entity.getEffect(DestinyEffects.SCORCH)
        if (stacks > 1 && current != null) {
            entity.removeEffect(DestinyEffects.SCORCH)
            entity.addEffect(MobEffectInstance(DestinyEffects.SCORCH, current.duration, stacks - 2))
        } else if (current != null && current.duration <= 10) {
            (entity as SolarScorchCarrier).destiny2modSetScorchContext(null)
        }

        if (level != null) {
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
}
