package atopos.destiny2.common

import atopos.destiny2.common.ability.DestinyAbility
import atopos.destiny2.common.ability.DestinySubclass
import atopos.destiny2.common.classes.DestinyClass
import atopos.destiny2.common.equipment.DestinyEquipmentDefinition
import atopos.destiny2.common.weapon.DestinyWeaponDefinition
import atopos.destiny2.common.weapon.WeaponPerk
import net.minecraft.resources.ResourceLocation

object DestinyRegistry {
    val CLASSES = mutableMapOf<ResourceLocation, DestinyClass>()
    val SUBCLASSES = mutableMapOf<ResourceLocation, DestinySubclass>()
    val ABILITIES = mutableMapOf<ResourceLocation, DestinyAbility>()
    val WEAPONS = mutableMapOf<ResourceLocation, DestinyWeaponDefinition>()
    val EQUIPMENT = mutableMapOf<ResourceLocation, DestinyEquipmentDefinition>()
    val PERKS = mutableMapOf<ResourceLocation, WeaponPerk>()

    fun registerClass(destinyClass: DestinyClass) {
        CLASSES[destinyClass.id] = destinyClass
    }

    fun registerWeapon(weapon: DestinyWeaponDefinition) {
        WEAPONS[weapon.id] = weapon
    }
    
    // ... other register methods
    
    fun validate() {
        // Validation logic
        // 1. Check if all subclasses reference valid abilities
        SUBCLASSES.values.forEach { subclass ->
            if (!ABILITIES.containsKey(subclass.superAbility.id)) {
                 // Log warning or error
            }
        }
        
        // 2. Check if weapons reference valid perks
        WEAPONS.values.forEach { weapon ->
            weapon.possiblePerksColumn1.forEach { perk ->
                if (!PERKS.containsKey(perk.id)) {
                    // Log warning
                }
            }
        }
    }
}
