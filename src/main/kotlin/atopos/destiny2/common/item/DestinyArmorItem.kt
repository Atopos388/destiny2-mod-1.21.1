package atopos.destiny2.common.item

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ArmorMaterial
import net.minecraft.core.Holder
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

class DestinyArmorItem(
    material: Holder<ArmorMaterial>,
    type: Type,
    properties: Properties,
    val modelSet: DestinyArmorModelSet
) : ArmorItem(material, type, properties), GeoItem {
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "armor_controller", 0) { state ->
                state.controller.setAnimation(RawAnimation.begin().thenLoop(modelSet.animationName))
                PlayState.CONTINUE
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return cache
    }
}

enum class DestinyArmorModelSet(
    val model: ResourceLocation,
    val texture: ResourceLocation,
    val animation: ResourceLocation,
    val animationName: String
) {
    BAORAN(
        model = destinyArmorAsset("geo/baoran.geo.json"),
        texture = destinyArmorAsset("textures/models/armor/baoran_leggings.png"),
        animation = destinyArmorAsset("animations/baoran.animation.json"),
        animationName = "xz"
    ),
    CHAOZAI(
        model = destinyArmorAsset("geo/chaozai.geo.json"),
        texture = destinyArmorAsset("textures/models/armor/chaozai.png"),
        animation = destinyArmorAsset("animations/chaozai.animation.json"),
        animationName = "chaozai"
    ),
    YANYANG(
        model = destinyArmorAsset("geo/yanyang.geo.json"),
        texture = destinyArmorAsset("textures/models/armor/yanyang.png"),
        animation = destinyArmorAsset("animations/yanyang.animation.json"),
        animationName = "1"
    )
}

private fun destinyArmorAsset(path: String): ResourceLocation {
    return ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
}
