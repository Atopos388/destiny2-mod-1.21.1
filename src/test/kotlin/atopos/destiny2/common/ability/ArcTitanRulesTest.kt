package atopos.destiny2.common.ability

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArcTitanRulesTest {
    @Test
    fun `thunderclap impact waits for authored fist contact`() {
        assertFalse(ArcTitanRules.thunderclapImpactReady(3))
        assertTrue(ArcTitanRules.thunderclapImpactReady(4))
        assertTrue(ArcTitanRules.thunderclapImpactReady(5))
    }

    @Test
    fun `thunderclap charge is server clamped`() {
        assertEquals(0.0, ArcTitanRules.chargeRatio(-5, 40), 0.0001)
        assertEquals(0.5, ArcTitanRules.chargeRatio(20, 40), 0.0001)
        assertEquals(1.0, ArcTitanRules.chargeRatio(80, 40), 0.0001)
    }

    @Test
    fun `rare thunderclap is one in one thousand and multiplies server damage by five`() {
        assertEquals(0.001, ArcTitanRules.THUNDERCLAP_RARE_IMPACT_CHANCE, 0.0)
        assertTrue(ArcTitanRules.isRareThunderclapImpact(0.0))
        assertTrue(ArcTitanRules.isRareThunderclapImpact(0.000999999))
        assertFalse(ArcTitanRules.isRareThunderclapImpact(0.001))
        assertFalse(ArcTitanRules.isRareThunderclapImpact(1.0))
        assertEquals(45.0f, ArcTitanRules.thunderclapDamage(9.0f, true), 0.0001f)
        assertEquals(9.0f, ArcTitanRules.thunderclapDamage(9.0f, false), 0.0001f)
    }

    @Test
    fun `thruster respects four directional inputs`() {
        val forward = Vec3(0.0, 0.0, 1.0)
        assertEquals(1.0, ArcTitanRules.directionForInput(forward, 0).z, 0.0001)
        assertEquals(-1.0, ArcTitanRules.directionForInput(forward, 1).z, 0.0001)
        assertEquals(1.0, ArcTitanRules.directionForInput(forward, 2).x, 0.0001)
        assertEquals(-1.0, ArcTitanRules.directionForInput(forward, 3).x, 0.0001)
    }

    @Test
    fun `thunderclap only accepts targets inside its forward cone`() {
        val origin = Vec3.ZERO
        val forward = Vec3(0.0, 0.0, 1.0)
        assertTrue(ArcTitanRules.insideThunderclapCone(origin, forward, Vec3(1.0, 0.0, 4.0), 6.0))
        assertFalse(ArcTitanRules.insideThunderclapCone(origin, forward, Vec3(0.0, 0.0, -3.0), 6.0))
        assertFalse(ArcTitanRules.insideThunderclapCone(origin, forward, Vec3(0.0, 0.0, 7.0), 6.0))
    }

    @Test
    fun `thundercrash keeps meaningful edge damage and rewards direct hits`() {
        val center = ArcTitanRules.thundercrashDamage(0.0, 6.5)
        val edge = ArcTitanRules.thundercrashDamage(6.5, 6.5)
        assertEquals(52.0f, center, 0.0001f)
        assertEquals(18.0f, edge, 0.0001f)
        assertTrue(center > edge)
    }
}
