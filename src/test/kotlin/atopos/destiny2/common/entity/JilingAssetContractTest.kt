package atopos.destiny2.common.entity

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JilingAssetContractTest {
    private val geometry = resourceJson("assets/destiny2-mod/geo/entity/jiling.geo.json")
    private val materials = resourceJson(
        "assets/destiny2-mod/geckolib_multitexture/entity/jiling.bone_textures.json"
    )

    @Test
    fun `every visible bone has an explicit material`() {
        val visibleBones = geometry.getAsJsonArray("minecraft:geometry")[0]
            .asJsonObject
            .getAsJsonArray("bones")
            .filter { bone -> bone.asJsonObject.getAsJsonArray("cubes")?.isEmpty == false }
            .map { bone -> bone.asJsonObject.get("name").asString }
            .toSet()
        val mappedBones = materials.getAsJsonObject("bones").keySet()

        assertEquals(35, visibleBones.size)
        assertEquals(visibleBones, mappedBones)
    }

    @Test
    fun `all four authored textures are packaged`() {
        val textureIds = materials.getAsJsonObject("bones").entrySet()
            .map { (_, value) -> value.asJsonObject.get("texture").asString }
            .plus(materials.get("default_texture").asString)
            .toSet()

        assertEquals(
            setOf(
                "destiny2-mod:textures/entity/jiling/texture.png",
                "destiny2-mod:textures/entity/jiling/texture2.png",
                "destiny2-mod:textures/entity/jiling/texture3.png",
                "destiny2-mod:textures/entity/jiling/texture4.png"
            ),
            textureIds
        )
        textureIds.forEach { textureId ->
            val path = textureId.substringAfter(':')
            assertNotNull(javaClass.classLoader.getResource("assets/destiny2-mod/$path"), textureId)
        }
    }

    @Test
    fun `manifest only requests supported render modes`() {
        assertEquals(1, materials.get("format_version").asInt)
        assertTrue(
            materials.getAsJsonObject("bones").entrySet().all { (_, value) ->
                value.asJsonObject.get("render_type").asString in setOf("cutout", "translucent")
            }
        )
    }

    private fun resourceJson(path: String): JsonObject = javaClass.classLoader
        .getResourceAsStream(path)
        .also(::assertNotNull)
        .use { JsonParser.parseReader(it!!.reader()).asJsonObject }
}
