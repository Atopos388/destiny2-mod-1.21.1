package atopos.destiny2.common.item

import atopos.destiny2.common.weapon.TaczGunPackItem
import net.minecraft.world.item.Item
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.util.GeckoLibUtil

/**
 * Shared registry/rendering base for data-driven TaCZ-style gun packs.
 * Concrete subclasses still own their server-authoritative gameplay traits.
 */
abstract class TaczGunPackWeaponItem(properties: Properties) :
    Item(properties),
    GeoItem,
    TaczGunPackItem {

    private val gunPackAnimationCache: AnimatableInstanceCache =
        GeckoLibUtil.createInstanceCache(this)

    init {
        GeoItem.registerSyncedAnimatable(this)
    }

    final override fun getAnimatableInstanceCache(): AnimatableInstanceCache =
        gunPackAnimationCache
}
