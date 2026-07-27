package atopos.destiny2.common.ability

import net.minecraft.resources.ResourceLocation

object DestinySubclasses {
    // Example Abilities
    val GOLDEN_GUN = DestinyAbility(
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "golden_gun"),
        AbilityType.SUPER,
        "Golden Gun",
        "Summon a flaming pistol that disintegrates enemies with Solar Light.",
        300,
        DamageType.SOLAR,
        listOf("ignite")
    )

    val DODGE = DestinyAbility(
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "marksman_dodge"),
        AbilityType.CLASS_ABILITY,
        "Marksman's Dodge",
        "Dodge to reload your weapon.",
        20,
        DamageType.KINETIC
    )
    
    val TRIPLE_JUMP = DestinyAbility(
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "triple_jump"),
        AbilityType.JUMP,
        "Triple Jump",
        "Sustain control with a second and third jump.",
        0,
        DamageType.KINETIC
    )

    // Example Subclass: Gunslinger (Solar Hunter)
    val GUNSLINGER = DestinySubclass(
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "gunslinger"),
        "Gunslinger",
        DamageType.SOLAR,
        GOLDEN_GUN,
        DODGE,
        listOf(TRIPLE_JUMP),
        listOf(), // Melees
        listOf()  // Grenades
    )
    
    // In a full implementation, we would have Voidwalker, Striker, etc.
}
