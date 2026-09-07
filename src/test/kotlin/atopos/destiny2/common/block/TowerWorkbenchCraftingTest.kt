package atopos.destiny2.common.block

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class TowerWorkbenchCraftingTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private fun session(count: Int = 1) = TowerWorkbenchCrafting(Items.HEART_OF_THE_SEA, Items.AMETHYST_SHARD, Items.IRON_SWORD).apply {
        setItem(3, ItemStack(Items.AMETHYST_SHARD, count))
        setItem(4, ItemStack(Items.HEART_OF_THE_SEA, count))
        setItem(5, ItemStack(Items.AMETHYST_SHARD, count))
    }
    @Test fun `exact middle row consumes one each and produces one weapon`() {
        val s = session(4)
        assertTrue(s.craft())
        for (i in 3..5) assertEquals(3, s.getItem(i).count)
        assertTrue(s.getItem(9).`is`(Items.IRON_SWORD))
        assertEquals(1, s.getItem(9).count)
    }
    @Test fun `missing ingredient or wrong item never consumes`() {
        for (i in 3..5) {
            val s = session(3); s.setItem(i, ItemStack.EMPTY)
            assertFalse(s.craft()); assertTrue(s.getItem(9).isEmpty)
            for (other in (3..5).filter { it != i }) assertEquals(3, s.getItem(other).count)
        }
        val s = session(); s.setItem(4, ItemStack(Items.DIRT)); assertFalse(s.craft())
    }
    @Test fun `wrong row and extra ingredients are not this shaped recipe`() {
        val s = session(); for (i in 0..2) s.setItem(i, s.removeItemNoUpdate(i + 3))
        assertFalse(s.craft())
        val extra = session(); extra.setItem(0, ItemStack(Items.DIRT)); assertFalse(extra.craft())
    }
    @Test fun `double click cannot overwrite output or consume twice`() {
        val s = session(2)
        assertTrue(s.craft()); assertFalse(s.craft())
        assertEquals(1, s.getItem(4).count)
        assertEquals(1, s.removeItemNoUpdate(9).count)
        assertTrue(s.craft()); assertFalse(s.craft())
    }
    @Test fun `closing returns leftovers and unclaimed output exactly once`() {
        val s = session(2); s.craft()
        val returned = s.closeAndDrain()
        assertEquals(4, returned.sumOf { it.count })
        assertEquals(1, returned.count { it.`is`(Items.IRON_SWORD) })
        assertTrue(s.isEmpty); assertTrue(s.closeAndDrain().isEmpty()); assertFalse(s.craft())
    }
    @Test fun `two players have independent sessions`() {
        val a = session(); val b = session()
        a.craft(); a.closeAndDrain()
        assertTrue(b.canCraft()); assertTrue(b.craft())
    }
    @Test fun `closing an unmatched inventory preserves arbitrary items and components`() {
        val s = session(); s.setItem(0, ItemStack(Items.DIAMOND, 12))
        val returned = s.closeAndDrain()
        assertEquals(12, returned.single { it.`is`(Items.DIAMOND) }.count)
        assertEquals(15, returned.sumOf { it.count })
    }
    @Test fun `material models names and native pixel exports are bundled`() {
        val root = java.nio.file.Path.of("src/main/resources/assets/destiny2-mod")
        for (id in listOf("forgotten_heart", "ancient_shard")) {
            val image = javax.imageio.ImageIO.read(root.resolve("textures/item/$id.png").toFile())
            assertEquals(32, image.width); assertEquals(32, image.height)
            assertTrue(image.colorModel.hasAlpha())
            val model = java.nio.file.Files.readString(root.resolve("models/item/$id.json"))
            assertTrue(model.contains("destiny2-mod:item/$id"))
            for (lang in listOf("zh_cn", "en_us")) {
                assertTrue(java.nio.file.Files.readString(root.resolve("lang/$lang.json")).contains("item.destiny2-mod.$id"))
            }
        }
    }
    @Test fun `server cleanup hook is registered and crafting remains server routed`() {
        val config = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/destiny2-mod.mixins.json"))
        assertTrue(config.contains("TowerWorkbenchMenuCleanupMixin"))
        val ui = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/kotlin/atopos/destiny2/common/block/TowerWorkbenchUI.kt"))
        assertTrue(ui.contains("setOnServerClick")); assertTrue(ui.contains("block.stillValid(holder)"))
        assertTrue(ui.contains("session.closeAndDrain()")); assertTrue(ui.contains("override fun mayPlace"))
    }
    @Test fun `workbench shell has no recipe instructions or ingredient specific input filters`() {
        val ui = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/kotlin/atopos/destiny2/common/block/TowerWorkbenchUI.kt"))
        for (id in listOf("material_hint", "preview_status", "recipe_hint", "return_hint")) {
            assertFalse(ui.contains(id))
        }
        assertFalse(ui.contains("中排放入三份材料"))
        assertFalse(ui.contains("stack.`is`(DestinyItems.ANCIENT_SHARD)"))
        assertFalse(ui.contains("stack.`is`(DestinyItems.FORGOTTEN_HEART)"))
        assertTrue(ui.contains("Slot(session, index, 0, 0)"))
    }
}

