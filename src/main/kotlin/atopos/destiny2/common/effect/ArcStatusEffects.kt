package atopos.destiny2.common.effect

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob

/**
 * 电弧增幅：提供机动和武器操控，不直接提高伤害。
 * PvE 减伤与敌方远程攻击落空判定由 MixinLivingEntity 统一处理。
 */
class AmplifiedStatusEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0x75E6FF) {
    init {
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "amplified_movement_speed"),
            0.20,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }
}

/**
 * 速度推进：由增幅状态下持续冲刺触发；提供高速移动和更强的跳跃机动。
 */
class SpeedBoosterStatusEffect : MobEffect(MobEffectCategory.BENEFICIAL, 0xC9F6FF) {
    init {
        addAttributeModifier(
            Attributes.MOVEMENT_SPEED,
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "speed_booster_movement_speed"),
            0.125,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
        addAttributeModifier(
            Attributes.JUMP_STRENGTH,
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "speed_booster_jump_strength"),
            0.15,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
    }
}

/**
 * Arc Blind is kept separate from vanilla Blindness so combat rules can test
 * the Destiny keyword without treating unrelated potions as Arc damage.
 */
class ArcBlindStatusEffect : MobEffect(MobEffectCategory.HARMFUL, 0xDDF9FF) {
    override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean = true

    override fun applyEffectTick(entity: LivingEntity, amplifier: Int): Boolean {
        (entity as? Mob)?.let { mob ->
            mob.target = null
            mob.navigation.stop()
        }
        return true
    }
}
