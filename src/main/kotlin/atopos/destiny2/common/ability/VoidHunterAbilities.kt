package atopos.destiny2.common.ability

import atopos.destiny2.common.ability.GamblerDodgeAbility
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.entity.ShadowshotAnchorEntity
import atopos.destiny2.common.entity.SnareBombEntity
import atopos.destiny2.common.entity.VoidGrenadeEntity
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.sound.DestinySounds
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource

object VoidHunterAbilities {
    private const val MOD_ID = "destiny2-mod"

    val VOID_GRENADE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_hunter_void_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "虚空手雷",
        baseCooldownTicks = 121 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val level = context.player.serverLevel()
            val grenade = VoidGrenadeEntity(level, context.player)
            grenade.shootFromRotation(context.player, context.player.xRot, context.player.yRot, 0.0f, 1.5f, 1.0f)
            level.addFreshEntity(grenade)
            level.playSound(null, context.player.blockPosition(), DestinySounds.VOID_GRENADE_CAST, SoundSource.PLAYERS, 0.7f, 1.0f)
            return true
        }
    }

    val SNARE_BOMB = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_hunter_snare_bomb"),
        slot = AbilitySlot.MELEE,
        displayName = "陷阱炸弹",
        baseCooldownTicks = 90 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val level = context.player.serverLevel()
            val snare = SnareBombEntity(level, context.player)
            snare.shootFromRotation(context.player, context.player.xRot, context.player.yRot, 0.0f, 1.5f, 1.0f)
            level.addFreshEntity(snare)
            level.sendParticles(ParticleTypes.SMOKE, context.player.x, context.player.y + 0.35, context.player.z, 12, 0.3, 0.15, 0.3, 0.02)
            level.playSound(null, context.player.blockPosition(), DestinySounds.SNARE_BOMB_CAST, SoundSource.PLAYERS, 0.75f, 1.0f)
            return true
        }
    }

    val GAMBLER_DODGE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_hunter_gambler_dodge"),
        slot = AbilitySlot.CLASS_ABILITY,
        displayName = "赌徒闪身",
        baseCooldownTicks = 38 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            GamblerDodgeAbility.perform(context.player, context.extraData)
            return true
        }
    }

    val SHADOWSHOT = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_hunter_shadowshot"),
        slot = AbilitySlot.SUPER,
        displayName = "暗影陷阱",
        baseCooldownTicks = 455 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val level = context.player.serverLevel()
            val anchor = ShadowshotAnchorEntity(level, context.player)
            anchor.shootFromRotation(context.player, context.player.xRot, context.player.yRot, 0.0f, 3.0f, 1.0f)
            level.addFreshEntity(anchor)
            DestinyStatusRules.applyVoidInvisibility(context.player, 100)
            level.sendParticles(ParticleTypes.DRAGON_BREATH, context.player.x, context.player.eyeY, context.player.z, 28, 0.35, 0.25, 0.35, 0.05)
            level.playSound(null, context.player.blockPosition(), DestinySounds.SHADOWSHOT_CAST, SoundSource.PLAYERS, 0.9f, 1.0f)
            return true
        }
    }

    val ALL = listOf(VOID_GRENADE, SNARE_BOMB, GAMBLER_DODGE, SHADOWSHOT)
}
