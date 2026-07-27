// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.weapon

import net.minecraft.resources.ResourceLocation

/**
 * TaCZ-compatible names used by the GeckoLib weapon animation adapter.
 *
 * Blockbench assets may follow the TaCZ production guide verbatim. The game
 * still renders them with GeckoLib; this contract only standardises names and
 * action transitions, it does not load TaCZ gun-pack assets.
 */
object TaczWeaponAnimationContract {
    const val BASE_CONTROLLER = "tacz_base_controller"
    const val ACTION_CONTROLLER = "tacz_action_controller"
    const val SHOOT_CONTROLLER = "tacz_shoot_controller"

    const val STATIC_IDLE = "static_idle"
    const val STATIC_BOLT_CAUGHT = "static_bolt_caught"
    const val DRAW = "draw"
    const val PUT_AWAY = "put_away"
    const val RELOAD_TACTICAL = "reload_tactical"
    const val RELOAD_EMPTY = "reload_empty"
    const val INSPECT = "inspect"
    const val INSPECT_EMPTY = "inspect_empty"
    const val SHOOT = "shoot"
    const val BOLT = "bolt"
    const val RUN_START = "run_start"
    const val RUN = "run"
    const val RUN_HOLD = "run_hold"
    const val RUN_END = "run_end"
    const val WALK_AIMING = "walk_aiming"
    const val WALK_FORWARD = "walk_forward"
    const val WALK_BACKWARD = "walk_backward"
    const val WALK_SIDEWAY = "walk_sideway"

    const val ROOT_BONE = "root"
    const val CAMERA_BONE = "camera"
    const val CONSTRAINT_BONE = "constraint"
    const val MAGAZINE_BONE = "magazine"
    const val ADDITIONAL_MAGAZINE_BONE = "additional_magazine"
    const val LEFT_HAND_BONE = "lefthand_pos"
    const val RIGHT_HAND_BONE = "righthand_pos"
    const val MUZZLE_FLASH_BONE = "muzzle_flash"
    const val SHELL_BONE = "shell"

    val essentialAnimations = linkedSetOf(
        STATIC_IDLE,
        RELOAD_TACTICAL,
        RELOAD_EMPTY,
        INSPECT,
        SHOOT
    )

    val optionalAnimations = linkedSetOf(
        STATIC_BOLT_CAUGHT,
        DRAW,
        PUT_AWAY,
        INSPECT_EMPTY,
        BOLT,
        RUN_START,
        RUN,
        RUN_HOLD,
        RUN_END,
        WALK_AIMING,
        WALK_FORWARD,
        WALK_BACKWARD,
        WALK_SIDEWAY
    )

    val essentialBones = linkedSetOf(ROOT_BONE, CAMERA_BONE, CONSTRAINT_BONE)

    val workflowBones = linkedSetOf(
        MAGAZINE_BONE,
        ADDITIONAL_MAGAZINE_BONE,
        LEFT_HAND_BONE,
        RIGHT_HAND_BONE,
        MUZZLE_FLASH_BONE,
        SHELL_BONE
    )
}

/** Client-installed queries keep common weapon classes safe on a dedicated server. */
object TaczWeaponAnimationBridge {
    @Volatile
    var animationResolver: (ResourceLocation, String, String) -> String = { _, requested, fallback ->
        if (requested.isBlank()) fallback else requested
    }

    @Volatile
    var locomotionResolver: (ResourceLocation) -> String = { TaczWeaponAnimationContract.STATIC_IDLE }

    fun resolve(weaponId: ResourceLocation, requested: String, fallback: String): String =
        animationResolver(weaponId, requested, fallback)

    fun locomotion(weaponId: ResourceLocation): String = locomotionResolver(weaponId)
}

enum class TaczWeaponAction {
    DRAW,
    PUT_AWAY,
    RELOAD_TACTICAL,
    RELOAD_EMPTY,
    INSPECT,
    INSPECT_EMPTY,
    SHOOT,
    SHOOT_AND_BOLT
}
