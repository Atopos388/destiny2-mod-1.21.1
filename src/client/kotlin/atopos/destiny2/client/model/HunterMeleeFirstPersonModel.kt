package atopos.destiny2.client.model

import atopos.destiny2.client.combat.HunterMeleeFirstPersonClient
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

class HunterMeleeFirstPersonModel : GeoModel<HunterMeleeFirstPersonClient>() {
    override fun getModelResource(animatable: HunterMeleeFirstPersonClient): ResourceLocation =
        id("geo/hunter_melee_first_person.geo.json")

    override fun getTextureResource(animatable: HunterMeleeFirstPersonClient): ResourceLocation =
        TEXTURE

    override fun getAnimationResource(animatable: HunterMeleeFirstPersonClient): ResourceLocation =
        id("animations/hunter_melee_first_person.animation.json")

    private fun id(path: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)

    companion object {
        val TEXTURE: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "textures/entity/hunter_melee.png")
    }
}
