package atopos.destiny2.common.player

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VoidHunterShadowshotBendContractTest {
    private val animation = javaClass.classLoader
        .getResourceAsStream("assets/destiny2-mod/player_animation/void_hunter_shadowshot.json")
        .also(::assertNotNull)
        .use { JsonParser.parseReader(it!!.reader()).asJsonObject }
        .getAsJsonObject("animations")
        .getAsJsonObject("void_hunter_shadowshot")

    private val bones = animation.getAsJsonObject("bones")

    @Test
    fun `shadowshot keeps its original timing and limb tracks`() {
        assertEquals(1.4583f, animation.get("animation_length").asFloat, 0.0001f)
        listOf("body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg")
            .forEach { assertTrue(bones.has(it), "Missing original Shadowshot bone: $it") }
    }

    @Test
    fun `shadowshot exposes its existing lower limb motion as bend tracks`() {
        val pairs = mapOf(
            "rightForearm" to "right_arm_bend",
            "leftForearm" to "left_arm_bend",
            "rightForeleg" to "right_leg_bend",
            "leftForeleg" to "left_leg_bend"
        )

        pairs.forEach { (sourceName, bendName) ->
            val sourceFrames = rotation(sourceName)
            val bendFrames = rotation(bendName)
            assertEquals(sourceFrames.keySet(), bendFrames.keySet(), "$bendName timing changed")
            assertTrue(
                bendFrames.entrySet().any { (_, frame) ->
                    val vector = when {
                        frame.isJsonArray -> frame.asJsonArray
                        frame.asJsonObject.has("vector") -> frame.asJsonObject.getAsJsonArray("vector")
                        frame.asJsonObject.has("post") -> frame.asJsonObject
                            .getAsJsonObject("post")
                            .getAsJsonArray("vector")
                        else -> null
                    }
                    vector != null && vector[0].asFloat != 0.0f
                },
                "$bendName has no bend angle"
            )
        }
    }

    @Test
    fun `shadowshot bend conversion preserves the draw pose`() {
        val rightElbow = rotation("right_arm_bend")
            .getAsJsonObject("0.5833")
            .getAsJsonObject("post")
            .getAsJsonArray("vector")
        val leftKnee = rotation("left_leg_bend")
            .getAsJsonObject("0.5833")
            .getAsJsonObject("post")
            .getAsJsonArray("vector")

        assertEquals(19.69772f, rightElbow[0].asFloat, 0.0001f)
        assertEquals(-113.96249f, rightElbow[1].asFloat, 0.0001f)
        assertEquals(22.0f, leftKnee[0].asFloat, 0.0001f)
        assertEquals(0.0f, leftKnee[1].asFloat, 0.0001f)
    }

    private fun rotation(boneName: String): JsonObject =
        bones.getAsJsonObject(boneName).getAsJsonObject("rotation")
}
