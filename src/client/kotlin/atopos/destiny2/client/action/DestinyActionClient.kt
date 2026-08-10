package atopos.destiny2.client.action

import atopos.destiny2.client.camera.VoidHunterSuperCameraClient
import atopos.destiny2.client.camera.ThunderclapCameraClient
import atopos.destiny2.client.renderer.ThunderclapPlayerProxyClient
import atopos.destiny2.client.util.PlayerAnimationHelper
import atopos.destiny2.common.action.DestinyActionBackend
import atopos.destiny2.common.action.DestinyActionCameraPolicy
import atopos.destiny2.common.action.DestinyActionDefinition
import atopos.destiny2.common.action.DestinyActionRegistry
import atopos.destiny2.common.entity.ThunderclapPlayerProxyEntity
import com.mojang.brigadier.Command
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry
import net.fabricmc.loader.api.FabricLoader
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
                literal("destinyui")
                    .then(
                        literal("action")
                            .then(
                                literal("check").executes { context ->
                                    DestinyActionRegistry.definitions().forEach { definition ->
                                        context.source.sendFeedback(Component.literal(audit(definition)))
                                    }
                                    Command.SINGLE_SUCCESS
                                }
                            )
                            .then(
                                literal("bendtest").executes { context ->
                                    if (!FabricLoader.getInstance().isModLoaded("bendy-lib")) {
                                        context.source.sendFeedback(Component.literal("BendyLib 未加载，膝盖弯曲不可用"))
                                        return@executes 0
                                    }
                                    val player = Minecraft.getInstance().player ?: return@executes 0
                                    val played = PlayerAnimationHelper.playAnimation(
                                        player = player,
                                        animationId = ResourceLocation.fromNamespaceAndPath(
                                            "destiny2-mod",
                                            "bendy_knee_test"
                                        ),
                                        forceThirdPerson = true,
                                        cameraResetDelayMs = 3_000L,
                                        blendInTicks = 2,
                                        blendOutTicks = 3
                                    )
                                    context.source.sendFeedback(
                                        Component.literal(
                                            if (played) "开始播放无缝膝盖弯曲测试"
                                            else "膝盖测试动画资源未加载"
                                        )
                                    )
                                    if (played) Command.SINGLE_SUCCESS else 0
                                }
                            )
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
            DestinyActionBackend.PLAYER_LAYER -> {
                val usesShoulderCamera = localPlayer && definition.thirdPersonCameraDurationMs != null
                val thunderclapPhase = when (definition.id) {
                    DestinyActionRegistry.ARC_TITAN_THUNDERCLAP_CHARGE ->
                        ThunderclapPlayerProxyEntity.Phase.CHARGE
                    DestinyActionRegistry.ARC_TITAN_THUNDERCLAP_RELEASE ->
                        ThunderclapPlayerProxyEntity.Phase.RELEASE
                    else -> null
                }
                val usesThunderclapCamera = localPlayer && thunderclapPhase != null
                val played = if (thunderclapPhase != null) {
                    ThunderclapPlayerProxyClient.play(player, thunderclapPhase, definition.durationTicks)
                } else {
                    PlayerAnimationHelper.playAnimation(
                        player = player,
                        animationId = definition.animationId,
                        forceThirdPerson = localPlayer &&
                            definition.cameraPolicy == DestinyActionCameraPolicy.THIRD_PERSON &&
                            !usesShoulderCamera &&
                            !usesThunderclapCamera,
                        cameraResetDelayMs = definition.durationMs,
                        blendInTicks = definition.blendInTicks,
                        blendOutTicks = definition.blendOutTicks
                    )
                }
                if (played && usesShoulderCamera) {
                    VoidHunterSuperCameraClient.start(
                        durationMs = definition.thirdPersonCameraDurationMs!!,
                        rightOffsetBlocks = definition.thirdPersonRightOffsetBlocks
                    )
                }
                if (played) {
                    when (definition.id) {
                        DestinyActionRegistry.ARC_TITAN_THUNDERCLAP_CHARGE ->
                            ThunderclapCameraClient.startCharge(player, definition.durationTicks)
                        DestinyActionRegistry.ARC_TITAN_THUNDERCLAP_RELEASE ->
                            ThunderclapCameraClient.startRelease(player, definition.durationTicks)
                        else -> Unit
                    }
                }
                played
            }
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
