package atopos.destiny2.common.entity

/** Shared balance values for the world Orb of Power entity. */
object OrbOfPowerRules {
    const val SUPER_ENERGY_GAIN = 2.5f
    const val PICKUP_DELAY_TICKS = 10
    const val LIFETIME_TICKS = 20 * 60 * 5
    const val PICKUP_RADIUS = 0.75

    fun addSuperEnergy(current: Float, amount: Float = SUPER_ENERGY_GAIN): Float =
        (current + amount.coerceAtLeast(0.0f)).coerceIn(0.0f, 100.0f)
}
