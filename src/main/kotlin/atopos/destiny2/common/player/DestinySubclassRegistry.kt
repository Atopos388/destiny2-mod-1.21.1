package atopos.destiny2.common.player

import net.minecraft.resources.ResourceLocation

object DestinySubclassRegistry {
    private const val MOD_ID = "destiny2-mod"

    val SOLAR_WARLOCK = DestinySubclassProfile(
        type = DestinySubclassType.SOLAR_WARLOCK,
        title = "烈日术士",
        element = "烈日",
        description = "以恢复、焕光和火焰法术为核心的团队增益子职业。",
        icon = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/subclass/solar_warlock.png")
    )

    val VOID_HUNTER = DestinySubclassProfile(
        type = DestinySubclassType.VOID_HUNTER,
        title = "虚空猎人",
        element = "虚空",
        description = "以隐身、虚弱、陷阱和控制为核心的机动子职业。",
        icon = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/subclass/void_hunter.png")
    )

    val ARC_TITAN = DestinySubclassProfile(
        type = DestinySubclassType.ARC_TITAN,
        title = "电弧泰坦",
        element = "电弧",
        description = "以冲锋、近战和高压爆发为核心的前排子职业，目前技能仍是占位。",
        icon = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/subclass/arc_titan.png")
    )

    private val profiles = listOf(SOLAR_WARLOCK, VOID_HUNTER, ARC_TITAN).associateBy { it.type }

    fun profileFor(type: DestinySubclassType): DestinySubclassProfile {
        return profiles[type] ?: SOLAR_WARLOCK
    }

    fun all(): Collection<DestinySubclassProfile> {
        return profiles.values
    }

    fun availableFor(destinyClass: DestinyClassType): List<DestinySubclassProfile> {
        return DestinySubclassType.availableFor(destinyClass).map(::profileFor)
    }
}
