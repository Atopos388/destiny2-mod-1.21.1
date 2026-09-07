package atopos.destiny2.common.weapon

import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

object WeaponAmmoState {
    private const val ROOT = "DestinyWeaponAmmo"
    private const val MAGAZINE = "magazine"
    private const val RESERVE = "reserve"
    private const val RELOAD_REMAINING = "reloadRemaining"
    private const val RELOAD_TOTAL = "reloadTotal"
    private const val RELOAD_COMMIT_REMAINING = "reloadCommitRemaining"
    private const val RELOAD_COMMITTED = "reloadCommitted"
    private const val RELOAD_PHASE = "reloadPhase"
    private const val RELOAD_SEQUENCE = "reloadSequence"
    private const val RELOAD_BLOCKED_UNTIL = "reloadBlockedUntil"
    private const val CHAMBER_EMPTY = "chamberEmpty"
    private const val BOLT_REMAINING = "boltRemaining"

    data class State(
        val magazine: Int,
        val reserve: Int = 0,
        val reloadRemaining: Int,
        val reloadTotal: Int,
        val reloadCommitRemaining: Int = 0,
        val reloadCommitted: Boolean = false,
        val reloadPhase: WeaponReloadPhase = WeaponReloadPhase.NONE,
        val chamberEmpty: Boolean = false,
        val boltRemaining: Int = 0,
        val reloadSequence: Int = 0,
        val reloadBlockedUntil: Long = 0L
    ) {
        val isReloading: Boolean get() = reloadRemaining > 0
        val isCycling: Boolean get() = boltRemaining > 0
    }

    fun read(
        stack: ItemStack,
        profile: WeaponCombatProfile,
        magazineLimit: Int = profile.magazineSize,
        initialMagazine: Int = profile.magazineSize
    ): State = read(
        stack,
        magazineLimit,
        profile.ammoType,
        profile.reserveCapacity,
        initialMagazine
    )

    fun read(stack: ItemStack, capacity: Int): State =
        read(stack, capacity, DestinyAmmoType.SPECIAL, 0, capacity)

    private fun read(
        stack: ItemStack,
        capacity: Int,
        ammoType: DestinyAmmoType,
        reserveCapacity: Int,
        initialMagazine: Int
    ): State {
        val root = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getCompound(ROOT)
        val reserve = if (ammoType == DestinyAmmoType.PRIMARY) Int.MAX_VALUE else
            root?.takeIf { it.contains(RESERVE) }?.getInt(RESERVE)?.coerceIn(0, reserveCapacity) ?: 0
        if (root == null || !root.contains(MAGAZINE)) {
            return State(initialMagazine.coerceIn(0, capacity), reserve, 0, 0)
        }
        return State(
            root.getInt(MAGAZINE).coerceIn(0, capacity),
            reserve,
            root.getInt(RELOAD_REMAINING).coerceAtLeast(0),
            root.getInt(RELOAD_TOTAL).coerceAtLeast(0),
            root.getInt(RELOAD_COMMIT_REMAINING).coerceAtLeast(0),
            root.getBoolean(RELOAD_COMMITTED),
            WeaponReloadPhase.entries.getOrElse(root.getInt(RELOAD_PHASE)) { WeaponReloadPhase.NONE },
            root.getBoolean(CHAMBER_EMPTY),
            root.getInt(BOLT_REMAINING).coerceAtLeast(0),
            root.getInt(RELOAD_SEQUENCE).coerceAtLeast(0),
            root.getLong(RELOAD_BLOCKED_UNTIL).coerceAtLeast(0L)
        )
    }

    fun setMagazine(stack: ItemStack, capacity: Int, value: Int) {
        update(stack, capacity) {
            val magazine = value.coerceIn(0, capacity)
            it.copy(magazine = magazine, chamberEmpty = magazine <= 0)
        }
    }

    fun setMagazine(
        stack: ItemStack,
        profile: WeaponCombatProfile,
        value: Int,
        magazineLimit: Int = profile.magazineSize
    ) {
        write(stack, read(stack, profile, magazineLimit).let {
            val magazine = value.coerceIn(0, magazineLimit)
            it.copy(magazine = magazine, chamberEmpty = magazine <= 0)
        })
    }

    fun consumeRound(stack: ItemStack, capacity: Int, boltTicks: Int = 0): Boolean {
        val state = read(stack, capacity)
        if (state.isReloading || state.isCycling || state.magazine <= 0) return false
        val magazine = state.magazine - 1
        write(
            stack,
            state.copy(
                magazine = magazine,
                chamberEmpty = magazine <= 0,
                boltRemaining = boltTicks.coerceAtLeast(0)
            )
        )
        return true
    }

    fun consumeRound(stack: ItemStack, profile: WeaponCombatProfile, boltTicks: Int = 0): Boolean {
        val state = read(stack, profile)
        if (state.isReloading || state.isCycling || state.magazine <= 0) return false
        val magazine = state.magazine - 1
        write(stack, state.copy(magazine = magazine, chamberEmpty = magazine <= 0, boltRemaining = boltTicks.coerceAtLeast(0)))
        return true
    }

    fun startReload(
        stack: ItemStack,
        profile: WeaponCombatProfile,
        ticks: Int,
        emptyBonusTicks: Int = 0,
        feedFraction: Float = 0.72f
    ): Boolean {
        val state = read(stack, profile)
        if (state.isReloading || state.magazine >= profile.magazineSize) return false
        val duration = (ticks + if (state.chamberEmpty) emptyBonusTicks else 0).coerceAtLeast(1)
        val commitRemaining = (duration * (1.0f - feedFraction.coerceIn(0.05f, 0.95f))).toInt()
            .coerceIn(1, duration)
        write(
            stack,
            state.copy(
                reloadRemaining = duration,
                reloadTotal = duration,
                reloadCommitRemaining = commitRemaining,
                reloadCommitted = false,
                reloadPhase = WeaponReloadPhase.STARTING,
                reloadSequence = if (state.reloadSequence == Int.MAX_VALUE) 1 else state.reloadSequence + 1
            )
        )
        return true
    }

    fun cancelReload(stack: ItemStack, profile: WeaponCombatProfile): Boolean {
        val state = read(stack, profile)
        if (!state.isReloading) return false
        write(
            stack,
            state.copy(
                reloadRemaining = 0,
                reloadTotal = 0,
                reloadCommitRemaining = 0,
                reloadCommitted = false,
                reloadPhase = WeaponReloadPhase.NONE
            )
        )
        return true
    }

    fun isReloadBlocked(state: State, currentGameTime: Long): Boolean =
        currentGameTime < state.reloadBlockedUntil

    /** Returns true when a reload completes on this tick. */
    fun tickReload(stack: ItemStack, player: ServerPlayer, profile: WeaponCombatProfile): Boolean {
        val state = read(stack, profile)
        if (state.boltRemaining > 0) {
            write(stack, state.copy(boltRemaining = state.boltRemaining - 1))
            return false
        }
        if (!state.isReloading) return false
        if (!state.reloadCommitted && state.reloadRemaining <= state.reloadCommitRemaining) {
            val needed = WeaponAmmoMath.needed(state.magazine, profile.magazineSize)
            val loaded = consumeReserve(stack, profile, needed, player.abilities.instabuild)
            write(
                stack,
                state.copy(
                    magazine = WeaponAmmoMath.completedMagazine(state.magazine, profile.magazineSize, loaded),
                    reloadCommitted = true,
                    reloadPhase = WeaponReloadPhase.FINISHING,
                    chamberEmpty = false
                )
            )
            return false
        }
        if (state.reloadRemaining > 1) {
            val nextRemaining = state.reloadRemaining - 1
            val nextPhase = if (state.reloadCommitted) {
                WeaponReloadPhase.FINISHING
            } else if (nextRemaining <= state.reloadCommitRemaining + 2) {
                WeaponReloadPhase.FEEDING
            } else {
                WeaponReloadPhase.STARTING
            }
            write(stack, state.copy(reloadRemaining = nextRemaining, reloadPhase = nextPhase))
            return false
        }

        var magazine = state.magazine
        if (!state.reloadCommitted) {
            val needed = WeaponAmmoMath.needed(magazine, profile.magazineSize)
            val loaded = consumeReserve(stack, profile, needed, player.abilities.instabuild)
            magazine = WeaponAmmoMath.completedMagazine(magazine, profile.magazineSize, loaded)
        }
        write(
            stack,
            read(stack, profile).copy(
                magazine = magazine,
                reloadRemaining = 0,
                reloadTotal = 0,
                reloadCommitRemaining = 0,
                reloadCommitted = false,
                reloadPhase = WeaponReloadPhase.NONE,
                chamberEmpty = magazine <= 0,
                reloadSequence = state.reloadSequence,
                reloadBlockedUntil = player.level().gameTime + POST_RELOAD_LOCK_TICKS
            )
        )
        return true
    }

    fun reserveCount(stack: ItemStack, profile: WeaponCombatProfile, creative: Boolean = false): Int =
        if (creative || profile.ammoType == DestinyAmmoType.PRIMARY) Int.MAX_VALUE else read(stack, profile).reserve

    fun addReserve(stack: ItemStack, profile: WeaponCombatProfile, amount: Int): Int {
        if (amount <= 0 || profile.ammoType == DestinyAmmoType.PRIMARY) return 0
        val state = read(stack, profile)
        val accepted = WeaponAmmoMath.acceptedReserve(state.reserve, profile.reserveCapacity, amount)
        if (accepted > 0) write(stack, state.copy(reserve = state.reserve + accepted))
        return accepted
    }

    fun holsterRound(stack: ItemStack, player: ServerPlayer, profile: WeaponCombatProfile): Boolean {
        val state = read(stack, profile)
        if (state.isReloading || state.magazine >= profile.magazineSize) return false
        if (consumeReserve(stack, profile, 1, player.abilities.instabuild) <= 0) return false
        setMagazine(stack, profile, state.magazine + 1)
        return true
    }

    /** Echo of Domineering: immediately reload the equipped weapon from reserves. */
    fun reloadFromReservesInstantly(
        stack: ItemStack,
        player: ServerPlayer,
        profile: WeaponCombatProfile
    ): Boolean {
        val state = read(stack, profile)
        if (state.magazine >= profile.magazineSize) return false
        val needed = WeaponAmmoMath.needed(state.magazine, profile.magazineSize)
        val loaded = consumeReserve(stack, profile, needed, player.abilities.instabuild)
        if (loaded <= 0 && !player.abilities.instabuild) return false
        write(
            stack,
            read(stack, profile).copy(
                magazine = WeaponAmmoMath.completedMagazine(state.magazine, profile.magazineSize, loaded),
                reloadRemaining = 0,
                reloadTotal = 0,
                reloadCommitRemaining = 0,
                reloadCommitted = false,
                reloadPhase = WeaponReloadPhase.NONE,
                chamberEmpty = false,
                reloadSequence = state.reloadSequence,
                reloadBlockedUntil = player.level().gameTime
            )
        )
        return true
    }

    fun consumeReserve(
        stack: ItemStack,
        profile: WeaponCombatProfile,
        requested: Int,
        creative: Boolean = false
    ): Int {
        if (requested <= 0) return 0
        if (creative || profile.ammoType == DestinyAmmoType.PRIMARY) return requested
        val state = read(stack, profile)
        val consumed = requested.coerceAtMost(state.reserve)
        if (consumed > 0) write(stack, state.copy(reserve = state.reserve - consumed))
        return consumed
    }

    private fun update(stack: ItemStack, capacity: Int, transform: (State) -> State) {
        write(stack, transform(read(stack, capacity)))
    }

    private fun write(stack: ItemStack, state: State) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
            data.update { tag ->
                val root = CompoundTag()
                root.putInt(MAGAZINE, state.magazine)
                if (state.reserve != Int.MAX_VALUE) root.putInt(RESERVE, state.reserve.coerceAtLeast(0))
                root.putInt(RELOAD_REMAINING, state.reloadRemaining)
                root.putInt(RELOAD_TOTAL, state.reloadTotal)
                root.putInt(RELOAD_COMMIT_REMAINING, state.reloadCommitRemaining)
                root.putBoolean(RELOAD_COMMITTED, state.reloadCommitted)
                root.putInt(RELOAD_PHASE, state.reloadPhase.ordinal)
                root.putInt(RELOAD_SEQUENCE, state.reloadSequence)
                root.putLong(RELOAD_BLOCKED_UNTIL, state.reloadBlockedUntil)
                root.putBoolean(CHAMBER_EMPTY, state.chamberEmpty)
                root.putInt(BOLT_REMAINING, state.boltRemaining)
                tag.put(ROOT, root)
            }
        }
    }

    private const val POST_RELOAD_LOCK_TICKS = 10L
}
