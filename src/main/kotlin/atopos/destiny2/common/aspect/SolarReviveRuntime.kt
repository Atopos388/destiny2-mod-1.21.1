package atopos.destiny2.common.aspect

import atopos.destiny2.common.item.DestinyItems
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Minimal Destiny-style ally revive flow used by Ember of Mercy's explicit revive branch. */
object SolarReviveRuntime {
    private const val MINECRAFT_CALIBRATION_REVIVE_WINDOW_TICKS = 20 * 20L

    private data class ReviveMarker(
        val itemId: UUID,
        val deadPlayerId: UUID,
        val dimension: ResourceKey<Level>,
        val position: Vec3,
        val expiresAt: Long
    )

    private val markersByItem = mutableMapOf<UUID, ReviveMarker>()
    private val markerByDeadPlayer = mutableMapOf<UUID, UUID>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            val iterator = markersByItem.iterator()
            while (iterator.hasNext()) {
                val (itemId, marker) = iterator.next()
                val dead = server.playerList.getPlayer(marker.deadPlayerId)
                val level = server.getLevel(marker.dimension)
                val now = level?.gameTime ?: Long.MAX_VALUE
                if (now < marker.expiresAt && dead?.isDeadOrDying == true) continue
                (level?.getEntity(itemId) as? ItemEntity)?.discard()
                markerByDeadPlayer.remove(marker.deadPlayerId)
                iterator.remove()
            }
        }
    }

    fun onPlayerDeath(player: ServerPlayer) {
        if (markerByDeadPlayer.containsKey(player.uuid)) return
        val level = player.serverLevel()
        val markerItem = ItemEntity(level, player.x, player.y + 0.45, player.z, ItemStack(DestinyItems.REVIVE_GHOST))
        markerItem.setPickUpDelay(20)
        markerItem.setUnlimitedLifetime()
        level.addFreshEntity(markerItem)
        val marker = ReviveMarker(
            markerItem.uuid,
            player.uuid,
            level.dimension(),
            player.position(),
            level.gameTime + MINECRAFT_CALIBRATION_REVIVE_WINDOW_TICKS
        )
        markersByItem[markerItem.uuid] = marker
        markerByDeadPlayer[player.uuid] = markerItem.uuid
        level.sendParticles(ParticleTypes.END_ROD, markerItem.x, markerItem.y, markerItem.z, 28, 0.30, 0.45, 0.30, 0.02)
    }

    /** Always cancels vanilla inventory pickup; invalid/enemy contact leaves the marker in-world. */
    fun handlePickup(reviver: ServerPlayer, item: ItemEntity): Boolean {
        val marker = markersByItem[item.uuid] ?: run {
            item.discard()
            return true
        }
        val dead = reviver.server.playerList.getPlayer(marker.deadPlayerId) ?: return true
        if (dead === reviver || !dead.isDeadOrDying || !reviver.isAlive || !reviver.isAlliedTo(dead)) return true
        val level = reviver.server.getLevel(marker.dimension) ?: return true

        item.discard()
        markersByItem.remove(marker.itemId)
        markerByDeadPlayer.remove(marker.deadPlayerId)
        val revived = reviver.server.playerList.respawn(dead, false, Entity.RemovalReason.DISCARDED)
        revived.teleportTo(level, marker.position.x, marker.position.y, marker.position.z, dead.yRot, dead.xRot)
        revived.health = (revived.maxHealth * 0.5f).coerceAtLeast(1.0f)
        SolarWarlockFragmentRuntime.onAllyRevived(reviver, revived)
        level.sendParticles(ParticleTypes.END_ROD, revived.x, revived.eyeY, revived.z, 48, 0.55, 0.80, 0.55, 0.04)
        level.playSound(null, revived.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9f, 1.15f)
        return true
    }
}
