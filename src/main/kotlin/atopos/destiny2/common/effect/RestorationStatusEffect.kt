package atopos.destiny2.common.effect

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity

/**
 * 恢复效果 (Restoration Status Effect)
 * 
 * 机制说明：
 * 1. 恢复 I 每秒 3.5 生命，恢复 II 每秒 5 生命。
 * 2. 不被打断：受到伤害不会打断恢复效果。
 */
class RestorationStatusEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0xFF6347) { // Tomato color

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return duration % 20 == 0
    }

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int): Boolean {
        entity.heal(if (amplifier >= 1) 5.0f else 3.5f)
        return true
    }
}
