package atopos.destiny2.common.weapon

object IzanagiBurdenRules {
    const val MAGAZINE_SIZE = 4
    const val BASE_DAMAGE = 25.0f
    const val PRECISION_MULTIPLIER = 1.7f
    const val MAX_HONED_EDGE_ROUNDS = 4

    fun honedRounds(magazine: Int): Int =
        magazine.coerceIn(0, MAX_HONED_EDGE_ROUNDS)

    fun canLoadHonedEdge(magazine: Int): Boolean =
        honedRounds(magazine) >= 2

    fun damageMultiplier(rounds: Int): Float =
        honedRounds(rounds).coerceAtLeast(1).toFloat()

    fun rangeMultiplier(rounds: Int): Double =
        1.0 + (honedRounds(rounds).coerceAtLeast(1) - 1) * 0.2
}
