package atopos.destiny2.common.weapon

import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeaponGuiRenderContractTest {
    @Test
    fun `generic gun item model has a bounded gui transform`() {
        val root = javaClass.classLoader
            .getResourceAsStream("assets/destiny2-mod/models/item/gun.json")
            .also(::assertNotNull)
            .use { JsonParser.parseReader(it!!.reader()).asJsonObject }

        val gui = root.getAsJsonObject("display").getAsJsonObject("gui")
        val rotation = gui.getAsJsonArray("rotation").map { it.asFloat }
        val scale = gui.getAsJsonArray("scale")
            .map { it.asFloat }

        assertEquals(listOf(0.0f, 0.0f, 0.0f), rotation)
        assertEquals(listOf(1f, 1f, 1f), scale)
    }

    @Test
    fun `imported guns retain authored non first person scales`() {
        val adapterRoot = Path.of("run", "destiny_gunpacks", "asc-destiny-adapter", "assets", "ascgun", "destiny_gunpacks")
        listOf("ace", "outbreakprefected", "riskrunner", "summit", "whisper", "xeno").forEach { gun ->
            val definition = Files.newBufferedReader(adapterRoot.resolve("$gun.json")).use {
                JsonParser.parseReader(it).asJsonObject
            }
            assertEquals(listOf(0.6f, 0.6f, 0.6f), definition.getAsJsonArray("third_person_scale").map { it.asFloat })
            assertEquals(listOf(0.6f, 0.6f, 0.6f), definition.getAsJsonArray("ground_scale").map { it.asFloat })
            assertEquals(listOf(1.2f, 1.2f, 1.2f), definition.getAsJsonArray("fixed_scale").map { it.asFloat })
        }

        val renderer = Files.readString(Path.of("src", "client", "kotlin", "atopos", "destiny2", "client", "renderer", "GenericGunPackItemRenderer.kt"))
        assertTrue(renderer.contains("runtime.definition.displayScale(TaczGunPackResources.THIRD_PERSON_SCALE)"))
    }

    @Test
    fun `forgotten name keeps its side profile and renderer owns the gui tilt`() {
        val root = javaClass.classLoader
            .getResourceAsStream("assets/destiny2-mod/models/item/forgotten_name.json")
            .also(::assertNotNull)
            .use { JsonParser.parseReader(it!!.reader()).asJsonObject }

        val rotation = root.getAsJsonObject("display")
            .getAsJsonObject("gui")
            .getAsJsonArray("rotation")
            .map { it.asFloat }

        assertEquals(listOf(0f, 0f, 0f), rotation)

        val renderer = Files.readString(
            Path.of(
                "src", "client", "kotlin", "atopos", "destiny2", "client", "renderer",
                "TaczGunPackItemRenderer.kt"
            )
        )
        assertTrue(renderer.contains("floatArrayOf(55.94f, -82.44f, 55.83f)"))
        assertTrue(renderer.contains("GuiItemModelFit.render"))
    }
}

