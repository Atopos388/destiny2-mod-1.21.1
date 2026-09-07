package atopos.destiny2.client

import atopos.destiny2.Destiny2MODClient
import dev.kosmx.playerAnim.api.layered.IAnimation
import dev.kosmx.playerAnim.api.layered.ModifierLayer
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import atopos.destiny2.client.gui.DestinyNavigationOverlay
import atopos.destiny2.client.gui.DestinyNavigationState
import atopos.destiny2.client.gui.DestinyWeaponLoadoutState
import net.minecraft.client.CameraType
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.ResourceLocation

object ClientPlayerLifecycle {
    fun register() {
        registerCameraResetTick()
        registerAnimationCacheCleanup()
        registerAnimationFactory()
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            DestinyNavigationOverlay.close()
            DestinyNavigationState.reset()
            DestinyWeaponLoadoutState.reset()
        }
    }

    private fun registerCameraResetTick() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (Destiny2MODClient.cameraResetTime > 0 && client.level != null) {
                if (System.currentTimeMillis() >= Destiny2MODClient.cameraResetTime) {
                    Destiny2MODClient.cameraResetTime = -1L
                    if (
                        Destiny2MODClient.shouldResetToFirstPerson &&
                        client.options.cameraType == CameraType.THIRD_PERSON_BACK
                    ) {
                        client.options.cameraType = CameraType.FIRST_PERSON
                        Destiny2MODClient.firstPersonReturnVisualUntil = System.currentTimeMillis() + CAMERA_RETURN_TRANSITION_MS
                        Destiny2MODClient.shouldResetToFirstPerson = false
                    }
                }
            }
        }
    }

    private fun registerAnimationCacheCleanup() {
        ClientEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity is AbstractClientPlayer) {
                Destiny2MODClient.animationLayers.remove(entity)
            }
        }
    }

    private fun registerAnimationFactory() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "animation"),
            42
        ) { player ->
            val layer = ModifierLayer<IAnimation>()
            Destiny2MODClient.animationLayers[player] = layer
            layer
        }
    }

    private const val CAMERA_RETURN_TRANSITION_MS = 300L
}
