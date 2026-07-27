package atopos.destiny2.common.weapon

import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStatsResolver
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import kotlin.math.floor

object AmmoDropSystem {
    fun onWeaponKill(level: ServerLevel, target: LivingEntity, sourceAmmo: DestinyAmmoType, shooter: ServerPlayer? = null) {
        val rule = AmmoDropRules.dropFor(sourceAmmo)
        if (!AmmoDropRules.shouldDrop(sourceAmmo, target.random.nextFloat())) return
        val baseCount = if (rule.maxCount <= rule.minCount) rule.minCount
        else target.random.nextInt(rule.minCount, rule.maxCount + 1)
        val bonusProgress = shooter?.let { DestinyStatFormulas.specialization(DestinyStatsResolver.resolve(it).weapons) } ?: 0.0f
        val adjusted = baseCount * (1.0f + bonusProgress * 0.25f)
        val whole = floor(adjusted).toInt()
        val count = whole + if (target.random.nextFloat() < adjusted - whole) 1 else 0
        val drop = ItemEntity(level, target.x, target.y + target.bbHeight * 0.45, target.z, ItemStack(DestinyItems.ammoItem(rule.type), count))
        drop.setDefaultPickUpDelay()
        level.addFreshEntity(drop)
    }
}
