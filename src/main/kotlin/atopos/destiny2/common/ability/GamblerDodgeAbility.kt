package atopos.destiny2.common.ability

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.sound.DestinySounds
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.minecraft.sounds.SoundSource

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.monster.Monster
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

object GamblerDodgeAbility {
    private const val DASH_DURATION_TICKS = 5
    private const val DASH_BLOCKS = 5.0
    private val pendingLaunches = ConcurrentLinkedQueue<PendingLaunch>()
    private val activeDashes = ConcurrentLinkedQueue<ActiveDash>()

    private data class PendingLaunch(
        val playerId: UUID,
        val direction: Vec3
    )

    private data class ActiveDash(
        val playerId: UUID,
        val direction: Vec3,
        var remainingTicks: Int
    )

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            while (true) {
                val launch = pendingLaunches.peek() ?: break
                pendingLaunches.poll()
                val player = server.playerList.getPlayer(launch.playerId) ?: continue
                activeDashes.add(ActiveDash(player.uuid, launch.direction, DASH_DURATION_TICKS))
            }

            val continuingDashes = mutableListOf<ActiveDash>()
            while (true) {
                val dash = activeDashes.poll() ?: break
                val player = server.playerList.getPlayer(dash.playerId) ?: continue
                if (applyDashTick(player, dash.direction)) {
                    dash.remainingTicks--
                    if (dash.remainingTicks > 0) {
                        continuingDashes.add(dash)
                    }
                }
            }
            continuingDashes.forEach(activeDashes::add)
        }
    }

    private fun forwardDirection(player: ServerPlayer): Vec3 {
        val look = player.lookAngle
        val horizontal = Vec3(look.x, 0.0, look.z)
        if (horizontal.lengthSqr() >= 0.01) {
            return horizontal.normalize()
        }

        val yawRadians = Math.toRadians(player.yRot.toDouble())
        return Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians)).normalize()
    }

    private fun applyDashTick(player: ServerPlayer, direction: Vec3): Boolean {
        val horizontal = Vec3(direction.x, 0.0, direction.z)
        if (horizontal.lengthSqr() < 0.01) {
            return false
        }

        player.hasImpulse = true
        val step = horizontal.normalize().scale(DASH_BLOCKS / DASH_DURATION_TICKS.toDouble())
        player.teleportTo(
            player.serverLevel() as ServerLevel,
            player.x + step.x,
            player.y,
            player.z + step.z,
            player.yRot,
            player.xRot
        )
        player.deltaMovement = step.add(0.0, 0.04, 0.0)
        return true
    }

    // Gambler dodge is a forced forward lunge; movement keys do not affect the dash direction.
    fun perform(player: ServerPlayer, direction: Int) {
        val launchDirection = forwardDirection(player)
        pendingLaunches.add(
            PendingLaunch(
                player.uuid,
                launchDirection
            )
        )
        
        val nearbyEnemies = player.level().getEntities(player, player.boundingBox.inflate(15.0)) {
            it is Monster 
        }
        
        if (nearbyEnemies.isNotEmpty()) {
            PlayerDestinyDataApi.get(player).cooldowns.clear(AbilitySlot.MELEE)
        }

        if (DestinyAspectRuntime.hasAspect(player, VANISHING_STEP_ASPECT)) {
            DestinyStatusRules.applyVoidInvisibility(player, 6 * 20)
            player.level().playSound(
                null,
                player.blockPosition(),
                DestinySounds.VOID_INVISIBILITY,
                SoundSource.PLAYERS,
                0.65f,
                1.0f
            )
        }

        player.level().playSound(
            null,
            player.blockPosition(),
            DestinySounds.GAMBLER_DODGE,
            SoundSource.PLAYERS,
            0.6f,
            1.0f
        )
    }

    private const val VANISHING_STEP_ASPECT = "destiny2-mod:aspect_vanishing_step"
}
