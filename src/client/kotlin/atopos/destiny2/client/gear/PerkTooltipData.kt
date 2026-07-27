package atopos.destiny2.client.gear

import atopos.destiny2.common.gear.GearCategory
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.gear.GearRarity
import atopos.destiny2.common.gear.GearRolls
import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.common.gear.ArmorModRegistry
import atopos.destiny2.common.gear.DestinyArmorSlot
import net.minecraft.core.component.DataComponents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemAttributeModifiers

data class PerkTooltipData(
    val definitionId: ResourceLocation,
    val name: Component,
    val rarity: GearRarity,
    val category: GearCategory,
    val frameName: String,
    val elementName: String?,
    val power: Int?,
    val damage: Double?,
    val fireRate: Double?,
    val armorStats: DestinyStats?,
    val armorArchetypeName: String?,
    val armorTier: Int,
    val armorSlotName: String?,
    val equippedArmorMods: List<ArmorModTooltipEntry?>,
    val availableArmorModsBySocket: List<List<ArmorModTooltipEntry>>,
    val armorEnergyUsed: Int,
    val armorEnergyCapacity: Int,
    val perks: List<PerkTooltipEntry>,
    val hasCatalystSlot: Boolean,
    val catalysts: List<PerkTooltipEntry>,
    val equippedCatalystId: ResourceLocation?,
    val rollMissing: Boolean
)

data class PerkTooltipEntry(
    val id: ResourceLocation,
    val columnName: String?,
    val name: String,
    val description: String,
    val icon: ResourceLocation
)

data class ArmorModTooltipEntry(
    val id: ResourceLocation,
    val name: String,
    val description: String,
    val statBonus: Int,
    val energyCost: Int,
    val icon: ResourceLocation?
)

/** Converts the common ItemStack roll into a client-only, render-ready model. */
object PerkTooltipDataAdapter {
    fun from(stack: ItemStack): PerkTooltipData? {
        if (stack.isEmpty) return null
        val definition = GearRegistry.definitionFor(stack) ?: return null
        val roll = GearRolls.read(stack)
        val perks = roll?.perkIds.orEmpty().mapIndexedNotNull { index, id ->
            val perk = GearRegistry.perk(id) ?: return@mapIndexedNotNull null
            entry(id, definition.rollColumnLabels.getOrNull(index), perk.displayName, perk.description)
        }
        val catalysts = definition.catalysts.map { catalyst ->
            entry(catalyst.id, "催化剂", catalyst.displayName, catalyst.description)
        }

        val customData = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
        val rollTag = customData?.getCompound("DestinyGearRoll")
        val power = listOf("DestinyPower", "power", "Power", "level", "Level")
            .firstNotNullOfOrNull { key ->
                when {
                    customData?.contains(key) == true -> customData.getInt(key)
                    rollTag?.contains(key) == true -> rollTag.getInt(key)
                    else -> null
                }
            }
            ?.takeIf { it > 0 }
            ?: GearRolls.defaultPower(definition)
        val element = listOf("DestinyElement", "element", "Element")
            .firstNotNullOfOrNull { key ->
                when {
                    customData?.contains(key) == true -> customData.getString(key)
                    rollTag?.contains(key) == true -> rollTag.getString(key)
                    else -> null
                }
            }
            ?.takeIf(String::isNotBlank)

        val damageMultiplier = GearRolls.damageMultiplier(stack).toDouble()
        val cooldownMultiplier = GearRolls.cooldownMultiplier(stack).toDouble().coerceAtLeast(0.01)
        val attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS) ?: ItemAttributeModifiers.EMPTY
        val isMelee = definition.frame.id.path == "vanilla_melee"
        val damage = when {
            definition.category != GearCategory.WEAPON -> null
            definition.baseDamage > 0.0f -> definition.baseDamage * damageMultiplier
            isMelee -> attributes.compute(1.0, EquipmentSlot.MAINHAND) * damageMultiplier
            stack.item is BowItem || stack.item is CrossbowItem -> 2.0 * damageMultiplier
            else -> null
        }
        val fireRate = when {
            definition.category != GearCategory.WEAPON -> null
            definition.baseDamage > 0.0f ->
                20.0 * definition.frame.burstCount / (definition.frame.cooldownTicks * cooldownMultiplier)
            isMelee -> attributes.compute(4.0, EquipmentSlot.MAINHAND) / cooldownMultiplier
            stack.item is BowItem -> 1.0 / cooldownMultiplier
            stack.item is CrossbowItem -> {
                val player = Minecraft.getInstance().player
                if (player == null) null else 20.0 / CrossbowItem.getChargeDuration(stack, player) / cooldownMultiplier
            }
            else -> null
        }
        val armorSlot = DestinyArmorSlot.from(stack)
        val availableArmorModsBySocket = if (definition.category == GearCategory.ARMOR && armorSlot != null) {
            List(GearRolls.ARMOR_MOD_SOCKET_COUNT) { socket -> ArmorModRegistry.available(armorSlot, socket).map { mod ->
                armorModEntry(mod)
            } }
        } else emptyList()
        val equippedArmorMods = GearRolls.equippedArmorMods(stack).map { mod -> mod?.let {
            armorModEntry(it)
        } }
        return PerkTooltipData(
            definitionId = definition.id,
            name = stack.hoverName.copy(),
            rarity = definition.rarity,
            category = definition.category,
            frameName = definition.frame.displayName,
            elementName = element,
            power = power,
            damage = damage,
            fireRate = fireRate,
            armorStats = roll?.armorStats,
            armorArchetypeName = roll?.armorArchetype?.displayName,
            armorTier = roll?.armorTier ?: 0,
            armorSlotName = armorSlot?.displayName,
            equippedArmorMods = equippedArmorMods,
            availableArmorModsBySocket = availableArmorModsBySocket,
            armorEnergyUsed = GearRolls.armorEnergyUsed(stack),
            armorEnergyCapacity = GearRolls.ARMOR_ENERGY_CAPACITY,
            perks = perks,
            hasCatalystSlot = definition.hasCatalystSlot,
            catalysts = catalysts,
            equippedCatalystId = roll?.catalystId,
            rollMissing = roll == null
        )
    }

    private fun entry(id: ResourceLocation, columnName: String?, name: String, description: String) =
        PerkTooltipEntry(
            id = id,
            columnName = columnName,
            name = name,
            description = description,
            // Creators may add these textures later without changing code.
            icon = ResourceLocation.fromNamespaceAndPath(id.namespace, "textures/gui/perks/${id.path}.png")
        )

    private fun armorModEntry(mod: atopos.destiny2.common.gear.ArmorModDefinition): ArmorModTooltipEntry {
        val icon = ResourceLocation.fromNamespaceAndPath(
            mod.id.namespace,
            "textures/gui/armor_mods/${mod.id.path.removePrefix("armor_mod/")}.png"
        ).takeIf { Minecraft.getInstance().resourceManager.getResource(it).isPresent }
        return ArmorModTooltipEntry(
            mod.id,
            mod.displayName,
            mod.description,
            mod.statBonus,
            mod.energyCost,
            icon
        )
    }
}
