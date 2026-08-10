package atopos.destiny2.common.weapon

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TheDeicideAnimationContractTest {
    private val root: JsonObject = javaClass.classLoader
        .getResourceAsStream("assets/destiny2-mod/animations/the_deicide.animation.json")
        .also(::assertNotNull)
        .use { JsonParser.parseReader(it!!.reader()).asJsonObject }

    private val gunPack: JsonObject = javaClass.classLoader
        .getResourceAsStream("assets/destiny2-mod/destiny_gunpacks/the_deicide.json")
        .also(::assertNotNull)
        .use { JsonParser.parseReader(it!!.reader()).asJsonObject }

    @Test
    fun `static idle carries TaCZ ICA freedom on all constraint axes`() {
        val constraint = root.getAsJsonObject("animations")
            .getAsJsonObject("static_idle")
            .getAsJsonObject("bones")
            .getAsJsonObject("constraint")
        assertNotNull(constraint)
        listOf("position", "rotation").forEach { channel ->
            val values = boundary(constraint.get(channel), false)
            assertTrue(values.all { kotlin.math.abs(it) == 0.2f }, "$channel must be 0.2 on every ICA axis")
        }
    }

    @Test
    fun `shoot gun kick returns to its neutral boundary`() {
        val animations = root.getAsJsonObject("animations")
        val shoot = animations.getAsJsonObject("shoot")
        assertEquals(setOf("root"), shoot.getAsJsonObject("bones").keySet())
        assertEquals(0.7083f, shoot.get("animation_length").asFloat, 0.0001f)

        val rootTrack = shoot.getAsJsonObject("bones").getAsJsonObject("root")
        assertBoundary(rootTrack.get("position"), ZERO)
        assertBoundary(rootTrack.get("rotation"), ZERO)
        assertBoundary(rootTrack.get("scale"), ONE)
    }

    @Test
    fun `mechanical cycle preserves authored timing and returns every owned channel to static idle`() {
        val animations = root.getAsJsonObject("animations")
        val idleBones = animations.getAsJsonObject("static_idle").getAsJsonObject("bones")
        val cycle = animations.getAsJsonObject("cycle")
        assertEquals(0.8333f, cycle.get("animation_length").asFloat, 0.0001f)
        assertEquals(
            setOf("bone3", "bone7", "righthand", "righthand_pos"),
            cycle.getAsJsonObject("bones").keySet()
        )

        cycle.getAsJsonObject("bones").entrySet().forEach { (boneName, boneElement) ->
            val idleBone = idleBones.getAsJsonObject(boneName)
            boneElement.asJsonObject.entrySet().forEach { (channelName, channel) ->
                val fallback = if (channelName == "scale") ONE else ZERO
                val expected = idleBone?.get(channelName)?.let { boundary(it, false) } ?: fallback
                assertBoundary(channel, expected)
            }
        }

        val sounds = cycle.getAsJsonObject("sound_effects")
        assertEquals("the_deicide_cockback", sounds.getAsJsonObject("0.4583").get("effect").asString)
        assertEquals("the_deicide_cockforward", sounds.getAsJsonObject("0.625").get("effect").asString)

        val pumpPosition = cycle.getAsJsonObject("bones")
            .getAsJsonObject("bone3")
            .getAsJsonObject("position")
        assertVectorEquals(
            listOf(-2.0625f, 0.0f, 0.0f),
            boundary(pumpPosition.getAsJsonObject("0.5").get("post"), false)
        )
        assertVectorEquals(listOf(-2.0625f, 0.0f, 0.0f), boundary(pumpPosition.get("0.625"), false))

        val rightHand = cycle.getAsJsonObject("bones").getAsJsonObject("righthand")
        assertTrue(rightHand.getAsJsonObject("position").has("0.1667"))
        assertTrue(rightHand.getAsJsonObject("rotation").has("0.1667"))
        assertTrue(
            cycle.getAsJsonObject("bones")
                .getAsJsonObject("righthand_pos")
                .getAsJsonObject("rotation")
                .has("0.1667")
        )
    }

    @Test
    fun `gun pack drives recoil and mechanical motion from one shot clock`() {
        assertEquals("iron_view", gunPack.get("idle_view_bone").asString)
        assertVectorEquals(
            listOf(0.15f, 15.93f, 9.125f),
            boundary(gunPack.get("idle_view_pivot"), false)
        )
        assertEquals("cycle", gunPack.get("cycle_animation").asString)
        assertTrue(gunPack.get("merge_cycle_into_shoot").asBoolean)
        assertTrue(gunPack.get("additive_shoot").asBoolean)
    }

    @Test
    fun `server firing lock lasts through the authored pump reset`() {
        val combat = javaClass.classLoader
            .getResourceAsStream("data/destiny2-mod/destiny_weapons/the_deicide.json")
            .also(::assertNotNull)
            .use { JsonParser.parseReader(it!!.reader()).asJsonObject }
        val boltTicks = combat.get("bolt_ticks").asInt
        val pumpResetSeconds = root.getAsJsonObject("animations")
            .getAsJsonObject("cycle")
            .get("animation_length")
            .asFloat
        assertEquals(18, boltTicks)
        // One extra server tick covers either tick ordering: consume then
        // inventory tick, or inventory tick then consume.
        assertTrue((boltTicks - 1) / 20.0f >= pumpResetSeconds)
    }

    private fun assertBoundary(channel: JsonElement, expected: List<Float>) {
        assertVectorEquals(expected, boundary(channel, false))
        assertVectorEquals(expected, boundary(channel, true))
    }

    private fun boundary(element: JsonElement, last: Boolean): List<Float> {
        if (element.isJsonPrimitive) {
            val value = element.asFloat
            return listOf(value, value, value)
        }
        if (element.isJsonArray) {
            return (0..2).map { index -> element.asJsonArray.get(index).asFloat }
        }
        val objectValue = element.asJsonObject
        objectValue.get("vector")?.let { return boundary(it, last) }
        val frame = objectValue.entrySet()
            .mapNotNull { (time, value) -> time.toFloatOrNull()?.let { it to value } }
            .sortedBy { it.first }
            .let { if (last) it.last().second else it.first().second }
        if (!frame.isJsonObject) return boundary(frame, last)
        val frameObject = frame.asJsonObject
        val preferred = if (last) listOf("post", "pre", "vector") else listOf("pre", "post", "vector")
        return boundary(preferred.firstNotNullOf { frameObject.get(it) }, last)
    }

    private fun assertVectorEquals(expected: List<Float>, actual: List<Float>) {
        expected.zip(actual).forEach { (expectedComponent, actualComponent) ->
            assertEquals(expectedComponent, actualComponent, 0.0005f)
        }
    }

    private companion object {
        val ZERO = listOf(0.0f, 0.0f, 0.0f)
        val ONE = listOf(1.0f, 1.0f, 1.0f)
    }
}
