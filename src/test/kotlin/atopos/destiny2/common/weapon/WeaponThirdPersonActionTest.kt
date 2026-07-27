package atopos.destiny2.common.weapon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WeaponThirdPersonActionTest {
    @Test
    fun `network action ordinal order remains stable`() {
        assertEquals(
            listOf("SHOOT", "RELOAD", "DRAW", "PUT_AWAY", "INSPECT"),
            WeaponThirdPersonAction.entries.map { it.name }
        )
    }
}
