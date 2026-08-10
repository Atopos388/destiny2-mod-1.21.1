package atopos.destiny2.client

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW

object DestinyKeybindings {
    
    // 手雷 (G)
    lateinit var GRENADE_KEY: KeyMapping
    
    // 近战 (C)
    lateinit var MELEE_KEY: KeyMapping
    
    // 职业技能 (V)
    lateinit var CLASS_ABILITY_KEY: KeyMapping
    
    // 终极技能 (F)
    lateinit var SUPER_ABILITY_KEY: KeyMapping
    lateinit var ICARUS_DASH_KEY: KeyMapping

    lateinit var HUD_EDITOR_KEY: KeyMapping
    lateinit var LDLIB_EDITOR_KEY: KeyMapping
    lateinit var RELOAD_WEAPON_KEY: KeyMapping
    lateinit var FIRE_MODE_KEY: KeyMapping
    lateinit var INSPECT_WEAPON_KEY: KeyMapping

    fun register() {
        GRENADE_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.grenade_ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "category.destiny2-mod.controls"
        ))

        MELEE_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.melee_ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "category.destiny2-mod.controls"
        ))

        CLASS_ABILITY_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.class_ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "category.destiny2-mod.controls"
        ))

        SUPER_ABILITY_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.super_ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "category.destiny2-mod.controls"
        ))

        ICARUS_DASH_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.icarus_dash",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "category.destiny2-mod.controls"
        ))

        HUD_EDITOR_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.hud_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.destiny2-mod.controls"
        ))

        LDLIB_EDITOR_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.ldlib_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "category.destiny2-mod.controls"
        ))

        RELOAD_WEAPON_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.reload_weapon",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.destiny2-mod.controls"
        ))

        FIRE_MODE_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.fire_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "category.destiny2-mod.controls"
        ))

        INSPECT_WEAPON_KEY = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.destiny2-mod.inspect_weapon",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "category.destiny2-mod.controls"
        ))


    }
}
