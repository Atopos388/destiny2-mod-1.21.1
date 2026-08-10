package atopos.destiny2.common.gear

import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.weapon.DestinyDamageElement
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item

data class GearDefinition(
    val item: Item,
    val id: ResourceLocation,
    val rarity: GearRarity,
    val category: GearCategory,
    val frame: WeaponFrameDefinition,
    val baseDamage: Float = 0.0f,
    val explosionRadius: Float = 0.0f,
    val ammoItem: Item? = null,
    val ammoType: DestinyAmmoType = DestinyAmmoType.PRIMARY,
    val precisionMultiplier: Float = 1.0f,
    val fixedPerks: List<GearPerk> = emptyList(),
    val perkColumns: List<List<GearPerk>> = emptyList(),
    val rollColumnLabels: List<String> = emptyList(),
    val hasCatalystSlot: Boolean = false,
    /** Catalysts which the server allows this exact gear definition to equip. */
    val catalysts: List<GearPerk> = emptyList(),
    val damageElement: DestinyDamageElement = DestinyDamageElement.KINETIC
)
