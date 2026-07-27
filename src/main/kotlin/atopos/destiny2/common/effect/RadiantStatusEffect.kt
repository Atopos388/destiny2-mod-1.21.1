package atopos.destiny2.common.effect

import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity

/**
 * 焕光效果 (Radiant Status Effect)
 * 
 * 机制说明：
 * 1. PvE 武器伤害提升 25%，由统一伤害结算按武器来源处理。
 * 2. 破盾：(未实现) 对护盾造成额外伤害。
 * 3. 叠加规则：不可叠加。
 */
class RadiantStatusEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0xFFD700) {

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return false // 不需要每 tick 执行逻辑，属性修饰符会自动处理
    }

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int): Boolean {
        return true
    }
}
