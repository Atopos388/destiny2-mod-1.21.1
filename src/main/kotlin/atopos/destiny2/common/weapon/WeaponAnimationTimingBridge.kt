package atopos.destiny2.common.weapon

import net.minecraft.resources.ResourceLocation

/** Keeps common GeckoLib controllers independent of client HUD classes. */
object WeaponAnimationTimingBridge {
    @Volatile
    var reloadTotalTicksProvider: ((weaponId: ResourceLocation) -> Int)? = null

    fun reloadTotalTicks(weaponId: ResourceLocation): Int =
        reloadTotalTicksProvider?.invoke(weaponId)?.coerceAtLeast(0) ?: 0
}
