package atopos.destiny2.common.effect

import atopos.destiny2.common.player.AbilitySlot
import java.util.UUID

/** Server-owned attribution retained while Scorch is active on an entity. */
data class SolarScorchContext(
    val sourcePlayerId: UUID,
    val sourceKind: SolarDamageKind,
    val castId: UUID = UUID.randomUUID()
) {
    val abilitySlot: AbilitySlot?
        get() = when (sourceKind) {
            SolarDamageKind.GRENADE -> AbilitySlot.GRENADE
            SolarDamageKind.POWERED_MELEE -> AbilitySlot.MELEE
            SolarDamageKind.SUPER -> AbilitySlot.SUPER
            SolarDamageKind.WEAPON,
            SolarDamageKind.IGNITION,
            SolarDamageKind.GENERIC -> null
        }
}

enum class SolarDamageKind {
    GENERIC,
    GRENADE,
    POWERED_MELEE,
    SUPER,
    WEAPON,
    IGNITION;

    companion object {
        fun fromSerializedName(value: String): SolarDamageKind =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GENERIC
    }
}

/** Implemented by the LivingEntity mixin so attribution survives entity saves. */
interface SolarScorchCarrier {
    fun destiny2modScorchContext(): SolarScorchContext?
    fun destiny2modSetScorchContext(context: SolarScorchContext?)
}
