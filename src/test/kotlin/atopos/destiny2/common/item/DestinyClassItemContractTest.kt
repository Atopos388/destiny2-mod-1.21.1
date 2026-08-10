package atopos.destiny2.common.item

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class DestinyClassItemContractTest {
    private val resources = Path.of("src", "main", "resources")
    private val namespaceData = resources.resolve(Path.of("data", "destiny2-mod"))
    private val namespaceAssets = resources.resolve(Path.of("assets", "destiny2-mod"))

    @Test
    fun `class item recipes preserve the three exploration identities`() {
        assertRecipe(
            "hunter_cloak",
            mapOf(
                "minecraft:leather" to 3,
                "minecraft:string" to 2,
                "minecraft:feather" to 2,
                "minecraft:compass" to 1,
                "#destiny2-mod:light_crystals" to 1
            )
        )
        assertRecipe(
            "titan_mark",
            mapOf(
                "minecraft:iron_ingot" to 4,
                "minecraft:copper_ingot" to 2,
                "minecraft:shield" to 1,
                "minecraft:leather" to 1,
                "#destiny2-mod:light_crystals" to 1
            )
        )
        assertRecipe(
            "warlock_bond",
            mapOf(
                "minecraft:gold_ingot" to 2,
                "minecraft:lapis_lazuli" to 3,
                "minecraft:redstone" to 2,
                "minecraft:book" to 1,
                "#destiny2-mod:light_crystals" to 1
            )
        )
    }

    @Test
    fun `light crystal tag keeps multiple world progression routes open`() {
        val tag = readJson(namespaceData.resolve(Path.of("tags", "item", "light_crystals.json")))
        val values = tag.getAsJsonArray("values").map { it.asString }.toSet()
        assertEquals(
            setOf("minecraft:amethyst_shard", "minecraft:diamond", "minecraft:ender_pearl"),
            values
        )
    }

    @Test
    fun `class item models use dedicated compact textures`() {
        listOf("hunter_cloak", "titan_mark", "warlock_bond").forEach { id ->
            val model = readJson(namespaceAssets.resolve(Path.of("models", "item", "$id.json")))
            assertEquals("minecraft:item/generated", model.get("parent").asString)
            assertEquals(
                "destiny2-mod:item/$id",
                model.getAsJsonObject("textures").get("layer0").asString
            )

            val texturePath = namespaceAssets.resolve(Path.of("textures", "item", "$id.png"))
            assertTrue(Files.size(texturePath) > 0L)
            val image = ImageIO.read(texturePath.toFile())
            assertEquals(32, image.width)
            assertEquals(32, image.height)
        }
    }

    private fun assertRecipe(id: String, expectedIngredients: Map<String, Int>) {
        val recipe = readJson(namespaceData.resolve(Path.of("recipe", "$id.json")))
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
