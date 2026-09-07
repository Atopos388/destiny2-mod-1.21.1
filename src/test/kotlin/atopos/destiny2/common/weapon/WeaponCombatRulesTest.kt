package atopos.destiny2.common.weapon

import atopos.destiny2.common.item.GenericGunPackItem
import atopos.destiny2.common.tacz.TaczEntityHitbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeaponCombatRulesTest {
    @Test
    fun `fractional weapon cadence preserves configured rpm without idle drift`() {
        val first = GenericGunPackItem.nextFireTime(0, 0.0, 750)
        val second = GenericGunPackItem.nextFireTime(2, first, 750)
        val third = GenericGunPackItem.nextFireTime(4, second, 750)
        val fourth = GenericGunPackItem.nextFireTime(5, third, 750)

        assertEquals(1.6, first, 0.0001)
        assertEquals(3.2, second, 0.0001)
        assertEquals(4.8, third, 0.0001)
        assertEquals(6.4, fourth, 0.0001)
        assertEquals(101.6, GenericGunPackItem.nextFireTime(100, fourth, 750), 0.0001)
    }

    @Test
    fun `tacz default headshot band is eye height plus or minus quarter block`() {
        assertTrue(TaczEntityHitbox.isHeadshot(1.72, 1.62))
        assertFalse(TaczEntityHitbox.isHeadshot(1.20, 1.62))
    }

    @Test
    fun `primary kills favor special and special kills favor heavy`() {
        assertEquals(DestinyAmmoType.SPECIAL, AmmoDropRules.dropFor(DestinyAmmoType.PRIMARY).type)
        assertEquals(DestinyAmmoType.HEAVY, AmmoDropRules.dropFor(DestinyAmmoType.SPECIAL).type)
        assertTrue(AmmoDropRules.shouldDrop(DestinyAmmoType.PRIMARY, 0.1f))
        assertFalse(AmmoDropRules.shouldDrop(DestinyAmmoType.PRIMARY, 0.5f))
    }

    @Test
    fun `reload only takes what fits and what reserve provides`() {
        assertEquals(6, WeaponAmmoMath.needed(2, 8))
        assertEquals(3, WeaponAmmoMath.loadAmount(2, 8, 3))
        assertEquals(5, WeaponAmmoMath.completedMagazine(2, 8, 3))
        assertEquals(8, WeaponAmmoMath.completedMagazine(7, 8, 99))
        assertEquals(4, WeaponAmmoMath.acceptedReserve(12, 16, 9))
        assertEquals(0, WeaponAmmoMath.acceptedReserve(16, 16, 9))
    }

    @Test
    fun `primary reserve is infinite while special and heavy reserves are per weapon`() {
        val primary = WeaponCombatProfile(DestinyAmmoType.PRIMARY, 1.0f, magazineSize = 8, reloadTicks = 20)
        val special = WeaponCombatProfile(DestinyAmmoType.SPECIAL, 1.0f, magazineSize = 4, reloadTicks = 20)
        val heavy = WeaponCombatProfile(DestinyAmmoType.HEAVY, 1.0f, magazineSize = 3, reloadTicks = 20)

        assertEquals(Int.MAX_VALUE, primary.reserveCapacity)
        assertEquals(16, special.reserveCapacity)
        assertEquals(9, heavy.reserveCapacity)
    }

    @Test
    fun `completed reload blocks another reload for ten ticks`() {
        val state = WeaponAmmoState.State(
            magazine = 4,
            reloadRemaining = 0,
            reloadTotal = 0,
            reloadBlockedUntil = 110L
        )

        assertTrue(WeaponAmmoState.isReloadBlocked(state, 100L))
        assertTrue(WeaponAmmoState.isReloadBlocked(state, 109L))
        assertFalse(WeaponAmmoState.isReloadBlocked(state, 110L))
    }

    @Test
    fun `six remnant stacks reach the precision cap and unlock rapid reload`() {
        assertEquals(1.0f, ForgottenNameExoticRules.precisionBonusMultiplier(0), 0.0001f)
        assertEquals(1.25f, ForgottenNameExoticRules.precisionBonusMultiplier(6), 0.0001f)
        assertEquals(1.25f, ForgottenNameExoticRules.precisionBonusMultiplier(99), 0.0001f)
        assertEquals(32, ForgottenNameExoticRules.reloadTicks(32, 0))
        assertEquals(32, ForgottenNameExoticRules.reloadTicks(32, 5))
        assertEquals(28, ForgottenNameExoticRules.reloadTicks(32, 6))
        assertEquals(25, ForgottenNameExoticRules.aimAssistValue(6))
    }

    @Test
    fun `nameless requires an already full precision kill and mark propagation caps at five`() {
        assertFalse(ForgottenNameExoticRules.shouldTriggerNameless(4, true, true))
        assertFalse(ForgottenNameExoticRules.shouldTriggerNameless(6, false, true))
        assertFalse(ForgottenNameExoticRules.shouldTriggerNameless(6, true, false))
        assertFalse(ForgottenNameExoticRules.shouldTriggerNameless(5, true, true))
        assertTrue(ForgottenNameExoticRules.shouldTriggerNameless(6, true, true))
        assertEquals(5, ForgottenNameExoticRules.remainingMarkSlots(0))
        assertEquals(2, ForgottenNameExoticRules.remainingMarkSlots(3))
        assertEquals(0, ForgottenNameExoticRules.remainingMarkSlots(8))
        assertEquals(1.15f, ForgottenNameExoticRules.markedDamageMultiplier(true), 0.0001f)
    }
}
