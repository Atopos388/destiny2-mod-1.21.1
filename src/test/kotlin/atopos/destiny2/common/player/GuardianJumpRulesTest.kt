package atopos.destiny2.common.player

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuardianJumpRulesTest {
    @Test
    fun `hunter gets two extra jumps`() {
        assertEquals(2, GuardianJumpRules.HUNTER_MAX_EXTRA_JUMPS)
        assertEquals(
            0.60,
            GuardianJumpRules.hunterVelocity(Vec3(0.2, -0.4, 0.1), Vec3.ZERO, 1).y,
            0.0001
        )
        assertEquals(
            0.60,
            GuardianJumpRules.hunterVelocity(Vec3(0.2, -0.4, 0.1), Vec3.ZERO, 2).y,
            0.0001
        )
    }

    @Test
    fun `hunter ground jump is armed without consuming a triple jump charge`() {
        assertTrue(GuardianJumpRules.hunterShouldArmGroundJump(false, 0.42))
        assertTrue(!GuardianJumpRules.hunterShouldArmGroundJump(true, 0.42))
        assertTrue(!GuardianJumpRules.hunterShouldArmGroundJump(false, -0.01))
    }

    @Test
    fun `hunter jump steers without discarding stronger upward momentum`() {
        val result = GuardianJumpRules.hunterVelocity(Vec3(0.3, 0.7, 0.0), Vec3(0.0, 0.0, 1.0))
        assertEquals(0.7, result.y, 0.0001)
        assertTrue(result.z > 0.0)
        assertTrue(result.z > result.x, "The jump did not rotate far enough toward the new input")
        assertTrue(result.horizontalDistance() in 0.66..0.67)
    }

    @Test
    fun `hunter jump can reverse direction without losing horizontal speed`() {
        val result = GuardianJumpRules.hunterVelocity(
            Vec3(0.3, -0.2, 0.0),
            Vec3(-1.0, 0.0, 0.0),
            1
        )
        assertTrue(result.x < 0.0)
        assertTrue(result.horizontalDistance() in 0.66..0.67)
    }

    @Test
    fun `hunter consecutive forward jumps add distance without a direction hitch`() {
        val input = Vec3(0.0, 0.0, 1.0)
        val first = GuardianJumpRules.hunterVelocity(Vec3(0.0, -0.1, 0.28), input, 1)
        val second = GuardianJumpRules.hunterVelocity(first, input, 2)

        assertEquals(0.0, first.x, 0.0001)
        assertEquals(0.0, second.x, 0.0001)
        assertTrue(first.z > 0.28)
        assertTrue(second.z > first.z)
        assertTrue(second.z <= 0.855)
    }

    @Test
    fun `hunter air jump immediately enters a gravity driven arc`() {
        val jumpVelocity = GuardianJumpRules.hunterVelocity(
            Vec3(0.0, -0.075, 0.0),
            Vec3.ZERO,
            1
        )
        val nextTick = (jumpVelocity.y - 0.08) * 0.98
        val followingTick = (nextTick - 0.08) * 0.98

        assertEquals(0.60, jumpVelocity.y, 0.0001)
        assertTrue(nextTick < jumpVelocity.y)
        assertTrue(followingTick < nextTick)
    }

    @Test
    fun `hunter triple jump reaches six blocks with two upward impulses`() {
        var velocity = Vec3(0.0, 0.42, 0.0)
        var height = 0.0
        var maximumHeight = 0.0
        var airJumpIndex = 0

        for (tick in 0 until 80) {
            height += velocity.y
            maximumHeight = maxOf(maximumHeight, height)
            velocity = Vec3(velocity.x, (velocity.y - 0.08) * 0.98, velocity.z)
            if (
                velocity.y <= 0.0 &&
                airJumpIndex < GuardianJumpRules.HUNTER_MAX_EXTRA_JUMPS
            ) {
                airJumpIndex++
                velocity = GuardianJumpRules.hunterVelocity(
                    velocity,
                    Vec3.ZERO,
                    airJumpIndex
                )
                assertTrue(velocity.y > 0.0, "Air jump $airJumpIndex did not apply upward force")
            }
        }

        assertEquals(2, airJumpIndex)
        assertTrue(maximumHeight in 5.9..6.1, "Expected a six-block triple jump, got $maximumHeight")
    }

    @Test
    fun `hunter full forward triple jump can traverse about fifteen blocks`() {
        var velocity = Vec3(0.0, 0.42, 0.28)
        var height = 0.0
        var distance = 0.0
        var airJumpIndex = 0

        for (tick in 0 until 80) {
            distance += velocity.z
            height += velocity.y
            velocity = Vec3(
                0.0,
                (velocity.y - 0.08) * 0.98,
                (velocity.z + 0.02) * 0.91
            )
            if (
                velocity.y <= 0.0 &&
                airJumpIndex < GuardianJumpRules.HUNTER_MAX_EXTRA_JUMPS
            ) {
                airJumpIndex++
                velocity = GuardianJumpRules.hunterVelocity(
                    velocity,
                    Vec3(0.0, 0.0, 1.0),
                    airJumpIndex
                )
            }
            if (height <= 0.0 && airJumpIndex == GuardianJumpRules.HUNTER_MAX_EXTRA_JUMPS) {
                break
            }
        }

        assertTrue(distance in 14.5..15.5, "Expected about fifteen blocks, got $distance")
    }

    @Test
    fun `warlock activation captures the current vector without an instant jump`() {
        val current = Vec3(0.3, 0.1, 0.4)
        val activation = GuardianJumpRules.warlockActivationVector(current, Vec3(1.0, 0.0, 0.0))
        assertEquals(0.20, activation.y, 0.0001)
        assertTrue(activation.z > activation.x)
    }

    @Test
    fun `warlock double tap inside the window still ascends near the apex`() {
        val activation = GuardianJumpRules.warlockActivationVector(
            Vec3(0.0, -0.05, 0.0),
            Vec3.ZERO,
            allowAscent = GuardianJumpRules.warlockAllowsAscent(0, 30)
        )
        val firstTick = GuardianJumpRules.warlockGlideVelocity(
            Vec3(0.0, -0.05, 0.0),
            Vec3.ZERO,
            activation,
            GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS,
            0
        )
        assertTrue(firstTick.y > 0.25, "A valid double tap was incorrectly treated as slow fall")
    }

    @Test
    fun `warlock glide reaches at least six blocks with reduced takeoff speed`() {
        var velocity = Vec3(0.0, 0.34, 0.0)
        var height = 0.0
        var maximumHeight = 0.0
        var ticksToSixBlocks = Int.MAX_VALUE
        val activation = GuardianJumpRules.warlockActivationVector(velocity, Vec3.ZERO)
        repeat(GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS) { tick ->
            height += velocity.y
            maximumHeight = maxOf(maximumHeight, height)
            if (height >= 6.0 && ticksToSixBlocks == Int.MAX_VALUE) {
                ticksToSixBlocks = tick
            }
            velocity = Vec3(velocity.x, (velocity.y - 0.08) * 0.98, velocity.z)
            velocity = GuardianJumpRules.warlockGlideVelocity(
                velocity,
                Vec3.ZERO,
                activation,
                GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS - tick,
                tick
            )
        }
        assertTrue(maximumHeight >= 6.0, "Expected at least six blocks of glide height, got $maximumHeight")
        assertTrue(ticksToSixBlocks <= 24, "Controlled ascent was too slow: needed $ticksToSixBlocks ticks")
        assertTrue(maximumHeight < 7.2, "Reduced takeoff still carried too high: $maximumHeight")
    }

    @Test
    fun `warlock ascent accelerates smoothly instead of launching instantly`() {
        val activation = Vec3(0.0, 0.34, 0.0)
        val afterGravity = Vec3(0.0, (0.34 - 0.08) * 0.98, 0.0)
        val firstGlideTick = GuardianJumpRules.warlockGlideVelocity(
            afterGravity,
            Vec3.ZERO,
            activation,
            GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS,
            0
        )
        assertTrue(firstGlideTick.y > afterGravity.y, "The ascent did not accelerate")
        assertTrue(firstGlideTick.y < 0.4, "The first ascent tick launched too abruptly")
    }

    @Test
    fun `warlock ascent overshoots into a slow one second hover descent`() {
        var velocity = Vec3(0.0, 0.34, 0.0)
        val activation = GuardianJumpRules.warlockActivationVector(velocity, Vec3.ZERO)
        var hoverStarted = false
        var hoverTicksRemaining = 0
        var observedTransitionTicks = 0
        var observedHoverTicks = 0
        val transitionVelocities = mutableListOf<Double>()
        var verticalAfterHover = 0.0

        repeat(GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS) { tick ->
            velocity = Vec3(velocity.x, (velocity.y - 0.08) * 0.98, velocity.z)
            if (
                GuardianJumpRules.warlockShouldStartApexHover(
                    velocity,
                    activation,
                    tick,
                    hoverStarted
                )
            ) {
                hoverStarted = true
                hoverTicksRemaining = GuardianJumpRules.WARLOCK_APEX_PHASE_TICKS
            }
            velocity = GuardianJumpRules.warlockGlideVelocity(
                velocity,
                Vec3.ZERO,
                activation,
                GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS - tick,
                tick,
                hoverTicksRemaining
            )
            if (hoverTicksRemaining > GuardianJumpRules.WARLOCK_APEX_HOVER_TICKS) {
                transitionVelocities += velocity.y
                observedTransitionTicks++
                hoverTicksRemaining--
            } else if (hoverTicksRemaining > 0) {
                assertEquals(-0.006, velocity.y, 0.0001)
                observedHoverTicks++
                hoverTicksRemaining--
            } else if (hoverStarted && verticalAfterHover == 0.0) {
                verticalAfterHover = velocity.y
            }
        }

        assertTrue(hoverStarted, "The ascent never entered its apex phase")
        assertEquals(5, observedTransitionTicks)
        assertEquals(20, observedHoverTicks)
        assertTrue(transitionVelocities.first() > 0.0, "The apex transition did not preserve upward motion")
        assertTrue(transitionVelocities.any { it < 0.0 }, "The apex transition never crossed into descent")
        assertTrue(
            transitionVelocities.min() < transitionVelocities.last(),
            "The apex transition did not settle back after its overshoot"
        )
        assertTrue(verticalAfterHover < 0.0, "The glide did not begin falling after the apex hover")
    }

    @Test
    fun `warlock glide brakes a fall without turning it into a hunter jump`() {
        val activation = GuardianJumpRules.warlockActivationVector(
            Vec3(0.2, -0.6, 0.0),
            Vec3.ZERO,
            allowAscent = false
        )
        val result = GuardianJumpRules.warlockGlideVelocity(
            Vec3(0.2, -0.6, 0.0),
            Vec3.ZERO,
            activation,
            GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS,
            0
        )
        assertEquals(-0.075, result.y, 0.0001)
    }

    @Test
    fun `warlock glide near the apex sustains but does not create a second jump`() {
        var velocity = Vec3.ZERO
        var height = 0.0
        var maximumHeight = 0.0
        val activation = GuardianJumpRules.warlockActivationVector(
            velocity,
            Vec3.ZERO,
            allowAscent = false
        )
        repeat(GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS) { tick ->
            height += velocity.y
            maximumHeight = maxOf(maximumHeight, height)
            velocity = Vec3(0.0, (velocity.y - 0.08) * 0.98, 0.0)
            velocity = GuardianJumpRules.warlockGlideVelocity(
                velocity,
                Vec3.ZERO,
                activation,
                GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS - tick,
                tick
            )
        }
        assertTrue(maximumHeight < 1.0, "Apex activation created too much height: $maximumHeight")

        repeat(8) {
            velocity = Vec3(0.0, (velocity.y - 0.08) * 0.98, 0.0)
        }
        assertTrue(velocity.y < -0.4, "Normal gravity did not resume after fuel exhaustion")
    }

    @Test
    fun `warlock activation outside the thirty tick window is slow fall only`() {
        assertTrue(GuardianJumpRules.warlockAllowsAscent(0, 30))
        assertTrue(!GuardianJumpRules.warlockAllowsAscent(0, 31))
        assertTrue(!GuardianJumpRules.warlockAllowsAscent(1, 5))

        val activation = GuardianJumpRules.warlockActivationVector(
            Vec3(0.2, 0.3, 0.0),
            Vec3.ZERO,
            allowAscent = false
        )
        assertEquals(0.0, activation.y, 0.0001)

        val result = GuardianJumpRules.warlockGlideVelocity(
            Vec3(0.2, -0.2, 0.0),
            Vec3.ZERO,
            activation,
            GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS,
            0
        )
        assertTrue(result.y <= 0.0, "Slow-fall activation must not create upward velocity")
    }

    @Test
    fun `warlock slow fall has a slightly softer terminal speed`() {
        val activation = Vec3.ZERO
        var velocity = Vec3.ZERO
        repeat(40) { tick ->
            velocity = Vec3(0.0, (velocity.y - 0.08) * 0.98, 0.0)
            velocity = GuardianJumpRules.warlockGlideVelocity(
                velocity,
                Vec3.ZERO,
                activation,
                GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS - tick,
                tick
            )
        }
        assertEquals(-0.075, velocity.y, 0.0001)
    }

    @Test
    fun `warlock first press always arms double tap before glide`() {
        assertTrue(GuardianJumpRules.warlockShouldArmFirstPress(false))
        assertTrue(!GuardianJumpRules.warlockShouldArmFirstPress(true))
    }

    @Test
    fun `warlock can redirect horizontal thrust while holding glide`() {
        val activation = Vec3(0.0, 0.34, 1.0)
        var velocity = Vec3(0.0, 0.2, 0.45)
        repeat(10) { tick ->
            velocity = GuardianJumpRules.warlockGlideVelocity(
                velocity,
                Vec3(1.0, 0.0, 0.0),
                activation,
                GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS - tick,
                tick
            )
        }
        assertTrue(velocity.x > velocity.z, "Side input did not redirect the glide")
        assertTrue(velocity.z < 0.2, "Stored forward vector overpowered directional control")
    }

    @Test
    fun `warlock can begin moving forward after a stationary activation`() {
        val activation = Vec3(0.0, 0.34, 0.0)
        var velocity = Vec3(0.0, 0.2, 0.0)
        repeat(10) { tick ->
            velocity = GuardianJumpRules.warlockGlideVelocity(
                velocity,
                Vec3(0.0, 0.0, 1.0),
                activation,
                GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS - tick,
                tick
            )
        }
        assertTrue(velocity.z > 0.25, "Stationary activation did not accept later forward input")
        assertTrue(velocity.z <= 0.30, "Stationary activation exceeded the converted glide speed")
    }

    @Test
    fun `warlock converted glide distance stays near a Destiny traversal jump`() {
        val activation = Vec3(0.0, 0.34, 1.0)
        var velocity = Vec3.ZERO
        var distance = 0.0
        repeat(GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS) { tick ->
            distance += velocity.z
            velocity = Vec3(0.0, velocity.y, velocity.z * 0.91)
            velocity = GuardianJumpRules.warlockGlideVelocity(
                velocity,
                Vec3(0.0, 0.0, 1.0),
                activation,
                GuardianJumpRules.WARLOCK_MAX_GLIDE_TICKS - tick,
                tick
            )
        }
        assertTrue(distance in 17.0..25.0, "Converted glide distance was $distance blocks")
    }

    @Test
    fun `movement input follows player yaw`() {
        val forwardAtZero = GuardianJumpRules.movementInputDirection(1.0f, 0.0f, 0.0f)
        val forwardAtRight = GuardianJumpRules.movementInputDirection(1.0f, 0.0f, -90.0f)
        assertEquals(1.0, forwardAtZero.z, 0.0001)
        assertEquals(1.0, forwardAtRight.x, 0.0001)
    }

    @Test
    fun `titan thrust accelerates upward but is capped`() {
        var velocity = Vec3(0.2, 0.0, 0.1)
        repeat(GuardianJumpRules.TITAN_MAX_FUEL_TICKS) {
            velocity = GuardianJumpRules.titanSustainedVelocity(velocity)
        }
        assertEquals(0.43, velocity.y, 0.0001)
        assertEquals(0.2, velocity.x, 0.0001)
        assertEquals(0.1, velocity.z, 0.0001)
    }
}
