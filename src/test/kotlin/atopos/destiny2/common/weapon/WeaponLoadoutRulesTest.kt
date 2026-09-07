package atopos.destiny2.common.weapon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeaponLoadoutRulesTest {
    @Test
    fun `weapon columns follow Destiny element and heavy ammo rules`() {
        assertEquals(
            DestinyWeaponSlot.KINETIC,
            DestinyWeaponSlot.forWeapon(DestinyAmmoType.PRIMARY, DestinyDamageElement.KINETIC)
        )
        assertEquals(
            DestinyWeaponSlot.KINETIC,
            DestinyWeaponSlot.forWeapon(DestinyAmmoType.SPECIAL, DestinyDamageElement.STASIS)
        )
        assertEquals(
            DestinyWeaponSlot.ENERGY,
            DestinyWeaponSlot.forWeapon(DestinyAmmoType.PRIMARY, DestinyDamageElement.ARC)
        )
        assertEquals(
            DestinyWeaponSlot.ENERGY,
            DestinyWeaponSlot.forWeapon(DestinyAmmoType.SPECIAL, DestinyDamageElement.SOLAR)
        )
        assertEquals(
            DestinyWeaponSlot.POWER,
            DestinyWeaponSlot.forWeapon(DestinyAmmoType.HEAVY, DestinyDamageElement.SOLAR)
        )
    }

    @Test
    fun `three equipped columns use hotbar one through three`() {
        assertEquals(listOf(0, 1, 2), DestinyWeaponSlot.entries.map(DestinyWeaponSlot::hotbarIndex))
    }

    @Test
    fun `each weapon column owns exactly nine reserve positions`() {
        assertEquals(9, DestinyWeaponReserves.CAPACITY_PER_SLOT)
        assertEquals(3, DestinyWeaponSlot.entries.size)
    }
}
