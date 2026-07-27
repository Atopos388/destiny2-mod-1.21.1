package atopos.destiny2.client.gui

import atopos.destiny2.client.gear.PerkTooltipDataAdapter
import atopos.destiny2.common.gear.ArmorModRegistry
import atopos.destiny2.common.gear.ArmorModKind
import atopos.destiny2.common.gear.DestinyArmorSlot
import atopos.destiny2.common.gear.GearCategory
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.gear.GearPerk
import atopos.destiny2.common.item.DestinyClassItem
import atopos.destiny2.common.player.DestinyClassType
import atopos.destiny2.common.player.DestinySubclassConfigRegistry
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.weapon.DestinyWeaponDataRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

enum class DestinyCollectionCategory(val title: String, val description: String, val glyph: String) {
    WEAPONS("武器", "已收录的异域、传说与框架武器", "◆"),
    MODS("模组", "护甲属性、战斗与能量模组档案", "◇"),
    ARMOR("护甲", "头盔、胸甲、护腿与职业物品", "⬡"),
    ASPECTS("星象", "改变职业玩法结构的核心能力", "✦"),
    FRAGMENTS("星象碎片", "补充属性与战斗联动的碎片", "✧")
}

data class DestinyCollectionEntry(
    val id: String,
    val title: String,
    val description: String,
    val meta: String,
    val item: ItemStack = ItemStack.EMPTY,
    val icon: ResourceLocation? = null,
    val weapon: DestinyCollectionWeapon? = null,
    val facts: List<Pair<String, String>> = emptyList()
)

data class DestinyCollectionPerk(
    val id: ResourceLocation,
    val title: String,
    val description: String,
    val icon: ResourceLocation = ResourceLocation.fromNamespaceAndPath(id.namespace, "textures/gui/perks/${id.path}.png")
)

data class DestinyCollectionPerkColumn(
    val label: String,
    val perks: List<DestinyCollectionPerk>
)

data class DestinyCollectionWeapon(
    val damage: Float,
    val precisionMultiplier: Float,
    val roundsPerMinute: Int,
    val magazineSize: Int?,
    val reloadTicks: Int?,
    val range: Double?,
    val explosionRadius: Float,
    val columns: List<DestinyCollectionPerkColumn>
)

data class DestinyCollectionFolder(
    val category: DestinyCollectionCategory,
    val sections: List<DestinyCollectionSection>
) {
    val entryCount: Int get() = sections.sumOf { it.entries.size }
}

data class DestinyCollectionSection(
    val id: String,
    val title: String,
    val description: String,
    val entries: List<DestinyCollectionEntry>
)

object DestinyCollectionsDataAdapter {
    fun snapshot(): List<DestinyCollectionFolder> {
        val modGear = BuiltInRegistries.ITEM.entrySet().asSequence()
            .filter { it.key.location().namespace == "destiny2-mod" }
            .mapNotNull { entry ->
                val stack = ItemStack(entry.value)
                GearRegistry.definitionFor(stack)?.let { definition -> stack to definition }
            }
            .toList()

        val weapons = GearRegistry.allDefinitions().asSequence()
            .filter { it.category == GearCategory.WEAPON }
            .map { ItemStack(it.item) to it }
            .groupBy { it.second.ammoType }
            .map { (ammoType, values) -> DestinyCollectionSection(
                ammoType.name.lowercase(), ammoType.displayName, "使用${ammoType.displayName}的武器",
                values.map { (stack, definition) -> DestinyCollectionEntry(
                    definition.id.toString(), stack.hoverName.string, definition.frame.description,
                    "${definition.rarity.displayName}  //  ${definition.frame.displayName}", stack,
                    weapon = weaponData(definition, stack)
                ) }
            ) }

        val armorGroups = linkedMapOf(
            "warlock" to Triple("术士护甲", "仅限术士装备的护甲与职业物品", DestinyClassType.WARLOCK),
            "hunter" to Triple("猎人护甲", "仅限猎人装备的护甲与职业物品", DestinyClassType.HUNTER),
            "titan" to Triple("泰坦护甲", "仅限泰坦装备的护甲与职业物品", DestinyClassType.TITAN)
        )
        val armorItems = modGear.filter { it.second.category == GearCategory.ARMOR }
        val armor = buildList {
            armorGroups.forEach { (id, info) ->
                add(DestinyCollectionSection(id, info.first, info.second, armorItems
                    .filter { (stack, _) -> (stack.item as? DestinyClassItem)?.requiredClass == info.third }
                    .map(::armorEntry)))
            }
            add(DestinyCollectionSection("universal", "通用护甲", "当前没有职业限制的护甲", armorItems
                .filter { (stack, _) -> stack.item !is DestinyClassItem }
                .map(::armorEntry)))
        }.filter { it.entries.isNotEmpty() }

        val mods = ArmorModRegistry.all().groupBy { mod ->
            if (mod.kind == ArmorModKind.STAT) "stat" else mod.allowedSlots.firstOrNull()?.name?.lowercase() ?: "general"
        }.map { (id, values) ->
            val title = if (id == "stat") "属性模组" else "${values.first().allowedSlots.firstOrNull()?.displayName ?: "通用"}模组"
            DestinyCollectionSection(id, title, "${title}收藏档案", values.map { mod -> DestinyCollectionEntry(
                mod.id.toString(), mod.displayName, mod.description,
                "${mod.energyCost} 能量  //  ${mod.allowedSlots.joinToString(" · ") { it.displayName }}",
                facts = listOf(
                    "能量消耗" to mod.energyCost.toString(),
                    "模组类型" to if (mod.kind == ArmorModKind.STAT) "属性模组" else "战斗模组",
                    "适用部位" to mod.allowedSlots.joinToString(" · ") { it.displayName },
                    "叠加规则" to if (mod.stackRule.name == "NO_DUPLICATES") "不可重复" else "收益递减",
                    "运行效果" to mod.effect.name.replace('_', ' ')
                )
            ) })
        }
        val definitions = DestinySubclassType.entries.map(DestinySubclassConfigRegistry::definitionFor)
        val aspects = definitions.map { definition ->
            DestinyCollectionSection(definition.subclass.id, "${definition.subclass.requiredClass.displayName} · ${definition.subclass.displayName}",
                "${definition.subclass.displayName}星象", definition.aspectOptions.map { option ->
                DestinyCollectionEntry(
                    option.id, option.title, option.description,
                    "${definition.subclass.displayName}  //  ${option.fragmentSlots} 个碎片槽", icon = option.icon,
                    facts = listOf(
                        "收藏类型" to "星象",
                        "职业" to definition.subclass.requiredClass.displayName,
                        "子职业" to definition.subclass.displayName,
                        "碎片槽" to option.fragmentSlots.toString()
                    )
                )
            }.distinctBy(DestinyCollectionEntry::id))
        }.filter { it.entries.isNotEmpty() }
        val fragments = definitions.map { definition ->
            DestinyCollectionSection(definition.subclass.id, "${definition.subclass.requiredClass.displayName} · ${definition.subclass.displayName}",
                "${definition.subclass.displayName}星象碎片", definition.fragmentOptions.map { option ->
                DestinyCollectionEntry(
                    option.id, option.title, option.description,
                    definition.subclass.displayName, icon = option.icon,
                    facts = listOf(
                        "收藏类型" to "星象碎片",
                        "职业" to definition.subclass.requiredClass.displayName,
                        "子职业" to definition.subclass.displayName
                    )
                )
            }.distinctBy(DestinyCollectionEntry::id))
        }.filter { it.entries.isNotEmpty() }

        return listOf(
            DestinyCollectionFolder(DestinyCollectionCategory.WEAPONS, weapons),
            DestinyCollectionFolder(DestinyCollectionCategory.MODS, mods),
            DestinyCollectionFolder(DestinyCollectionCategory.ARMOR, armor),
            DestinyCollectionFolder(DestinyCollectionCategory.ASPECTS, aspects),
            DestinyCollectionFolder(DestinyCollectionCategory.FRAGMENTS, fragments)
        )
    }

    private fun armorEntry(value: Pair<ItemStack, atopos.destiny2.common.gear.GearDefinition>): DestinyCollectionEntry {
        val (stack, definition) = value
        return DestinyCollectionEntry(
            definition.id.toString(), stack.hoverName.string, definition.frame.description,
            "${definition.rarity.displayName}  //  ${definition.category.displayName}", stack,
            facts = buildList {
                add("稀有度" to definition.rarity.displayName)
                add("装备部位" to (DestinyArmorSlot.from(stack)?.displayName ?: "通用"))
                add("装备框架" to definition.frame.displayName)
                (stack.item as? DestinyClassItem)?.let { add("职业限制" to it.requiredClass.displayName) }
            }
        )
    }

    private fun weaponData(definition: atopos.destiny2.common.gear.GearDefinition, stack: ItemStack): DestinyCollectionWeapon {
        val profile = DestinyWeaponDataRegistry.profile(definition.id)
        val computed = PerkTooltipDataAdapter.from(stack)
        val usesMagazine = profile != null || definition.ammoItem != null || definition.baseDamage > 0f
        val definedColumns = if (definition.fixedPerks.isNotEmpty()) {
            definition.fixedPerks.mapIndexed { index, perk ->
                DestinyCollectionPerkColumn(definition.rollColumnLabels.getOrNull(index) ?: "特性 ${index + 1}", listOf(perk.toCollectionPerk()))
            }.toMutableList()
        } else {
            definition.perkColumns.mapIndexed { index, perks ->
                DestinyCollectionPerkColumn(definition.rollColumnLabels.getOrNull(index) ?: "槽位 ${index + 1}", perks.map { it.toCollectionPerk() })
            }.toMutableList()
        }
        if (definition.hasCatalystSlot && definedColumns.size < 4) {
            definedColumns += DestinyCollectionPerkColumn("催化剂", definition.catalysts.map { it.toCollectionPerk() })
        }
        while (definedColumns.size < 4) {
            definedColumns += DestinyCollectionPerkColumn("槽位 ${definedColumns.size + 1}", emptyList())
        }
        return DestinyCollectionWeapon(
            damage = profile?.baseDamage ?: computed?.damage?.toFloat() ?: definition.baseDamage,
            precisionMultiplier = profile?.precisionMultiplier ?: definition.precisionMultiplier,
            roundsPerMinute = profile?.roundsPerMinute
                ?: computed?.fireRate?.times(60.0)?.toInt()
                ?: (1200f * definition.frame.burstCount / definition.frame.cooldownTicks.coerceAtLeast(1)).toInt(),
            magazineSize = if (usesMagazine) profile?.magazineSize ?: definition.frame.magazineSize else null,
            reloadTicks = if (usesMagazine) profile?.reloadTicks ?: definition.frame.reloadTicks else null,
            range = profile?.ballistics?.range,
            explosionRadius = definition.explosionRadius,
            columns = definedColumns.take(4)
        )
    }

    private fun GearPerk.toCollectionPerk() = DestinyCollectionPerk(id, displayName, description)
}
