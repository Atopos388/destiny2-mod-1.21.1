package atopos.destiny2.common.item

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.bernie.geckolib.animation.RawAnimation

class ForgottenNameAnimationTriggerTest {
    @Test
    fun `alternating shot triggers remain distinct while playing the same visible animation`() {
        val first = RawAnimation.begin().thenPlay("shoot")
        val second = RawAnimation.begin().thenPlay("shoot").thenWait(0)

        // GeckoLib only resets an in-flight controller when the incoming
        // RawAnimation differs from the currently cached animation.
        assertNotEquals(first, second)
    }

    @Test
    fun `alternating reload triggers restart the same visible reload`() {
        val first = RawAnimation.begin().thenPlay("reload_tactical")
        val second = RawAnimation.begin().thenPlay("reload_tactical").thenWait(0)

        assertNotEquals(first, second)
    }

    @Test
    fun `held action alternate starts with the visible action instead of an empty frame`() {
        val first = RawAnimation.begin().thenPlayAndHold("inspect")
        val second = RawAnimation.begin().thenPlayAndHold("inspect").thenWait(0)

        assertNotEquals(first, second)
        assertEquals("inspect", second.animationStages.first().animationName())
    }

    @Test
    fun `shorter reload duration increases animation speed`() {
        val normal = ForgottenNameItem.reloadAnimationSpeed(34)
        val accelerated = ForgottenNameItem.reloadAnimationSpeed(27)
        val notSyncedYet = ForgottenNameItem.reloadAnimationSpeed(0)

        assertEquals(1.0f, normal, 0.0001f)
        assertEquals(1.0f, notSyncedYet, 0.0001f)
        assertTrue(accelerated > normal)
    }
}
