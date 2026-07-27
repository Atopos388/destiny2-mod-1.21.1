package atopos.destiny2.common.action

import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DestinyActionContractTest {
    @Test
    fun `registered action ids are unique and durations are usable`() {
        val definitions = DestinyActionRegistry.definitions()
        assertEquals(definitions.size, definitions.map { it.id }.toSet().size)
        assertTrue(definitions.all { it.durationTicks > 0 && it.durationMs > 0L })
        assertTrue(definitions.all { it.blendInTicks >= 0 && it.blendOutTicks >= 0 })
        assertTrue(definitions.all { it.blendInTicks + it.blendOutTicks < it.durationTicks })
    }

    @Test
    fun `only abilities with active animations resolve through the action registry`() {
        val healingRift = DestinyActionRegistry.definitionForAbility(id("solar_warlock_healing_rift"))
        val gamblerDodge = DestinyActionRegistry.definitionForAbility(id("void_hunter_gambler_dodge"))

        assertNotNull(healingRift)
        assertNull(gamblerDodge)
        assertEquals(DestinyActionBackend.PLAYER_LAYER, healingRift?.backend)
    }

    private fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
}
