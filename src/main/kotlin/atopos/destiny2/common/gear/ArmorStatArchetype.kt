package atopos.destiny2.common.gear

import atopos.destiny2.common.stats.StatType

enum class ArmorStatArchetype(
    val displayName: String,
    val primary: StatType,
    val secondary: StatType
) {
    PARAGON("典范", StatType.SUPER, StatType.MELEE),
    GRENADIER("掷弹兵", StatType.GRENADE, StatType.SUPER),
    SPECIALIST("专家", StatType.CLASS, StatType.WEAPONS),
    BRAWLER("斗士", StatType.MELEE, StatType.HEALTH),
    BULWARK("壁垒", StatType.HEALTH, StatType.CLASS),
    GUNNER("枪手", StatType.WEAPONS, StatType.GRENADE),
    MEDIC("战地医师", StatType.HEALTH, StatType.GRENADE),
    SKIRMISHER("突击手", StatType.MELEE, StatType.WEAPONS),
    TACTICIAN("战术家", StatType.GRENADE, StatType.CLASS),
    WARDEN("守望者", StatType.SUPER, StatType.HEALTH),
    VANGUARD("先锋", StatType.CLASS, StatType.MELEE),
    HEAVY_GUNNER("重装枪手", StatType.WEAPONS, StatType.SUPER)
}
