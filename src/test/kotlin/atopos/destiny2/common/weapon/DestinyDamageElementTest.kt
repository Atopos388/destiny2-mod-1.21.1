package atopos.destiny2.common.weapon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DestinyDamageElementTest {
    @Test
    fun `serialized names are case insensitive and unknown values are kinetic`() {
        assertEquals(DestinyDamageElement.SOLAR, DestinyDamageElement.fromSerializedName("solar"))
        assertEquals(DestinyDamageElement.ARC, DestinyDamageElement.fromSerializedName("ArC"))
        assertEquals(DestinyDamageElement.KINETIC, DestinyDamageElement.fromSerializedName("unknown"))
        assertEquals(DestinyDamageElement.KINETIC, DestinyDamageElement.fromSerializedName(null))
    }
}
