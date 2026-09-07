package atopos.destiny2.common.ability

import atopos.destiny2.common.player.AbilitySlot
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation

class PlaceholderAbility(
    id: ResourceLocation,
    slot: AbilitySlot,
    displayName: String
) : AbstractExecutableAbility(
    id = id,
    slot = slot,
    displayName = displayName,
    baseCooldownTicks = when (slot) {
        AbilitySlot.GRENADE -> 30 * 20
        AbilitySlot.MELEE -> 20 * 20
        AbilitySlot.CLASS_ABILITY -> 35 * 20
        AbilitySlot.SUPER -> 180 * 20
    }
) {
    override fun cast(context: DestinyAbilityContext): Boolean {
        val player = context.player
        val level = player.serverLevel()
        level.sendParticles(
            ParticleTypes.ELECTRIC_SPARK,
            player.x,
            player.y + 1.0,
            player.z,
            28,
            0.45,
            0.55,
            0.45,
            0.06
        )
        return true
    }
}
