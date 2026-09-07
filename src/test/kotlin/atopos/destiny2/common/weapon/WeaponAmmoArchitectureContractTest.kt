package atopos.destiny2.common.weapon

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeaponAmmoArchitectureContractTest {
    private val root = Path.of("src", "main")

    @Test
    fun `ammo bricks feed weapon stacks instead of vanilla inventory`() {
        val pickup = Files.readString(root.resolve("kotlin/atopos/destiny2/common/weapon/WeaponAmmoPickupSystem.kt"))
        val state = Files.readString(root.resolve("kotlin/atopos/destiny2/common/weapon/WeaponAmmoState.kt"))

        assertTrue(pickup.contains("WeaponAmmoState.addReserve"))
        assertTrue(pickup.contains("return true"))
        assertTrue(state.contains("private const val RESERVE = \"reserve\""))
        assertFalse(state.contains("player.inventory.items + player.inventory.offhand"))
    }

    @Test
    fun `internal ammo bricks are not exposed in the creative tab`() {
        val items = Files.readString(root.resolve("kotlin/atopos/destiny2/common/item/DestinyItems.kt"))
        val creativeBody = items.substringAfter("fun creativeTabItems()").substringBefore("fun ammoItem")

        assertFalse(creativeBody.contains("add(PRIMARY_AMMO)"))
        assertFalse(creativeBody.contains("add(SPECIAL_AMMO)"))
        assertFalse(creativeBody.contains("add(HEAVY_AMMO)"))
    }
}
