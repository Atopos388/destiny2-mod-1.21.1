package atopos.destiny2.common.block

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class IndustrialMachineContractTest {
    private val resources = Path.of("src", "main", "resources")

    @Test
    fun `all machines provide blockstate models items loot and recipes`() {
        IndustrialMachineKind.entries.forEach { kind ->
            val id = kind.id
            assertTrue(Files.exists(resources.resolve("assets/destiny2-mod/blockstates/$id.json")))
            assertTrue(Files.exists(resources.resolve("assets/destiny2-mod/models/block/$id.json")))
            assertTrue(Files.exists(resources.resolve("assets/destiny2-mod/models/block/${id}_on.json")))
            assertTrue(Files.exists(resources.resolve("assets/destiny2-mod/models/item/$id.json")))
            assertTrue(Files.exists(resources.resolve("data/destiny2-mod/loot_table/blocks/$id.json")))
            val recipe = readJson(resources.resolve("data/destiny2-mod/recipe/$id.json"))
            assertEquals("destiny2-mod:$id", recipe.getAsJsonObject("result").get("id").asString)
        }
    }

    @Test
    fun `machine blockstates cover facing and powered visuals`() {
        IndustrialMachineKind.entries.forEach { kind ->
            val variants = readJson(resources.resolve("assets/destiny2-mod/blockstates/${kind.id}.json"))
                .getAsJsonObject("variants")
            assertEquals(8, variants.size())
            assertTrue(variants.has("facing=north,lit=false"))
            assertTrue(variants.has("facing=north,lit=true"))
        }
    }

    private fun readJson(path: Path): JsonObject =
        Files.newBufferedReader(path).use(JsonParser::parseReader).asJsonObject
}
