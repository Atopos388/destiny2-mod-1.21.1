package atopos.destiny2.common.aspect

import atopos.destiny2.common.player.DestinyStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolarWarlockFragmentRulesTest {
    @Test
    fun `catalog contains all sixteen official fragments and stat modifiers`() {
        assertEquals(16, SolarWarlockFragmentRules.ALL_FRAGMENT_IDS.size)
        assertEquals(
            SolarWarlockFragmentRules.ALL_FRAGMENT_IDS,
            SolarWarlockFragmentRules.OFFICIAL_STATIC_STAT_BONUSES.keys
        )

        val expected = mapOf(
            SolarWarlockFragmentRules.EMBER_OF_ASHES to DestinyStats(0, 0, 0, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_BEAMS to DestinyStats(0, 0, 0, 0, 10, 0),
            SolarWarlockFragmentRules.EMBER_OF_BENEVOLENCE to DestinyStats(0, 0, 0, -10, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_BLISTERING to DestinyStats(0, 0, 0, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_CHAR to DestinyStats(0, 0, 0, 10, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_COMBUSTION to DestinyStats(0, 0, 0, 0, 0, 10),
            SolarWarlockFragmentRules.EMBER_OF_EMPYREAN to DestinyStats(0, -10, 0, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_ERUPTION to DestinyStats(0, 0, 0, 0, 0, 10),
            SolarWarlockFragmentRules.EMBER_OF_MERCY to DestinyStats(0, 10, 0, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_RESOLVE to DestinyStats(0, 0, 0, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_SEARING to DestinyStats(0, 0, 10, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_SINGEING to DestinyStats(0, 0, 0, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_SOLACE to DestinyStats(0, 0, 0, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_TEMPERING to DestinyStats(0, 0, -10, 0, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_TORCHES to DestinyStats(0, 0, 0, -10, 0, 0),
            SolarWarlockFragmentRules.EMBER_OF_WONDER to DestinyStats(0, 10, 0, 0, 0, 0)
        )
        assertEquals(expected, SolarWarlockFragmentRules.OFFICIAL_STATIC_STAT_BONUSES)

        val bonuses = SolarWarlockFragmentRules.staticStatBonuses(
            setOf(
                SolarWarlockFragmentRules.EMBER_OF_BEAMS,
                SolarWarlockFragmentRules.EMBER_OF_BENEVOLENCE,
                SolarWarlockFragmentRules.EMBER_OF_CHAR,
                SolarWarlockFragmentRules.EMBER_OF_COMBUSTION,
                SolarWarlockFragmentRules.EMBER_OF_EMPYREAN,
                SolarWarlockFragmentRules.EMBER_OF_ERUPTION,
                SolarWarlockFragmentRules.EMBER_OF_MERCY,
                SolarWarlockFragmentRules.EMBER_OF_SEARING,
                SolarWarlockFragmentRules.EMBER_OF_TEMPERING,
                SolarWarlockFragmentRules.EMBER_OF_TORCHES,
                SolarWarlockFragmentRules.EMBER_OF_WONDER
            )
        )
        assertEquals(0, bonuses.weapons)
        assertEquals(10, bonuses.health)
        assertEquals(0, bonuses.classAbility)
        assertEquals(-10, bonuses.grenade)
        assertEquals(10, bonuses.superStat)
        assertEquals(20, bonuses.melee)
    }

    @Test
    fun `ashes uses source specific scorch bonuses instead of a global multiplier`() {
        assertEquals(10, SolarWarlockFragmentRules.scorchStacks(SolarScorchProfile(10, 5), hasAshes = false))
        assertEquals(15, SolarWarlockFragmentRules.scorchStacks(SolarScorchProfile(10, 5), hasAshes = true))
        assertEquals(40, SolarWarlockFragmentRules.scorchStacks(SolarScorchProfile(30, 10), hasAshes = true))
    }

    @Test
    fun `beams exclusively affects solar super projectiles`() {
        assertTrue(
            SolarWarlockFragmentRules.beamsAffectsProjectile(
                hasBeams = true,
                SolarFragmentCombatSource.SOLAR_SUPER_PROJECTILE
            )
        )
        assertFalse(SolarWarlockFragmentRules.beamsAffectsProjectile(true, SolarFragmentCombatSource.SOLAR_SUPER))
        assertFalse(SolarWarlockFragmentRules.beamsAffectsProjectile(false, SolarFragmentCombatSource.SOLAR_SUPER_PROJECTILE))

        val baseline = SolarWarlockFragmentRules.beamsTrackingProfile(false)
        val enhanced = SolarWarlockFragmentRules.beamsTrackingProfile(true)
        assertTrue(enhanced.acquisitionRange > baseline.acquisitionRange)
        assertTrue(enhanced.minimumForwardDot < baseline.minimumForwardDot)
        assertTrue(enhanced.steeringStrength > baseline.steeringStrength)
    }

    @Test
    fun `benevolence requires applying a supported solar buff to another ally`() {
        assertTrue(SolarWarlockFragmentRules.benevolenceTriggers(true, SolarFragmentBuff.CURE, true, false))
        assertTrue(SolarWarlockFragmentRules.benevolenceTriggers(true, SolarFragmentBuff.RADIANT, true, false))
        assertFalse(SolarWarlockFragmentRules.benevolenceTriggers(true, SolarFragmentBuff.OTHER, true, false))
        assertFalse(SolarWarlockFragmentRules.benevolenceTriggers(true, SolarFragmentBuff.RESTORATION, true, true))
        assertEquals(40, SolarWarlockFragmentRules.benevolenceDuration(hasSolace = false))
        assertEquals(60, SolarWarlockFragmentRules.benevolenceDuration(hasSolace = true))
    }

    @Test
    fun `blistering requires an equipped ignition final blow`() {
        assertTrue(SolarWarlockFragmentRules.blisteringTriggers(true, ignitionFinalBlow = true))
        assertFalse(SolarWarlockFragmentRules.blisteringTriggers(true, ignitionFinalBlow = false))
        assertFalse(SolarWarlockFragmentRules.blisteringTriggers(false, ignitionFinalBlow = true))
    }

    @Test
    fun `char only spreads scorch while equipped`() {
        assertNull(SolarWarlockFragmentRules.charSpreadScorchStacks(hasChar = false, hasAshes = true))
        assertEquals(40, SolarWarlockFragmentRules.charSpreadScorchStacks(hasChar = true, hasAshes = false))
        assertEquals(60, SolarWarlockFragmentRules.charSpreadScorchStacks(hasChar = true, hasAshes = true))
    }

    @Test
    fun `combustion requires a solar super final blow`() {
        assertTrue(SolarWarlockFragmentRules.combustionTriggers(true, SolarFragmentCombatSource.SOLAR_SUPER, true))
        assertTrue(SolarWarlockFragmentRules.combustionTriggers(true, SolarFragmentCombatSource.SOLAR_SUPER_PROJECTILE, true))
        assertFalse(SolarWarlockFragmentRules.combustionTriggers(true, SolarFragmentCombatSource.SOLAR_WEAPON, true))
        assertFalse(SolarWarlockFragmentRules.combustionTriggers(true, SolarFragmentCombatSource.SOLAR_SUPER, false))
    }

    @Test
    fun `empyrean extends only an existing buff after solar weapon or ability final blows`() {
        assertTrue(
            SolarWarlockFragmentRules.empyreanTriggers(
                true,
                SolarFragmentCombatSource.SOLAR_WEAPON,
                finalBlow = true,
                hasRadiantOrRestoration = true
            )
        )
        assertFalse(SolarWarlockFragmentRules.empyreanTriggers(true, SolarFragmentCombatSource.OTHER, true, true))
        assertFalse(SolarWarlockFragmentRules.empyreanTriggers(true, SolarFragmentCombatSource.SOLAR_ABILITY, true, false))
        assertEquals(120, SolarWarlockFragmentRules.empyreanExtendedDuration(100, SolarCombatantTier.MINOR))
        assertEquals(300, SolarWarlockFragmentRules.empyreanExtendedDuration(280, SolarCombatantTier.BOSS))
    }

    @Test
    fun `eruption expands the authoritative ignition radius`() {
        assertEquals(4.0, SolarWarlockFragmentRules.ignitionRadius(4.0, hasEruption = false))
        assertEquals(6.0, SolarWarlockFragmentRules.ignitionRadius(4.0, hasEruption = true))
        assertEquals(0.0, SolarWarlockFragmentRules.ignitionRadius(-1.0, hasEruption = true))
    }

    @Test
    fun `mercy distinguishes ally revives and firesprite pickups`() {
        assertTrue(SolarWarlockFragmentRules.mercyTriggersOnRevive(hasMercy = true, revivedAlly = true))
        assertFalse(SolarWarlockFragmentRules.mercyTriggersOnRevive(hasMercy = true, revivedAlly = false))
        assertTrue(SolarWarlockFragmentRules.mercyTriggersOnFirespritePickup(true, pickedUpFiresprite = true))
        assertFalse(SolarWarlockFragmentRules.mercyTriggersOnFirespritePickup(false, pickedUpFiresprite = true))
    }

    @Test
    fun `resolve exclusively accepts solar grenade final blows`() {
        assertTrue(SolarWarlockFragmentRules.resolveTriggers(true, SolarFragmentCombatSource.SOLAR_GRENADE, true))
        assertFalse(SolarWarlockFragmentRules.resolveTriggers(true, SolarFragmentCombatSource.SOLAR_ABILITY, true))
        assertFalse(SolarWarlockFragmentRules.resolveTriggers(true, SolarFragmentCombatSource.SOLAR_GRENADE, false))
    }

    @Test
    fun `searing requires a final blow against an already scorched target`() {
        assertTrue(SolarWarlockFragmentRules.searingTriggers(true, targetWasScorched = true, finalBlow = true))
        assertFalse(SolarWarlockFragmentRules.searingTriggers(true, targetWasScorched = false, finalBlow = true))
        assertFalse(SolarWarlockFragmentRules.searingTriggers(true, targetWasScorched = true, finalBlow = false))
    }

    @Test
    fun `singeing requires actually applying scorch to a combatant`() {
        assertTrue(SolarWarlockFragmentRules.singeingTriggers(true, appliedScorchToCombatant = true))
        assertFalse(SolarWarlockFragmentRules.singeingTriggers(true, appliedScorchToCombatant = false))
        assertFalse(SolarWarlockFragmentRules.singeingTriggers(false, appliedScorchToCombatant = true))
    }

    @Test
    fun `solace extends radiant and restoration but never cure`() {
        assertEquals(240, SolarWarlockFragmentRules.solarBuffDuration(160, SolarFragmentBuff.RADIANT, true))
        assertEquals(90, SolarWarlockFragmentRules.solarBuffDuration(60, SolarFragmentBuff.RESTORATION, true))
        assertEquals(60, SolarWarlockFragmentRules.solarBuffDuration(60, SolarFragmentBuff.CURE, true))
        assertEquals(160, SolarWarlockFragmentRules.solarBuffDuration(160, SolarFragmentBuff.RADIANT, false))
    }

    @Test
    fun `tempering requires solar weapon final blows and caps at three stacks`() {
        assertTrue(SolarWarlockFragmentRules.temperingTriggers(true, SolarFragmentCombatSource.SOLAR_WEAPON, true))
        assertFalse(SolarWarlockFragmentRules.temperingTriggers(true, SolarFragmentCombatSource.SOLAR_ABILITY, true))
        assertEquals(1, SolarWarlockFragmentRules.temperingNextStacks(0, triggered = true))
        assertEquals(3, SolarWarlockFragmentRules.temperingNextStacks(3, triggered = true))
        assertEquals(15, SolarWarlockFragmentRules.temperingHealthBonus(99))
    }

    @Test
    fun `torches accepts powered melee only and uses official eight second duration`() {
        assertTrue(
            SolarWarlockFragmentRules.torchesTriggers(
                hasTorches = true,
                SolarFragmentCombatSource.SOLAR_POWERED_MELEE,
                targetIsCombatant = true
            )
        )
        assertFalse(SolarWarlockFragmentRules.torchesTriggers(true, SolarFragmentCombatSource.UNCHARGED_MELEE, true))
        assertFalse(SolarWarlockFragmentRules.torchesTriggers(true, SolarFragmentCombatSource.SOLAR_POWERED_MELEE, false))
        assertEquals(160, SolarWarlockFragmentRules.torchesDuration(hasSolace = false))
        assertEquals(240, SolarWarlockFragmentRules.torchesDuration(hasSolace = true))
    }

    @Test
    fun `wonder requires rapid ignition multikills and respects its calibration cooldown`() {
        val first = SolarWarlockFragmentRules.recordWonderIgnitionFinalBlow(
            SolarIgnitionMultikillState(),
            currentTick = 100,
            hasWonder = true
        )
        assertFalse(first.shouldGenerateOrb)
        assertEquals(1, first.state.killCount)

        val second = SolarWarlockFragmentRules.recordWonderIgnitionFinalBlow(
            first.state,
            currentTick = 150,
            hasWonder = true
        )
        assertTrue(second.shouldGenerateOrb)
        assertEquals(350, second.state.nextOrbEligibleTick)

        val coolingDown = SolarWarlockFragmentRules.recordWonderIgnitionFinalBlow(
            second.state,
            currentTick = 200,
            hasWonder = true
        )
        assertFalse(coolingDown.shouldGenerateOrb)
        assertEquals(0, coolingDown.state.killCount)

        val outsideWindowFirst = SolarWarlockFragmentRules.recordWonderIgnitionFinalBlow(
            SolarIgnitionMultikillState(),
            currentTick = 400,
            hasWonder = true
        )
        val outsideWindowSecond = SolarWarlockFragmentRules.recordWonderIgnitionFinalBlow(
            outsideWindowFirst.state,
            currentTick = 451,
            hasWonder = true
        )
        assertFalse(outsideWindowSecond.shouldGenerateOrb)
        assertEquals(1, outsideWindowSecond.state.killCount)
        assertEquals(451, outsideWindowSecond.state.windowStartedAtTick)

        assertFalse(SolarWarlockFragmentRules.wonderTracksFinalBlow(true, ignitionFinalBlow = false))
        assertTrue(SolarWarlockFragmentRules.wonderTracksFinalBlow(true, ignitionFinalBlow = true))
    }
}
