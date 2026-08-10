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
            DestinyBlocks.creativeTabItems().forEach(content::accept)
            DestinyItems.creativeTabItems().forEach(content::accept)
            DestinyWeaponDataRegistry.ids()
                .filterNot { it == LEGACY_FORGOTTEN_NAME }
                .map(GenericGunPackItem::stack)
                .forEach(content::accept)
        }
    }

    private val LEGACY_FORGOTTEN_NAME =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name")
}
