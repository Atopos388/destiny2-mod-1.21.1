package atopos.destiny2.common.item

import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.player.DestinyClassType
import atopos.destiny2.common.entity.DestinyEntities
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ArmorMaterials
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.SpawnEggItem

object DestinyItems {
    val GHOST_CORE = registerSimple("ghost_core", Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant())

    val GLIMMER = registerSimple("glimmer", Item.Properties().stacksTo(64).rarity(Rarity.COMMON))

    val STRANGE_COIN = registerSimple("strange_coin", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val RIGID_SYNTHCORD = registerSimple("rigid_synthcord", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val SYNTHWEAVE_PLATE = registerSimple("synthweave_plate", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val SLEEK_SYNTHCORD = registerSimple("sleek_synthcord", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val LEGENDARY_SHARD = registerSimple("legendary_shard", Item.Properties().stacksTo(64).rarity(Rarity.RARE))

    val EXOTIC_ENGRAM = registerSimple("exotic_engram", Item.Properties().stacksTo(16).rarity(Rarity.EPIC))

    val PRIMARY_AMMO = registerSimple("primary_ammo", Item.Properties().stacksTo(64).rarity(Rarity.COMMON))

    val SPECIAL_AMMO = registerSimple("special_ammo", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val HEAVY_AMMO = registerSimple("heavy_ammo", Item.Properties().stacksTo(64).rarity(Rarity.RARE))

    /** World pickup consumed directly by ArmorModRuntime; it never enters normal inventory. */
    val ORB_OF_POWER = registerSimple("orb_of_power", Item.Properties().stacksTo(1).rarity(Rarity.RARE))

    @Deprecated("Use HEAVY_AMMO; retained for old worlds and item ids")
    val PERFECT_RETROGRADE_AMMO = registerSimple("perfect_retrograde_ammo", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val PERFECT_RETROGRADE = register(
        "perfect_retrograde",
        PerfectRetrogradeItem(Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    )

    val FORGOTTEN_NAME = register(
        "forgotten_name",
        ForgottenNameItem(Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    )

    val IZANAGIS_BURDEN = register(
        "izanagis_burden",
        IzanagiBurdenItem(Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    )

    val MICRO_MISSILE_TEST = register(
        "micro_missile_test",
        MicroMissileBurstWeaponItem(Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    )

    val FALLEN_CAPTAIN_SPAWN_EGG = register(
        "fallen_captain_spawn_egg",
        SpawnEggItem(
            DestinyEntities.FALLEN_CAPTAIN,
            0x182431,
            0x77C9E8,
            Item.Properties()
        )
    )

    val OVERLOAD_HELMET = register(
        "overload_helmet",
        DestinyArmorItem(
            ArmorMaterials.NETHERITE,
            ArmorItem.Type.HELMET,
            Item.Properties().stacksTo(1).rarity(Rarity.RARE),
            DestinyArmorModelSet.CHAOZAI
        )
    )

    val YANYANG_CHESTPLATE = register(
        "yanyang_chestplate",
        DestinyArmorItem(
            ArmorMaterials.NETHERITE,
            ArmorItem.Type.CHESTPLATE,
            Item.Properties().stacksTo(1).rarity(Rarity.RARE),
            DestinyArmorModelSet.YANYANG
        )
    )

    val BAORAN_LEGGINGS = register(
        "baoran_leggings",
        DestinyArmorItem(
            ArmorMaterials.NETHERITE,
            ArmorItem.Type.LEGGINGS,
            Item.Properties().stacksTo(1).rarity(Rarity.RARE),
            DestinyArmorModelSet.BAORAN
        )
    )

    val HUNTER_CLOAK = register(
        "hunter_cloak",
        DestinyClassItem(DestinyClassType.HUNTER, "猎人披风", Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    )

    val WARLOCK_BOND = register(
        "warlock_bond",
        DestinyClassItem(DestinyClassType.WARLOCK, "术士臂环", Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    )

    val TITAN_MARK = register(
        "titan_mark",
        DestinyClassItem(DestinyClassType.TITAN, "泰坦印记", Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    )

    fun register() {
        // Touching this object registers all item constants above.
    }

    fun creativeTabItems(): List<Item> {
        return buildList {
            add(GHOST_CORE)
            add(FORGOTTEN_NAME)
            add(IZANAGIS_BURDEN)
            add(PERFECT_RETROGRADE)
            add(MICRO_MISSILE_TEST)
            add(FALLEN_CAPTAIN_SPAWN_EGG)
            add(PRIMARY_AMMO)
            add(SPECIAL_AMMO)
            add(HEAVY_AMMO)
            add(GLIMMER)
            add(STRANGE_COIN)
            add(RIGID_SYNTHCORD)
            add(SYNTHWEAVE_PLATE)
            add(SLEEK_SYNTHCORD)
            add(LEGENDARY_SHARD)
            add(EXOTIC_ENGRAM)
            add(OVERLOAD_HELMET)
            add(YANYANG_CHESTPLATE)
            add(BAORAN_LEGGINGS)
            add(HUNTER_CLOAK)
            add(WARLOCK_BOND)
            add(TITAN_MARK)
        }
    }

    fun ammoItem(type: DestinyAmmoType): Item = when (type) {
        DestinyAmmoType.PRIMARY -> PRIMARY_AMMO
        DestinyAmmoType.SPECIAL -> SPECIAL_AMMO
        DestinyAmmoType.HEAVY -> HEAVY_AMMO
    }

    private fun registerSimple(path: String, properties: Item.Properties): Item {
        return register(path, Item(properties))
    }

    private fun <T : Item> register(path: String, item: T): T {
        return Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", path),
            item
        )
    }
}
