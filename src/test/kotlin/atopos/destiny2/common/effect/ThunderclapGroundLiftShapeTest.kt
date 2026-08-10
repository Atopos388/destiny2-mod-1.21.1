package atopos.destiny2.common.effect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThunderclapGroundLiftShapeTest {
    @Test
    fun `footprint reaches six blocks and widens toward the front`() {
        val nearWidth = ThunderclapGroundLiftShape.halfWidth(1.0)
        val middleWidth = ThunderclapGroundLiftShape.halfWidth(3.0)
        val frontWidth = ThunderclapGroundLiftShape.halfWidth(6.0)

        assertTrue(nearWidth > 0.0)
        assertTrue(nearWidth < middleWidth)
        assertTrue(middleWidth < frontWidth)
        assertEquals(2.7, frontWidth, 0.0001)
        assertEquals(0.0, ThunderclapGroundLiftShape.halfWidth(6.01), 0.0001)
    }

    @Test
    fun `footprint has a narrow neck and excludes blocks behind the player`() {
        assertFalse(ThunderclapGroundLiftShape.contains(0.2, 0.0))
        assertFalse(ThunderclapGroundLiftShape.contains(1.0, 1.8))
        assertTrue(ThunderclapGroundLiftShape.contains(3.0, 1.5))
        assertTrue(ThunderclapGroundLiftShape.contains(6.0, 2.5))
        assertFalse(ThunderclapGroundLiftShape.contains(6.0, 3.1))
    }

    @Test
    fun `centre strip stays fixed while both wings are eligible to tilt`() {
        assertFalse(ThunderclapGroundLiftShape.shouldTilt(0.0))
        assertFalse(ThunderclapGroundLiftShape.shouldTilt(0.6))
        assertTrue(ThunderclapGroundLiftShape.shouldTilt(-0.8))
        assertTrue(ThunderclapGroundLiftShape.shouldTilt(0.8))
    }

    @Test
    fun `tilt intensity accelerates toward the outer rim`() {
        val inner = ThunderclapGroundLiftShape.sideIntensity(0.8)
        val middle = ThunderclapGroundLiftShape.sideIntensity(1.6)
        val outer = ThunderclapGroundLiftShape.sideIntensity(2.7)

        assertEquals(0.0, ThunderclapGroundLiftShape.sideIntensity(0.6), 0.0001)
        assertTrue(inner < middle)
        assertTrue(middle < outer)
        assertEquals(1.0, outer, 0.0001)
    }
}
