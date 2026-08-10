package atopos.destiny2.common.player

import atopos.destiny2.common.aspect.ArcTitanFragmentRules
import atopos.destiny2.common.aspect.SolarWarlockFragmentRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DestinyStatsResolverFragmentTest {
    @Test
    fun `solar fragment stats are applied only for solar warlock`() {
        val selected = listOf(
            SolarWarlockFragmentRules.EMBER_OF_BEAMS,
            SolarWarlockFragmentRules.EMBER_OF_TORCHES,
            SolarWarlockFragmentRules.EMBER_OF_WONDER
        )

        assertEquals(
            DestinyStats(0, 10, 0, -10, 10, 0),
            DestinyStatsResolver.subclassFragmentStatBonuses(DestinySubclassType.SOLAR_WARLOCK, selected)
        )
        assertEquals(
            DestinyStats(0, 0, 0, 0, 0, 0),
            DestinyStatsResolver.subclassFragmentStatBonuses(DestinySubclassType.ARC_TITAN, selected)
        )
    }

    @Test
    fun `arc fragment stats are applied only for arc titan`() {
        val selected = listOf(
            ArcTitanFragmentRules.SPARK_OF_FOCUS,
            ArcTitanFragmentRules.SPARK_OF_FEEDBACK,
            ArcTitanFragmentRules.SPARK_OF_VOLTS,
            ArcTitanFragmentRules.SPARK_OF_BRILLIANCE,
            ArcTitanFragmentRules.SPARK_OF_SHOCK
        )

        assertEquals(
            DestinyStats(-10, 0, 0, -10, 10, 0),
            DestinyStatsResolver.subclassFragmentStatBonuses(DestinySubclassType.ARC_TITAN, selected)
        )
        assertEquals(
            DestinyStats(0, 0, 0, 0, 0, 0),
            DestinyStatsResolver.subclassFragmentStatBonuses(DestinySubclassType.SOLAR_WARLOCK, selected)
        )
    }
}
