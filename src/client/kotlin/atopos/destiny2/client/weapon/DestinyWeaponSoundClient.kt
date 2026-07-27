package atopos.destiny2.client.weapon

import atopos.destiny2.common.item.ForgottenNameItem
import atopos.destiny2.common.sound.WeaponSoundKeyframeBridge
import atopos.destiny2.common.weapon.WeaponAmmoState
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import software.bernie.geckolib.animatable.GeoItem

/** Plays sounds authored on Blockbench/GeckoLib item-animation keyframes. */
object DestinyWeaponSoundClient {
    private val aliases = mapOf(
        "huandan" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_reload"),
        "reload_close" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_reload_close"),
        "reload_insert" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_reload_insert"),
        "weapon_draw" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_draw"),
        "inspect_mechanical" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_inspect"),
        "jixie" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_ready"),
        "kaiqiangchaozai" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_ready"),
        "xiangzhi" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_ready"),
        "zhengqi" to ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_ready")
    )
    private val lastPlayedAtNanos = mutableMapOf<ResourceLocation, Long>()
    private val playedReloadEvents = mutableMapOf<ReloadSoundEvent, Long>()

    fun register() {
        WeaponSoundKeyframeBridge.listener = ::play
    }

    private fun play(rawSound: String) {
        val key = rawSound.substringBefore('|').trim()
        val id = aliases[key] ?: runCatching {
            if (':' in key) ResourceLocation.parse(key)
            else ResourceLocation.fromNamespaceAndPath("destiny2-mod", key)
        }.getOrNull() ?: return

        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        if (!BuiltInRegistries.SOUND_EVENT.containsKey(id)) return
        val sound = BuiltInRegistries.SOUND_EVENT.get(id) ?: return
        val now = System.nanoTime()
        val reloadEvent = if (id == FORGOTTEN_NAME_RELOAD_ID) {
            val stack = player.mainHandItem
            val item = stack.item as? ForgottenNameItem
            val state = item?.let { WeaponAmmoState.read(stack, it.combatProfile(stack).magazineSize) }
            state?.takeIf { it.reloadSequence > 0 }?.let {
                ReloadSoundEvent(
                    System.identityHashCode(level),
                    GeoItem.getId(stack),
                    it.reloadSequence,
                    id
                )
            }
        } else {
            null
        }
        if (reloadEvent != null) {
            if (playedReloadEvents.putIfAbsent(reloadEvent, now) != null) return
            playedReloadEvents.entries.removeIf { now - it.value > EVENT_HISTORY_NANOS }
        } else {
            val lastPlayed = lastPlayedAtNanos[id]
            if (lastPlayed != null && now - lastPlayed < DUPLICATE_WINDOW_NANOS) return
            lastPlayedAtNanos[id] = now
        }
        val volume = when (id) {
            FORGOTTEN_NAME_INSPECT_ID -> 0.6f
            FORGOTTEN_NAME_RELOAD_INSERT_ID -> 0.7f
            FORGOTTEN_NAME_RELOAD_ID,
            FORGOTTEN_NAME_RELOAD_CLOSE_ID,
            FORGOTTEN_NAME_DRAW_ID,
            FORGOTTEN_NAME_READY_ID -> 0.75f
            else -> 1.0f
        }
        level.playLocalSound(
            player.x,
            player.y,
            player.z,
            sound,
            SoundSource.PLAYERS,
            volume,
            1.0f,
            false
        )
    }

    private data class ReloadSoundEvent(
        val levelIdentity: Int,
        val stackId: Long,
        val reloadSequence: Int,
        val sound: ResourceLocation
    )

    private val FORGOTTEN_NAME_RELOAD_ID =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_reload")
    private val FORGOTTEN_NAME_RELOAD_CLOSE_ID =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_reload_close")
    private val FORGOTTEN_NAME_RELOAD_INSERT_ID =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_reload_insert")
    private val FORGOTTEN_NAME_DRAW_ID =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_draw")
    private val FORGOTTEN_NAME_INSPECT_ID =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_inspect")
    private val FORGOTTEN_NAME_READY_ID =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_ready")
    private const val DUPLICATE_WINDOW_NANOS = 500_000_000L
    private const val EVENT_HISTORY_NANOS = 30_000_000_000L
}
