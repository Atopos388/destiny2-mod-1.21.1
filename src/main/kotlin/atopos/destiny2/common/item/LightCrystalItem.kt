package atopos.destiny2.common.item

import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class LightCrystalItem(properties: Properties) : Item(properties) {
    override fun isFoil(stack: ItemStack): Boolean = true
}
