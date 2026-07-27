package atopos.destiny2.common.player

enum class DestinySubclassType(
    val id: String,
    val displayName: String,
    val requiredClass: DestinyClassType
) {
    SOLAR_WARLOCK("solar_warlock", "烈日术士", DestinyClassType.WARLOCK),
    VOID_HUNTER("void_hunter", "虚空猎人", DestinyClassType.HUNTER),
    ARC_TITAN("arc_titan", "电弧泰坦", DestinyClassType.TITAN);

    companion object {
        val DEFAULT = SOLAR_WARLOCK

        fun fromId(id: String): DestinySubclassType {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }

        fun findById(id: String): DestinySubclassType? {
            return entries.firstOrNull { it.id == id }
        }

        fun availableFor(destinyClass: DestinyClassType): List<DestinySubclassType> {
            return entries.filter { it.requiredClass == destinyClass }
        }

        fun defaultFor(destinyClass: DestinyClassType): DestinySubclassType {
            return DestinyClassRegistry.profileFor(destinyClass).defaultSubclass
        }
    }
}
