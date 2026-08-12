package atopos.destiny2

import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.entity.DestinyEntities
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.gear.GearRollEvents
import atopos.destiny2.common.block.DestinyBlocks
import atopos.destiny2.common.item.DestinyCreativeModeTabs
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.loot.DestinyLoot
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.DestinyPlayerCommands
import atopos.destiny2.common.player.GuardianAwakeningRuntime
import atopos.destiny2.common.player.ClassResonanceRuntime
import atopos.destiny2.common.player.LightCrystalRuntime
import atopos.destiny2.common.sound.DestinySounds
import atopos.destiny2.common.ability.DestinyAbilityRegistry
import atopos.destiny2.common.ability.ArcTitanAbilities
import atopos.destiny2.common.ability.GamblerDodgeAbility
import atopos.destiny2.common.ability.SolarWarlockAbilities
import atopos.destiny2.common.ability.VoidHunterAbilities
import atopos.destiny2.common.aspect.VoidHunterAspectRuntime
import atopos.destiny2.common.aspect.SolarWarlockFragmentRuntime
import atopos.destiny2.common.aspect.ArcTitanFragmentRuntime
import atopos.destiny2.common.worldgen.DestinyWorldgen
import atopos.destiny2.common.weapon.WeaponHudSync
import atopos.destiny2.common.weapon.ForgottenNameExoticRuntime
import atopos.destiny2.common.weapon.DestinyWeaponDataRegistry
import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object Destiny2MOD : ModInitializer {
    private val logger = LoggerFactory.getLogger("destiny2-mod")

	override fun onInitialize() {
		logger.info("Initializing Destiny 2 Mod...")
        
        DestinyEffects.register()
        DestinyEntities.register()
        DestinySounds.register()
        DestinyBlocks.register()
        DestinyItems.register()
        GearRegistry.register()
        GearRollEvents.register()
        DestinyLoot.register()
        DestinyCreativeModeTabs.register()
        DestinyAbilityRegistry.register()
        ArcTitanAbilities.register()
        SolarWarlockAbilities.register()
        VoidHunterAbilities.register()
        GamblerDodgeAbility.register()
        VoidHunterAspectRuntime.register()
        SolarWarlockFragmentRuntime.register()
        ArcTitanFragmentRuntime.register()
        DestinyWorldgen.register()
        DestinyNetworking.register()
        DestinyWeaponDataRegistry.register()
        WeaponHudSync.register()
        ForgottenNameExoticRuntime.register()
        DestinyPlayerCommands.register()
        GuardianAwakeningRuntime.register()
        ClassResonanceRuntime.register()
        LightCrystalRuntime.register()
        
        logger.info("Destiny 2 Mod Initialized!")
	}
}
