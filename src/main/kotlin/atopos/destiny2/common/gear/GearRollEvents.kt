package atopos.destiny2.common.gear

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.player.GuardianPowerRuntime
import atopos.destiny2.common.player.GuardianPowerSystem
import atopos.destiny2.common.network.DestinyNetworking

object GearRollEvents {
    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            server.playerList.players.forEach { player ->
                // Roll migration is a background safeguard. Gameplay entry points
                // already ensure their active stack, so scanning every slot every
                // tick only repeats expensive CustomData copies.
                if (player.tickCount % INVENTORY_SCAN_INTERVAL_TICKS == 0) {
                    val data = PlayerDestinyDataApi.get(player)
                    val previousPower = GuardianPowerRuntime.current(player)
                    val acquisitionPower = GuardianPowerSystem.normalDropPower(
                        data.journeyStage,
                        previousPower.highestAvailable,
                        kotlin.random.Random.Default.nextInt(-2, 3)
                    )
                    ensureInventory(player.inventory, acquisitionPower)
                    ensure(data.classItem, acquisitionPower)
                    GuardianPowerRuntime.refreshIfChanged(player)?.let {
                        DestinyNetworking.syncNavigationState(player, it)
                    }
                }
                GearPerkRuntime.tick(player)
                ArmorModRuntime.tick(player)
            }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            GearPerkRuntime.clear(handler.player.uuid)
            ArmorModRuntime.clear(handler.player.uuid)
            GuardianPowerRuntime.clear(handler.player.uuid)
        }
    }

    private fun ensureInventory(inventory: Inventory, acquisitionPower: Int) {
        inventory.items.forEach { ensure(it, acquisitionPower) }
        inventory.armor.forEach { ensure(it, acquisitionPower) }
        inventory.offhand.forEach { ensure(it, acquisitionPower) }
    }

    private fun ensure(stack: ItemStack, acquisitionPower: Int) {
        if (!stack.isEmpty) {
            GearRolls.ensureRoll(stack, powerOverride = acquisitionPower)
        }
    }

    private const val INVENTORY_SCAN_INTERVAL_TICKS = 10
}
