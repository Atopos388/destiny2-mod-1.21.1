package atopos.destiny2.common.player

import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.EntityType

object GuardianAwakeningEntityTags {
    val ELITES: TagKey<EntityType<*>> = TagKey.create(
        Registries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "awakening_elites")
    )
}

object GuardianAwakeningSafetyRules {
    data class HorizontalOffset(val x: Int, val z: Int)

    fun shouldRemoveOrdinaryMonster(hasCustomName: Boolean, elite: Boolean): Boolean =
        !hasCustomName && !elite

    fun safeSearchOffsets(
        minimumRadius: Int = 24,
        maximumRadius: Int = 72,
        step: Int = 8
    ): List<HorizontalOffset> {
        require(minimumRadius > 0)
        require(maximumRadius >= minimumRadius)
        require(step > 0)

        return buildList {
            var radius = minimumRadius
            while (radius <= maximumRadius) {
                var cursor = -radius
                while (cursor <= radius) {
                    add(HorizontalOffset(cursor, -radius))
                    add(HorizontalOffset(cursor, radius))
                    if (cursor != -radius && cursor != radius) {
                        add(HorizontalOffset(-radius, cursor))
                        add(HorizontalOffset(radius, cursor))
                    }
                    cursor += step
                }
                radius += step
            }
        }.distinct()
    }
}
