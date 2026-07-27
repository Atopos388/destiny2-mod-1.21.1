package atopos.destiny2.common.player

import net.minecraft.resources.ResourceLocation

object DestinyClassRegistry {
    private const val MOD_ID = "destiny2-mod"

    val HUNTER = DestinyClassProfile(
        type = DestinyClassType.HUNTER,
        title = "猎人",
        description = "高机动、高爆发，适合闪避、精确打击和灵活走位。",
        baseStats = DestinyStats.UNIFIED_BASE,
        defaultSubclass = DestinySubclassType.VOID_HUNTER,
        icon = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/class/hunter.png")
    )

    val TITAN = DestinyClassProfile(
        type = DestinyClassType.TITAN,
        title = "泰坦",
        description = "高韧性、强近战，适合防御、冲锋和前排压制。",
        baseStats = DestinyStats.UNIFIED_BASE,
        defaultSubclass = DestinySubclassType.ARC_TITAN,
        icon = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/class/titan.png")
    )

    val WARLOCK = DestinyClassProfile(
        type = DestinyClassType.WARLOCK,
        title = "术士",
        description = "高恢复、高技能循环，适合治疗、增益和元素法术。",
        baseStats = DestinyStats.UNIFIED_BASE,
        defaultSubclass = DestinySubclassType.SOLAR_WARLOCK,
        icon = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/class/warlock.png")
    )

    private val profiles = listOf(HUNTER, TITAN, WARLOCK).associateBy { it.type }

    fun profileFor(type: DestinyClassType): DestinyClassProfile {
        return profiles[type] ?: WARLOCK
    }

    fun all(): Collection<DestinyClassProfile> {
        return profiles.values
    }
}
