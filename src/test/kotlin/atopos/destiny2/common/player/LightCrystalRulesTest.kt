package atopos.destiny2.common.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LightCrystalRulesTest {
    @Test
    fun `ordinary amethyst under open daytime sky creates light crystal after awakening`() {
        assertEquals(
            LightCrystalRules.CreationResult.ALLOWED,
            LightCrystalRules.creationResult(
                awakened = true,
                ordinaryAmethystBlock = true,
                daytime = true,
                skyVisible = true
            )
        )
    }

    @Test
    fun `creation rejects unawakened players and missing sunlight`() {
        assertEquals(
            LightCrystalRules.CreationResult.NOT_AWAKENED,
            LightCrystalRules.creationResult(false, true, true, true)
        )
        assertEquals(
            LightCrystalRules.CreationResult.NO_SUNLIGHT,
            LightCrystalRules.creationResult(true, true, false, true)
        )
        assertEquals(
            LightCrystalRules.CreationResult.NO_SUNLIGHT,
            LightCrystalRules.creationResult(true, true, true, false)
        )
    }

    @Test
    fun `budding amethyst and crystal growth blocks are never valid targets`() {
        assertEquals(
            LightCrystalRules.CreationResult.WRONG_BLOCK,
            LightCrystalRules.creationResult(true, false, true, true)
        )
    }

    @Test
    fun `particle ring contracts smoothly toward the amethyst`() {
        val start = LightCrystalAnimation.ringRadius(0, LightCrystalRuntime.RITUAL_TICKS)
        val middle = LightCrystalAnimation.ringRadius(
            LightCrystalRuntime.RITUAL_TICKS / 2,
            LightCrystalRuntime.RITUAL_TICKS
        )
        val end = LightCrystalAnimation.ringRadius(
            LightCrystalRuntime.RITUAL_TICKS,
            LightCrystalRuntime.RITUAL_TICKS
        )

        assertTrue(start > middle)
        assertTrue(middle > end)
        assertEquals(1.45, start, 0.0001)
        assertEquals(0.12, end, 0.0001)
    }
}
