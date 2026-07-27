package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.SolarGrenadeModel
import atopos.destiny2.common.entity.SolarGrenadeEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

class SolarGrenadeRenderer(renderManager: EntityRendererProvider.Context) :
    GeoEntityRenderer<SolarGrenadeEntity>(renderManager, SolarGrenadeModel()) {
}
