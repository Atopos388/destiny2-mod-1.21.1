package atopos.destiny2.common.player

import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Shared velocity tuning for the three class jump styles. */
object GuardianJumpRules {
    const val WARLOCK_VECTOR_GLIDE_ID = "destiny2-mod:warlock_vector_glide"
    const val HUNTER_TRIPLE_JUMP_ID = "destiny2-mod:hunter_triple_jump"
    const val TITAN_LIFT_ID = "destiny2-mod:titan_lift"
    const val HUNTER_MAX_EXTRA_JUMPS = 2
    const val WARLOCK_ASCENT_INPUT_WINDOW_TICKS = 30
    const val WARLOCK_MAX_GLIDE_TICKS = 120
    const val WARLOCK_MAX_ACTIVATIONS = 3
    const val WARLOCK_APEX_TRANSITION_TICKS = 5
    const val WARLOCK_APEX_HOVER_TICKS = 20
    const val WARLOCK_APEX_PHASE_TICKS =
        WARLOCK_APEX_TRANSITION_TICKS + WARLOCK_APEX_HOVER_TICKS
    const val TITAN_MAX_FUEL_TICKS = 24

    private const val HUNTER_TRIPLE_VERTICAL_SPEED = 0.60
    private const val HUNTER_TRIPLE_VERTICAL_IMPULSE = 0.18
    private const val HUNTER_TRIPLE_MAX_UPWARD_SPEED = 0.64
    private const val HUNTER_TRIPLE_HORIZONTAL_SPEED = 0.855
    private const val HUNTER_TRIPLE_DIRECTION_BLEND = 0.55
    private const val HUNTER_TRIPLE_FIRST_SPEED_BLEND = 0.65
    private const val HUNTER_TRIPLE_SECOND_SPEED_BLEND = 0.70
    private const val HUNTER_TRIPLE_STRAIGHT_ALIGNMENT = 0.94
    private const val WARLOCK_MAX_HORIZONTAL_SPEED = 0.30
    private const val WARLOCK_ASCENT_INITIAL_THRUST = 0.084
    private const val WARLOCK_ASCENT_PEAK_THRUST = 0.1056
    private const val WARLOCK_ASCENT_SUSTAIN_THRUST = 0.035
    private const val WARLOCK_MIN_ASCENT_ACTIVATION_SPEED = 0.20
    private const val WARLOCK_ASCENT_RAMP_TICKS = 5
    private const val WARLOCK_ASCENT_BOOST_END_TICK = 16
    private const val WARLOCK_LEVEL_INITIAL_SUPPORT = 0.075
    private const val WARLOCK_LEVEL_FINAL_SUPPORT = 0.035
    private const val WARLOCK_MAX_UPWARD_SPEED = 0.448
    private const val WARLOCK_LEVEL_MAX_UPWARD_SPEED = 0.06
    private const val WARLOCK_MAX_FALL_SPEED = -0.075
    private const val WARLOCK_APEX_TRANSITION_START_SPEED = 0.10
    private const val WARLOCK_APEX_HOVER_DESCENT_SPEED = -0.006
    private const val WARLOCK_BURST_ACCELERATION = 0.034
    private const val WARLOCK_CRUISE_ACCELERATION = 0.014
    private const val WARLOCK_BURST_TICKS = 16
    private const val TITAN_INITIAL_VERTICAL_SPEED = 0.31
    private const val TITAN_VERTICAL_ACCELERATION = 0.055
    private const val TITAN_MAX_VERTICAL_SPEED = 0.43

    fun movementIdFor(destinyClass: DestinyClassType): String = when (destinyClass) {
        DestinyClassType.WARLOCK -> WARLOCK_VECTOR_GLIDE_ID
        DestinyClassType.HUNTER -> HUNTER_TRIPLE_JUMP_ID
        DestinyClassType.TITAN -> TITAN_LIFT_ID
    }

    fun isMovementSelected(destinyClass: DestinyClassType, selectedMovementId: String): Boolean =
        selectedMovementId == movementIdFor(destinyClass)

    fun hunterShouldArmGroundJump(alreadyArmed: Boolean, currentVerticalSpeed: Double): Boolean =
        !alreadyArmed && currentVerticalSpeed > 0.0

    fun movementInputDirection(forward: Float, strafe: Float, yawDegrees: Float): Vec3 {
        if (abs(forward) < 0.001f && abs(strafe) < 0.001f) return Vec3.ZERO
        val yaw = Math.toRadians(yawDegrees.toDouble())
        val x = strafe * cos(yaw) - forward * sin(yaw)
        val z = forward * cos(yaw) + strafe * sin(yaw)
        val direction = Vec3(x, 0.0, z)
        return if (direction.lengthSqr() > 1.0) direction.normalize() else direction
    }

    fun hunterVelocity(current: Vec3, inputDirection: Vec3): Vec3 =
        hunterVelocity(current, inputDirection, 1)

    fun hunterVelocity(current: Vec3, inputDirection: Vec3, airJumpIndex: Int): Vec3 {
        val speedBlend = if (airJumpIndex <= 1) {
            HUNTER_TRIPLE_FIRST_SPEED_BLEND
        } else {
            HUNTER_TRIPLE_SECOND_SPEED_BLEND
        }
        val horizontal = redirectHorizontal(
            current,
            inputDirection,
            HUNTER_TRIPLE_HORIZONTAL_SPEED,
            HUNTER_TRIPLE_DIRECTION_BLEND,
            speedBlend
        )
        val boostedVerticalSpeed = current.y + HUNTER_TRIPLE_VERTICAL_IMPULSE
        val vertical = if (current.y > HUNTER_TRIPLE_MAX_UPWARD_SPEED) {
            current.y
        } else {
            max(
                HUNTER_TRIPLE_VERTICAL_SPEED,
                min(HUNTER_TRIPLE_MAX_UPWARD_SPEED, boostedVerticalSpeed)
            )
        }
        return Vec3(horizontal.x, vertical, horizontal.z)
    }

    fun warlockAllowsAscent(activationCount: Int, elapsedSinceGroundJump: Long): Boolean =
        activationCount == 0 &&
            elapsedSinceGroundJump in 0..WARLOCK_ASCENT_INPUT_WINDOW_TICKS.toLong()

    fun warlockShouldArmFirstPress(alreadyArmed: Boolean): Boolean = !alreadyArmed

    fun warlockActivationVector(current: Vec3, inputDirection: Vec3, allowAscent: Boolean = true): Vec3 {
        val currentHorizontal = sqrt(current.x * current.x + current.z * current.z)
        val direction = when {
            currentHorizontal > 0.04 -> Vec3(current.x / currentHorizontal, 0.0, current.z / currentHorizontal)
            inputDirection.horizontalDistanceSqr() > 0.001 -> {
                val normalized = inputDirection.normalize()
                Vec3(normalized.x, 0.0, normalized.z)
            }
            else -> Vec3.ZERO
        }
        val vertical = if (allowAscent) {
            max(current.y, WARLOCK_MIN_ASCENT_ACTIVATION_SPEED)
        } else {
            0.0
        }
        return Vec3(direction.x, vertical, direction.z)
    }

    fun warlockShouldStartApexHover(
        current: Vec3,
        activationVector: Vec3,
        activeTicks: Int,
        alreadyHovered: Boolean
    ): Boolean =
            !alreadyHovered &&
            activationVector.y > 0.05 &&
            activeTicks >= WARLOCK_ASCENT_BOOST_END_TICK &&
            current.y <= WARLOCK_APEX_TRANSITION_START_SPEED

    fun warlockGlideVelocity(
        current: Vec3,
        inputDirection: Vec3,
        activationVector: Vec3,
        fuelTicksRemaining: Int,
        activeTicks: Int,
        apexHoverTicksRemaining: Int = 0
    ): Vec3 {
        val fuelProgress = 1.0 -
            fuelTicksRemaining.coerceIn(0, WARLOCK_MAX_GLIDE_TICKS).toDouble() / WARLOCK_MAX_GLIDE_TICKS
        val currentHorizontal = current.horizontalDistance()
        val storedTrajectoryDirection = when {
            activationVector.horizontalDistanceSqr() > 0.001 ->
                Vec3(activationVector.x, 0.0, activationVector.z).normalize()
            currentHorizontal > 0.001 ->
                Vec3(current.x / currentHorizontal, 0.0, current.z / currentHorizontal)
            else -> Vec3.ZERO
        }
        val desiredDirection = if (inputDirection.horizontalDistanceSqr() > 0.001) {
            Vec3(inputDirection.x, 0.0, inputDirection.z).normalize()
        } else {
            storedTrajectoryDirection
        }
        val burstProgress = (activeTicks.toDouble() / WARLOCK_BURST_TICKS).coerceIn(0.0, 1.0)
        val trajectoryAcceleration = lerp(
            WARLOCK_BURST_ACCELERATION,
            WARLOCK_CRUISE_ACCELERATION,
            burstProgress
        )
        val horizontal = steerHorizontal(
            current,
            desiredDirection,
            WARLOCK_MAX_HORIZONTAL_SPEED,
            trajectoryAcceleration
        )
        val vertical = when {
            apexHoverTicksRemaining > WARLOCK_APEX_HOVER_TICKS -> {
                val transitionTick =
                    WARLOCK_APEX_PHASE_TICKS - apexHoverTicksRemaining + 1
                val transitionProgress =
                    (transitionTick.toDouble() / WARLOCK_APEX_TRANSITION_TICKS)
                        .coerceIn(0.0, 1.0)
                val smoothProgress =
                    transitionProgress * transitionProgress * (3.0 - 2.0 * transitionProgress)
                val overshootProgress =
                    smoothProgress +
                        transitionProgress * transitionProgress * (1.0 - transitionProgress)
                val targetVelocity =
                    WARLOCK_APEX_TRANSITION_START_SPEED +
                        (WARLOCK_APEX_HOVER_DESCENT_SPEED -
                            WARLOCK_APEX_TRANSITION_START_SPEED) * overshootProgress
                if (apexHoverTicksRemaining == WARLOCK_APEX_PHASE_TICKS) {
                    min(current.y, targetVelocity)
                } else {
                    targetVelocity
                }
            }
            apexHoverTicksRemaining > 0 -> WARLOCK_APEX_HOVER_DESCENT_SPEED
            activeTicks == 0 && activationVector.y < -0.08 -> 0.0
            activationVector.y > 0.05 -> {
                val thrust = when {
                    activeTicks < WARLOCK_ASCENT_RAMP_TICKS -> lerp(
                        WARLOCK_ASCENT_INITIAL_THRUST,
                        WARLOCK_ASCENT_PEAK_THRUST,
                        activeTicks.toDouble() / WARLOCK_ASCENT_RAMP_TICKS
                    )
                    activeTicks < WARLOCK_ASCENT_BOOST_END_TICK -> lerp(
                        WARLOCK_ASCENT_PEAK_THRUST,
                        WARLOCK_ASCENT_SUSTAIN_THRUST,
                        (activeTicks - WARLOCK_ASCENT_RAMP_TICKS).toDouble() /
                            (WARLOCK_ASCENT_BOOST_END_TICK - WARLOCK_ASCENT_RAMP_TICKS)
                    )
                    else -> WARLOCK_ASCENT_SUSTAIN_THRUST
                }
                val ascentVelocity = if (activeTicks == 0 && current.y <= 0.05) {
                    max(current.y, activationVector.y)
                } else {
                    current.y
                }
                min(WARLOCK_MAX_UPWARD_SPEED, ascentVelocity + thrust)
                    .coerceAtLeast(WARLOCK_MAX_FALL_SPEED)
            }
            else -> {
                val support = lerp(
                    WARLOCK_LEVEL_INITIAL_SUPPORT,
                    WARLOCK_LEVEL_FINAL_SUPPORT,
                    fuelProgress
                )
                min(WARLOCK_LEVEL_MAX_UPWARD_SPEED, current.y + support)
                    .coerceAtLeast(WARLOCK_MAX_FALL_SPEED)
            }
        }
        return Vec3(horizontal.x, vertical, horizontal.z)
    }

    fun titanInitialVelocity(current: Vec3): Vec3 =
        Vec3(current.x, max(current.y, TITAN_INITIAL_VERTICAL_SPEED), current.z)

    fun titanSustainedVelocity(current: Vec3): Vec3 =
        Vec3(current.x, min(TITAN_MAX_VERTICAL_SPEED, current.y + TITAN_VERTICAL_ACCELERATION), current.z)

    private fun steerHorizontal(current: Vec3, inputDirection: Vec3, targetSpeed: Double, acceleration: Double): Vec3 {
        if (inputDirection.horizontalDistanceSqr() <= 0.001) return current
        val input = inputDirection.normalize()
        val targetX = input.x * targetSpeed
        val targetZ = input.z * targetSpeed
        return Vec3(
            approach(current.x, targetX, acceleration),
            current.y,
            approach(current.z, targetZ, acceleration)
        )
    }

    private fun redirectHorizontal(
        current: Vec3,
        inputDirection: Vec3,
        targetSpeed: Double,
        directionBlend: Double,
        speedBlend: Double
    ): Vec3 {
        if (inputDirection.horizontalDistanceSqr() <= 0.001) return current
        val input = inputDirection.normalize()
        val currentSpeed = current.horizontalDistance()
        val currentDirection = if (currentSpeed > 0.001) {
            Vec3(current.x / currentSpeed, 0.0, current.z / currentSpeed)
        } else {
            input
        }
        val alignment = currentDirection.dot(input)
        val redirected = if (alignment >= HUNTER_TRIPLE_STRAIGHT_ALIGNMENT) {
            currentDirection
        } else {
            val blendedDirection = Vec3(
                lerp(currentDirection.x, input.x, directionBlend),
                0.0,
                lerp(currentDirection.z, input.z, directionBlend)
            )
            if (blendedDirection.horizontalDistanceSqr() > 0.001) {
                blendedDirection.normalize()
            } else {
                input
            }
        }
        val redirectedSpeed = if (currentSpeed >= targetSpeed) {
            currentSpeed
        } else {
            lerp(currentSpeed, targetSpeed, speedBlend)
        }
        return Vec3(
            redirected.x * redirectedSpeed,
            current.y,
            redirected.z * redirectedSpeed
        )
    }

    private fun approach(current: Double, target: Double, maximumChange: Double): Double =
        when {
            current < target -> min(current + maximumChange, target)
            current > target -> max(current - maximumChange, target)
            else -> current
        }

    private fun lerp(start: Double, end: Double, progress: Double): Double =
        start + (end - start) * progress.coerceIn(0.0, 1.0)
}
