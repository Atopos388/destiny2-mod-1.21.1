package atopos.destiny2.common.item

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class GrenadeMemoryContractTest {
    private val resources = Path.of("src", "main", "resources")
    private val data = resources.resolve(Path.of("data", "destiny2-mod"))
    private val assets = resources.resolve(Path.of("assets", "destiny2-mod"))

    @Test
    fun `one neutral recipe crafts the universal grenade memory`() {
        assertRecipe(
            "grenade_memory",
            mapOf(
                "minecraft:amethyst_shard" to 1,
                "minecraft:gunpowder" to 2,
                "#destiny2-mod:light_crystals" to 1,
                "minecraft:book" to 1
            )
        )
        listOf("void_grenade_memory", "arc_grenade_memory", "solar_grenade_memory").forEach { id ->
            assertFalse(Files.exists(data.resolve(Path.of("recipe", "$id.json"))))
        }
    }

    @Test
    fun `universal grenade memory has one neutral model`() {
        val model = readJson(assets.resolve(Path.of("models", "item", "grenade_memory.json")))
        assertEquals("minecraft:item/generated", model.get("parent").asString)
        assertEquals("destiny2-mod:item/grenade_memory", model.getAsJsonObject("textures").get("layer0").asString)
    }

    @Test
    fun `element memories are passive beacon materials and only one grenade item remains`() {
        val source = Files.readString(Path.of("src/main/kotlin/atopos/destiny2/common/item/DestinyItems.kt"))
        assertEquals(1, source.split("GrenadeMemoryItem(").size - 1)
        for (id in listOf("void_grenade_memory", "arc_grenade_memory", "solar_grenade_memory", "combat_memory")) {
            org.junit.jupiter.api.Assertions.assertTrue(source.contains("""register("$id", Item("""))
        }
        val model = readJson(assets.resolve("models/item/combat_memory.json"))
        assertEquals("destiny2-mod:item/combat_memory", model.getAsJsonObject("textures").get("layer0").asString)
        val image = javax.imageio.ImageIO.read(assets.resolve("textures/item/combat_memory.png").toFile())
        assertEquals(32, image.width); assertEquals(32, image.height)
    }

    private fun assertRecipe(id: String, expectedIngredients: Map<String, Int>) {
        val recipe = readJson(data.resolve(Path.of("recipe", "$id.json")))
        assertEquals("minecraft:crafting_shaped", recipe.get("type").asString)
        assertEquals("destiny2-mod:$id", recipe.getAsJsonObject("result").get("id").asString)

        val key = recipe.getAsJsonObject("key")
        val symbolCounts = recipe.getAsJsonArray("pattern")
            .flatMap { it.asString.toList() }
            .filterNot(Char::isWhitespace)
            .groupingBy { it.toString() }
            .eachCount()
        val ingredients = symbolCounts.entries.associate { (symbol, count) ->
            val ingredient = key.getAsJsonObject(symbol)
            val name = if (ingredient.has("tag")) {
                "#${ingredient.get("tag").asString}"
            } else {
                ingredient.get("item").asString
            }
            name to count
        }
        assertEquals(expectedIngredients, ingredients)
    }

    private fun readJson(path: Path): JsonObject =
        Files.newBufferedReader(path).use(JsonParser::parseReader).asJsonObject
}
