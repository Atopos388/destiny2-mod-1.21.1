package atopos.destiny2.common.sound

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

object DestinySounds {
    val HUNTER_CLASS_ACTIVATE = register("hunter_class_activate")
    val FORGOTTEN_NAME_FIRE = register("forgotten_name_fire")
    val FORGOTTEN_NAME_RELOAD = register("forgotten_name_reload")
    val FORGOTTEN_NAME_RELOAD_CLOSE = register("forgotten_name_reload_close")
    val FORGOTTEN_NAME_RELOAD_INSERT = register("forgotten_name_reload_insert")
    val FORGOTTEN_NAME_DRAW = register("forgotten_name_draw")
    val FORGOTTEN_NAME_INSPECT = register("forgotten_name_inspect")
    val FORGOTTEN_NAME_READY = register("forgotten_name_ready")

    val THE_DEICIDE_RELOAD_INSERT = register("the_deicide_reload_insert")
    val THE_DEICIDE_RELOAD_PUMP = register("the_deicide_reload_pump")
    val THE_DEICIDE_FIRE = register("the_deicide_fire")
    val THE_DEICIDE_COCKBACK = register("the_deicide_cockback")
    val THE_DEICIDE_COCKFORWARD = register("the_deicide_cockforward")

    val FALLEN_SHOCK_RIFLE_FIRE = register("fallen_shock_rifle_fire")
    val FALLEN_CAPTAIN_ROAR = register("fallen_captain_roar")
    val FALLEN_CAPTAIN_MELEE_SWING = register("fallen_captain_melee_swing")

    val HUNTER_MELEE_SWING = register("hunter_melee_swing")
    val HUNTER_MELEE_HIT_COMMON = register("hunter_melee_hit_common")
    val HUNTER_MELEE_HIT_FLESH = register("hunter_melee_hit_flesh")

    val VOID_GRENADE_CAST = register("void_grenade_cast")
    val VOID_GRENADE_IMPACT = register("void_grenade_impact")
    val SNARE_BOMB_CAST = register("snare_bomb_cast")
    val SNARE_BOMB_IMPACT = register("snare_bomb_impact")
    val SNARE_BOMB_DEPLOY = register("snare_bomb_deploy")
    val SNARE_BOMB_TRIGGER = register("snare_bomb_trigger")
    val SHADOWSHOT_CAST = register("shadowshot_cast")
    val SHADOWSHOT_IMPACT = register("shadowshot_impact")
    val GAMBLER_DODGE = register("gambler_dodge")
    val VOID_INVISIBILITY = register("void_invisibility")

    val SOLAR_GRENADE_CAST = register("solar_grenade_cast")
    val SOLAR_GRENADE_IMPACT = register("solar_grenade_impact")
    val INCINERATOR_SNAP = register("incinerator_snap")
    val HEALING_RIFT_CAST = register("healing_rift_cast")
    val WELL_OF_RADIANCE_CAST = register("well_of_radiance_cast")
    val WELL_OF_RADIANCE_LOOP = register("well_of_radiance_loop")
    val HEAT_RISES_ACTIVATE = register("heat_rises_activate")
    val GUARDIAN_AWAKENING = register("guardian_awakening")
    val AWAKENING_EYES_UP = register("awakening_eyes_up")
    val AWAKENING_SEARCHED = register("awakening_searched")
    val AWAKENING_GHOST = register("awakening_ghost")
    val AWAKENING_WAIT = register("awakening_wait")
    val AWAKENING_MOVE = register("awakening_move")
    val GHOST_AWAKENING_MOVE_IN = register("awakening_ghost_move_in")
    val GHOST_AWAKENING_MOVE_OUT = register("awakening_ghost_move_out")

    private fun register(path: String): SoundEvent {
        val id = ResourceLocation.fromNamespaceAndPath("destiny2-mod", path)
        return Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id,
            SoundEvent.createVariableRangeEvent(id)
        )
    }

    fun register() {
        // Registration occurs when this object is initialized.
    }
}
