package atopos.destiny2.common.weapon

data class WeaponCombatProfile(
    val ammoType: DestinyAmmoType,
    val baseDamage: Float,
    val precisionMultiplier: Float = 1.0f,
    val magazineSize: Int,
    val reloadTicks: Int,
    val emptyReloadBonusTicks: Int = 6,
    val reloadFeedFraction: Float = 0.72f,
    val fireMode: WeaponFireMode = WeaponFireMode.SEMI,
    val supportedFireModes: List<WeaponFireMode> = listOf(fireMode),
    val roundsPerMinute: Int = 80,
    val boltTicks: Int = 0,
    val recoil: WeaponRecoilProfile = WeaponRecoilProfile(),
    val accuracy: WeaponAccuracyProfile = WeaponAccuracyProfile(),
    val crosshair: WeaponCrosshairProfile = WeaponCrosshairProfile(),
    val ballistics: WeaponBallisticsProfile = WeaponBallisticsProfile(),
    val projectilesPerShot: Int = 1,
    val projectileSpreadDegrees: Float = 0.0f,
    val damageElement: DestinyDamageElement = DestinyDamageElement.KINETIC
)

enum class DestinyDamageElement {
    KINETIC,
    ARC,
    SOLAR,
    VOID,
    STASIS,
    STRAND;

    companion object {
        fun fromSerializedName(value: String?): DestinyDamageElement =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: KINETIC
    }
}

enum class WeaponFireMode(val displayName: String) {
    SEMI("半自动"),
    BURST("点射"),
    AUTO("全自动")
}

data class WeaponRecoilProfile(
    val pitchMin: Float = 2.4f,
    val pitchMax: Float = 3.0f,
    val yawMin: Float = -0.38f,
    val yawMax: Float = 0.38f,
    val yawPattern: List<Float> = emptyList(),
    val kickDurationMs: Int = 70,
    val recoverDurationMs: Int = 310,
    val aimedMultiplier: Float = 0.68f,
    val stability: Float = 60.0f,
    val recoilDirection: Float = 70.0f
)

data class WeaponAccuracyProfile(
    val hipBaseDegrees: Float = 0.45f,
    val aimedBaseDegrees: Float = 0.04f,
    val movingPenaltyDegrees: Float = 0.15f,
    val airbornePenaltyDegrees: Float = 1.25f,
    val bloomPerShotDegrees: Float = 0.08f,
    val maxBloomDegrees: Float = 0.5f,
    val settleDelayTicks: Int = 3,
    val bloomDecayPerTick: Float = 0.06f
)

data class WeaponCrosshairProfile(
    val baseGap: Float = 5.0f,
    val movingPenalty: Float = 4.0f,
    val airbornePenalty: Float = 7.0f,
    val shotPenalty: Float = 5.0f,
    val shotDecayPerTick: Float = 0.55f,
    val hideAimProgress: Float = 0.92f
)

data class WeaponBallisticsProfile(
    val range: Double = 128.0,
    val entityTolerance: Double = 0.08,
    val tracerStep: Double = 1.35,
    val showBulletImpact: Boolean = true,
    val speed: Float = 8.0f,
    val gravity: Float = 0.0f,
    val friction: Float = 0.01f,
    val lifeTicks: Int = 40,
    val pierce: Int = 1,
    val distanceDamage: List<WeaponDistanceDamage> = emptyList()
)

data class WeaponDistanceDamage(val distance: Double, val damage: Float)

enum class WeaponReloadPhase {
    NONE,
    STARTING,
    FEEDING,
    FINISHING
}

data class WeaponHudStatus(
    val weaponId: String,
    val ammoType: DestinyAmmoType,
    val magazine: Int,
    val capacity: Int,
    val reserve: Int,
    val reloadRemaining: Int,
    val reloadTotal: Int,
    val precisionMultiplier: Float,
    val reloadPhase: WeaponReloadPhase = WeaponReloadPhase.NONE,
    val fireMode: WeaponFireMode = WeaponFireMode.SEMI,
    val chamberEmpty: Boolean = false,
    val boltRemaining: Int = 0,
    val crosshair: WeaponCrosshairProfile = WeaponCrosshairProfile()
) {
    companion object {
        val INACTIVE = WeaponHudStatus("", DestinyAmmoType.PRIMARY, 0, 0, 0, 0, 0, 1.0f)
    }
}

object AmmoDropRules {
    data class Drop(val type: DestinyAmmoType, val chance: Float, val minCount: Int, val maxCount: Int)

    fun dropFor(source: DestinyAmmoType): Drop = when (source) {
        DestinyAmmoType.PRIMARY -> Drop(DestinyAmmoType.SPECIAL, 0.22f, 3, 6)
        DestinyAmmoType.SPECIAL -> Drop(DestinyAmmoType.HEAVY, 0.14f, 1, 3)
        DestinyAmmoType.HEAVY -> Drop(DestinyAmmoType.SPECIAL, 0.06f, 2, 4)
    }

    fun shouldDrop(source: DestinyAmmoType, roll: Float): Boolean = roll < dropFor(source).chance
}

object WeaponAmmoMath {
    fun needed(magazine: Int, capacity: Int): Int = (capacity - magazine).coerceAtLeast(0)

    fun loadAmount(magazine: Int, capacity: Int, reserve: Int): Int =
        minOf(needed(magazine, capacity), reserve.coerceAtLeast(0))

    fun completedMagazine(magazine: Int, capacity: Int, loaded: Int): Int =
        (magazine + loaded.coerceAtLeast(0)).coerceIn(0, capacity.coerceAtLeast(0))
}
