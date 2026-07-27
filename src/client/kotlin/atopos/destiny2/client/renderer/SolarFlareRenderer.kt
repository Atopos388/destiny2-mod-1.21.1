package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.SolarFlareModel
import atopos.destiny2.common.entity.SolarFlareEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import software.bernie.geckolib.renderer.GeoEntityRenderer

class SolarFlareRenderer(renderManager: EntityRendererProvider.Context) :
    GeoEntityRenderer<SolarFlareEntity>(renderManager, SolarFlareModel()) {
}
