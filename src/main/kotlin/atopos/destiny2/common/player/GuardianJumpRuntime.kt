package atopos.destiny2.common.player

import atopos.destiny2.common.network.DestinyNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Server-authoritative state for Hunter, Titan, and Warlock class jumps. */
object GuardianJumpRuntime {
    private const val WARLOCK_HOLD_GRACE_TICKS = 3L
    private const val TITAN_HOLD_GRACE_TICKS = 3L

    private data class AirborneState(
        val destinyClass: DestinyClassType,
        var hunterExtraJumps: Int = 0,
        var warlockGlideTicks: Int = GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS,
        var warlockActivations: Int = 0,
        var warlockGroundJumpArmed: Boolean = false,
        var warlockGroundJumpTick: Long = Long.MIN_VALUE,
        var warlockActive: Boolean = false,
        var warlockActivationVector: Vec3 = Vec3.ZERO,
        var warlockInputDirection: Vec3 = Vec3.ZERO,
        var warlockActiveTicks: Int = 0,
        var warlockApexHoverStarted: Boolean = false,
        var warlockApexHoverTicks: Int = 0,
        var warlockLastAppliedTick: Long = Long.MIN_VALUE,
        var warlockHoldUntil: Long = Long.MIN_VALUE,
        var titanFuelTicks: Int = GuardianJumpRules.TITAN_MAX_FUEL_TICKS,
        var titanActive: Boolean = false,
        var titanHoldUntil: Long = Long.MIN_VALUE
    )

    private val states = mutableMapOf<UUID, AirborneState>()

    fun tickPlayer(player: ServerPlayer) {
        if (!canUseClassJump(player)) {
            states.remove(player.uuid)
            return
        }
        if (player.onGround()) {
            val pending = states[player.uuid]
            val now = player.serverLevel().gameTime
            if (
                pending?.destinyClass == DestinyClassType.WARLOCK &&
                pending.warlockGroundJumpArmed &&
                (
                    pending.warlockActivations == 0 ||
                        (pending.warlockActive && pending.warlockHoldUntil >= now)
                    )
            ) return
            states.remove(player.uuid)
            return
        }
        val state = states[player.uuid] ?: return
        val playerData = PlayerDestinyDataApi.get(player)
        val currentClass = playerData.destinyClass
        if (!playerData.isSubclassOptionUnlocked(playerData.subclassConfig.selectedMovementId)) {
            states.remove(player.uuid)
            return
        }
        if (!GuardianJumpRules.isMovementSelected(currentClass, playerData.subclassConfig.selectedMovementId)) {
            states.remove(player.uuid)
            return
        }
        if (state.destinyClass != currentClass) {
            states.remove(player.uuid)
            return
        }

        val now = player.serverLevel().gameTime
        when (currentClass) {
            DestinyClassType.WARLOCK -> {
                if (
                    state.warlockActive &&
                    state.warlockGlideTicks > 0 &&
                    state.warlockHoldUntil >= now &&
                    state.warlockLastAppliedTick < now
                ) {
                    if (
                        GuardianJumpRules.warlockShouldStartApexHover(
                            player.deltaMovement,
                            state.warlockActivationVector,
                            state.warlockActiveTicks,
                            state.warlockApexHoverStarted
                        )
                    ) {
                        state.warlockApexHoverStarted = true
                        state.warlockApexHoverTicks = GuardianJumpRules.WARLOCK_APEX_PHASE_TICKS
                    }
                    player.deltaMovement = GuardianJumpRules.warlockGlideVelocity(
                        player.deltaMovement,
                        state.warlockInputDirection,
                        state.warlockActivationVector,
                        state.warlockGlideTicks,
                        state.warlockActiveTicks,
                        state.warlockApexHoverTicks
                    )
                    markMovementChanged(player)
                    if (state.warlockApexHoverTicks > 0) state.warlockApexHoverTicks--
                    state.warlockGlideTicks--
                    state.warlockActiveTicks++
                    state.warlockLastAppliedTick = now
                    if (state.warlockGlideTicks <= 0) state.warlockActive = false
                } else if (state.warlockActive && state.warlockHoldUntil < now) {
                    state.warlockActive = false
                }
            }
            DestinyClassType.TITAN -> {
                if (state.titanActive && state.titanFuelTicks > 0 && state.titanHoldUntil >= now) {
                    player.deltaMovement = GuardianJumpRules.titanSustainedVelocity(player.deltaMovement)
                    markMovementChanged(player)
                    state.titanFuelTicks--
                    if (state.titanFuelTicks <= 0) state.titanActive = false
                } else if (state.titanActive && state.titanHoldUntil < now) {
                    state.titanActive = false
                }
            }
            DestinyClassType.HUNTER -> Unit
        }
    }

    fun handleMovement(player: ServerPlayer, action: Int, inputX: Float, inputZ: Float) {
        if (!canUseClassJump(player)) {
            states.remove(player.uuid)
            return
        }
        val playerData = PlayerDestinyDataApi.get(player)
        val destinyClass = playerData.destinyClass
        if (!playerData.isSubclassOptionUnlocked(playerData.subclassConfig.selectedMovementId)) {
            states.remove(player.uuid)
            return
        }
        if (!GuardianJumpRules.isMovementSelected(destinyClass, playerData.subclassConfig.selectedMovementId)) {
            states.remove(player.uuid)
            return
        }
        val inputDirection = sanitizeMovementInput(inputX, inputZ)
        if (action == DestinyNetworking.GuardianJumpPayload.ARM) {
            when (destinyClass) {
                DestinyClassType.WARLOCK -> {
                    states[player.uuid] = AirborneState(
                        destinyClass = destinyClass,
                        warlockInputDirection = inputDirection,
                        warlockGroundJumpArmed = true,
                        warlockGroundJumpTick = player.serverLevel().gameTime
                    )
                }
                DestinyClassType.HUNTER -> {
                    states[player.uuid] = AirborneState(destinyClass = destinyClass)
                }
                DestinyClassType.TITAN -> Unit
            }
            return
        }
        val armedWarlockState = states[player.uuid]
        if (
            action == DestinyNetworking.GuardianJumpPayload.PRESS &&
            destinyClass == DestinyClassType.WARLOCK &&
            armedWarlockState?.destinyClass == DestinyClassType.WARLOCK &&
            armedWarlockState.warlockGroundJumpArmed
        ) {
            handlePress(player, destinyClass, armedWarlockState, inputDirection)
            return
        }
        if (player.onGround()) {
            if (destinyClass == DestinyClassType.WARLOCK && action == DestinyNetworking.GuardianJumpPayload.PRESS) {
                states[player.uuid] = AirborneState(
                    destinyClass = destinyClass,
                    warlockGroundJumpArmed = true,
                    warlockGroundJumpTick = player.serverLevel().gameTime
                )
            } else {
                states.remove(player.uuid)
            }
            return
        }
        var state = states.getOrPut(player.uuid) { AirborneState(destinyClass) }
        if (state.destinyClass != destinyClass) {
            state = AirborneState(destinyClass)
            states[player.uuid] = state
        }

        when (action) {
            DestinyNetworking.GuardianJumpPayload.PRESS -> handlePress(
                player,
                destinyClass,
                state,
                inputDirection
            )
            DestinyNetworking.GuardianJumpPayload.HOLD -> {
                when (destinyClass) {
                    DestinyClassType.WARLOCK -> {
                        if (!state.warlockActive || state.warlockGlideTicks <= 0) return
                        state.warlockInputDirection = inputDirection
                        state.warlockHoldUntil = player.serverLevel().gameTime + WARLOCK_HOLD_GRACE_TICKS
                    }
                    DestinyClassType.TITAN -> {
                        if (!state.titanActive || state.titanFuelTicks <= 0) return
                        state.titanHoldUntil = player.serverLevel().gameTime + TITAN_HOLD_GRACE_TICKS
                    }
                    DestinyClassType.HUNTER -> return
                }
            }
            DestinyNetworking.GuardianJumpPayload.RELEASE -> {
                when (destinyClass) {
                    DestinyClassType.WARLOCK -> state.warlockActive = false
                    DestinyClassType.TITAN -> state.titanActive = false
                    DestinyClassType.HUNTER -> Unit
                }
            }
        }
    }

    private fun handlePress(
        player: ServerPlayer,
        destinyClass: DestinyClassType,
        state: AirborneState,
        inputDirection: Vec3
    ) {
        when (destinyClass) {
            DestinyClassType.HUNTER -> {
                if (state.hunterExtraJumps >= GuardianJumpRules.HUNTER_MAX_EXTRA_JUMPS) return
                state.hunterExtraJumps++
                player.deltaMovement = GuardianJumpRules.hunterVelocity(
                    player.deltaMovement,
                    inputDirection,
                    state.hunterExtraJumps
                )
            }
            DestinyClassType.WARLOCK -> {
                if (!state.warlockGroundJumpArmed || state.warlockActive) return
                if (
                    state.warlockGlideTicks <= 0 ||
                    state.warlockActivations >= GuardianJumpRules.WARLOCK_MAX_ACTIVATIONS
                ) return
                val elapsedSinceGroundJump = player.serverLevel().gameTime - state.warlockGroundJumpTick
                val allowAscent = state.warlockGroundJumpArmed &&
                    GuardianJumpRules.warlockAllowsAscent(
                        state.warlockActivations,
                        elapsedSinceGroundJump
                    )
                state.warlockActive = true
                state.warlockHoldUntil = player.serverLevel().gameTime + WARLOCK_HOLD_GRACE_TICKS
                state.warlockActivationVector = GuardianJumpRules.warlockActivationVector(
                    player.deltaMovement,
                    inputDirection,
                    allowAscent
                )
                state.warlockInputDirection = inputDirection
                state.warlockActiveTicks = 0
                state.warlockApexHoverStarted = false
                state.warlockApexHoverTicks = 0
                state.warlockActivations++
                player.deltaMovement = GuardianJumpRules.warlockGlideVelocity(
                    player.deltaMovement,
                    inputDirection,
                    state.warlockActivationVector,
                    state.warlockGlideTicks,
                    state.warlockActiveTicks
                )
                state.warlockGlideTicks--
                state.warlockActiveTicks++
                state.warlockLastAppliedTick = player.serverLevel().gameTime
            }
            DestinyClassType.TITAN -> {
                if (state.titanFuelTicks <= 0) return
                state.titanActive = true
                state.titanHoldUntil = player.serverLevel().gameTime + TITAN_HOLD_GRACE_TICKS
                player.deltaMovement = GuardianJumpRules.titanInitialVelocity(player.deltaMovement)
            }
        }
        markMovementChanged(
            player,
            forceVelocitySync = destinyClass != DestinyClassType.HUNTER
        )
    }

    private fun sanitizeMovementInput(inputX: Float, inputZ: Float): Vec3 {
        if (!inputX.isFinite() || !inputZ.isFinite()) return Vec3.ZERO
        val input = Vec3(inputX.toDouble(), 0.0, inputZ.toDouble())
        return if (input.horizontalDistanceSqr() > 1.0) input.normalize() else input
    }

    private fun markMovementChanged(player: ServerPlayer, forceVelocitySync: Boolean = false) {
        player.fallDistance = 0.0f
        if (forceVelocitySync) {
            player.hasImpulse = true
            player.hurtMarked = true
        }
    }

    private fun canUseClassJump(player: ServerPlayer): Boolean =
        player.isAlive &&
            !player.isSpectator &&
            !player.abilities.flying &&
            !player.isInWater &&
            !player.isInLava &&
            !player.isFallFlying &&
            !player.isPassenger &&
            !player.onClimbable()
}
