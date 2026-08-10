package atopos.destiny2.common.player

import atopos.destiny2.common.action.DestinyActionRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.kosmx.playerAnim.core.data.gson.GeckoLibSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThunderclapAnimationContractTest {
    @Test
    fun `player animator runtime parser accepts both thunderclap resources`() {
        listOf("thunderclap_charge", "thunderclap_release").forEach { animationName ->
            val parsed = GeckoLibSerializer.deserialize(
                resourceJson("assets/destiny2-mod/player_animation/$animationName.json")
            )
            assertEquals(1, parsed.size, "$animationName was rejected by PlayerAnimator")
        }
    }

    @Test
    fun `geckolib proxy preserves authored limb hierarchy`() {
        val geometry = resourceJson("assets/destiny2-mod/geo/thunderclap_player.geo.json")
            .getAsJsonArray("minecraft:geometry")[0].asJsonObject
        val parents = geometry.getAsJsonArray("bones").associate { boneElement ->
            val bone = boneElement.asJsonObject
            bone.get("name").asString to bone.get("parent")?.asString
        }

        assertEquals("body", parents["torso"])
        assertEquals("torso", parents["head"])
        assertEquals("torso", parents["right_arm"])
        assertEquals("right_arm", parents["right_forearm"])
        assertEquals("torso", parents["left_arm"])
        assertEquals("left_arm", parents["left_forearm"])
        assertEquals("body", parents["right_leg"])
        assertEquals("right_leg", parents["right_foreleg"])
        assertEquals("body", parents["left_leg"])
        assertEquals("left_leg", parents["left_foreleg"])
    }

    @Test
    fun `geckolib proxy animations target segmented bones without flat bend helpers`() {
        val animations = resourceJson("assets/destiny2-mod/animations/thunderclap_player.animation.json")
            .getAsJsonObject("animations")
        val expectedBones = setOf(
            "body", "torso", "head",
            "right_arm", "right_forearm", "left_arm", "left_forearm",
            "right_leg", "right_foreleg", "left_leg", "left_foreleg"
        )

        listOf(
            "animation.destiny2.player.thunderclap_charge",
            "animation.destiny2.player.thunderclap_release"
        ).forEach { animationName ->
            val bones = animations.getAsJsonObject(animationName).getAsJsonObject("bones")
            assertEquals(expectedBones, bones.keySet(), "$animationName lost a segmented bone")
            assertTrue(bones.keySet().none { it.endsWith("_bend") })
            assertTrue(
                bones.toString().contains("lerp_mode"),
                "$animationName must preserve the authored GeckoLib Catmull-Rom keys"
            )
        }
    }

    @Test
    fun `thunderclap actions keep authored charge and release timing`() {
        val charge = playerAnimation("thunderclap_charge")
        val release = playerAnimation("thunderclap_release")

        assertEquals(2.04167f, charge.get("animation_length").asFloat, 0.00001f)
        assertEquals(1.79167f, release.get("animation_length").asFloat, 0.00001f)
        assertEquals(
            DestinyActionRegistry.ARC_TITAN_THUNDERCLAP_CHARGE,
            DestinyActionRegistry.definitionForAbility(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "destiny2-mod",
                    "arc_titan_thunderclap"
                )
            )?.id
        )
    }

    @Test
    fun `thunderclap exports elbow and knee bend helper tracks`() {
        listOf("thunderclap_charge", "thunderclap_release").forEach { animationName ->
            val bones = playerAnimation(animationName).getAsJsonObject("bones")
            listOf("right_arm_bend", "left_arm_bend", "right_leg_bend", "left_leg_bend")
                .forEach { bone ->
                    assertTrue(bones.has(bone), "$animationName missing $bone")
                    assertEquals(
                        setOf("rotation"),
                        bones.getAsJsonObject(bone).keySet(),
                        "$bone must only contain PlayerAnimator bend rotation"
                    )
                    bones.getAsJsonObject(bone)
                        .getAsJsonObject("rotation")
                        .entrySet()
                        .forEach { (_, frame) ->
                            assertEquals(
                                2,
                                frame.asJsonObject.getAsJsonArray("vector").size(),
                                "$bone must use [bendAngle, bendAxis]"
                            )
                        }
                }
        }

        val chargeBones = playerAnimation("thunderclap_charge").getAsJsonObject("bones")
        val rightKnee = chargeBones.getAsJsonObject("right_leg_bend")
            .getAsJsonObject("rotation").getAsJsonObject("2.0").getAsJsonArray("vector")
        val leftKnee = chargeBones.getAsJsonObject("left_leg_bend")
            .getAsJsonObject("rotation").getAsJsonObject("2.0").getAsJsonArray("vector")
        assertEquals(54.0f, rightKnee[0].asFloat, 0.0001f)
        assertEquals(0.0f, rightKnee[1].asFloat, 0.0001f)
        assertEquals(57.86f, leftKnee[0].asFloat, 0.0001f)
        assertEquals(-5.09f, leftKnee[1].asFloat, 0.0001f)
    }

    @Test
    fun `camera track is baked and reaches authored orbit position at quarter second`() {
        val root = resourceJson("assets/destiny2-mod/animations/player/thunderclap.animation.json")
        val animations = root.getAsJsonObject("animations")
        val charge = animations.getAsJsonObject("animation.destiny2.player.thunderclap_charge")
        val release = animations.getAsJsonObject("animation.destiny2.player.thunderclap_release")
        val chargeBones = charge.getAsJsonObject("bones")

        val arrival = chargeBones.getAsJsonObject("camera")
            .getAsJsonObject("position")
            .getAsJsonObject("0.25")
            .getAsJsonArray("vector")
        assertEquals(-20.873125f, arrival[0].asFloat, 0.00001f)
        assertEquals(26.824375f, arrival[1].asFloat, 0.00001f)
        assertEquals(-26.066875f, arrival[2].asFloat, 0.00001f)
        assertTrue(chargeBones.getAsJsonObject("camera").getAsJsonObject("position").size() > 15)
        assertTrue(release.getAsJsonObject("bones").has("camera_fov"))
    }

    @Test
    fun `player spline tracks are baked instead of retaining incompatible catmull keys`() {
        listOf("thunderclap_charge", "thunderclap_release").forEach { animationName ->
            val serialized = playerAnimation(animationName).toString()
            assertTrue(!serialized.contains("lerp_mode"), "$animationName still contains Catmull-Rom keys")
            val bodyFrames = playerAnimation(animationName)
                .getAsJsonObject("bones")
                .getAsJsonObject("body")
                .getAsJsonObject("rotation")
            assertTrue(bodyFrames.size() > 30, "$animationName body track was not densely baked")
        }
    }

    @Test
    fun `player pose channels match Blockbench exported local axes`() {
        val bones = playerAnimation("thunderclap_charge")
            .getAsJsonObject("bones")
        val bodyRotation = bones.getAsJsonObject("body")
            .getAsJsonObject("rotation")
            .getAsJsonObject("0.125")
            .getAsJsonArray("vector")
        val bodyDrop = bones.getAsJsonObject("body")
            .getAsJsonObject("position")
            .getAsJsonObject("2.0")
            .getAsJsonArray("vector")
        val leftLegOffset = bones.getAsJsonObject("left_leg")
            .getAsJsonObject("position")
            .getAsJsonObject("0.41667")
            .getAsJsonArray("vector")

        // Raw .bbmodel value is [1.2726, -22.9667, -3.2585].
        assertEquals(-1.2726f, bodyRotation[0].asFloat, 0.0001f)
        assertEquals(22.9667f, bodyRotation[1].asFloat, 0.0001f)
        assertEquals(-3.2585f, bodyRotation[2].asFloat, 0.0001f)

        // Preserve the authored whole-body drop and leg translation. Position
        // uses Blockbench's [-X, Y, Z], not the camera/world coordinate map.
        assertEquals(-2.28f, bodyDrop[1].asFloat, 0.0001f)
        assertEquals(-0.015625f, leftLegOffset[0].asFloat, 0.0001f)
        assertEquals(-0.1f, leftLegOffset[1].asFloat, 0.0001f)
        assertEquals(0.046875f, leftLegOffset[2].asFloat, 0.0001f)
    }

    private fun playerAnimation(name: String): JsonObject =
        resourceJson("assets/destiny2-mod/player_animation/$name.json")
            .getAsJsonObject("animations")
            .getAsJsonObject(name)

    private fun resourceJson(path: String): JsonObject = javaClass.classLoader
        .getResourceAsStream(path)
        .also(::assertNotNull)
        .use { JsonParser.parseReader(it!!.reader()).asJsonObject }
}
