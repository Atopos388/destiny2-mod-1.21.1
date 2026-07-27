package atopos.destiny2.common.weapon

enum class WeaponType {
    AUTO_RIFLE,
    PULSE_RIFLE,
    HAND_CANNON,
    SCOUT_RIFLE,
    SMG,
    BOW,
    SIDEARM,
    SHOTGUN,
    SNIPER_RIFLE,
    FUSION_RIFLE,
    ROCKET_LAUNCHER,
    GRENADE_LAUNCHER,
    SWORD,
    MACHINE_GUN
}

data class WeaponStats(
    val fireRate: Int, // RPM
    val impact: Int,
    val range: Int,
    val stability: Int,
    val handling: Int,
    val reloadSpeed: Int,
    val magazineSize: Int
)
