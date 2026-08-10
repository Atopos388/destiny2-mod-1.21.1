package atopos.destiny2.client.util

import atopos.destiny2.Destiny2MODClient
import dev.kosmx.playerAnim.api.layered.IAnimation
import dev.kosmx.playerAnim.api.layered.ModifierLayer
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier
import dev.kosmx.playerAnim.core.data.KeyframeAnimation
import dev.kosmx.playerAnim.core.util.Ease
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.util.WeakHashMap

object PlayerAnimationHelper {
    private val logger = LoggerFactory.getLogger("destiny2-mod")
    private data class PendingFadeOut(
        val layer: ModifierLayer<IAnimation>,
        val fadeAtMs: Long,
        val blendTicks: Int
    )

    private val pendingFadeOuts = WeakHashMap<AbstractClientPlayer, PendingFadeOut>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { updateTransitions() }
    }

    fun isActionActive(player: AbstractClientPlayer): Boolean =
        Destiny2MODClient.animationLayers[player]?.isActive == true

    fun forceThirdPersonFor(durationMs: Long) {
        val client = Minecraft.getInstance()
        if (client.options.cameraType.isFirstPerson) {
            client.options.cameraType = CameraType.THIRD_PERSON_BACK
        }
        Destiny2MODClient.cameraResetTime = System.currentTimeMillis() + durationMs
        Destiny2MODClient.shouldResetToFirstPerson = true
    }

    fun playAnimation(
        player: AbstractClientPlayer,
        animationName: String,
        modId: String = "destiny2-mod",
        forceThirdPerson: Boolean = true,
        cameraResetDelayMs: Long? = null,
        blendInTicks: Int = 3,
        blendOutTicks: Int = 5
    ): Boolean = playAnimation(
        player = player,
        animationId = ResourceLocation.fromNamespaceAndPath(modId, animationName),
        forceThirdPerson = forceThirdPerson,
        cameraResetDelayMs = cameraResetDelayMs,
        blendInTicks = blendInTicks,
        blendOutTicks = blendOutTicks
    )

    fun playAnimation(
        player: AbstractClientPlayer,
        animationId: ResourceLocation,
        forceThirdPerson: Boolean = true,
        cameraResetDelayMs: Long? = null,
        blendInTicks: Int = 3,
        blendOutTicks: Int = 5
    ): Boolean {
        val animationLayer = Destiny2MODClient.animationLayers[player] ?: findOrInjectLayer(player)
        if (animationLayer == null) {
            logger.warn("Could not get or inject animation layer. Animation failed.")
            return false
        }

        val playable = PlayerAnimationRegistry.getAnimation(animationId)
        if (playable == null) {
            logger.warn("Animation file not found: {}", animationId)
            return false
        }

        val playerAnimation = playable.playAnimation()
        pendingFadeOuts.remove(player)
        if (blendInTicks > 0) {
            animationLayer.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(blendInTicks, Ease.INOUTSINE),
                playerAnimation,
                true
            )
        } else {
            animationLayer.setAnimation(playerAnimation)
        }

        val durationMs = cameraResetDelayMs
            ?: ((playable as? KeyframeAnimation)?.endTick?.times(50L) ?: 0L)
        if (blendOutTicks > 0) {
            val fadeDurationMs = blendOutTicks * 50L
            pendingFadeOuts[player] = PendingFadeOut(
                animationLayer,
                System.currentTimeMillis() + (durationMs - fadeDurationMs).coerceAtLeast(0L),
                blendOutTicks
            )
        }
        updateCameraForAnimation(player, forceThirdPerson, durationMs)
        return true
    }

    private fun updateTransitions() {
        if (pendingFadeOuts.isEmpty()) return
        val now = System.currentTimeMillis()
        val iterator = pendingFadeOuts.entries.iterator()
        while (iterator.hasNext()) {
            val (_, transition) = iterator.next()
            if (now < transition.fadeAtMs) continue
            transition.layer.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(transition.blendTicks, Ease.INOUTSINE),
                null,
                true
            )
            iterator.remove()
        }
    }

    private fun updateCameraForAnimation(
        player: AbstractClientPlayer,
        forceThirdPerson: Boolean,
        durationMs: Long
    ) {
        val client = Minecraft.getInstance()
        if (player != client.player || !forceThirdPerson) {
            return
        }

        if (client.options.cameraType.isFirstPerson) {
            client.options.cameraType = CameraType.THIRD_PERSON_BACK
        }
        Destiny2MODClient.cameraResetTime = System.currentTimeMillis() + durationMs
        Destiny2MODClient.shouldResetToFirstPerson = true
    }

    private fun findOrInjectLayer(player: AbstractClientPlayer): ModifierLayer<IAnimation>? {
        return try {
            val rootAnim = PlayerAnimationAccess.getPlayerAnimLayer(player) ?: return null
            val layersField = findLayersField(rootAnim.javaClass) ?: return null
            layersField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val layers = layersField.get(rootAnim) as? MutableList<Any> ?: return null
            recoverExistingLayer(player, layers) ?: injectLayer(player, layers)
        } catch (e: Exception) {
            logger.warn("Failed to recover animation layer.", e)
            null
        }
    }

    private fun findLayersField(startClass: Class<*>): Field? {
        var currentClass: Class<*>? = startClass
        while (currentClass != null) {
            currentClass.declaredFields.firstOrNull { List::class.java.isAssignableFrom(it.type) }?.let {
                return it
            }
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun recoverExistingLayer(
        player: AbstractClientPlayer,
        layers: MutableList<Any>
    ): ModifierLayer<IAnimation>? {
        for (obj in layers) {
            val value = getPairValue(obj)
            if (value is ModifierLayer<*>) {
                @Suppress("UNCHECKED_CAST")
                val recovered = value as ModifierLayer<IAnimation>
                Destiny2MODClient.animationLayers[player] = recovered
                return recovered
            }
        }
        return null
    }

    private fun injectLayer(
        player: AbstractClientPlayer,
        layers: MutableList<Any>
    ): ModifierLayer<IAnimation>? {
        val newLayer = ModifierLayer<IAnimation>()
        val pairInstance = createPair(42, newLayer) ?: return null
        layers.add(pairInstance)
        Destiny2MODClient.animationLayers[player] = newLayer
        return newLayer
    }

    private fun createPair(priority: Int, layer: Any): Any? {
        return try {
            val pairClass = Class.forName("dev.kosmx.playerAnim.core.util.Pair")
            val ctor = pairClass.getConstructor(Any::class.java, Any::class.java)
            ctor.newInstance(priority, layer)
        } catch (e: Exception) {
            logger.warn("Failed to create animation layer pair.", e)
            null
        }
    }

    private fun getPairValue(pairObj: Any): Any? {
        return try {
            for (fieldName in listOf("right", "b", "second", "value")) {
                try {
                    val field = pairObj.javaClass.getDeclaredField(fieldName)
                    field.isAccessible = true
                    return field.get(pairObj)
                } catch (_: NoSuchFieldException) {
                }
            }

            val fields = pairObj.javaClass.declaredFields
            if (fields.size >= 2) {
                fields[1].isAccessible = true
                fields[1].get(pairObj)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Failed to inspect animation pair.", e)
            null
        }
    }
}
