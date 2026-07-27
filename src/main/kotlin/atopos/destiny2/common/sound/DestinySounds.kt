package atopos.destiny2.common.sound

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

object DestinySounds {
    val FORGOTTEN_NAME_FIRE = register("forgotten_name_fire")
    val FORGOTTEN_NAME_RELOAD = register("forgotten_name_reload")
    val FORGOTTEN_NAME_RELOAD_CLOSE = register("forgotten_name_reload_close")
    val FORGOTTEN_NAME_RELOAD_INSERT = register("forgotten_name_reload_insert")
    val FORGOTTEN_NAME_DRAW = register("forgotten_name_draw")
    val FORGOTTEN_NAME_INSPECT = register("forgotten_name_inspect")
    val FORGOTTEN_NAME_READY = register("forgotten_name_ready")

    val FALLEN_SHOCK_RIFLE_FIRE = register("fallen_shock_rifle_fire")
    val FALLEN_CAPTAIN_ROAR = register("fallen_captain_roar")
    val FALLEN_CAPTAIN_MELEE_SWING = register("fallen_captain_melee_swing")

    val VOID_GRENADE_CAST = register("void_grenade_cast")
    val VOID_GRENADE_IMPACT = register("void_grenade_impact")
    val SNARE_BOMB_CAST = register("snare_bomb_cast")
    val SNARE_BOMB_IMPACT = register("snare_bomb_impact")
    val SHADOWSHOT_CAST = register("shadowshot_cast")
    val SHADOWSHOT_IMPACT = register("shadowshot_impact")
    val GAMBLER_DODGE = register("gambler_dodge")
    val VOID_INVISIBILITY = register("void_invisibility")

    val SOLAR_GRENADE_CAST = register("solar_grenade_cast")
    val SOLAR_GRENADE_IMPACT = register("solar_grenade_impact")
    val INCINERATOR_SNAP = register("incinerator_snap")
    val HEALING_RIFT_CAST = register("healing_rift_cast")
    val WELL_OF_RADIANCE_CAST = register("well_of_radiance_cast")
    val WELL_OF_RADIANCE_IMPACT = register("well_of_radiance_impact")
    val HEAT_RISES_ACTIVATE = register("heat_rises_activate")
    val GUARDIAN_AWAKENING = register("guardian_awakening")

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
