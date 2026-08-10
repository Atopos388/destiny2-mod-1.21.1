package atopos.destiny2.client

import atopos.destiny2.common.player.DestinyClassType
import atopos.destiny2.common.player.GuardianJumpRules
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.client.gui.DestinyHUDState
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3

/** Immediate local prediction for the server-authoritative class jump runtime. */
object GuardianJumpClient {
    private var airborneClass: DestinyClassType? = null
    private var hunterExtraJumps = 0
    private var hunterGroundJumpArmed = false
    private var warlockGlideTicks = GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS
    private var warlockActivations = 0
    private var warlockGroundJumpArmed = false
    private var warlockGroundJumpTick = Int.MIN_VALUE
    private var warlockActive = false
    private var warlockActivationVector = Vec3.ZERO
    private var warlockActiveTicks = 0
    private var warlockApexHoverStarted = false
    private var warlockApexHoverTicks = 0
    private var titanFuelTicks = GuardianJumpRules.TITAN_MAX_FUEL_TICKS
    private var titanActive = false
    private var lastPressAction = DestinyNetworking.GuardianJumpPayload.PRESS
    private var lastMovementInput = Vec3.ZERO

    fun tick(player: LocalPlayer, classId: String) {
        val currentClass = DestinyClassType.findById(classId)
        if (player.onGround() || currentClass == null || currentClass != airborneClass || !canUse(player)) {
            reset(currentClass)
            return
        }
    }

    fun press(player: LocalPlayer, classId: String): Boolean {
        if (!canUse(player)) return false
        val destinyClass = DestinyClassType.findById(classId) ?: return false
        if (!GuardianJumpRules.isMovementSelected(destinyClass, DestinyHUDState.selectedMovementId)) return false
        val inputDirection = movementInput(player)
        if (destinyClass == DestinyClassType.HUNTER && !hunterGroundJumpArmed) {
            if (GuardianJumpRules.hunterShouldArmGroundJump(hunterGroundJumpArmed, player.deltaMovement.y)) {
                reset(destinyClass)
                hunterGroundJumpArmed = true
                lastMovementInput = inputDirection
                lastPressAction = DestinyNetworking.GuardianJumpPayload.ARM
                return true
            }
            hunterGroundJumpArmed = true
        }
        if (
            destinyClass == DestinyClassType.WARLOCK &&
            GuardianJumpRules.warlockShouldArmFirstPress(warlockGroundJumpArmed)
        ) {
            reset(destinyClass)
            lastMovementInput = inputDirection
            warlockGroundJumpArmed = true
            warlockGroundJumpTick = player.tickCount
            lastPressAction = DestinyNetworking.GuardianJumpPayload.ARM
            return true
        }
        if (player.onGround()) return false
        if (airborneClass != destinyClass) reset(destinyClass)
        lastMovementInput = inputDirection
        lastPressAction = DestinyNetworking.GuardianJumpPayload.PRESS

        when (destinyClass) {
            DestinyClassType.HUNTER -> {
                if (hunterExtraJumps >= GuardianJumpRules.HUNTER_MAX_EXTRA_JUMPS) return false
                hunterExtraJumps++
                player.deltaMovement = GuardianJumpRules.hunterVelocity(
                    player.deltaMovement,
                    inputDirection,
                    hunterExtraJumps
                )
            }
            DestinyClassType.WARLOCK -> {
                if (!warlockGroundJumpArmed || warlockActive) return false
                if (
                    warlockGlideTicks <= 0 ||
                    warlockActivations >= GuardianJumpRules.WARLOCK_MAX_ACTIVATIONS
                ) return false
                val elapsedSinceGroundJump = player.tickCount - warlockGroundJumpTick
                val allowAscent = warlockGroundJumpArmed &&
                    GuardianJumpRules.warlockAllowsAscent(
                        warlockActivations,
                        elapsedSinceGroundJump.toLong()
                    )
                warlockActive = true
                warlockActivationVector = GuardianJumpRules.warlockActivationVector(
                    player.deltaMovement,
                    inputDirection,
                    allowAscent
                )
                warlockActiveTicks = 0
                warlockApexHoverStarted = false
                warlockApexHoverTicks = 0
                warlockActivations++
            }
            DestinyClassType.TITAN -> {
                if (titanFuelTicks <= 0) return false
                titanActive = true
                player.deltaMovement = GuardianJumpRules.titanInitialVelocity(player.deltaMovement)
            }
        }
        markMovementChanged(player)
        return true
    }

    fun hold(player: LocalPlayer, classId: String): Boolean {
        if (!canUse(player)) {
            warlockActive = false
            titanActive = false
            return false
        }
        val inputDirection = movementInput(player)
        lastMovementInput = inputDirection
        val destinyClass = DestinyClassType.findById(classId) ?: return false
        if (!GuardianJumpRules.isMovementSelected(destinyClass, DestinyHUDState.selectedMovementId)) return false
        return when (destinyClass) {
            DestinyClassType.WARLOCK -> {
                if (!warlockActive || warlockGlideTicks <= 0) return false
                if (
                    GuardianJumpRules.warlockShouldStartApexHover(
                        player.deltaMovement,
                        warlockActivationVector,
                        warlockActiveTicks,
                        warlockApexHoverStarted
                    )
                ) {
                    warlockApexHoverStarted = true
                    warlockApexHoverTicks = GuardianJumpRules.WARLOCK_APEX_PHASE_TICKS
                }
                player.deltaMovement = GuardianJumpRules.warlockGlideVelocity(
                    player.deltaMovement,
                    inputDirection,
                    warlockActivationVector,
                    warlockGlideTicks,
                    warlockActiveTicks,
                    warlockApexHoverTicks
                )
                markMovementChanged(player)
                if (warlockApexHoverTicks > 0) warlockApexHoverTicks--
                warlockGlideTicks--
                warlockActiveTicks++
                if (warlockGlideTicks <= 0) warlockActive = false
                true
            }
            DestinyClassType.TITAN -> {
                if (!titanActive || titanFuelTicks <= 0) return false
                player.deltaMovement = GuardianJumpRules.titanSustainedVelocity(player.deltaMovement)
                markMovementChanged(player)
                titanFuelTicks--
                if (titanFuelTicks <= 0) titanActive = false
                true
            }
            else -> false
        }
    }

    fun release(): Boolean {
        val handled = warlockActive || titanActive
        warlockActive = false
        titanActive = false
        return handled
    }

    fun lastPressAction(): Int = lastPressAction

    fun payload(action: Int = lastPressAction): DestinyNetworking.GuardianJumpPayload =
        DestinyNetworking.GuardianJumpPayload(
            action,
            lastMovementInput.x.toFloat(),
            lastMovementInput.z.toFloat()
        )

    private fun reset(destinyClass: DestinyClassType?) {
        airborneClass = destinyClass
        hunterExtraJumps = 0
        hunterGroundJumpArmed = false
        warlockGlideTicks = GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS
        warlockActivations = 0
        warlockGroundJumpArmed = false
        warlockGroundJumpTick = Int.MIN_VALUE
        warlockActive = false
        warlockActivationVector = Vec3.ZERO
        warlockActiveTicks = 0
        warlockApexHoverStarted = false
        warlockApexHoverTicks = 0
        titanFuelTicks = GuardianJumpRules.TITAN_MAX_FUEL_TICKS
        titanActive = false
        lastMovementInput = Vec3.ZERO
    }

    private fun movementInput(player: LocalPlayer): net.minecraft.world.phys.Vec3 =
        GuardianJumpRules.movementInputDirection(
            player.input.forwardImpulse,
            player.input.leftImpulse,
            player.yRot
        )

    private fun markMovementChanged(player: LocalPlayer) {
        player.fallDistance = 0.0f
        player.hasImpulse = true
    }

    private fun canUse(player: LocalPlayer): Boolean =
        !player.isSpectator &&
            !player.abilities.flying &&
            !player.isInWater &&
            !player.isInLava &&
            !player.isFallFlying &&
            !player.isPassenger &&
            !player.onClimbable()
}
