package atopos.destiny2.client.gui

import atopos.destiny2.common.network.DestinyNetworking

object DestinyHUDState {
    var classId = ""
        private set
    var className = ""
        private set
    var subclassName = ""
        private set
    private val abilityNames = mutableMapOf<Int, String>()
    private val selectedAbilityIds = mutableMapOf<Int, String>()
    var selectedMovementId = ""
        private set
    var selectedAspectIds: List<String> = emptyList()
        private set
    var selectedFragmentIds: List<String> = emptyList()
        private set
    var weapons = 30
        private set
    var health = 30
        private set
    var classAbility = 30
        private set
    var grenade = 30
        private set
    var superStat = 30
        private set
    var melee = 30
        private set
    var superEnergy = 0.0f
        private set
    var healthShield = 0.0f
        private set
    var healthShieldCapacity = 4.0f
        private set
    var classOvershield = 0.0f
        private set
    var armorCharge = 0
        private set
    var armorChargeMax = 3
        private set

    fun update(payload: DestinyNetworking.SyncPlayerDataPayload) {
        classId = payload.classId
        className = payload.className
        subclassName = payload.subclassName
        abilityNames[DestinyNetworking.ABILITY_GRENADE] = payload.grenadeName
        abilityNames[DestinyNetworking.ABILITY_MELEE] = payload.meleeName
        abilityNames[DestinyNetworking.ABILITY_CLASS] = payload.classAbilityName
        abilityNames[DestinyNetworking.ABILITY_SUPER] = payload.superName
        selectedAbilityIds[DestinyNetworking.ABILITY_GRENADE] = payload.grenadeId
        selectedAbilityIds[DestinyNetworking.ABILITY_MELEE] = payload.meleeId
        selectedAbilityIds[DestinyNetworking.ABILITY_CLASS] = payload.classAbilityId
        selectedAbilityIds[DestinyNetworking.ABILITY_SUPER] = payload.superId
        selectedMovementId = payload.movementId
        selectedAspectIds = readCsv(payload.aspectIds)
        selectedFragmentIds = readCsv(payload.fragmentIds)
    }

    fun update(payload: DestinyNetworking.SyncStatStatePayload) {
        weapons = payload.weapons
        health = payload.health
        classAbility = payload.classAbility
        grenade = payload.grenade
        superStat = payload.superStat
        melee = payload.melee
        superEnergy = payload.superEnergy.coerceIn(0.0f, 100.0f)
        healthShield = payload.healthShield.coerceAtLeast(0.0f)
        healthShieldCapacity = payload.healthShieldCapacity.coerceAtLeast(0.0f)
        classOvershield = payload.classOvershield.coerceAtLeast(0.0f)
        armorCharge = payload.armorCharge.coerceAtLeast(0)
        armorChargeMax = payload.armorChargeMax.coerceIn(3, 6)
    }

    fun abilityName(abilityType: Int): String {
        return abilityNames[abilityType].orEmpty()
    }

    fun selectedAbilityId(abilityType: Int): String {
        return selectedAbilityIds[abilityType].orEmpty()
    }

    private fun readCsv(value: String): List<String> {
        return value.split(",").map(String::trim).filter(String::isNotBlank)
    }
}
