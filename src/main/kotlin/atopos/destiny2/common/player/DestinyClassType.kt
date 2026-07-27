package atopos.destiny2.common.player

enum class DestinyClassType(val id: String, val displayName: String) {
    HUNTER("hunter", "猎人"),
    TITAN("titan", "泰坦"),
    WARLOCK("warlock", "术士");

    companion object {
        val DEFAULT = WARLOCK

        fun fromId(id: String): DestinyClassType {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }

        fun findById(id: String): DestinyClassType? {
            return entries.firstOrNull { it.id == id }
        }
    }
}
