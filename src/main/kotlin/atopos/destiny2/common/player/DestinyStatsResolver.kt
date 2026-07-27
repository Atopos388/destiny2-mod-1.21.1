package atopos.destiny2.common.player

import atopos.destiny2.common.gear.GearRolls
import atopos.destiny2.common.gear.ArmorModRuntime
import atopos.destiny2.common.item.DestinyClassItem
import net.minecraft.server.level.ServerPlayer

/** One aggregation boundary for base, armor and future mod/fragment contributions. */
object DestinyStatsResolver {
    fun resolve(player: ServerPlayer): DestinyStats {
        val data = PlayerDestinyDataApi.get(player)
        var total = data.stats
        player.inventory.armor.forEach { stack ->
            GearRolls.read(stack)?.armorStats?.let { total += it }
            GearRolls.equippedArmorStatMod(stack)?.bonusStats?.let { total += it }
        }
        val classItem = data.classItem
        if ((classItem.item as? DestinyClassItem)?.requiredClass == data.destinyClass) {
            GearRolls.read(classItem)?.armorStats?.let { total += it }
            GearRolls.equippedArmorStatMod(classItem)?.bonusStats?.let { total += it }
        }
        total += ArmorModRuntime.statBonuses(player)
        return total.clamped()
    }
}
