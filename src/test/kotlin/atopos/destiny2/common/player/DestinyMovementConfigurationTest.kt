package atopos.destiny2.common.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DestinyMovementConfigurationTest {
    @Test
    fun `subclasses receive their implemented movement defaults`() {
        assertEquals(
            GuardianJumpRules.WARLOCK_VECTOR_GLIDE_ID,
            DestinySubclassConfigRegistry.defaultFor(DestinySubclassType.SOLAR_WARLOCK).selectedMovementId
        )
        assertEquals(
            GuardianJumpRules.HUNTER_TRIPLE_JUMP_ID,
            DestinySubclassConfigRegistry.defaultFor(DestinySubclassType.VOID_HUNTER).selectedMovementId
        )
    }

    @Test
    fun `movement selection survives player data serialization`() {
        val original = DestinySubclassConfigRegistry.defaultFor(DestinySubclassType.VOID_HUNTER)
        val restored = PlayerSubclassConfiguration.fromTag(original.toTag())
        assertEquals(GuardianJumpRules.HUNTER_TRIPLE_JUMP_ID, restored.selectedMovementId)
    }

    @Test
    fun `server movement validation rejects an option from another subclass`() {
        val config = DestinySubclassConfigRegistry.defaultFor(DestinySubclassType.SOLAR_WARLOCK)
        assertFalse(
            DestinySubclassConfigRegistry.setMovement(
                DestinySubclassType.SOLAR_WARLOCK,
                config,
                GuardianJumpRules.HUNTER_TRIPLE_JUMP_ID
            )
        )
        assertTrue(
            DestinySubclassConfigRegistry.setMovement(
                DestinySubclassType.SOLAR_WARLOCK,
                config,
                GuardianJumpRules.WARLOCK_VECTOR_GLIDE_ID
            )
        )
    }
}
