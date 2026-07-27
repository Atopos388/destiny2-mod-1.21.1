package atopos.destiny2.common.aspect

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.PlayerDestinyDataApi
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerPlayer
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import java.util.UUID

enum class VoidAbilitySource {
    GRENADE,
    MELEE,
    CLASS_ABILITY,
    SUPER
}

interface VoidAbilityDamageCarrier : DestinyAbilityDamageCarrier {
    val voidAbilitySource: VoidAbilitySource

    override val destinyAbilitySlot: AbilitySlot
        get() = when (voidAbilitySource) {
            VoidAbilitySource.GRENADE -> AbilitySlot.GRENADE
            VoidAbilitySource.MELEE -> AbilitySlot.MELEE
            VoidAbilitySource.CLASS_ABILITY -> AbilitySlot.CLASS_ABILITY
            VoidAbilitySource.SUPER -> AbilitySlot.SUPER
        }
}

object VoidHunterAspectRules {
    const val BASE_VORTEX_DURATION_TICKS = 4 * 20
    const val REMNANTS_VORTEX_DURATION_TICKS = 6 * 20
    const val PROVISION_COOLDOWN_REDUCTION_TICKS = 20
    const val PROVISION_TRIGGER_INTERVAL_TICKS = 20L

    fun vortexDuration(hasRemnants: Boolean): Int =
        if (hasRemnants) REMNANTS_VORTEX_DURATION_TICKS else BASE_VORTEX_DURATION_TICKS

    fun shouldApplyGrenadeWeaken(hasUndermining: Boolean): Boolean = hasUndermining

    fun canTriggerProvision(
        source: VoidAbilitySource,
        hasProvision: Boolean,
        currentTick: Long,
        nextEligibleTick: Long
    ): Boolean = source == VoidAbilitySource.GRENADE && hasProvision && currentTick >= nextEligibleTick
}

/** Server-authoritative runtime for Void Hunter Aspect and Fragment effects. */
object VoidHunterAspectRuntime {
    const val ECHO_OF_UNDERMINING = "destiny2-mod:fragment_echo_of_undermining"
    const val ECHO_OF_REMNANTS = "destiny2-mod:fragment_echo_of_remnants"
    const val ECHO_OF_PROVISION = "destiny2-mod:fragment_echo_of_provision"

    private val provisionNextEligibleTick = mutableMapOf<UUID, Long>()

    fun register() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            provisionNextEligibleTick.remove(handler.player.uuid)
        }
    }

    fun vortexDuration(owner: ServerPlayer?): Int = VoidHunterAspectRules.vortexDuration(
        owner != null && DestinyAspectRuntime.hasFragment(owner, ECHO_OF_REMNANTS)
    )

    fun shouldApplyGrenadeWeaken(owner: ServerPlayer?): Boolean =
        owner != null && VoidHunterAspectRules.shouldApplyGrenadeWeaken(
            DestinyAspectRuntime.hasFragment(owner, ECHO_OF_UNDERMINING)
        )

    fun onVoidAbilityDamage(owner: ServerPlayer, source: VoidAbilitySource) {
        val now = owner.serverLevel().gameTime
        val hasProvision = DestinyAspectRuntime.hasFragment(owner, ECHO_OF_PROVISION)
        val nextEligible = provisionNextEligibleTick[owner.uuid] ?: Long.MIN_VALUE
        if (!VoidHunterAspectRules.canTriggerProvision(source, hasProvision, now, nextEligible)) {
            return
        }

        val data = PlayerDestinyDataApi.get(owner)
        val reduced = data.cooldowns.reduce(
            AbilitySlot.MELEE,
            now,
            VoidHunterAspectRules.PROVISION_COOLDOWN_REDUCTION_TICKS
        )
        if (reduced <= 0) {
            return
        }

        provisionNextEligibleTick[owner.uuid] = now + VoidHunterAspectRules.PROVISION_TRIGGER_INTERVAL_TICKS
        val remaining = (data.cooldowns.nextAvailableTick(AbilitySlot.MELEE) - now).toInt().coerceAtLeast(0)
        ServerPlayNetworking.send(
            owner,
            DestinyNetworking.SyncCooldownPayload(
                AbilitySlot.MELEE.legacyNetworkId,
                remaining,
                data.cooldowns.totalDurationTicks(AbilitySlot.MELEE).coerceAtLeast(remaining)
            )
        )
        DestinyStatusRules.syncTemporaryBuff(
            owner,
            "destiny2-mod:fragment/echo_of_provision",
            "补能回声",
            VoidHunterAspectRules.PROVISION_TRIGGER_INTERVAL_TICKS.toInt(),
            reduced
        )
    }
}
