package atopos.destiny2.common.player

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.bernie.geckolib.animation.EasingType
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter
import software.bernie.geckolib.loading.`object`.BakedAnimations
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class GuardianAwakeningVideoContractTest {
    private val resources = Path.of("src", "main", "resources")
    private val frames = resources.resolve(
        Path.of("assets", "destiny2-mod", "textures", "cinematic", "guardian_awakening")
    )

    @Test
    fun `awakening film exports the complete ordered frame sequence`() {
        val exported = Files.list(frames).use { stream ->
            stream
                .filter { it.extension == "jpg" }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }

        assertEquals(312, exported.size)
        assertEquals("frame_0001.jpg", exported.first())
        assertEquals("frame_0312.jpg", exported.last())
        exported.forEachIndexed { index, name ->
            assertEquals("frame_%04d.jpg".format(index + 1), name)
        }
    }

    @Test
    fun `awakening film audio is present and streamed`() {
        val audio = resources.resolve(
            Path.of("assets", "destiny2-mod", "sounds", "cinematic", "guardian_awakening.ogg")
        )
        assertTrue(Files.size(audio) > 0L)

        val sounds = Files.newBufferedReader(
            resources.resolve(Path.of("assets", "destiny2-mod", "sounds.json"))
        ).use(JsonParser::parseReader).asJsonObject
        val entry = sounds.getAsJsonObject("guardian_awakening")
            .getAsJsonArray("sounds")
            .first()
            .asJsonObject
        assertEquals("destiny2-mod:cinematic/guardian_awakening", entry.get("name").asString)
        assertTrue(entry.get("stream").asBoolean)
    }

    @Test
    fun `awakening dialogue keeps verified audio subtitle and playback order`() {
        val expected = listOf(
            Triple("awakening_eyes_up", "eyes_up", 3),
            Triple("awakening_searched", "searched", 1),
            Triple("awakening_ghost", "ghost", 5),
            Triple("awakening_wait", "wait", 1),
            Triple("awakening_move", "move", 2)
        )
        val sounds = Files.newBufferedReader(
            resources.resolve(Path.of("assets", "destiny2-mod", "sounds.json"))
        ).use(JsonParser::parseReader).asJsonObject
        val chinese = Files.newBufferedReader(
            resources.resolve(Path.of("assets", "destiny2-mod", "lang", "zh_cn.json"))
        ).use(JsonParser::parseReader).asJsonObject
        val clientSource = Files.readString(
            Path.of(
                "src", "client", "kotlin", "atopos", "destiny2", "client",
                "cinematic", "AwakeningDialogueClient.kt"
            )
        )

        var previousOrder = -1
        expected.forEach { (event, file, segmentCount) ->
            val entry = sounds.getAsJsonObject(event).getAsJsonArray("sounds").first().asJsonObject
            assertEquals(
                "destiny2-mod:cinematic/guardian_awakening_dialogue/$file",
                entry.get("name").asString
            )
            assertTrue(entry.get("stream").asBoolean)
            assertTrue(
                Files.size(
                    resources.resolve(
                        Path.of(
                            "assets", "destiny2-mod", "sounds", "cinematic",
                            "guardian_awakening_dialogue", "$file.ogg"
                        )
                    )
                ) > 0L
            )

            val subtitlePrefix = "dialogue.destiny2-mod.awakening.${event.removePrefix("awakening_")}"
            assertTrue(chinese.has(subtitlePrefix))
            (1..segmentCount).forEach { segment ->
                val segmentKey = "$subtitlePrefix.$segment"
                assertTrue(chinese.has(segmentKey))
                assertTrue(clientSource.contains("\"$segmentKey\""))
            }

            val order = clientSource.indexOf("DestinySounds.${event.uppercase()}")
            assertTrue(order > previousOrder)
            previousOrder = order
        }
        assertTrue(!clientSource.contains("font.split("))
    }

    @Test
    fun `film transitions into the authored Ghost animation and camera`() {
        val animation = Files.newBufferedReader(
            resources.resolve(
                Path.of("assets", "destiny2-mod", "animations", "entity", "jiling.animation.json")
            )
        ).use(JsonParser::parseReader).asJsonObject
            .getAsJsonObject("animations")
            .getAsJsonObject("animation.destiny2.jiling.awakening")

        assertEquals(27.04167, animation.get("animation_length").asDouble, 0.00001)
        val bones = animation.getAsJsonObject("bones")
        assertTrue(bones.has("group41"))
        assertTrue(bones.has("camera"))
        assertTrue(bones.getAsJsonObject("camera").getAsJsonObject("rotation").has("25.83333"))
        val bakedPosition = bones.getAsJsonObject("group41").getAsJsonObject("position")
        assertTrue(bakedPosition.entrySet().size > 1_000)
        val bakedTimes = bakedPosition.entrySet().map { (timestamp) -> timestamp.toDouble() }
        assertEquals(bakedTimes.sorted(), bakedTimes)
        assertTrue(
            bakedPosition.entrySet().all { (_, keyframe) ->
                val value = keyframe.asJsonObject
                value.has("vector") && !value.has("easing") && !value.has("lerp_mode")
            }
        )
        assertEquals(
            -11.03125,
            bones.getAsJsonObject("camera")
                .getAsJsonObject("position")
                .getAsJsonObject("0.0")
                .getAsJsonArray("vector")[2]
                .asDouble,
            0.00001
        )
        val openingPitch = bones.getAsJsonObject("camera").getAsJsonObject("rotation")
        assertEquals(
            -57.0,
            openingPitch.getAsJsonObject("0.0").getAsJsonArray("vector")[0].asDouble,
            0.00001
        )
        assertTrue(
            openingPitch.getAsJsonObject("3.99167").getAsJsonArray("vector")[0].asDouble < 0.0,
            "the first four seconds must lower the view instead of playing with reversed pitch"
        )
        val effects = animation.getAsJsonObject("sound_effects")
        assertEquals("eyes_up", effects.getAsJsonObject("0.0").get("effect").asString)
        assertEquals("move", effects.getAsJsonObject("21.79167").get("effect").asString)

        val clientSource = Files.readString(
            Path.of(
                "src", "client", "kotlin", "atopos", "destiny2", "client",
                "cinematic", "CinematicCameraClient.kt"
            )
        )
        assertTrue(clientSource.indexOf("Phase.VIDEO") < clientSource.indexOf("Phase.ANIMATION"))
        assertTrue(clientSource.contains("renderEyeOpening"))
        assertTrue(!clientSource.contains("AwakeningDialogueClient.start {"))
    }

    @Test
    fun `GeckoLib receives the densely baked Blockbench spline without its broken easing`() {
        val animationFile = Files.newBufferedReader(
            resources.resolve(
                Path.of("assets", "destiny2-mod", "animations", "entity", "jiling.animation.json")
            )
        ).use(JsonParser::parseReader).asJsonObject
        val baked = KeyFramesAdapter.GEO_GSON.fromJson(
            animationFile.getAsJsonObject("animations"),
            BakedAnimations::class.java
        )
        val group41 = requireNotNull(baked.getAnimation("animation.destiny2.jiling.awakening"))
            .boneAnimations()
            .first { it.boneName() == "group41" }

        assertTrue(group41.positionKeyFrames().xKeyframes().size > 1_000)
        assertTrue(group41.rotationKeyFrames().xKeyframes().size > 1_000)
        assertTrue(group41.positionKeyFrames().xKeyframes().all { it.easingType() === EasingType.LINEAR })
        assertTrue(group41.rotationKeyFrames().xKeyframes().all { it.easingType() === EasingType.LINEAR })
    }

    @Test
    fun `awakening geometry preserves the animated Blockbench hierarchy`() {
        val geometry = Files.newBufferedReader(
            resources.resolve(Path.of("assets", "destiny2-mod", "geo", "entity", "jiling.geo.json"))
        ).use(JsonParser::parseReader).asJsonObject
            .getAsJsonArray("minecraft:geometry")
            .first()
            .asJsonObject
        val bones = geometry.getAsJsonArray("bones")
            .associate { bone ->
                val json = bone.asJsonObject
                json.get("name").asString to json
            }

        assertEquals("group41", bones.getValue("bone").get("parent").asString)
        assertEquals("bone", bones.getValue("group").get("parent").asString)
        assertEquals("bone", bones.getValue("group40").get("parent").asString)
        assertEquals("group12", bones.getValue("bone2").get("parent").asString)
    }

    @Test
    fun `authored Ghost movement sounds are packaged separately`() {
        val sounds = Files.newBufferedReader(
            resources.resolve(Path.of("assets", "destiny2-mod", "sounds.json"))
        ).use(JsonParser::parseReader).asJsonObject
        listOf("in", "out").forEach { direction ->
            val event = "awakening_ghost_move_$direction"
            val file = "ghost_move_$direction"
            assertEquals(
                "destiny2-mod:cinematic/guardian_awakening_dialogue/$file",
                sounds.getAsJsonObject(event).getAsJsonArray("sounds").first().asString
            )
            assertTrue(
                Files.size(
                    resources.resolve(
                        Path.of(
                            "assets", "destiny2-mod", "sounds", "cinematic",
                            "guardian_awakening_dialogue", "$file.ogg"
                        )
                    )
                ) > 0L
            )
        }
    }
}
