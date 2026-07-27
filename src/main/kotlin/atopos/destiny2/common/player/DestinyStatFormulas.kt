package atopos.destiny2.common.player

import atopos.destiny2.common.weapon.DestinyAmmoType

object DestinyStatFormulas {
    /** Armor 3.0 recharge curve: 0=70%, 70=100%, 100+=115%. */
    fun rechargeRate(stat: Int): Float {
        val value = stat.coerceIn(0, 100)
        return if (value <= 70) {
            0.70f + value / 70.0f * 0.30f
        } else {
            1.0f + (value - 70) / 30.0f * 0.15f
        }
    }

    fun cooldownTicks(baseTicks: Int, slot: AbilitySlot, stats: DestinyStats): Int {
        val relevantStat = when (slot) {
            AbilitySlot.GRENADE -> stats.grenade
            AbilitySlot.MELEE -> stats.melee
            AbilitySlot.CLASS_ABILITY -> stats.classAbility
            AbilitySlot.SUPER -> return baseTicks.coerceAtLeast(20)
        }
        return (baseTicks / rechargeRate(relevantStat)).toInt().coerceAtLeast(20)
    }

    fun healthShieldCapacity(stats: DestinyStats): Float {
        return 4.0f + specialization(stats.health) * 4.0f
    }

    fun healthShieldRechargeDelayTicks(stats: DestinyStats): Int {
        val baseProgress = stats.health.coerceIn(0, 100) / 100.0f
        return ((8.0f - baseProgress * 3.0f) * 20.0f).toInt()
    }

    fun healthShieldRechargePerTick(stats: DestinyStats): Float {
        val baseProgress = stats.health.coerceIn(0, 100) / 100.0f
        val perSecond = 0.5f + baseProgress * 0.5f + specialization(stats.health)
        return perSecond / 20.0f
    }

    fun classOvershieldCapacity(stats: DestinyStats): Float = specialization(stats.classAbility) * 4.0f

    fun weaponDamageMultiplier(stats: DestinyStats): Float {
        return weaponDamageMultiplier(stats, isBoss = false, DestinyAmmoType.PRIMARY)
    }

    fun weaponDamageMultiplier(stats: DestinyStats, isBoss: Boolean, ammoType: DestinyAmmoType): Float {
        if (!isBoss) {
            return 1.0f + stats.weapons.coerceIn(0, 100) / 100.0f * 0.15f
        }
        val bossCap = if (ammoType == DestinyAmmoType.HEAVY) 0.10f else 0.15f
        return 1.0f + specialization(stats.weapons) * bossCap
    }

    fun weaponReloadTimeMultiplier(stats: DestinyStats): Float {
        val value = stats.weapons.coerceIn(0, 100)
        return if (value <= 70) {
            1.0f - value / 70.0f * 0.07f
        } else {
            0.93f - (value - 70) / 30.0f * 0.03f
        }
    }

    fun grenadeDamageMultiplier(stats: DestinyStats): Float = 1.0f + specialization(stats.grenade) * 0.35f

    fun meleeDamageMultiplier(stats: DestinyStats): Float = 1.0f + specialization(stats.melee) * 0.30f

    fun superDamageMultiplier(stats: DestinyStats): Float = 1.0f + specialization(stats.superStat) * 0.30f

    fun damageMultiplier(slot: AbilitySlot, stats: DestinyStats): Float = when (slot) {
        AbilitySlot.GRENADE -> grenadeDamageMultiplier(stats)
        AbilitySlot.MELEE -> meleeDamageMultiplier(stats)
        AbilitySlot.SUPER -> superDamageMultiplier(stats)
        AbilitySlot.CLASS_ABILITY -> 1.0f
    }

    fun superEnergyGainMultiplier(stats: DestinyStats): Float = rechargeRate(stats.superStat)

    fun specialization(stat: Int): Float = ((stat.coerceIn(100, 200) - 100) / 100.0f).coerceIn(0.0f, 1.0f)

    fun clampEnergy(value: Float): Float = value.coerceIn(0.0f, 100.0f)

    const val PASSIVE_SUPER_FULL_CHARGE_TICKS = 6 * 60 * 20

    fun passiveSuperEnergyPerTick(): Float = 100.0f / PASSIVE_SUPER_FULL_CHARGE_TICKS

    fun superEnergyFromDamage(actualDamage: Float, stats: DestinyStats): Float {
        val base = actualDamage.coerceIn(0.0f, 20.0f) * 0.10f
        return (base * superEnergyGainMultiplier(stats)).coerceAtMost(2.0f)
    }
}
