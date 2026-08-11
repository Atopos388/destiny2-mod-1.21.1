package atopos.destiny2.common.ability

import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DestinyGrenadeThrowTest {
    @Test
    fun `shared grenade profiles are controlled and predictable`() {
        DestinyGrenadeThrow.Profile.entries.forEach { profile ->
            assertTrue(profile.speed in 1.0f..1.2f)
            assertTrue(profile.inaccuracy <= 0.05f)
            assertTrue(profile.movementInheritance <= 0.25)
            assertTrue(profile.pitchOffsetDegrees < 0.0f)
        }
        assertTrue(
            DestinyGrenadeThrow.Profile.ATTACHMENT.speed >
                DestinyGrenadeThrow.Profile.FRAG.speed
        )
    }

    @Test
    fun `vertical wall ricochet loses energy without artificial upward kick`() {
        val incoming = Vec3(1.0, -0.10, 0.20)
        val bounced = DestinyGrenadeThrow.fragRicochet(incoming, Vec3(-1.0, 0.0, 0.0))

        assertEquals(-0.26, bounced.x, 1.0e-9)
        assertEquals(-0.064, bounced.y, 1.0e-9)
        assertEquals(0.128, bounced.z, 1.0e-9)
        assertTrue(bounced.lengthSqr() < incoming.lengthSqr())
    }

    @Test
    fun `floor ricochet stays shallow and loses energy`() {
        val incoming = Vec3(0.50, -0.60, 0.10)
        val bounced = DestinyGrenadeThrow.fragRicochet(incoming, Vec3(0.0, 1.0, 0.0))

        assertEquals(0.32, bounced.x, 1.0e-9)
        assertEquals(0.156, bounced.y, 1.0e-9)
        assertEquals(0.064, bounced.z, 1.0e-9)
        assertTrue(bounced.lengthSqr() < incoming.lengthSqr())
    }
}
