package atopos.destiny2.common.player

import atopos.destiny2.common.item.DestinyItems
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Server-owned Light Crystal ritual, including reservation, animation, and payout. */
object LightCrystalRuntime {
    const val RITUAL_TICKS = 36
    private const val RING_PARTICLES = 10

    enum class StartResult {
        NOT_AWAKENED,
        WRONG_BLOCK,
        NO_SUNLIGHT,
        ALREADY_CHARGING,
        STARTED
    }

    private data class TargetKey(val dimension: ResourceKey<Level>, val pos: BlockPos)
    private data class Ritual(val playerId: UUID, var remainingTicks: Int = RITUAL_TICKS)

    private val rituals = mutableMapOf<TargetKey, Ritual>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tickServer)
    }

    fun tryBegin(player: ServerPlayer, pos: BlockPos): StartResult {
        val level = player.serverLevel()
        val key = TargetKey(level.dimension(), pos.immutable())
        if (rituals.containsKey(key)) return StartResult.ALREADY_CHARGING

        return when (
            LightCrystalRules.creationResult(
                awakened = PlayerDestinyDataApi.get(player).journeyStage != GuardianJourneyStage.MORTAL,
                ordinaryAmethystBlock = level.getBlockState(pos).`is`(Blocks.AMETHYST_BLOCK),
                daytime = level.isDay,
                skyVisible = level.canSeeSky(pos.above())
            )
        ) {
            LightCrystalRules.CreationResult.NOT_AWAKENED -> StartResult.NOT_AWAKENED
            LightCrystalRules.CreationResult.WRONG_BLOCK -> StartResult.WRONG_BLOCK
            LightCrystalRules.CreationResult.NO_SUNLIGHT -> StartResult.NO_SUNLIGHT
            LightCrystalRules.CreationResult.ALLOWED -> {
                rituals[key] = Ritual(player.uuid)
                player.cooldowns.addCooldown(DestinyItems.GHOST_CORE, RITUAL_TICKS)
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8f, 0.75f)
                StartResult.STARTED
            }
        }
    }

    private fun tickServer(server: MinecraftServer) {
        val iterator = rituals.iterator()
        while (iterator.hasNext()) {
            val (key, ritual) = iterator.next()
            val level = server.getLevel(key.dimension)
            val player = server.playerList.getPlayer(ritual.playerId)
            if (level == null || player == null || !player.isAlive || !stillValid(level, key.pos)) {
                player?.displayClientMessage(Component.translatable("message.destiny2-mod.light_crystal.interrupted"), true)
                iterator.remove()
                continue
            }

            val elapsedTicks = RITUAL_TICKS - ritual.remainingTicks
            emitConvergingRing(level, key.pos, elapsedTicks)
            if (elapsedTicks > 0 && elapsedTicks % 9 == 0) {
                level.playSound(
                    null,
                    key.pos,
                    SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.BLOCKS,
                    0.55f,
                    0.8f + elapsedTicks.toFloat() / RITUAL_TICKS.toFloat() * 0.45f
                )
            }

            ritual.remainingTicks--
            if (ritual.remainingTicks <= 0) {
                complete(level, player, key.pos)
                iterator.remove()
            }
        }
    }

    private fun stillValid(level: ServerLevel, pos: BlockPos): Boolean =
        level.getBlockState(pos).`is`(Blocks.AMETHYST_BLOCK) &&
            level.isDay &&
            level.canSeeSky(pos.above())

    private fun emitConvergingRing(level: ServerLevel, pos: BlockPos, elapsedTicks: Int) {
        if (elapsedTicks % 2 != 0) return
        val radius = LightCrystalAnimation.ringRadius(elapsedTicks, RITUAL_TICKS)
        val centerX = pos.x + 0.5
        val centerY = pos.y + 0.62
        val centerZ = pos.z + 0.5
        val rotation = elapsedTicks * 0.22
        repeat(RING_PARTICLES) { index ->
            val angle = rotation + index * (PI * 2.0 / RING_PARTICLES)
            val wave = sin(angle * 2.0 + elapsedTicks * 0.16) * 0.16
            level.sendParticles(
                if (index % 3 == 0) ParticleTypes.WAX_ON else ParticleTypes.END_ROD,
                centerX + cos(angle) * radius,
                centerY + wave,
                centerZ + sin(angle) * radius,
                1,
                0.015,
                0.02,
                0.015,
                0.005
            )
        }
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, centerX, centerY, centerZ, 2, radius * 0.18, 0.18, radius * 0.18, 0.01)
    }

    private fun complete(level: ServerLevel, player: ServerPlayer, pos: BlockPos) {
        if (!level.removeBlock(pos, false)) {
            player.displayClientMessage(Component.translatable("message.destiny2-mod.light_crystal.interrupted"), true)
            return
        }
        val crystal = ItemStack(DestinyItems.LIGHT_CRYSTAL)
        if (!player.addItem(crystal)) player.drop(crystal, false)

        val x = pos.x + 0.5
        val y = pos.y + 0.65
        val z = pos.z + 0.5
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 28, 0.32, 0.42, 0.32, 0.035)
        level.sendParticles(ParticleTypes.WAX_ON, x, y, z, 36, 0.42, 0.5, 0.42, 0.055)
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.9f, 1.35f)
        player.displayClientMessage(Component.translatable("message.destiny2-mod.light_crystal.created"), true)
    }
}
