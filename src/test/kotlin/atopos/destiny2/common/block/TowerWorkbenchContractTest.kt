package atopos.destiny2.common.block

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class TowerWorkbenchContractTest {
    private val root = Path.of("src/main/resources")
    private val assets = root.resolve("assets/destiny2-mod")
    private fun json(path: Path): JsonObject = Files.newBufferedReader(path).use(JsonParser::parseReader).asJsonObject
    private fun model(part: Int) = json(assets.resolve("models/block/tower_workbench_$part.json"))
    private fun full() = json(assets.resolve("models/block/tower_workbench.json"))

    @Test
    fun `two adjacent cells have exactly eight model states`() {
        val variants = json(assets.resolve("blockstates/tower_workbench.json")).getAsJsonObject("variants")
        assertEquals(8, variants.size())
        listOf("north", "east", "south", "west").forEachIndexed { turns, facing ->
            for (part in 0..1) {
                val entry = variants.getAsJsonObject("facing=$facing,part=$part")
                assertEquals("destiny2-mod:block/tower_workbench_$part", entry.get("model").asString)
                assertEquals(turns * 90, entry.get("y")?.asInt ?: 0)
            }
        }
    }

    @Test
    fun `source geometry keeps width height and face UVs with depth fitted into one cell`() {
        val approved = json(Path.of("src/test/resources/fixtures/tower_workbench/tower_workbench.bbmodel"))
        val originals = approved.getAsJsonArray("elements")
        val elements = full().getAsJsonArray("elements")
        assertEquals(153, elements.size())
        elements.forEachIndexed { index, element ->
            val c = element.asJsonObject
            val source = originals[index].asJsonObject
            for (axis in 0..2) for (edge in listOf("from", "to")) {
                val v = source.getAsJsonArray(edge)[axis].asDouble
                val expected = if (axis == 2) (v + 8.25) * 16.0 / 16.25 else v + if (axis == 0) 16.0 else 0.0
                assertEquals(expected, c.getAsJsonArray(edge)[axis].asDouble, 0.000001)
            }
            c.getAsJsonObject("faces").entrySet().forEach { (name, f) ->
                f.asJsonObject.getAsJsonArray("uv").forEachIndexed { indexUV, v ->
                    assertEquals(source.getAsJsonObject("faces").getAsJsonObject(name).getAsJsonArray("uv")[indexUV].asDouble / 64.0, v.asDouble, 0.000001)
                }
            }
        }
    }

    @Test
    fun `split models fit local horizontal collision bounds and preserve volume`() {
        fun volume(m: JsonObject) = m.getAsJsonArray("elements").sumOf { e ->
            val c = e.asJsonObject
            (0..2).map { c.getAsJsonArray("to")[it].asDouble - c.getAsJsonArray("from")[it].asDouble }.reduce(Double::times)
        }
        assertEquals(volume(full()), (0..1).sumOf { volume(model(it)) }, 0.0001)
        for (part in 0..1) for (element in model(part).getAsJsonArray("elements")) {
            val c = element.asJsonObject
            for (axis in 0..2) {
                val min = c.getAsJsonArray("from")[axis].asDouble
                val max = c.getAsJsonArray("to")[axis].asDouble
                assertTrue(min >= 0.0 && max <= if (axis == 1) 28.0 else 16.0)
                assertTrue(min < max)
            }
            c.getAsJsonObject("faces").entrySet().forEach { (_, f) ->
                f.asJsonObject.getAsJsonArray("uv").forEach { assertTrue(it.asDouble in 0.0..16.0) }
            }
        }
    }

    @Test
    fun `ordinary two cell structure avoids complex collision unions and entities`() {
        val s = Files.readString(Path.of("src/main/kotlin/atopos/destiny2/common/block/TowerWorkbenchBlock.kt"))
        assertTrue(s.contains("IntegerProperty.create(\"part\", 0, 1)"))
        assertTrue(s.contains("doubleArrayOf(0.0, 0.0, 0.0, 16.0, 20.0, 16.0)"))
        assertFalse(s.contains("EntityBlock"))
        assertEquals(6, Regex("doubleArrayOf").findAll(s).count())
        assertTrue(s.contains("private val SHAPES"))
    }

    @Test
    fun `approved texture is byte identical`() {
        assertArrayEquals(Files.readAllBytes(Path.of("src/test/resources/fixtures/tower_workbench/tower_workbench.png")),
            Files.readAllBytes(assets.resolve("textures/block/tower_workbench.png")))
    }

    @Test
    fun `only anchor drops one workbench`() {
        val loot = json(root.resolve("data/destiny2-mod/loot_table/blocks/tower_workbench.json"))
        val pool = loot.getAsJsonArray("pools").single().asJsonObject
        val condition = pool.getAsJsonArray("conditions").map { it.asJsonObject }.single { it.get("condition").asString == "minecraft:block_state_property" }
        assertEquals("0", condition.getAsJsonObject("properties").get("part").asString)
        assertEquals(1, pool.get("rolls").asInt)
        assertEquals("destiny2-mod:tower_workbench", pool.getAsJsonArray("entries").single().asJsonObject.get("name").asString)
    }

    @Test
    fun `localized item model remains registered`() {
        for (lang in listOf("zh_cn", "en_us")) assertTrue(json(assets.resolve("lang/$lang.json")).has("block.destiny2-mod.tower_workbench"))
        assertEquals("destiny2-mod:block/tower_workbench", json(assets.resolve("models/item/tower_workbench.json")).get("parent").asString)
    }
}

