package atopos.destiny2.common.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class SolarArcSubclassCatalogTest {
    @Test
    fun `current manifest catalog exposes all solar and arc fragments`() {
        val solar = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.SOLAR_WARLOCK)
        val arc = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.ARC_TITAN)

        assertEquals(16, solar.fragmentOptions.size)
        assertEquals(
            setOf(
                "仁慈余烬", "光线余烬", "决心余烬", "喷发余烬",
                "回火余烬", "惊奇余烬", "慈悲余烬", "抚慰余烬",
                "火炬余烬", "炽热余烬", "烧焦余烬", "焦燃余烬",
                "燃烧余烬", "至高天余烬", "起泡余烬", "骨灰余烬"
            ),
            solar.fragmentOptions.mapTo(mutableSetOf(), DestinyConfigOption::title)
        )

        assertEquals(16, arc.fragmentOptions.size)
        assertEquals(
            setOf(
                "专注火花", "伏特火花", "信标火花", "充能火花",
                "光辉火花", "动量火花", "反馈火花", "增幅火花",
                "急速火花", "抗性火花", "放电火花", "直觉火花",
                "离子火花", "量级火花", "震颤火花", "频率火花"
            ),
            arc.fragmentOptions.mapTo(mutableSetOf(), DestinyConfigOption::title)
        )
        assertFalse(arc.fragmentOptions.any { "placeholder" in it.id })
    }

    @Test
    fun `current manifest aspect catalog preserves names and slot counts`() {
        val solar = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.SOLAR_WARLOCK)
        val arc = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.ARC_TITAN)

        assertEquals(
            mapOf("炙热升腾" to 2, "火焰之触" to 2, "伊卡洛斯突进" to 3, "地狱火" to 2),
            solar.aspectOptions.associate { it.title to it.fragmentSlots }
        )
        assertEquals(
            mapOf("无畏护甲" to 2, "暴雷之触" to 2, "重击" to 2, "风暴要塞" to 2),
            arc.aspectOptions.associate { it.title to it.fragmentSlots }
        )
        assertFalse(arc.aspectOptions.any { "placeholder" in it.id })
    }

    @Test
    fun `every catalog entry has an imported official icon`() {
        val options = listOf(DestinySubclassType.SOLAR_WARLOCK, DestinySubclassType.ARC_TITAN)
            .flatMap { subclass ->
                DestinySubclassConfigRegistry.definitionFor(subclass).let { it.aspectOptions + it.fragmentOptions }
            }

        options.forEach { option ->
            val icon = requireNotNull(option.icon) { "Missing icon id for ${option.id}" }
            val path = Path.of("src", "main", "resources", "assets", icon.namespace, icon.path)
            assertTrue(Files.isRegularFile(path), "Missing icon file for ${option.id}: $path")
            val image = ImageIO.read(path.toFile())
            assertEquals(96, image.width, "Unexpected icon width for ${option.id}")
            assertEquals(96, image.height, "Unexpected icon height for ${option.id}")
        }
    }
}
