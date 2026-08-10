package atopos.destiny2.common.player

import atopos.destiny2.common.aspect.VoidHunterAspectRuntime
import atopos.destiny2.common.aspect.ArcTitanFragmentRules
import atopos.destiny2.common.aspect.ArcTitanFragmentRuntime
import atopos.destiny2.common.aspect.SolarWarlockFragmentRules
import atopos.destiny2.common.aspect.SolarWarlockFragmentRuntime
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
        total += VoidHunterAspectRuntime.fragmentStatBonuses(player)
        total += subclassFragmentStatBonuses(data.subclass, data.subclassConfig.selectedFragments)
        total += SolarWarlockFragmentRuntime.dynamicStatBonuses(player)
        total += ArcTitanFragmentRuntime.dynamicStatBonuses(player)
        return total.clamped()
    }

    /**
     * Applies only the currently active subclass' fragment modifiers. Keeping
     * this boundary pure prevents stale fragments from another subclass save
     * from leaking into the live Armor 3.0 stat total.
     */
    fun subclassFragmentStatBonuses(
        subclass: DestinySubclassType,
        selectedFragmentIds: Collection<String>
    ): DestinyStats = when (subclass) {
        DestinySubclassType.SOLAR_WARLOCK ->
            SolarWarlockFragmentRules.staticStatBonuses(selectedFragmentIds.toSet())
        DestinySubclassType.ARC_TITAN ->
            ArcTitanFragmentRules.staticStatBonuses(selectedFragmentIds)
        DestinySubclassType.VOID_HUNTER -> ZERO_STATS
    }

    private val ZERO_STATS = DestinyStats(0, 0, 0, 0, 0, 0)
}
