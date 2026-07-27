package atopos.destiny2.common.player

import atopos.destiny2.common.network.DestinyNetworking
import net.minecraft.server.level.ServerPlayer

object DestinyCombatRuntime {
    fun tick(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        val state = data.combatState
        val stats = DestinyStatsResolver.resolve(player)
        val now = player.serverLevel().gameTime

        state.superEnergy = DestinyStatFormulas.clampEnergy(
            state.superEnergy + DestinyStatFormulas.passiveSuperEnergyPerTick()
        )

        if (state.classOvershieldExpiresAt > 0L && now >= state.classOvershieldExpiresAt) {
            state.classOvershield = 0.0f
            state.classOvershieldExpiresAt = 0L
        }

        val shieldCapacity = DestinyStatFormulas.healthShieldCapacity(stats)
        state.healthShield = state.healthShield.coerceIn(0.0f, shieldCapacity)
        val rechargeAt = safeAdd(state.lastDamageGameTime, DestinyStatFormulas.healthShieldRechargeDelayTicks(stats).toLong())
        if (now >= rechargeAt && state.healthShield < shieldCapacity) {
            state.healthShield = (state.healthShield + DestinyStatFormulas.healthShieldRechargePerTick(stats))
                .coerceAtMost(shieldCapacity)
        }

        if (player.tickCount % 20 == 0) {
            DestinyNetworking.syncStatState(player)
        }
    }

    /** Returns the damage left for vanilla health after both Destiny shield layers. */
    fun absorbIncomingDamage(player: ServerPlayer, amount: Float): Float {
        if (amount <= 0.0f) return amount
        val state = PlayerDestinyDataApi.get(player).combatState
        state.lastDamageGameTime = player.serverLevel().gameTime
        var remaining = amount
        remaining = consumeLayer(remaining, state.classOvershield) { state.classOvershield = it }
        remaining = consumeLayer(remaining, state.healthShield) { state.healthShield = it }
        DestinyNetworking.syncStatState(player)
        return remaining
    }

    fun grantSuperEnergyFromDamage(player: ServerPlayer, actualDamage: Float) {
        if (actualDamage <= 0.0f) return
        val data = PlayerDestinyDataApi.get(player)
        val stats = DestinyStatsResolver.resolve(player)
        data.combatState.superEnergy = DestinyStatFormulas.clampEnergy(
            data.combatState.superEnergy + DestinyStatFormulas.superEnergyFromDamage(actualDamage, stats)
        )
        DestinyNetworking.syncStatState(player)
    }

    fun consumeSuper(player: ServerPlayer): Boolean {
        val state = PlayerDestinyDataApi.get(player).combatState
        if (state.superEnergy < 100.0f) return false
        state.superEnergy = 0.0f
        DestinyNetworking.syncStatState(player)
        return true
    }

    fun grantClassOvershield(player: ServerPlayer, durationTicks: Int) {
        val data = PlayerDestinyDataApi.get(player)
        val capacity = DestinyStatFormulas.classOvershieldCapacity(DestinyStatsResolver.resolve(player))
        if (capacity <= 0.0f) return
        data.combatState.classOvershield = capacity
        data.combatState.classOvershieldExpiresAt = player.serverLevel().gameTime + durationTicks
        DestinyNetworking.syncStatState(player)
    }

    private inline fun consumeLayer(amount: Float, available: Float, update: (Float) -> Unit): Float {
        if (amount <= 0.0f || available <= 0.0f) return amount
        val consumed = minOf(amount, available)
        update((available - consumed).coerceAtLeast(0.0f))
        return amount - consumed
    }

    private fun safeAdd(value: Long, delta: Long): Long {
        if (value == Long.MIN_VALUE) return Long.MIN_VALUE
        return if (value > Long.MAX_VALUE - delta) Long.MAX_VALUE else value + delta
    }
}
