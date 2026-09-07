package atopos.destiny2.common.gear

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class WeaponLoreTest {
    @Test
    fun `all twelve firearms have stories`() {
        val stories = WeaponLore.all()

        assertEquals(12, stories.size)
        EXPECTED_FIREARMS.forEach { id ->
            assertTrue(stories[id].orEmpty().isNotBlank(), "$id should have a weapon story")
        }
    }

    @Test
    fun `stories are complete multi page narratives instead of summaries`() {
        WeaponLore.all().forEach { (id, story) ->
            val pages = WeaponLore.pages(story)
            assertTrue(pages.size >= 3, "$id should expose at least three story pages")
            assertTrue(story.length >= 180, "$id should contain a complete narrative")
            assertTrue(pages.all { it.length in 45..260 }, "$id pages should fit the navigation reader")
        }
    }

    @Test
    fun `all six imported weapons end with author credit`() {
        val stories = WeaponLore.all()

        ASC_FIREARMS.forEach { id ->
            assertTrue(
                stories[id].orEmpty().endsWith(WeaponLore.ASC_AUTHOR_SUFFIX),
                "$id should end with the imported-pack author credit"
            )
        }
    }

    @Test
    fun `official firearms have traceable lore sources and originals stay separate`() {
        val sources = WeaponLore.officialSources()

        assertEquals(OFFICIAL_FIREARMS, sources.keys)
        assertEquals(ORIGINAL_FIREARMS, EXPECTED_FIREARMS - sources.keys)
        sources.forEach { (id, source) ->
            assertTrue(source.startsWith("https://www.ishtar-collective.net/entries/"), "$id should link to its lore entry")
            assertTrue(!WeaponLore.all().getValue(id).contains("官方传说摘要"), "$id should not expose development wording to players")
        }
    }

    @Test
    fun `navigation weapon detail owns the legendary story instead of equipment cards`() {
        val collectionSource = Files.readString(Path.of("src/client/kotlin/atopos/destiny2/client/gui/DestinyCollectionsView.kt"))
        val equipmentCardSource = Files.readString(Path.of("src/client/kotlin/atopos/destiny2/client/gear/PerkTooltipData.kt"))

        assertTrue(collectionSource.contains("武器传奇故事"))
        assertTrue(collectionSource.contains("WeaponLore.pages(entry.description)"))
        assertTrue(collectionSource.contains("weapon_story_prev"))
        assertTrue(collectionSource.contains("weapon_story_next"))
        assertTrue(!equipmentCardSource.contains("weapon_lore/"))
        assertTrue(!equipmentCardSource.contains("武器故事"))
    }

    @Test
    fun `navigation previews build real generic gun stacks with their definition ids`() {
        val collectionDataSource = Files.readString(Path.of("src/client/kotlin/atopos/destiny2/client/gui/DestinyCollectionsData.kt"))

        assertTrue(collectionDataSource.contains("GenericGunPackItem.stack(definition.id)"))
        assertTrue(!collectionDataSource.contains(".map { ItemStack(it.item) to it }"))
    }

    private companion object {
        val ASC_FIREARMS = setOf(
            "ascgun:ace",
            "ascgun:outbreakprefected",
            "ascgun:riskrunner",
            "ascgun:summit",
            "ascgun:whisper",
            "ascgun:xeno"
        )

        val EXPECTED_FIREARMS = ASC_FIREARMS + setOf(
            "destiny2-mod:forgotten_name",
            "destiny2-mod:izanagis_burden",
            "destiny2-mod:monte_carlo",
            "destiny2-mod:the_deicide",
            "destiny2-mod:perfect_retrograde",
            "destiny2-mod:micro_missile_test"
        )

        val ORIGINAL_FIREARMS = setOf(
            "destiny2-mod:forgotten_name",
            "destiny2-mod:perfect_retrograde",
            "destiny2-mod:micro_missile_test"
        )

        val OFFICIAL_FIREARMS = EXPECTED_FIREARMS - ORIGINAL_FIREARMS
    }
}
