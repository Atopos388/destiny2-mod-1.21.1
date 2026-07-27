package atopos.destiny2.common.gear

import net.minecraft.resources.ResourceLocation

enum class GearPerkTrigger(val displayName: String) {
    PASSIVE("被动"),
    MELEE_HIT("近战命中"),
    MELEE_KILL("近战击杀"),
    BOW_HIT("弓弩命中"),
    BOW_KILL("弓弩击杀"),
    SWAP_IN("切换武器"),
    CHARGE_RELEASE("蓄力释放")
}

enum class GearPerkScope {
    MICRO_MISSILE,
    FORGOTTEN_NAME,
    IZANAGIS_BURDEN,
    MELEE,
    BOW,
    CROSSBOW,
    ARMOR
}

enum class GearPerkEffect {
    NONE,
    EXECUTIONERS_EDGE,
    GUARD_COUNTER,
    EMBER_CARVE,
    MOMENTUM_CLEAVE,
    DUELIST_PULSE,
    BLOODLESS_FINISH,
    CHAIN_STEP,
    HEAVY_REVERSAL,
    ECHO_ARROW,
    PINNING_SHOT,
    PERFECT_DRAW,
    SPLIT_STRING,
    GRAVITY_ARC,
    MARKED_QUIVER,
    RICOCHET_FLETCHING,
    CALM_RELEASE,
    FIREWORK_FINISHER,
    LUCKY_REBOUND,
    SOLAR_POPCORN,
    PANIC_GUARD,
    FEATHER_STEP,
    BONE_RHYTHM,
    HONED_EDGE,
    SERRATED_EDGE,
    EMBER_BLADE,
    LIGHTWEIGHT_LIMBS,
    REINFORCED_LIMBS,
    STEADY_LIMBS,
    REINFORCED_ARMS,
    LIGHTWEIGHT_ARMS,
    MULTISHOT_ARMS,
    PRECISION_BARREL,
    HIGH_EXPLOSIVE_BARREL,
    STABLE_LAUNCHER,
    IMPACT_CASING,
    VORPAL_WEAPON,
    FIELD_PREP,
    AMBITIOUS_ASSASSIN,
    HEAVY_GUARD,
    DUELIST_GUARD,
    ARROW_SPLIT,
    ARC_FLASH,
    PRECISION_BOMBARDMENT,
    SLAUGHTER_OVERTURE,
    EXTINCTION_PROTOCOL,
    HUNTING_MARK,
    EAGER_EDGE,
    DISCIPLINE_CORE,
    RECOVERY_CORE,
    RESILIENCE_CORE,
    MOBILITY_CORE
}

data class GearPerk(
    val id: ResourceLocation,
    val displayName: String,
    val description: String,
    val damageMultiplier: Float = 1.0f,
    val projectileSpeedMultiplier: Float = 1.0f,
    val projectileInaccuracyMultiplier: Float = 1.0f,
    val explosionRadiusMultiplier: Float = 1.0f,
    val cooldownMultiplier: Float = 1.0f,
    val ammoRefundChance: Float = 0.0f,
    val reloadTimeMultiplier: Float = 1.0f,
    val magazineSizeBonus: Int = 0,
    val triggers: Set<GearPerkTrigger> = emptySet(),
    val scopes: Set<GearPerkScope> = emptySet(),
    val effect: GearPerkEffect = GearPerkEffect.NONE,
    val durationTicks: Int = 0,
    val maxStacks: Int = 1,
    val internalCooldownTicks: Int = 0,
    val triggerChance: Float = 1.0f
)
