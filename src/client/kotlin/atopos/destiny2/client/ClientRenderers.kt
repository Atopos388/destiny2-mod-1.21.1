package atopos.destiny2.client

import atopos.destiny2.client.renderer.DestinyArmorRenderer
import atopos.destiny2.client.renderer.HealingRiftRenderer
import atopos.destiny2.client.renderer.FallenCaptainRenderer
import atopos.destiny2.client.renderer.HuntingMarkRenderer
import atopos.destiny2.client.renderer.IncineratorSnapProjectileRenderer
import atopos.destiny2.client.renderer.TaczGunPackItemRenderer
import atopos.destiny2.client.renderer.PerfectRetrogradeItemRenderer
import atopos.destiny2.client.renderer.ShimmeringCrystalOreRenderer
import atopos.destiny2.client.renderer.SphereModelBlockRenderer
import atopos.destiny2.client.renderer.WellOfRadianceRenderer
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
        registerItemRenderers()
    }

    private fun registerEntityRenderers() {
        EntityRendererRegistry.register(DestinyEntities.SOLAR_GRENADE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.SOLAR_ERUPTION_PROJECTILE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.INCINERATOR_SNAP_PROJECTILE, ::IncineratorSnapProjectileRenderer)
        EntityRendererRegistry.register(DestinyEntities.MICRO_MISSILE_PROJECTILE, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.FORGOTTEN_NAME_BULLET, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.HUNTING_MARK, ::HuntingMarkRenderer)
        EntityRendererRegistry.register(DestinyEntities.FORGOTTEN_REMNANT, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.VOID_GRENADE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.SNARE_BOMB, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.SHADOWSHOT_ANCHOR, ::ThrownItemRenderer)
        EntityRendererRegistry.register(DestinyEntities.SOLAR_FLARE, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.HEALING_RIFT, ::HealingRiftRenderer)
        EntityRendererRegistry.register(DestinyEntities.WELL_OF_RADIANCE, ::WellOfRadianceRenderer)
        EntityRendererRegistry.register(DestinyEntities.VOID_VORTEX, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.VOID_TETHER, ::NoopRenderer)
        EntityRendererRegistry.register(DestinyEntities.FALLEN_CAPTAIN, ::FallenCaptainRenderer)
        EntityRendererRegistry.register(DestinyEntities.FALLEN_CAPTAIN_PELLET, ::NoopRenderer)
    }

    private fun registerBlockEntityRenderers() {
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
