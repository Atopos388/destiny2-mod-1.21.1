package atopos.destiny2.common.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuardianPowerSystemTest {
    @Test
    fun `mortal players do not participate in power combat`() {
        val snapshot = GuardianPowerSystem.calculate(false, List(6) { 200 }, List(6) { 200 }, 190)
        assertEquals(0, snapshot.current)
        assertEquals(0, snapshot.highestAvailable)
        assertEquals(1.0f, snapshot.outgoingMultiplier)
        assertEquals(1.0f, snapshot.incomingMultiplier)
        assertFalse(snapshot.suppressed)
    }

    @Test
    fun `equipped and highest available loadouts are calculated independently`() {
        val snapshot = GuardianPowerSystem.calculate(
            awakened = true,
            equippedPower = listOf(175, 150, 125, 100, 100, 100),
            highestSlotPower = listOf(200, 175, 150, 125, 100, 100),
            activityRecommended = 160
        )
        assertEquals(125, snapshot.current)
        assertEquals(142, snapshot.highestAvailable)
        assertEquals(35, snapshot.deficit)
        assertTrue(snapshot.suppressed)
    }

    @Test
    fun `empty equipment slots use the universal starting power not the journey stage`() {
        val snapshot = GuardianPowerSystem.calculate(
            awakened = true,
            equippedPower = listOf(175, 0, 0, 0, 0, 0),
            highestSlotPower = listOf(175, 0, 0, 0, 0, 0),
            activityRecommended = 100
        )
        assertEquals(113, snapshot.current)
        assertEquals(113, snapshot.highestAvailable)
        assertFalse(snapshot.suppressed)
    }

    @Test
    fun `activity delta controls suppression without an overlevel damage bonus`() {
        val under = GuardianPowerSystem.calculate(true, List(6) { 140 }, List(6) { 140 }, 160)
        assertEquals(0.87f, under.outgoingMultiplier, 0.0001f)
        assertEquals(1.16f, under.incomingMultiplier, 0.0001f)
        assertEquals(13, under.suppressionPercent)

        val over = GuardianPowerSystem.calculate(true, List(6) { 180 }, List(6) { 180 }, 160)
        assertEquals(0, over.deficit)
        assertEquals(1.0f, over.outgoingMultiplier)
        assertEquals(1.0f, over.incomingMultiplier)
    }

    @Test
    fun `normal drops follow highest available power inside the stage range`() {
        assertEquals(100, GuardianPowerSystem.normalDropPower(GuardianJourneyStage.AWAKENED, 100, -2))
        assertEquals(105, GuardianPowerSystem.normalDropPower(GuardianJourneyStage.AWAKENED, 103, 2))
        assertEquals(120, GuardianPowerSystem.normalDropPower(GuardianJourneyStage.AWAKENED, 140, 2))
        assertEquals(165, GuardianPowerSystem.normalDropPower(GuardianJourneyStage.DIMENSION_BREAKTHROUGH, 150, -2))
        assertEquals(190, GuardianPowerSystem.normalDropPower(GuardianJourneyStage.DIMENSION_BREAKTHROUGH, 200, 2))
    }

    @Test
    fun `powerful and pinnacle rewards obey both system and journey caps`() {
        assertEquals(
            155,
            GuardianPowerSystem.rewardPower(GuardianJourneyStage.REARMED, 150, GuardianRewardTier.POWERFUL, 5)
        )
        assertEquals(
            180,
            GuardianPowerSystem.rewardPower(GuardianJourneyStage.ENDGAME, 178, GuardianRewardTier.POWERFUL, 5)
        )
        assertEquals(
            182,
            GuardianPowerSystem.rewardPower(GuardianJourneyStage.ENDGAME, 180, GuardianRewardTier.PINNACLE, 2)
        )
        assertEquals(
            190,
            GuardianPowerSystem.rewardPower(GuardianJourneyStage.DIMENSION_BREAKTHROUGH, 190, GuardianRewardTier.PINNACLE, 2)
        )
    }
}
