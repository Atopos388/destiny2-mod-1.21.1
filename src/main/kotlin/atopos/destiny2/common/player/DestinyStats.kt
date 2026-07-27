package atopos.destiny2.common.player

import atopos.destiny2.common.stats.StatType
import net.minecraft.nbt.CompoundTag

data class DestinyStats(
    val weapons: Int,
    val health: Int,
    val classAbility: Int,
    val grenade: Int,
    val superStat: Int,
    val melee: Int
) {
    fun get(type: StatType): Int {
        return when (type) {
            StatType.WEAPONS -> weapons
            StatType.HEALTH -> health
            StatType.CLASS -> classAbility
            StatType.GRENADE -> grenade
            StatType.SUPER -> superStat
            StatType.MELEE -> melee
        }
    }

    fun clamped(): DestinyStats = DestinyStats(
        weapons.coerceIn(MIN_VALUE, MAX_VALUE),
        health.coerceIn(MIN_VALUE, MAX_VALUE),
        classAbility.coerceIn(MIN_VALUE, MAX_VALUE),
        grenade.coerceIn(MIN_VALUE, MAX_VALUE),
        superStat.coerceIn(MIN_VALUE, MAX_VALUE),
        melee.coerceIn(MIN_VALUE, MAX_VALUE)
    )

    operator fun plus(other: DestinyStats): DestinyStats = DestinyStats(
        weapons + other.weapons,
        health + other.health,
        classAbility + other.classAbility,
        grenade + other.grenade,
        superStat + other.superStat,
        melee + other.melee
    ).clamped()

    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("schema_version", CURRENT_SCHEMA_VERSION)
        tag.putInt("weapons", weapons.coerceIn(MIN_VALUE, MAX_VALUE))
        tag.putInt("health", health.coerceIn(MIN_VALUE, MAX_VALUE))
        tag.putInt("class", classAbility.coerceIn(MIN_VALUE, MAX_VALUE))
        tag.putInt("grenade", grenade.coerceIn(MIN_VALUE, MAX_VALUE))
        tag.putInt("super", superStat.coerceIn(MIN_VALUE, MAX_VALUE))
        tag.putInt("melee", melee.coerceIn(MIN_VALUE, MAX_VALUE))
        return tag
    }

    companion object {
        const val MIN_VALUE = 0
        const val MAX_VALUE = 200
        const val BASE_VALUE = 30
        const val CURRENT_SCHEMA_VERSION = 2

        val UNIFIED_BASE = DestinyStats(BASE_VALUE, BASE_VALUE, BASE_VALUE, BASE_VALUE, BASE_VALUE, BASE_VALUE)

        fun defaultFor(destinyClass: DestinyClassType): DestinyStats {
            return UNIFIED_BASE
        }

        fun fromTag(tag: CompoundTag, destinyClass: DestinyClassType): DestinyStats {
            val defaults = defaultFor(destinyClass)
            if (!tag.contains("weapons") && tag.contains("mobility")) {
                // Armor 2.0 values were class-fixed 0-10 scores. They are deliberately
                // migrated to the unified Armor 3.0 baseline instead of being multiplied.
                return defaults
            }
            return DestinyStats(
                weapons = readInt(tag, "weapons", defaults.weapons),
                health = readInt(tag, "health", defaults.health),
                classAbility = readInt(tag, "class", defaults.classAbility),
                grenade = readInt(tag, "grenade", defaults.grenade),
                superStat = readInt(tag, "super", defaults.superStat),
                melee = readInt(tag, "melee", defaults.melee)
            ).clamped()
        }

        private fun readInt(tag: CompoundTag, key: String, fallback: Int): Int {
            return if (tag.contains(key)) tag.getInt(key) else fallback
        }
    }
}
