package atopos.destiny2.client.renderer

import atopos.destiny2.client.action.IncineratorSnapFirstPersonClient
import atopos.destiny2.client.model.IncineratorSnapArmModel
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.renderer.GeoObjectRenderer

class IncineratorSnapArmRenderer(
    model: IncineratorSnapArmModel
) : GeoObjectRenderer<IncineratorSnapFirstPersonClient>(model) {
    override fun getTextureLocation(animatable: IncineratorSnapFirstPersonClient): ResourceLocation =
        Minecraft.getInstance().player?.skin?.texture()
            ?: ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")

    override fun getInstanceId(animatable: IncineratorSnapFirstPersonClient): Long = 0L
}
