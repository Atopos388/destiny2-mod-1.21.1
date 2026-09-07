package atopos.destiny2.common.item

import atopos.destiny2.common.block.DestinyBlocks
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import atopos.destiny2.common.weapon.DestinyWeaponDataRegistry

object DestinyCreativeModeTabs {
    val DESTINY_TAB_KEY: ResourceKey<CreativeModeTab> = ResourceKey.create(
        Registries.CREATIVE_MODE_TAB,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "general")
    )

    val DESTINY_TAB: CreativeModeTab = Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        DESTINY_TAB_KEY,
        FabricItemGroup.builder()
            .icon { ItemStack(DestinyItems.PERFECT_RETROGRADE) }
            .title(Component.translatable("itemGroup.destiny2-mod.general"))
            .build()
    )

    fun register() {
        ItemGroupEvents.modifyEntriesEvent(DESTINY_TAB_KEY).register { content ->
            val debugBlocks = setOf(DestinyBlocks.SPHERE_MODEL_BLOCK.asItem())
            DestinyBlocks.creativeTabItems().filterNot { it in debugBlocks }.forEach(content::accept)
            val weapons = listOf(DestinyItems.FORGOTTEN_NAME, DestinyItems.PERFECT_RETROGRADE, DestinyItems.STRYKERS_SURE_HAND)
            weapons.forEach(content::accept)
            DestinyWeaponDataRegistry.ids()
                .filterNot { it == LEGACY_FORGOTTEN_NAME }
                .sortedBy { it.toString() }
                .map(GenericGunPackItem::stack)
                .forEach(content::accept)
            val testing = setOf(DestinyItems.MICRO_MISSILE_TEST, DestinyItems.FALLEN_CAPTAIN_SPAWN_EGG,
                DestinyItems.JILING_SPAWN_EGG, DestinyItems.VOID_BREACH)
            val remaining = DestinyItems.creativeTabItems().filterNot { it in weapons }
            remaining.filterNot { it in testing }.forEach(content::accept)
            remaining.filter { it in testing }.forEach(content::accept)
            debugBlocks.forEach(content::accept)
        }
    }

    private val LEGACY_FORGOTTEN_NAME =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name")
}

