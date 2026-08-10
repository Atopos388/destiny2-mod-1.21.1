package atopos.destiny2.common.weapon

/** Implemented by server damage proxies that snapshot their weapon element at fire time. */
interface DestinyElementalDamageCarrier {
    val destinyDamageElement: DestinyDamageElement
}

/** Weapon projectiles snapshot both element and ammo family at fire time. */
interface DestinyWeaponDamageCarrier : DestinyElementalDamageCarrier {
    val destinyAmmoType: DestinyAmmoType
}
