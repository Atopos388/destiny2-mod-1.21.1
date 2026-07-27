package atopos.destiny2.common.gear

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArmorModRegistryTest {
    @Test
    fun `every stat has one small and one major mod`() {
        val mods = ArmorModRegistry.all().filter { it.kind == ArmorModKind.STAT }
        assertEquals(12, mods.size)
        assertEquals(6, mods.count { it.statBonus == 5 && it.energyCost == 1 })
        assertEquals(6, mods.count { it.statBonus == 10 && it.energyCost == 3 })
        assertEquals(6, mods.map { it.stat }.distinct().size)
    }

    @Test
    fun `each mod contributes only its declared stat`() {
        ArmorModRegistry.all().filter { it.kind == ArmorModKind.STAT }.forEach { mod ->
            val values = listOf(
                mod.bonusStats.weapons,
                mod.bonusStats.health,
                mod.bonusStats.classAbility,
                mod.bonusStats.grenade,
                mod.bonusStats.superStat,
                mod.bonusStats.melee
            )
            assertEquals(mod.statBonus, values.sum())
            assertEquals(1, values.count { it > 0 })
            assertTrue(mod.energyCost <= GearRolls.ARMOR_ENERGY_CAPACITY)
        }
    }

    @Test
    fun `four sockets expose stat first and part specific mods after it`() {
        DestinyArmorSlot.entries.forEach { slot ->
            assertEquals(12, ArmorModRegistry.available(slot, 0).size)
            assertTrue(ArmorModRegistry.available(slot, 1).isNotEmpty())
            assertTrue(ArmorModRegistry.available(slot, 1).all { it.kind == ArmorModKind.SLOT && slot in it.allowedSlots })
        }
        assertTrue(ArmorModRegistry.all().size > 100)
    }

    @Test
    fun `boot adaptation does not expose gauntlet naming`() {
        ArmorModRegistry.available(DestinyArmorSlot.BOOTS, 1).forEach { mod ->
            assertTrue("臂铠" !in mod.displayName && "手臂" !in mod.displayName)
            assertTrue("臂铠" !in mod.description && "手臂" !in mod.description)
        }
    }
}
