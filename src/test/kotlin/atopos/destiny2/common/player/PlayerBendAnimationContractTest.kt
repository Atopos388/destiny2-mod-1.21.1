package atopos.destiny2.common.player

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerBendAnimationContractTest {
    private val root = javaClass.classLoader
        .getResourceAsStream("assets/destiny2-mod/player_animations/bendy_knee_test.json")
        .also(::assertNotNull)
        .use { JsonParser.parseReader(it!!.reader()).asJsonObject }

    @Test
    fun `bend test exports helper bones for elbows and knees`() {
        val animation = root.getAsJsonObject("animations").getAsJsonObject("bendy_knee_test")
        val bones = animation.getAsJsonObject("bones")

        assertEquals(3.0f, animation.get("animation_length").asFloat, 0.0001f)
        listOf("right_arm_bend", "left_arm_bend", "right_leg_bend", "left_leg_bend")
            .forEach { assertTrue(bones.has(it), "Missing bend helper bone: $it") }
    }

    @Test
    fun `bend tracks place bend angle in PlayerAnimator first component`() {
        val bones = root.getAsJsonObject("animations")
            .getAsJsonObject("bendy_knee_test")
            .getAsJsonObject("bones")

        listOf("right_arm_bend", "left_arm_bend", "right_leg_bend", "left_leg_bend").forEach { boneName ->
            val frames = bones.getAsJsonObject(boneName).getAsJsonObject("rotation")
            frames.entrySet().forEach { (time, value) ->
                assertEquals(0.0f, value.asJsonArray[1].asFloat, 0.0001f, "$boneName axis at $time")
                assertEquals(0.0f, value.asJsonArray[2].asFloat, 0.0001f, "$boneName at $time")
            }
            assertTrue(
                frames.entrySet().any { (_, value) -> value.asJsonArray[0].asFloat != 0.0f },
                "$boneName must animate the bend angle in the first component"
            )
        }
    }
}
