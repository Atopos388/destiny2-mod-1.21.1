package atopos.destiny2.client.weapon

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.WeaponThirdPersonAction
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import org.slf4j.LoggerFactory

/** Resolves action sounds from the selected external gun definition. */
object ExternalGunPackSoundClient {
    private val logger = LoggerFactory.getLogger("DestinyExternalGunSound")

    fun onAction(payload: DestinyNetworking.WeaponThirdPersonActionPayload) {
        val gunId = payload.weaponId ?: return
        val definition = TaczGunPackResources.definition(gunId) ?: return
        if (definition.sounds.isEmpty()) return

        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val sourcePlayer = level.getPlayerByUUID(payload.playerId) ?: return
        val local = payload.playerId == client.player?.uuid
        val requestedKey = payload.soundKey ?: defaultSoundKey(payload.action)
        val effectiveKey = if (payload.action == WeaponThirdPersonAction.SHOOT && !local) {
            "shoot_3p"
        } else {
            requestedKey
        }
        val resource = definition.sounds[effectiveKey]
            ?: definition.sounds[requestedKey]
            ?: return
        if (TaczGunPackResources.resourceBytes(resource) == null) {
            logger.warn("External gun sound {} for {} is unavailable", resource, gunId)
            return
        }

        client.soundManager.play(
            ExternalGunPackSoundInstance(
                resource,
                SoundSource.PLAYERS,
                if (payload.action == WeaponThirdPersonAction.SHOOT) 1.0f else 0.82f,
                sourcePlayer.x,
                sourcePlayer.y,
                sourcePlayer.z
            )
        )
    }

    private fun defaultSoundKey(action: WeaponThirdPersonAction): String = when (action) {
        WeaponThirdPersonAction.SHOOT -> "shoot"
        WeaponThirdPersonAction.RELOAD -> "reload_tactical"
        WeaponThirdPersonAction.DRAW -> "draw"
        WeaponThirdPersonAction.PUT_AWAY -> "put_away"
        WeaponThirdPersonAction.INSPECT -> "inspect"
    }
}

/** Converts Minecraft's generated sounds/... path back to the path stored by the gun pack. */
object ExternalGunPackSoundResources {
    @JvmStatic
    fun bytesForPlaybackPath(playbackPath: ResourceLocation): ByteArray? {
        if (!playbackPath.path.startsWith(SOUND_DIRECTORY) || !playbackPath.path.endsWith(OGG_SUFFIX)) {
            return null
        }
        val source = ResourceLocation.fromNamespaceAndPath(
            playbackPath.namespace,
            playbackPath.path.removePrefix(SOUND_DIRECTORY)
        )
        return TaczGunPackResources.resourceBytes(source)
    }

    private const val SOUND_DIRECTORY = "sounds/"
    private const val OGG_SUFFIX = ".ogg"
}
