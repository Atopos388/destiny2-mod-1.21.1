package atopos.destiny2.common.block

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IndustrialMachineRulesTest {
    @Test
    fun `light crystal fits only when the complete charge can be stored`() {
        assertTrue(IndustrialMachineRules.canStoreEnergy(0))
        assertTrue(IndustrialMachineRules.canStoreEnergy(6_000))
        assertFalse(IndustrialMachineRules.canStoreEnergy(6_001))
        assertFalse(IndustrialMachineRules.canStoreEnergy(8_000))
    }

    @Test
    fun `refinery yields reward more valuable feedstock without accepting arbitrary items`() {
        assertEquals(8, IndustrialMachineRules.glimmerYield("minecraft:redstone"))
        assertEquals(16, IndustrialMachineRules.glimmerYield("minecraft:copper_ingot"))
        assertEquals(24, IndustrialMachineRules.glimmerYield("minecraft:amethyst_shard"))
        assertEquals(0, IndustrialMachineRules.glimmerYield("minecraft:dirt"))
    }

    @Test
    fun `memory foundry requires the complete batch`() {
        assertTrue(IndustrialMachineRules.foundryIngredients(1, 2, 1, 32))
        assertFalse(IndustrialMachineRules.foundryIngredients(1, 1, 1, 32))
        assertFalse(IndustrialMachineRules.foundryIngredients(1, 2, 1, 31))
    }
}
