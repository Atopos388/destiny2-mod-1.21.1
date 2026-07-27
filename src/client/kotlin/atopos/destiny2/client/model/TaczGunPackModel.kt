package atopos.destiny2.client.model

import atopos.destiny2.client.weapon.TaczGunPackResources
import atopos.destiny2.common.item.TaczGunPackWeaponItem
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.model.GeoModel

/** Resolves all model assets from a gun-pack definition instead of per-gun code. */
class TaczGunPackModel : GeoModel<TaczGunPackWeaponItem>() {

    override fun getModelResource(animatable: TaczGunPackWeaponItem): ResourceLocation =
        TaczGunPackResources.model(animatable.gunPackId)

    override fun getTextureResource(animatable: TaczGunPackWeaponItem): ResourceLocation =
        TaczGunPackResources.texture(animatable.gunPackId)

    override fun getAnimationResource(animatable: TaczGunPackWeaponItem): ResourceLocation =
        TaczGunPackResources.animation(animatable.gunPackId)
}
