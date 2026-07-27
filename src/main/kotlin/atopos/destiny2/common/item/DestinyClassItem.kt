package atopos.destiny2.common.item

import atopos.destiny2.common.player.DestinyClassType
import net.minecraft.world.item.Item

class DestinyClassItem(
    val requiredClass: DestinyClassType,
    val classItemName: String,
    properties: Properties
) : Item(properties)
