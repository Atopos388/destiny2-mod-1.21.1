// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.action

import net.minecraft.resources.ResourceLocation

/** Rendering backend used by one player action. Gameplay always stays server-authoritative. */
enum class DestinyActionBackend {
    PLAYER_LAYER
}

enum class DestinyActionCameraPolicy {
    KEEP_CURRENT,
    THIRD_PERSON
}

/**
 * TaCZ-style action contract for Destiny abilities.
 *
 * The action id is what travels over the network. Animation paths, duration,
 * camera behaviour and renderer choice remain client-resolvable configuration.
 */
data class DestinyActionDefinition(
    val id: ResourceLocation,
    val animationId: ResourceLocation,
    val backend: DestinyActionBackend,
    val durationTicks: Int,
    val cameraPolicy: DestinyActionCameraPolicy = DestinyActionCameraPolicy.KEEP_CURRENT,
    val animationResource: ResourceLocation? = null,
    val speed: Double = 1.0,
    val blendInTicks: Int = 3,
    val blendOutTicks: Int = 5,
    val thirdPersonCameraDurationMs: Long? = null,
    val thirdPersonRightOffsetBlocks: Double = 0.0
) {
    val durationMs: Long
        get() = durationTicks.coerceAtLeast(1) * 50L
}

/** Central action catalogue and ability-to-action mapping. */
object DestinyActionRegistry {
    private const val MOD_ID = "destiny2-mod"
    private val definitions = linkedMapOf<ResourceLocation, DestinyActionDefinition>()
    private val abilityActions = linkedMapOf<ResourceLocation, ResourceLocation>()

    val HEALING_RIFT_CAST = id("action/healing_rift_cast")
    val INCINERATOR_SNAP_CAST = id("action/incinerator_snap_cast")
    val VOID_HUNTER_SHADOWSHOT_CAST = id("action/void_hunter_shadowshot_cast")
    val ARC_TITAN_THUNDERCLAP_CHARGE = id("action/arc_titan_thunderclap_charge")
    val ARC_TITAN_THUNDERCLAP_RELEASE = id("action/arc_titan_thunderclap_release")
    init {
        register(
            DestinyActionDefinition(
                id = INCINERATOR_SNAP_CAST,
                animationId = id("xiangzhi"),
                backend = DestinyActionBackend.PLAYER_LAYER,
                durationTicks = 26,
                cameraPolicy = DestinyActionCameraPolicy.KEEP_CURRENT,
                blendInTicks = 1,
                blendOutTicks = 3
            )
        )
        bindAbility(id("solar_warlock_incinerator_snap"), INCINERATOR_SNAP_CAST)

        register(
            DestinyActionDefinition(
                id = HEALING_RIFT_CAST,
                animationId = id("huoshuz"),
                backend = DestinyActionBackend.PLAYER_LAYER,
                durationTicks = 20,
                cameraPolicy = DestinyActionCameraPolicy.THIRD_PERSON,
                blendInTicks = 4,
                blendOutTicks = 6
            )
        )
        bindAbility(id("solar_warlock_healing_rift"), HEALING_RIFT_CAST)

        register(
            DestinyActionDefinition(
                id = VOID_HUNTER_SHADOWSHOT_CAST,
                animationId = id("void_hunter_shadowshot"),
                backend = DestinyActionBackend.PLAYER_LAYER,
                durationTicks = 29,
                cameraPolicy = DestinyActionCameraPolicy.THIRD_PERSON,
                blendInTicks = 2,
                blendOutTicks = 0,
                thirdPersonCameraDurationMs = 920L,
                thirdPersonRightOffsetBlocks = 0.58
            )
        )
        bindAbility(id("void_hunter_shadowshot"), VOID_HUNTER_SHADOWSHOT_CAST)

        register(
            DestinyActionDefinition(
                id = ARC_TITAN_THUNDERCLAP_CHARGE,
                animationId = id("thunderclap_charge"),
                backend = DestinyActionBackend.PLAYER_LAYER,
                durationTicks = 41,
                cameraPolicy = DestinyActionCameraPolicy.THIRD_PERSON,
                blendInTicks = 1,
                blendOutTicks = 1
            )
        )
        bindAbility(id("arc_titan_thunderclap"), ARC_TITAN_THUNDERCLAP_CHARGE)

        register(
            DestinyActionDefinition(
                id = ARC_TITAN_THUNDERCLAP_RELEASE,
                animationId = id("thunderclap_release"),
                backend = DestinyActionBackend.PLAYER_LAYER,
                durationTicks = ThunderclapTiming.RELEASE_DURATION_TICKS,
                cameraPolicy = DestinyActionCameraPolicy.THIRD_PERSON,
                blendInTicks = 0,
                blendOutTicks = 2
            )
        )
    }

    fun definition(id: ResourceLocation): DestinyActionDefinition? = definitions[id]

    fun definitionForAbility(abilityId: ResourceLocation): DestinyActionDefinition? =
        abilityActions[abilityId]?.let(definitions::get)

    fun definitions(): Collection<DestinyActionDefinition> = definitions.values.toList()

    private fun register(definition: DestinyActionDefinition) {
        check(definitions.putIfAbsent(definition.id, definition) == null) {
            "Duplicate Destiny action id: ${definition.id}"
        }
    }

    private fun bindAbility(abilityId: ResourceLocation, actionId: ResourceLocation) {
        check(actionId in definitions) { "Unknown Destiny action id: $actionId" }
        abilityActions[abilityId] = actionId
    }

    private fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
}
