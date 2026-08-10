package atopos.destiny2

import atopos.destiny2.client.ClientNetworking
import atopos.destiny2.client.ClientPlayerLifecycle
import atopos.destiny2.client.ClientRenderers
import atopos.destiny2.client.DestinyInputHandler
import atopos.destiny2.client.DestinyKeybindings
import atopos.destiny2.client.action.DestinyActionClient
import atopos.destiny2.client.camera.VoidHunterSuperCameraClient
import atopos.destiny2.client.camera.ThunderclapCameraClient
import atopos.destiny2.client.cinematic.AwakeningDialogueClient
import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.client.gear.GearTooltipClient
import atopos.destiny2.client.gui.DestinyHUDClientCommands
import atopos.destiny2.client.gui.DestinyHUDOverlay
import atopos.destiny2.client.gui.DestinyWeaponHUDOverlay
import atopos.destiny2.client.gui.DestinyWeaponScopeOverlay
import atopos.destiny2.client.gui.DestinyUiLayoutSettings
import atopos.destiny2.client.gui.DestinyDamageNumbers
import atopos.destiny2.client.gui.DestinyNavigationOverlay
import atopos.destiny2.client.gui.DestinyWeaponHUDState
import atopos.destiny2.client.particle.bedrock.BedrockParticleEngine
import atopos.destiny2.client.renderer.HealingRiftScreenOverlay
import atopos.destiny2.client.renderer.HealingRiftWorldRenderer
import atopos.destiny2.client.renderer.GeckoLibMultiTextureResources
import atopos.destiny2.client.renderer.AmplifiedPlayerAuraClient
import atopos.destiny2.client.renderer.AmplifiedSpeedLinesOverlay
import atopos.destiny2.client.renderer.ArcTitanBarricadeWorldRenderer
import atopos.destiny2.client.renderer.ThunderclapBlastRenderer
import atopos.destiny2.client.renderer.ThunderclapGroundLiftRenderer
import atopos.destiny2.client.renderer.ThunderclapPlayerProxyClient
import atopos.destiny2.client.renderer.SnareBombWorldRenderer
import atopos.destiny2.client.renderer.SolarGrenadeWorldRenderer
import atopos.destiny2.client.renderer.VoidGrenadeWorldRenderer
import atopos.destiny2.client.renderer.VoidHunterSuperAuraClient
import atopos.destiny2.client.renderer.WellOfRadianceScreenOverlay
import atopos.destiny2.client.renderer.WellOfRadianceWorldRenderer
import atopos.destiny2.client.util.PlayerAnimationHelper
import atopos.destiny2.client.weapon.TaczWeaponAnimationResources
import atopos.destiny2.client.weapon.TaczGunPackResources
import atopos.destiny2.client.weapon.DestinyWeaponSoundClient
import atopos.destiny2.client.weapon.GenericGunAnimationClient
import atopos.destiny2.common.particle.BedrockParticleKeyframeBridge
import atopos.destiny2.common.particle.BedrockWorldParticleBridge
import atopos.destiny2.common.weapon.WeaponAnimationTimingBridge
import dev.kosmx.playerAnim.api.layered.IAnimation
import dev.kosmx.playerAnim.api.layered.ModifierLayer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.player.AbstractClientPlayer
import java.util.WeakHashMap

object Destiny2MODClient : ClientModInitializer {
    val animationLayers = WeakHashMap<AbstractClientPlayer, ModifierLayer<IAnimation>>()
    val clientCooldowns = HashMap<Int, Pair<Long, Long>>()

    var cameraResetTime: Long = -1L
    var shouldResetToFirstPerson: Boolean = false
    var firstPersonReturnVisualUntil: Long = -1L

    override fun onInitializeClient() {
        BedrockParticleEngine.register()
        AmplifiedPlayerAuraClient.register()
        AmplifiedSpeedLinesOverlay.register()
        ArcTitanBarricadeWorldRenderer.register()
        ThunderclapBlastRenderer.register()
        ThunderclapGroundLiftRenderer.register()
        HealingRiftWorldRenderer.register()
        HealingRiftScreenOverlay.register()
        SnareBombWorldRenderer.register()
        SolarGrenadeWorldRenderer.register()
        VoidGrenadeWorldRenderer.register()
        VoidHunterSuperAuraClient.register()
        WellOfRadianceWorldRenderer.register()
        WellOfRadianceScreenOverlay.register()
        GeckoLibMultiTextureResources.register()
        TaczGunPackResources.register()
        TaczWeaponAnimationResources.register()
        DestinyWeaponSoundClient.register()
        GenericGunAnimationClient.register()
        WeaponAnimationTimingBridge.reloadTotalTicksProvider = { weaponId ->
            DestinyWeaponHUDState.snapshot
                ?.takeIf { it.weaponId == weaponId.toString() && it.reloadRemaining > 0 }
                ?.reloadTotal
                ?: 0
        }
        DestinyActionClient.register()
        VoidHunterSuperCameraClient.register()
        ThunderclapCameraClient.register()
        ThunderclapPlayerProxyClient.register()
        PlayerAnimationHelper.register()
        CinematicCameraClient.register()
        AwakeningDialogueClient.register()
        BedrockParticleKeyframeBridge.listener = BedrockParticleEngine::handleKeyframe
        BedrockWorldParticleBridge.listener = BedrockParticleEngine::playWorld
        DestinyKeybindings.register()
        DestinyInputHandler.register()
        DestinyDamageNumbers.register()

        GearTooltipClient.register()
        DestinyUiLayoutSettings.load()
        HudRenderCallback.EVENT.register(DestinyWeaponScopeOverlay)
        HudRenderCallback.EVENT.register(AwakeningDialogueClient)
        HudRenderCallback.EVENT.register(DestinyHUDOverlay())
        HudRenderCallback.EVENT.register(DestinyWeaponHUDOverlay())
        HudRenderCallback.EVENT.register(DestinyNavigationOverlay)
        DestinyHUDClientCommands.register()

        ClientNetworking.register()
        ClientPlayerLifecycle.register()
        ClientRenderers.register()
    }
}
