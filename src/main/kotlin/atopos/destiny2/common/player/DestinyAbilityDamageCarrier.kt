package atopos.destiny2.common.player

/** Implemented by projectiles and persistent fields whose damage belongs to an ability slot. */
interface DestinyAbilityDamageCarrier {
    val destinyAbilitySlot: AbilitySlot
}
