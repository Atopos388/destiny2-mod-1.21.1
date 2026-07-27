package atopos.destiny2.common.player

import atopos.destiny2.common.cinematic.CinematicSessionTracker
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.sound.DestinySounds
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import java.util.UUID

object GuardianAwakeningRuntime {
    private val cinematicSessions = CinematicSessionTracker()

    fun register() {
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ ->
            val player = entity as? ServerPlayer ?: return@register
            markAwakened(player)
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            val player = entity as? ServerPlayer
            player == null || !cinematicSessions.isActive(player.uuid)
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, alive ->
            if (!alive) presentAwakeningIfPending(newPlayer)
        }
        ServerPlayerEvents.JOIN.register(::presentAwakeningIfPending)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            cinematicSessions.clear(handler.player.uuid)
        }
        ServerTickEvents.END_SERVER_TICK.register {
            cinematicSessions.tick()
        }
    }

    private fun markAwakened(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        if (!GuardianAwakeningRules.canAwaken(data.journeyStage, player.isCreative, player.isSpectator)) return

        data.journeyStage = GuardianJourneyStage.AWAKENED
        data.awakeningPresentationPending = true
    }

    private fun presentAwakeningIfPending(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        if (!data.awakeningPresentationPending || data.journeyStage != GuardianJourneyStage.AWAKENED) return
        if (player.isSpectator || cinematicSessions.isActive(player.uuid)) return

        val sessionId = UUID.randomUUID()
        cinematicSessions.start(player.uuid, sessionId, CINEMATIC_SAFETY_TIMEOUT_TICKS)
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.StartCinematicPayload(
                sessionId = sessionId,
                cinematicId = AWAKENING_CINEMATIC_ID,
                anchorX = player.x,
                anchorY = player.y,
                anchorZ = player.z,
                anchorYaw = player.yRot
            )
        )

        // World effects begin with the camera track. The unique reward and text
        // are deferred until the client acknowledges actual completion.
        val level = player.serverLevel()
        level.sendParticles(ParticleTypes.END_ROD, player.x, player.y + 1.0, player.z, 48, 0.8, 1.0, 0.8, 0.035)
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.x, player.y + 1.0, player.z, 32, 0.65, 0.9, 0.65, 0.025)
        level.playSound(null, player.blockPosition(), DestinySounds.GUARDIAN_AWAKENING, SoundSource.PLAYERS, 0.9f, 1.0f)
    }

    fun finishCinematic(player: ServerPlayer, sessionId: UUID) {
        if (!cinematicSessions.finish(player.uuid, sessionId)) return

        val data = PlayerDestinyDataApi.get(player)
        if (!data.awakeningPresentationPending || data.journeyStage != GuardianJourneyStage.AWAKENED) return

        // Consume before granting the unique item so duplicate acknowledgements
        // and callback overlap can never duplicate the reward.
        data.awakeningPresentationPending = false
        giveGhostCore(player)
        player.connection.send(ClientboundSetTitlesAnimationPacket(10, 70, 20))
        player.connection.send(ClientboundSetTitleTextPacket(Component.translatable("journey.destiny2-mod.awakening.title")))
        player.connection.send(ClientboundSetSubtitleTextPacket(Component.translatable("journey.destiny2-mod.awakening.subtitle")))
        player.sendSystemMessage(Component.translatable("journey.destiny2-mod.awakening.message"))
        player.sendSystemMessage(Component.translatable("journey.destiny2-mod.awakening.next_objective"))
        DestinyNetworking.syncPlayerData(player)
    }

    private fun giveGhostCore(player: ServerPlayer) {
        val stack = ItemStack(DestinyItems.GHOST_CORE)
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
    }

    private const val AWAKENING_CINEMATIC_ID = "guardian_awakening"
    private const val CINEMATIC_SAFETY_TIMEOUT_TICKS = 20 * 90
}
