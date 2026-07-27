package atopos.destiny2.common.item

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.WeaponThirdPersonAction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil

class PerfectRetrogradeItem(properties: Properties) : MicroMissileBurstWeaponItem(properties), GeoItem {
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    init {
        GeoItem.registerSyncedAnimatable(this)
    }

    override fun triggerShootAnimation(level: Level, shooter: LivingEntity, stack: ItemStack) {
        if (!level.isClientSide && level is ServerLevel) {
            triggerAnim<PerfectRetrogradeItem>(shooter, GeoItem.getOrAssignId(stack, level), "shoot_controller", "shoot")
            (shooter as? ServerPlayer)?.let {
                DestinyNetworking.broadcastWeaponThirdPersonAction(it, WeaponThirdPersonAction.SHOOT, 6)
            }
        }
    }

    override fun triggerReloadAnimation(level: Level, shooter: LivingEntity, stack: ItemStack) {
        if (!level.isClientSide && level is ServerLevel) {
            triggerAnim<PerfectRetrogradeItem>(shooter, GeoItem.getOrAssignId(stack, level), "shoot_controller", "reload")
            (shooter as? ServerPlayer)?.let {
                DestinyNetworking.broadcastWeaponThirdPersonAction(
                    it,
                    WeaponThirdPersonAction.RELOAD,
                    combatProfile(stack).reloadTicks
                )
            }
        }
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "shoot_controller", 0) { PlayState.CONTINUE }
                .triggerableAnim("shoot", RawAnimation.begin().thenPlay("shoot"))
                .triggerableAnim("reload", RawAnimation.begin().thenPlay("reload"))
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache {
        return cache
    }
}
