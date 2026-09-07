package atopos.destiny2.client

import atopos.destiny2.client.renderer.LightCollectorRenderer
import atopos.destiny2.client.renderer.DestinyArmorRenderer
import atopos.destiny2.client.renderer.HealingRiftRenderer
import atopos.destiny2.client.renderer.FallenSoldierRenderer
import atopos.destiny2.client.renderer.FallenCaptainRenderer
import atopos.destiny2.client.renderer.HuntingMarkRenderer
import atopos.destiny2.client.renderer.IncineratorSnapProjectileRenderer
import atopos.destiny2.client.renderer.OrbOfPowerRenderer
import atopos.destiny2.client.renderer.JilingRenderer
import atopos.destiny2.client.renderer.TaczGunPackItemRenderer
import atopos.destiny2.client.renderer.GenericGunPackItemRenderer
import atopos.destiny2.client.renderer.PerfectRetrogradeItemRenderer
import atopos.destiny2.client.renderer.ShimmeringCrystalOreRenderer
import atopos.destiny2.client.renderer.SphereModelBlockRenderer
import atopos.destiny2.client.renderer.StrykersSureHandItemRenderer
import atopos.destiny2.client.renderer.WellOfRadianceRenderer
import atopos.destiny2.client.renderer.ThunderclapPlayerProxyRenderer
import atopos.destiny2.common.block.DestinyBlocks
import atopos.destiny2.common.entity.DestinyEntities
import atopos.destiny2.common.item.DestinyArmorItem
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.item.TaczGunPackWeaponItem
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.renderer.entity.NoopRenderer
import net.minecraft.client.renderer.entity.ThrownItemRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.registries.BuiltInRegistries

object ClientRenderers {
    fun register() {
        registerEntityRenderers()
        registerBlockEntityRenderers()
        atopos.destiny2.client.renderer.GuiItemModelFit.register()
        registerItemRenderers()
    }

    private fun registerEntityRenderers() {
        EntityRendererRegistry.register(DestinyEntities.FIRESPRITE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.ORB_OF_POWER, ::OrbOfPowerRenderer)
        EntityRendererRegistry.register(DestinyEntities.THUNDERCLAP_PLAYER_PROXY, ::ThunderclapPlayerProxyRenderer)
        EntityRendererRegistry.register(DestinyEntities.SOLAR_GRENADE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.HEALING_GRENADE, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.HEALING_GRENADE_ORB, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.FIREBOLT_GRENADE, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.FUSION_GRENADE, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.SOLAR_ERUPTION_PROJECTILE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.INCINERATOR_SNAP_PROJECTILE, ::IncineratorSnapProjectileRenderer)
        EntityRendererRegistry.register(DestinyEntities.MICRO_MISSILE_PROJECTILE, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.FORGOTTEN_NAME_BULLET, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.HUNTING_MARK, ::HuntingMarkRenderer)
        EntityRendererRegistry.register(DestinyEntities.FORGOTTEN_REMNANT, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.VOID_GRENADE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.SNARE_BOMB, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.SHADOWSHOT_ANCHOR, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.SOLAR_FLARE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.HEALING_RIFT, ::HealingRiftRenderer)
        EntityRendererRegistry.register(DestinyEntities.WELL_OF_RADIANCE, ::WellOfRadianceRenderer)
        EntityRendererRegistry.register(DestinyEntities.VOID_VORTEX, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.VOID_TETHER, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.ARC_PULSE_GRENADE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.ARC_FLASHBANG_GRENADE, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.ARC_LIGHTNING_GRENADE, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.ARC_STORM_GRENADE, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.ARC_ABILITY_DAMAGE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.ARC_TITAN_BARRICADE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.FALLEN_SOLDIER, ::FallenSoldierRenderer)
        EntityRendererRegistry.register(DestinyEntities.FALLEN_CAPTAIN, ::FallenCaptainRenderer)
        EntityRendererRegistry.register(DestinyEntities.FALLEN_CAPTAIN_PELLET, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.JILING, ::JilingRenderer)
    }

    private fun registerBlockEntityRenderers() {
        BlockEntityRendererRegistry.register(
            DestinyBlocks.LIGHT_COLLECTOR_BLOCK_ENTITY,
            ::LightCollectorRenderer
        )
        BlockEntityRendererRegistry.register(
            DestinyBlocks.SHIMMERING_CRYSTAL_ORE_BLOCK_ENTITY,
            ::ShimmeringCrystalOreRenderer
        )
        BlockEntityRendererRegistry.register(
            DestinyBlocks.SPHERE_MODEL_BLOCK_ENTITY,
            ::SphereModelBlockRenderer
        )
    }

    private fun registerItemRenderers() {
        BuiltinItemRendererRegistry.INSTANCE.register(DestinyItems.PERFECT_RETROGRADE, PerfectRetrogradeItemRenderer())
        BuiltinItemRendererRegistry.INSTANCE.register(
            DestinyItems.STRYKERS_SURE_HAND,
            StrykersSureHandItemRenderer()
        )
        BuiltinItemRendererRegistry.INSTANCE.register(
            DestinyItems.GENERIC_GUN,
            GenericGunPackItemRenderer.INSTANCE
        )
        BuiltInRegistries.ITEM
            .filterIsInstance<TaczGunPackWeaponItem>()
            .forEach { item ->
                BuiltinItemRendererRegistry.INSTANCE.register(item, TaczGunPackItemRenderer())
            }
        ArmorRenderer.register(
            ArmorRenderer { matrices, _, stack, entity, slot, light, contextModel ->
                if (stack.item !is DestinyArmorItem) {
                    return@ArmorRenderer
                }
                val renderer = DestinyArmorRenderer()
                renderer.prepForRender(entity, stack, slot, contextModel)
                renderer.renderToBuffer(matrices, null, light, OverlayTexture.NO_OVERLAY, -1)
            },
            DestinyItems.OVERLOAD_HELMET,
            DestinyItems.YANYANG_CHESTPLATE,
            DestinyItems.BAORAN_LEGGINGS
        )
    }
}
