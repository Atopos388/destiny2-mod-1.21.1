package atopos.destiny2.common.block

/** Pure balance rules for the first home-industry production chain. */
object IndustrialMachineRules {
    const val CAPACITOR_MAX_ENERGY = 8_000
    const val LIGHT_CRYSTAL_ENERGY = 2_000
    const val REFINERY_ENERGY_COST = 200
    const val REFINERY_PROCESS_TICKS = 100
    const val MEMORY_FOUNDRY_ENERGY_COST = 1_200
    const val MEMORY_FOUNDRY_PROCESS_TICKS = 160

    private val glimmerYields = mapOf(
        "minecraft:redstone" to 8,
        "minecraft:iron_ingot" to 12,
        "minecraft:copper_ingot" to 16,
        "minecraft:gold_ingot" to 20,
        "minecraft:amethyst_shard" to 24
    )

    fun glimmerYield(itemId: String): Int = glimmerYields[itemId] ?: 0

    fun canStoreEnergy(current: Int): Boolean = current <= CAPACITOR_MAX_ENERGY - LIGHT_CRYSTAL_ENERGY

    fun foundryIngredients(bookCount: Int, gunpowderCount: Int, amethystCount: Int, glimmerCount: Int): Boolean =
        bookCount >= 1 && gunpowderCount >= 2 && amethystCount >= 1 && glimmerCount >= 32
}
