package atopos.destiny2.common.weapon

import atopos.destiny2.common.tacz.TaczEntityHitbox
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeaponCombatRulesTest {
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
