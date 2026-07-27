package atopos.destiny2.common.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FallenCaptainCombatRulesTest {
    @Test
    fun `melee damage uses requested heart values`() {
        assertEquals(6.0f, FallenCaptainCombatRules.LEFT_MELEE_DAMAGE)
        assertEquals(7.0f, FallenCaptainCombatRules.RIGHT_MELEE_DAMAGE)
    }

    @Test
    fun `shotgun has five two-heart close pellets`() {
        assertEquals(5, FallenCaptainCombatRules.PELLET_COUNT)
        assertEquals(4.0f, FallenCaptainCombatRules.pelletDamage(4.0))
    }

    @Test
    fun `pellet damage falls with distance`() {
        val close = FallenCaptainCombatRules.pelletDamage(3.0)
        val medium = FallenCaptainCombatRules.pelletDamage(9.0)
        val far = FallenCaptainCombatRules.pelletDamage(17.0)
        assertTrue(close > medium)
        assertTrue(medium > far)
    }

    @Test
    fun `captain retreats at thirty five percent health`() {
        assertTrue(FallenCaptainCombatRules.shouldRetreat(70.0f, 200.0f))
        assertFalse(FallenCaptainCombatRules.shouldRetreat(71.0f, 200.0f))
    }

    @Test
    fun `melee sector includes front and rejects side and rear`() {
        assertTrue(FallenCaptainCombatRules.isInsideMeleeSector(0.0, 1.0, 0.0, 3.0, 2.0))
        assertFalse(FallenCaptainCombatRules.isInsideMeleeSector(0.0, 1.0, 3.0, 0.0, 2.0))
        assertFalse(FallenCaptainCombatRules.isInsideMeleeSector(0.0, 1.0, 0.0, -3.0, 2.0))
        assertFalse(FallenCaptainCombatRules.isInsideMeleeSector(0.0, 1.0, 0.0, 4.0, 3.0))
    }

    @Test
    fun `walk animation speed follows horizontal movement with safe limits`() {
        assertEquals(1.0, FallenCaptainCombatRules.walkAnimationSpeed(0.18), 0.001)
        assertEquals(0.2, FallenCaptainCombatRules.walkAnimationSpeed(0.01), 0.001)
        assertEquals(1.85, FallenCaptainCombatRules.walkAnimationSpeed(1.0), 0.001)
    }

    @Test
    fun `melee can only start inside edge range with sight and vertical overlap`() {
        assertTrue(FallenCaptainCombatRules.canStartMelee(2.25, 1.5, true))
        assertFalse(FallenCaptainCombatRules.canStartMelee(2.251, 1.5, true))
        assertFalse(FallenCaptainCombatRules.canStartMelee(2.0, 1.501, true))
        assertFalse(FallenCaptainCombatRules.canStartMelee(2.0, 1.0, false))
    }
}
