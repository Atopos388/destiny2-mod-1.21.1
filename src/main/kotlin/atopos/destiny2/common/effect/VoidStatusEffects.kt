package atopos.destiny2.common.effect

import atopos.destiny2.common.effect.DestinyEffects
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.level.Level
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.resources.ResourceLocation

/**
 * 虚空隐身 (Void Invisibility)
 * 效果：完全隐身。攻击或施放能力时解除。
 */
class VoidInvisibilityEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0x9370DB) {

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int): Boolean {
        // 隐身逻辑主要由 Mixin 处理 (渲染和仇恨)
        // 这里可以处理一些持续性的逻辑，比如粒子效果（如果需要，虽然隐身通常没有粒子）
        return true
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return false // 不需要每 tick 执行逻辑
    }
}

/**
 * 虚空吞食 (Devour)
 * 效果：击杀敌人恢复生命值和能量。
 */
class DevourEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0x4B0082) { // Indigo
    // 逻辑在 Event Handler 中处理
}

/**
 * 虚空护盾 (Void Overshield)
 * 效果：提供伤害减免，破碎时造成范围伤害。
 */
class VoidOvershieldEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0x8A2BE2) { // Blue Violet
    override fun applyEffectTick(entity: LivingEntity, amplifier: Int): Boolean {
        if (entity.absorptionAmount <= 0) {
            // 如果护盾（伤害吸收）归零，移除效果
            // 注意：这需要在 Mixin 中配合检测，或者在这里检测
            // 如果是在这里移除，可能无法精确捕捉"破碎"瞬间，但勉强可用
            // 更好的方式是在 Mixin 中检测 damage 导致的 absorption 归零
        }
        return true
    }

    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean {
        return true
    }
    
    override fun onEffectStarted(entity: LivingEntity, amplifier: Int) {
        super.onEffectStarted(entity, amplifier)
        entity.absorptionAmount += 4.5f
    }
}

/**
 * 虚空虚弱 (Weaken)
 * 效果：增加受到的伤害，降低移动速度。
 */
class WeakenEffect : MobEffect(MobEffectCategory.HARMFUL, 0x9932CC) { // Dark Orchid
    init {
        // 降低 15% 移动速度
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_weaken_slow"),
            -0.15,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }
    // 伤害增加逻辑在 Mixin 中处理
}

/**
 * 虚空压制 (Suppression)
 * 效果：封印技能，致盲/混乱 AI。
 */
class SuppressionEffect : MobEffect(MobEffectCategory.HARMFUL, 0x800080) { // Purple
    // 技能封印逻辑在 Networking/Ability Handler 中处理
}

/**
 * 虚空不稳定 (Volatile)
 * 效果：受到伤害或超时后爆炸。
 */
class VolatileEffect : MobEffect(MobEffectCategory.HARMFUL, 0xDDA0DD) { // Plum
    // 爆炸逻辑在 Mixin 中处理 (Damage Hook)
}
