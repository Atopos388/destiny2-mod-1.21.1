package atopos.destiny2.common.item

import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.player.DestinyClassType
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.player.GrenadeMemoryRules
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
    val GHOST_CORE = register(
        "ghost_core",
        GhostCoreItem(Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant())
    )

    val LIGHT_CRYSTAL = register(
        "light_crystal",
        LightCrystalItem(Item.Properties().stacksTo(64).rarity(Rarity.RARE))
    )

    val GLIMMER = registerSimple("glimmer", Item.Properties().stacksTo(64).rarity(Rarity.COMMON))

    val STRANGE_COIN = registerSimple("strange_coin", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val RIGID_SYNTHCORD = registerSimple("rigid_synthcord", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val SYNTHWEAVE_PLATE = registerSimple("synthweave_plate", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val SLEEK_SYNTHCORD = registerSimple("sleek_synthcord", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val LEGENDARY_SHARD = registerSimple("legendary_shard", Item.Properties().stacksTo(64).rarity(Rarity.RARE))

    val FORGOTTEN_HEART = registerSimple("forgotten_heart", Item.Properties().stacksTo(64).rarity(Rarity.RARE))
    val ANCIENT_SHARD = registerSimple("ancient_shard", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val EXOTIC_ENGRAM = registerSimple("exotic_engram", Item.Properties().stacksTo(16).rarity(Rarity.EPIC))

    val PRIMARY_AMMO = registerSimple("primary_ammo", Item.Properties().stacksTo(64).rarity(Rarity.COMMON))

    val SPECIAL_AMMO = registerSimple("special_ammo", Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON))

    val HEAVY_AMMO = registerSimple("heavy_ammo", Item.Properties().stacksTo(64).rarity(Rarity.RARE))

    /** Void subclass pickup; grants class-ability energy and is consumed on contact. */
    val VOID_BREACH = registerSimple("void_breach", Item.Properties().stacksTo(1).rarity(Rarity.RARE))

    /** Temporary world marker used by the server-owned ally revive flow. */
    val REVIVE_GHOST = registerSimple("revive_ghost", Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant())

    /** Arc subclass pickup that homes to its owner and grants ability/Bolt Charge energy. */
    val IONIC_TRACE = registerSimple("ionic_trace", Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant())

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

    /**
     * TaCZ-style shared registry item. The selected gun definition is stored
     * on each stack, so future packs do not require another item registration.
     */
    val GENERIC_GUN = register(
        "gun",
        GenericGunPackItem(Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    )

    /** Legendary heavy sword: Stryker's Sure-Hand. */
    val STRYKERS_SURE_HAND = register(
        "strykers_sure_hand",
        StrykersSureHandItem(Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant())
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

    val JILING_SPAWN_EGG = register(
        "jiling_spawn_egg",
        SpawnEggItem(
            DestinyEntities.JILING,
            0x25282D,
            0x57DDF4,
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
        DestinyClassItem(DestinyClassType.HUNTER, Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    )

    val WARLOCK_BOND = register(
        "warlock_bond",
        DestinyClassItem(DestinyClassType.WARLOCK, Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    )

    val TITAN_MARK = register(
        "titan_mark",
        DestinyClassItem(DestinyClassType.TITAN, Item.Properties().stacksTo(1).rarity(Rarity.RARE))
    )

    val GRENADE_MEMORY = register(
        "grenade_memory",
        GrenadeMemoryItem(
            properties = Item.Properties().stacksTo(16).rarity(Rarity.RARE)
        )
    )

    // Keep registry IDs so existing stacks become beacon materials instead of disappearing.
    val VOID_SUPER_MEMORY = register("void_grenade_memory", Item(Item.Properties().stacksTo(16).rarity(Rarity.RARE)))
    val ARC_SUPER_MEMORY = register("arc_grenade_memory", Item(Item.Properties().stacksTo(16).rarity(Rarity.RARE)))
    val SOLAR_SUPER_MEMORY = register("solar_grenade_memory", Item(Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant()))
    val COMBAT_MEMORY = register("combat_memory", Item(Item.Properties().stacksTo(16).rarity(Rarity.RARE)))
    fun register() {
        // Touching this object registers all item constants above.
    }

    fun creativeTabItems(): List<Item> {
        return buildList {
            add(GHOST_CORE)
            add(LIGHT_CRYSTAL)
            add(FORGOTTEN_NAME)
            add(PERFECT_RETROGRADE)
            add(STRYKERS_SURE_HAND)
            add(MICRO_MISSILE_TEST)
            add(FALLEN_CAPTAIN_SPAWN_EGG)
            add(JILING_SPAWN_EGG)
            add(VOID_BREACH)
            add(GLIMMER)
            add(STRANGE_COIN)
            add(RIGID_SYNTHCORD)
            add(SYNTHWEAVE_PLATE)
            add(SLEEK_SYNTHCORD)
            add(LEGENDARY_SHARD)
            add(FORGOTTEN_HEART)
            add(ANCIENT_SHARD)
            add(EXOTIC_ENGRAM)
            add(OVERLOAD_HELMET)
            add(YANYANG_CHESTPLATE)
            add(BAORAN_LEGGINGS)
            add(HUNTER_CLOAK)
            add(WARLOCK_BOND)
            add(TITAN_MARK)
            add(GRENADE_MEMORY)
            add(COMBAT_MEMORY)
            add(VOID_SUPER_MEMORY)
            add(ARC_SUPER_MEMORY)
            add(SOLAR_SUPER_MEMORY)
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

