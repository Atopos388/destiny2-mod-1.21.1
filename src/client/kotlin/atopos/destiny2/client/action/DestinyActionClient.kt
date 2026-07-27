package atopos.destiny2.client.action

import atopos.destiny2.client.util.PlayerAnimationHelper
import atopos.destiny2.common.action.DestinyActionBackend
import atopos.destiny2.common.action.DestinyActionCameraPolicy
import atopos.destiny2.common.action.DestinyActionDefinition
import atopos.destiny2.common.action.DestinyActionRegistry
import com.mojang.brigadier.Command
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.slf4j.LoggerFactory
import java.util.UUID

/** Resolves networked action ids into the appropriate local animation backend. */
object DestinyActionClient {
    private val logger = LoggerFactory.getLogger("DestinyAction")

    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                literal("destinyaction")
                    .then(
                        literal("check").executes { context ->
                            DestinyActionRegistry.definitions().forEach { definition ->
                                context.source.sendFeedback(Component.literal(audit(definition)))
                            }
                            Command.SINGLE_SUCCESS
                        }
                    )
            )
        }
    }

    fun play(playerId: UUID, actionId: String): Boolean {
        val parsedId = ResourceLocation.tryParse(actionId)
        val definition = parsedId?.let(DestinyActionRegistry::definition)
        if (definition == null) {
            logger.warn("Unknown Destiny action: {}", actionId)
            return false
        }

        val client = Minecraft.getInstance()
        val player = client.level?.getPlayerByUUID(playerId) as? AbstractClientPlayer
        if (player == null) {
            logger.debug("Player {} is not visible for action {}", playerId, actionId)
            return false
        }

        val localPlayer = client.player?.uuid == playerId
        if (localPlayer && definition.id == DestinyActionRegistry.INCINERATOR_SNAP_CAST) {
            IncineratorSnapFirstPersonClient.play()
        }
        return when (definition.backend) {
            DestinyActionBackend.PLAYER_LAYER -> PlayerAnimationHelper.playAnimation(
                player = player,
                animationId = definition.animationId,
                forceThirdPerson = localPlayer && definition.cameraPolicy == DestinyActionCameraPolicy.THIRD_PERSON,
                cameraResetDelayMs = definition.durationMs,
                blendInTicks = definition.blendInTicks,
                blendOutTicks = definition.blendOutTicks
            )
        }
    }

    private fun audit(definition: DestinyActionDefinition): String {
        val ready = when (definition.backend) {
            DestinyActionBackend.PLAYER_LAYER ->
                PlayerAnimationRegistry.getAnimation(definition.animationId) != null
        }
        return buildString {
            append(definition.id).append(": ")
            append(if (ready) "就绪" else "缺少动画资源")
            append(" · ").append(definition.backend.name.lowercase())
            append(" · ").append(definition.durationTicks).append(" tick")
        }
    }
}
