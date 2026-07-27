package atopos.destiny2.common.gear

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class GearArmorStatsTest {
    @Test
    fun `armor rolls keep tier totals and exactly three active stats`() {
        val ranges = mapOf(1 to 24..30, 2 to 32..38, 3 to 40..46, 4 to 48..54, 5 to 56..62)
        ranges.forEach { (tier, expectedRange) ->
            repeat(64) { seed ->
                val (_, stats) = GearRolls.generateArmorStatsForTier(tier, Random(seed))
                val values = listOf(stats.weapons, stats.health, stats.classAbility, stats.grenade, stats.superStat, stats.melee)
                assertTrue(values.sum() in expectedRange)
                assertEquals(3, values.count { it > 0 })
            }
        }
    }
}
