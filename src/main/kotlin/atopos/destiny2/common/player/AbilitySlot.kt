package atopos.destiny2.common.player

enum class AbilitySlot(val key: String, val legacyNetworkId: Int) {
    GRENADE("grenade", 0),
    MELEE("melee", 1),
    CLASS_ABILITY("class_ability", 2),
    SUPER("super", 3);

    companion object {
        fun fromKey(key: String): AbilitySlot? = entries.firstOrNull { it.key == key }

        fun fromLegacyNetworkId(id: Int): AbilitySlot? {
            return when (id) {
                0, 10 -> GRENADE
                1, 11 -> MELEE
                2, 12 -> CLASS_ABILITY
                3, 13 -> SUPER
                else -> null
            }
        }
    }
}
