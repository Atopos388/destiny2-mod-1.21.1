package atopos.destiny2.common.gear

import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.common.stats.StatType
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import kotlin.random.Random

object GearRolls {
    private const val ROOT = "DestinyGearRoll"
    private const val VERSION = 15
    private const val WEAPON_LAYOUT_VERSION = 11
    const val ARMOR_ENERGY_CAPACITY = 10

    data class Roll(
        val version: Int,
        val definitionId: ResourceLocation,
        val rarity: GearRarity,
        val frameId: ResourceLocation,
        val perkIds: List<ResourceLocation>,
        val power: Int,
        val catalystId: ResourceLocation? = null,
        val armorArchetype: ArmorStatArchetype? = null,
        val armorStats: DestinyStats? = null,
        val armorTier: Int = 0,
        val armorModIds: List<ResourceLocation?> = List(ARMOR_MOD_SOCKET_COUNT) { null }
    )

    fun ensureRoll(stack: ItemStack, random: Random = Random.Default, powerOverride: Int? = null): Boolean {
        val definition = GearRegistry.definitionFor(stack) ?: return false
        val existingRoll = read(stack)
        val needsWeaponLayoutUpgrade = existingRoll != null &&
            definition.category == GearCategory.WEAPON &&
            existingRoll.version < WEAPON_LAYOUT_VERSION
        val needsArmorStatsUpgrade = existingRoll != null &&
            definition.category == GearCategory.ARMOR && existingRoll.armorStats == null
        val needsPowerUpgrade = existingRoll != null && existingRoll.power <= 0
        val expectedPerkCount = if (definition.rarity == GearRarity.EXOTIC) {
            definition.fixedPerks.size
        } else {
            definition.perkColumns.size
        }
        if (existingRoll != null &&
            !needsWeaponLayoutUpgrade &&
            !needsArmorStatsUpgrade &&
            !needsPowerUpgrade &&
            existingRoll.perkIds.size == expectedPerkCount
        ) {
            return false
        }
        if (existingRoll != null && needsPowerUpgrade &&
            !needsWeaponLayoutUpgrade && !needsArmorStatsUpgrade &&
            existingRoll.perkIds.size == expectedPerkCount
        ) {
            write(stack, existingRoll.copy(version = VERSION, power = powerOverride ?: defaultPower(definition)))
            return true
        }
        write(stack, generate(definition, random, powerOverride))
        return true
    }

    /** 强制重铸，用于测试或后续重铸系统。 */
    fun reroll(stack: ItemStack, random: Random = Random.Default): Boolean {
        val definition = GearRegistry.definitionFor(stack) ?: return false
        val preservedPower = read(stack)?.power?.takeIf { it > 0 }
        write(stack, generate(definition, random, preservedPower))
        return true
    }

    fun read(stack: ItemStack): Roll? {
        val root = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompound(ROOT) ?: return null
        val definitionId = root.getString("definition")
        val frameId = root.getString("frame")
        if (definitionId.isBlank() || frameId.isBlank()) {
            return null
        }

        val rarity = runCatching { GearRarity.valueOf(root.getString("rarity")) }.getOrNull() ?: return null
        val perks = root.getList("perks", 8).mapNotNull { tag ->
            runCatching { ResourceLocation.parse(tag.asString) }.getOrNull()
        }

        val armorArchetype = root.getString("armor_archetype")
            .takeIf(String::isNotBlank)
            ?.let { runCatching { ArmorStatArchetype.valueOf(it) }.getOrNull() }
        val armorStats = if (root.contains("armor_stats")) {
            val statsTag = root.getCompound("armor_stats")
            DestinyStats(
                statsTag.getInt("weapons"),
                statsTag.getInt("health"),
                statsTag.getInt("class"),
                statsTag.getInt("grenade"),
                statsTag.getInt("super"),
                statsTag.getInt("melee")
            )
        } else null

        return Roll(
            version = root.getInt("version"),
            definitionId = ResourceLocation.parse(definitionId),
            rarity = rarity,
            frameId = ResourceLocation.parse(frameId),
            perkIds = perks,
            power = root.getInt("power"),
            catalystId = root.getString("catalyst")
                .takeIf(String::isNotBlank)
                ?.let { runCatching { ResourceLocation.parse(it) }.getOrNull() },
            armorArchetype = armorArchetype,
            armorStats = armorStats,
            armorTier = root.getInt("armor_tier"),
            armorModIds = readArmorMods(root)
        )
    }

    fun rollPerks(stack: ItemStack): List<GearPerk> {
        val roll = read(stack) ?: return emptyList()
        return buildList {
            addAll(roll.perkIds.mapNotNull(GearRegistry::perk))
            roll.catalystId?.let(GearRegistry::perk)?.let(::add)
        }
    }

    fun power(stack: ItemStack): Int = read(stack)?.power?.coerceAtLeast(0) ?: 0

    fun equippedCatalyst(stack: ItemStack): GearPerk? = read(stack)?.catalystId?.let(GearRegistry::perk)

    fun equippedArmorMods(stack: ItemStack): List<ArmorModDefinition?> =
        read(stack)?.armorModIds.orEmpty().let { ids -> List(ARMOR_MOD_SOCKET_COUNT) { ArmorModRegistry.get(ids.getOrNull(it)) } }

    fun equippedArmorStatMod(stack: ItemStack): ArmorModDefinition? = equippedArmorMods(stack).firstOrNull()

    fun armorEnergyUsed(stack: ItemStack): Int = equippedArmorMods(stack).sumOf { it?.energyCost ?: 0 }

    /** Server-authoritative fixed four-socket mutation: stat mod first, three slot-specific mods after it. */
    fun setArmorMod(stack: ItemStack, socketIndex: Int, modId: ResourceLocation?): Boolean {
        val definition = GearRegistry.definitionFor(stack) ?: return false
        if (definition.category != GearCategory.ARMOR) return false
        if (socketIndex !in 0 until ARMOR_MOD_SOCKET_COUNT) return false
        val roll = read(stack) ?: return false
        if (roll.armorStats == null) return false
        val armorSlot = DestinyArmorSlot.from(stack) ?: return false
        val mod = ArmorModRegistry.get(modId)
        if (modId != null && mod == null) return false
        if (mod != null) {
            if (socketIndex == 0 && mod.kind != ArmorModKind.STAT) return false
            if (socketIndex > 0 && (mod.kind != ArmorModKind.SLOT || armorSlot !in mod.allowedSlots)) return false
        }
        val installed = roll.armorModIds.toMutableList().also { while (it.size < ARMOR_MOD_SOCKET_COUNT) it += null }
        if (installed[socketIndex] == modId) return false
        if (mod != null && mod.stackRule == ArmorModStackRule.NO_DUPLICATES &&
            installed.withIndex().any { (index, id) -> index != socketIndex && id == modId }
        ) return false
        installed[socketIndex] = modId
        val energy = installed.sumOf { ArmorModRegistry.get(it)?.energyCost ?: 0 }
        if (energy > ARMOR_ENERGY_CAPACITY) return false
        write(stack, roll.copy(armorModIds = installed.take(ARMOR_MOD_SOCKET_COUNT)))
        return true
    }

    fun setArmorStatMod(stack: ItemStack, modId: ResourceLocation?): Boolean = setArmorMod(stack, 0, modId)

    /** Server-authoritative mutation for one random-roll perk column. Exotic fixed traits are immutable. */
    fun setPerk(stack: ItemStack, columnIndex: Int, perkId: ResourceLocation): Boolean {
        val definition = GearRegistry.definitionFor(stack) ?: return false
        if (definition.category != GearCategory.WEAPON || definition.rarity == GearRarity.EXOTIC) return false
        val column = definition.perkColumns.getOrNull(columnIndex) ?: return false
        if (column.none { it.id == perkId }) return false
        val roll = read(stack) ?: return false
        if (roll.perkIds.size != definition.perkColumns.size || roll.perkIds[columnIndex] == perkId) return false
        val perks = roll.perkIds.toMutableList()
        perks[columnIndex] = perkId
        write(stack, roll.copy(perkIds = perks))
        return true
    }

    /** Server-side mutation entry point. The candidate must belong to this gear definition. */
    fun setCatalyst(stack: ItemStack, catalystId: ResourceLocation?): Boolean {
        val definition = GearRegistry.definitionFor(stack) ?: return false
        if (!definition.hasCatalystSlot) return false
        if (catalystId != null && definition.catalysts.none { it.id == catalystId }) return false
        val roll = read(stack) ?: return false
        if (roll.catalystId == catalystId) return false
        write(stack, roll.copy(catalystId = catalystId))
        return true
    }

    fun damageMultiplier(stack: ItemStack): Float {
        return rollPerks(stack).fold(1.0f) { value, perk -> value * perk.damageMultiplier }
    }

    fun projectileSpeedMultiplier(stack: ItemStack): Float {
        return rollPerks(stack).fold(1.0f) { value, perk -> value * perk.projectileSpeedMultiplier }
    }

    fun projectileInaccuracyMultiplier(stack: ItemStack): Float {
        return rollPerks(stack).fold(1.0f) { value, perk -> value * perk.projectileInaccuracyMultiplier }.coerceAtLeast(0.1f)
    }

    fun explosionRadiusMultiplier(stack: ItemStack): Float {
        return rollPerks(stack).fold(1.0f) { value, perk -> value * perk.explosionRadiusMultiplier }.coerceAtLeast(0.1f)
    }

    fun cooldownMultiplier(stack: ItemStack): Float {
        return rollPerks(stack).fold(1.0f) { value, perk -> value * perk.cooldownMultiplier }
    }

    fun ammoRefundChance(stack: ItemStack): Float {
        return rollPerks(stack).sumOf { it.ammoRefundChance.toDouble() }.toFloat().coerceIn(0.0f, 1.0f)
    }

    fun reloadTimeMultiplier(stack: ItemStack): Float {
        return rollPerks(stack).fold(1.0f) { value, perk -> value * perk.reloadTimeMultiplier }.coerceAtLeast(0.1f)
    }

    fun magazineSizeBonus(stack: ItemStack): Int {
        return rollPerks(stack).sumOf { it.magazineSizeBonus }
    }

    private fun generate(definition: GearDefinition, random: Random, powerOverride: Int? = null): Roll {
        val perks = if (definition.rarity == GearRarity.EXOTIC) {
            definition.fixedPerks
        } else {
            definition.perkColumns.mapNotNull { column ->
                column.takeIf { it.isNotEmpty() }?.get(random.nextInt(column.size))
            }
        }

        val armorRoll = if (definition.category == GearCategory.ARMOR) generateArmorStats(definition, random) else null
        return Roll(
            version = VERSION,
            definitionId = definition.id,
            rarity = definition.rarity,
            frameId = definition.frame.id,
            perkIds = perks.map { it.id },
            power = (powerOverride ?: defaultPower(definition)).coerceIn(GuardianPowerBounds.MIN, GuardianPowerBounds.MAX),
            catalystId = null,
            armorArchetype = armorRoll?.first,
            armorStats = armorRoll?.second,
            armorTier = armorRoll?.third ?: 0,
            armorModIds = List(ARMOR_MOD_SOCKET_COUNT) { null }
        )
    }

    private fun write(stack: ItemStack, roll: Roll) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
            data.update { tag ->
                val root = CompoundTag()
                root.putInt("version", VERSION)
                root.putString("definition", roll.definitionId.toString())
                root.putString("rarity", roll.rarity.name)
                root.putString("frame", roll.frameId.toString())
                root.putInt("power", roll.power.coerceAtLeast(0))
                val perks = ListTag()
                roll.perkIds.forEach { perks.add(StringTag.valueOf(it.toString())) }
                root.put("perks", perks)
                roll.catalystId?.let { root.putString("catalyst", it.toString()) }
                roll.armorArchetype?.let { root.putString("armor_archetype", it.name) }
                val armorMods = ListTag()
                roll.armorModIds.take(ARMOR_MOD_SOCKET_COUNT).forEach { id ->
                    armorMods.add(StringTag.valueOf(id?.toString().orEmpty()))
                }
                if (armorMods.isNotEmpty()) root.put("armor_mods", armorMods)
                roll.armorStats?.let { stats ->
                    val statsTag = CompoundTag()
                    statsTag.putInt("weapons", stats.weapons)
                    statsTag.putInt("health", stats.health)
                    statsTag.putInt("class", stats.classAbility)
                    statsTag.putInt("grenade", stats.grenade)
                    statsTag.putInt("super", stats.superStat)
                    statsTag.putInt("melee", stats.melee)
                    root.put("armor_stats", statsTag)
                    root.putInt("armor_tier", roll.armorTier)
                }
                tag.put(ROOT, root)
            }
        }
    }

    private fun generateArmorStats(
        definition: GearDefinition,
        random: Random
    ): Triple<ArmorStatArchetype, DestinyStats, Int> {
        val tier = armorTier(definition)
        val generated = generateArmorStatsForTier(tier, random)
        return Triple(generated.first, generated.second, tier)
    }

    private fun readArmorMods(root: CompoundTag): List<ResourceLocation?> {
        if (root.contains("armor_mods")) {
            val stored = root.getList("armor_mods", 8).map { tag ->
                tag.asString.takeIf(String::isNotBlank)?.let { runCatching { ResourceLocation.parse(it) }.getOrNull() }
            }
            return List(ARMOR_MOD_SOCKET_COUNT) { stored.getOrNull(it) }
        }
        // v13 migration: preserve the old single stat mod in socket zero.
        val legacy = root.getString("armor_stat_mod")
            .takeIf(String::isNotBlank)
            ?.let { runCatching { ResourceLocation.parse(it) }.getOrNull() }
        return List(ARMOR_MOD_SOCKET_COUNT) { index -> if (index == 0) legacy else null }
    }

    internal fun generateArmorStatsForTier(
        tier: Int,
        random: Random
    ): Pair<ArmorStatArchetype, DestinyStats> {
        val range = when (tier) {
            1 -> 24..30
            2 -> 32..38
            3 -> 40..46
            5 -> 56..62
            else -> 48..54
        }
        val total = random.nextInt(range.first, range.last + 1)
        val archetype = ArmorStatArchetype.entries.random(random)
        val third = StatType.entries.filter { it != archetype.primary && it != archetype.secondary }.random(random)
        val values = IntArray(StatType.entries.size)
        values[archetype.primary.ordinal] = (total * 0.50f).toInt()
        values[archetype.secondary.ordinal] = (total * 0.35f).toInt()
        values[third.ordinal] = total - values.sum()
        return Pair(
            archetype,
            DestinyStats(values[0], values[1], values[2], values[3], values[4], values[5])
        )
    }

    private fun armorTier(definition: GearDefinition): Int {
        val path = definition.id.path
        return when {
            definition.rarity == GearRarity.EXOTIC -> 5
            definition.id.namespace == "destiny2-mod" -> 4
            path.startsWith("leather_") || path.startsWith("golden_") -> 1
            path.startsWith("chainmail_") || path.startsWith("iron_") -> 2
            path.startsWith("diamond_") -> 3
            else -> 4
        }
    }

    fun defaultPower(definition: GearDefinition): Int {
        if (definition.rarity == GearRarity.EXOTIC) return 200
        if (definition.category == GearCategory.ARMOR) {
            return when (armorTier(definition)) {
                1 -> 100
                2 -> 125
                3 -> 150
                5 -> 200
                else -> 175
            }
        }
        val path = definition.id.path
        return when {
            path.contains("wooden_") || path.contains("golden_") -> 100
            path.contains("stone_") -> 110
            path.contains("iron_") -> 130
            path.contains("diamond_") -> 150
            path.contains("netherite_") -> 180
            definition.id.namespace == "destiny2-mod" -> 175
            else -> 130
        }
    }

    const val ARMOR_MOD_SOCKET_COUNT = 4
}

private object GuardianPowerBounds {
    const val MIN = 100
    const val MAX = 200
}
