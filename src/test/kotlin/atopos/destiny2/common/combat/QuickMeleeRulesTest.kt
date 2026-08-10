package atopos.destiny2.common.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos

class QuickMeleeRulesTest {
    @Test
    fun `hunter melee audio keeps twenty percent source volume`() {
        assertEquals(0.2f, QuickMeleeRules.HUNTER_SOUND_VOLUME)
    }

    @Test
    fun `assist accepts targets inside range and cone`() {
        assertNotNull(QuickMeleeRules.candidateScore(2.0, cos(Math.toRadians(30.0))))
        assertNotNull(QuickMeleeRules.candidateScore(3.5, cos(Math.toRadians(20.0))))
        assertNull(QuickMeleeRules.candidateScore(3.8, 1.0))
        assertNull(QuickMeleeRules.candidateScore(3.5, cos(Math.toRadians(25.0))))
    }

    @Test
    fun `search angle narrows toward edge of lunge range`() {
        assertTrue(QuickMeleeRules.assistHalfAngle(1.5) > QuickMeleeRules.assistHalfAngle(3.5))
    }

    @Test
    fun `assist favors centered targets without ignoring distance`() {
        val centered = QuickMeleeRules.candidateScore(3.0, 1.0)!!
        val offCenter = QuickMeleeRules.candidateScore(2.8, cos(Math.toRadians(20.0)))!!
        assertTrue(centered > offCenter)
    }

    @Test
    fun `lunge starts outside contact range and respects its cap`() {
        assertEquals(0.0, QuickMeleeRules.lungeSpeed(1.25), 0.0001)
        assertTrue(QuickMeleeRules.lungeSpeed(1.6) > 0.0)
        assertEquals(
            QuickMeleeRules.MAX_LUNGE_SPEED,
            QuickMeleeRules.lungeSpeed(10.0),
            0.0001
        )
    }
}
