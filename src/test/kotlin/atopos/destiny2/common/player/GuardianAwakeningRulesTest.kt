package atopos.destiny2.common.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuardianAwakeningRulesTest {
    @Test
    fun `only mortal survival players can awaken`() {
        assertTrue(GuardianAwakeningRules.canAwaken(GuardianJourneyStage.MORTAL, creative = false, spectator = false))
        assertFalse(GuardianAwakeningRules.canAwaken(GuardianJourneyStage.AWAKENED, creative = false, spectator = false))
        assertFalse(GuardianAwakeningRules.canAwaken(GuardianJourneyStage.MORTAL, creative = true, spectator = false))
        assertFalse(GuardianAwakeningRules.canAwaken(GuardianJourneyStage.MORTAL, creative = false, spectator = true))
    }

    @Test
    fun `unknown saved stage safely falls back to mortal`() {
        assertEquals(GuardianJourneyStage.AWAKENED, GuardianJourneyStage.fromId("awakened"))
        assertEquals(GuardianJourneyStage.ENDGAME, GuardianJourneyStage.fromId("endgame"))
        assertEquals(GuardianJourneyStage.MORTAL, GuardianJourneyStage.fromId("future_or_corrupt_value"))
    }
}
