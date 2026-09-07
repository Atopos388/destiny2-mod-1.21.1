package atopos.destiny2.common.weapon

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.PlayerDestinyDataApi
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

/**
 * Server-authoritative Monte Carlo Method and Markov Chain state.
 *
 * The official behavior is preserved while the numeric conversion is scaled to
 * this mod: every accepted hit refunds melee energy, weapon kills build one
 * Markov stack, and melee kills grant five stacks plus a full magazine.
 */
object MonteCarloExoticRuntime {
    val ID: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "monte_carlo")

    private data class MarkovState(var stacks: Int, var ticks: Int)

    private val markovStates = mutableMapOf<java.util.UUID, MarkovState>()

    fun damageMultiplier(player: ServerPlayer): Float =
        1.0f + (markovStates[player.uuid]?.stacks ?: 0) * DAMAGE_PER_STACK

    fun onWeaponHit(player: ServerPlayer, killed: Boolean) {
        reduceMeleeCooldown(player, MELEE_REFUND_PER_HIT)
        if (!killed) return

        if (Random.nextFloat() < FULL_MELEE_CHARGE_CHANCE) {
            clearMeleeCooldown(player)
        }
        setMarkovStacks(player, (markovStates[player.uuid]?.stacks ?: 0) + 1)
    }

    fun onMeleeKill(player: ServerPlayer) {
        val stack = player.mainHandItem
        if (!isMonteCarlo(stack)) return
        setMarkovStacks(player, MAX_MARKOV_STACKS)
        val profile = DestinyWeaponDataRegistry.profile(ID) ?: return
        WeaponAmmoState.setMagazine(stack, profile, profile.magazineSize)
        player.inventoryMenu.broadcastChanges()
        WeaponHudSync.syncNow(player)
    }

    fun tick(player: ServerPlayer) {
        val state = markovStates[player.uuid] ?: return
        if (!isMonteCarlo(player.mainHandItem)) {
            clearMarkov(player)
            return
        }
        state.ticks--
        if (state.ticks <= 0) {
            clearMarkov(player)
        }
    }

    fun clear(player: ServerPlayer) {
        markovStates.remove(player.uuid)
    }

    fun isMonteCarlo(stack: ItemStack): Boolean =
        stack.gunPackIdOrNull() == ID

    private fun setMarkovStacks(player: ServerPlayer, requestedStacks: Int) {
        val stacks = requestedStacks.coerceIn(1, MAX_MARKOV_STACKS)
        markovStates[player.uuid] = MarkovState(stacks, MARKOV_DURATION_TICKS)
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.SyncPerkBuffPayload(
                MARKOV_PERK_ID.toString(),
                "马尔可夫链",
                MARKOV_DURATION_TICKS,
                stacks
            )
        )
    }

    private fun clearMarkov(player: ServerPlayer) {
        if (markovStates.remove(player.uuid) == null) return
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.SyncPerkBuffPayload(
                MARKOV_PERK_ID.toString(),
                "马尔可夫链",
                1,
                0
            )
        )
    }

    private fun reduceMeleeCooldown(player: ServerPlayer, ticks: Int) {
        val now = player.serverLevel().gameTime
        val data = PlayerDestinyDataApi.get(player)
        if (data.cooldowns.reduce(AbilitySlot.MELEE, now, ticks) <= 0) return
        syncMeleeCooldown(player, now)
    }

    private fun clearMeleeCooldown(player: ServerPlayer) {
        PlayerDestinyDataApi.get(player).cooldowns.clear(AbilitySlot.MELEE)
        syncMeleeCooldown(player, player.serverLevel().gameTime)
    }

    private fun syncMeleeCooldown(player: ServerPlayer, now: Long) {
        val cooldowns = PlayerDestinyDataApi.get(player).cooldowns
        val remaining = (cooldowns.nextAvailableTick(AbilitySlot.MELEE) - now).toInt().coerceAtLeast(0)
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.SyncCooldownPayload(
                AbilitySlot.MELEE.legacyNetworkId,
                remaining,
                cooldowns.totalDurationTicks(AbilitySlot.MELEE).coerceAtLeast(remaining)
            )
        )
    }

    private val MARKOV_PERK_ID =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "markov_chain")
    private const val MAX_MARKOV_STACKS = 5
    private const val MARKOV_DURATION_TICKS = 100
    private const val DAMAGE_PER_STACK = 0.08f
    private const val MELEE_REFUND_PER_HIT = 5
    private const val FULL_MELEE_CHARGE_CHANCE = 0.20f
}
