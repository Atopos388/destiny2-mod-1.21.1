package atopos.destiny2.common.effect

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.effect.MobEffect

object DestinyEffects {
    private const val MOD_ID = "destiny2-mod"

    val SCORCH_RAW = ScorchStatusEffect()
    val RADIANT_RAW = RadiantStatusEffect()
    val RESTORATION_RAW = RestorationStatusEffect()

    val AMPLIFIED_RAW = AmplifiedStatusEffect()
    val SPEED_BOOSTER_RAW = SpeedBoosterStatusEffect()
    val ARC_BLIND_RAW = ArcBlindStatusEffect()

    val VOID_INVISIBILITY_RAW = VoidInvisibilityEffect()
    val DEVOUR_RAW = DevourEffect()
    val VOID_OVERSHIELD_RAW = VoidOvershieldEffect()
    val WEAKEN_RAW = WeakenEffect()
    val SUPPRESSION_RAW = SuppressionEffect()
    val VOLATILE_RAW = VolatileEffect()

    lateinit var SCORCH: Holder<MobEffect>
    lateinit var RADIANT: Holder<MobEffect>
    lateinit var RESTORATION: Holder<MobEffect>

    lateinit var AMPLIFIED: Holder<MobEffect>
    lateinit var SPEED_BOOSTER: Holder<MobEffect>
    lateinit var ARC_BLIND: Holder<MobEffect>

    lateinit var VOID_INVISIBILITY: Holder<MobEffect>
    lateinit var DEVOUR: Holder<MobEffect>
    lateinit var VOID_OVERSHIELD: Holder<MobEffect>
    lateinit var WEAKEN: Holder<MobEffect>
    lateinit var SUPPRESSION: Holder<MobEffect>
    lateinit var VOLATILE: Holder<MobEffect>

    fun register() {
        SCORCH = registerEffect("scorch", SCORCH_RAW)
        RADIANT = registerEffect("radiant", RADIANT_RAW)
        RESTORATION = registerEffect("restoration", RESTORATION_RAW)

        AMPLIFIED = registerEffect("amplified", AMPLIFIED_RAW)
        SPEED_BOOSTER = registerEffect("speed_booster", SPEED_BOOSTER_RAW)
        ARC_BLIND = registerEffect("arc_blind", ARC_BLIND_RAW)

        VOID_INVISIBILITY = registerEffect("void_invisibility", VOID_INVISIBILITY_RAW)
        DEVOUR = registerEffect("devour", DEVOUR_RAW)
        VOID_OVERSHIELD = registerEffect("void_overshield", VOID_OVERSHIELD_RAW)
        WEAKEN = registerEffect("weaken", WEAKEN_RAW)
        SUPPRESSION = registerEffect("suppression", SUPPRESSION_RAW)
        VOLATILE = registerEffect("volatile", VOLATILE_RAW)
    }

    private fun registerEffect(name: String, effect: MobEffect): Holder<MobEffect> {
        Registry.register(BuiltInRegistries.MOB_EFFECT, ResourceLocation.fromNamespaceAndPath(MOD_ID, name), effect)
        return BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect)
    }
}
