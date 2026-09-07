package atopos.destiny2.common.block

import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.Bootstrap
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class LightCollectorProductionTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private fun machine(count: Int = 4) = LightCollectorInventory(Items.DIAMOND, Items.EMERALD).apply {
        setItem(0, ItemStack(Items.DIAMOND, count))
    }
    @Test fun `one output appears only at tick 600`() {
        val m = machine()
        repeat(599) { assertFalse(m.tickProduction()) }
        assertTrue(m.getItem(1).isEmpty)
        assertEquals(4, m.getItem(0).count)
        assertTrue(m.tickProduction())
        assertEquals(20, m.getItem(1).count)
        assertEquals(3, m.getItem(0).count)
        assertEquals(0, m.progress)
        repeat(600) { m.tickProduction() }
        assertEquals(40, m.getItem(1).count)
    }
    @Test fun `only crystals accepted and no input produces nothing`() {
        val m = machine()
        assertTrue(m.canPlaceItem(0, ItemStack(Items.DIAMOND)))
        assertFalse(m.canPlaceItem(0, ItemStack(Items.DIRT)))
        for (i in 1..3) assertFalse(m.canPlaceItem(i, ItemStack(Items.EMERALD)))
        repeat(70) { m.tickProduction() }
        m.setItem(0, ItemStack(Items.DIRT))
        repeat(200) { assertFalse(m.tickProduction()) }
        assertTrue(m.getItem(1).isEmpty); assertEquals(0, m.progress)
    }
    @Test fun `full storage pauses and never consumes or deletes input`() {
        val m = machine()
        repeat(90) { m.tickProduction() }
        for (i in 1..3) m.setItem(i, ItemStack(Items.EMERALD, 64))
        repeat(300) { assertFalse(m.tickProduction()) }
        assertEquals(90, m.progress); assertEquals(4, m.getItem(0).count)
        m.removeItem(2, 20)
        repeat(509) { assertFalse(m.tickProduction()) }
        assertTrue(m.tickProduction())
        assertEquals(64, m.getItem(2).count); assertEquals(3, m.getItem(0).count)
    }
    @Test fun `outputs fill successive slots without overwriting foreign stacks`() {
        val m = machine()
        m.setItem(1, ItemStack(Items.DIRT, 1))
        m.setItem(2, ItemStack(Items.EMERALD, 64))
        repeat(600) { m.tickProduction() }
        assertTrue(m.getItem(1).`is`(Items.DIRT)); assertEquals(20, m.getItem(3).count)
    }
    @Test fun `inventory and partial progress survive NBT roundtrip`() {
        val m = machine()
        m.setItem(1, ItemStack(Items.EMERALD, 12))
        repeat(79) { m.tickProduction() }
        val tag = CompoundTag()
        val registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
        m.save(tag, registries)
        val restored = machine(1)
        restored.load(tag, registries)
        assertEquals(79, restored.progress)
        assertEquals(4, restored.getItem(0).count); assertEquals(12, restored.getItem(1).count)
        repeat(520) { assertFalse(restored.tickProduction()) }
        assertTrue(restored.tickProduction()); assertEquals(32, restored.getItem(1).count)
    }
    @Test fun `batch splits across output stacks and less than twenty spaces pauses`() {
        val m = machine()
        m.setItem(1, ItemStack(Items.EMERALD, 60))
        m.setItem(2, ItemStack(Items.EMERALD, 55))
        m.setItem(3, ItemStack(Items.EMERALD, 58))
        repeat(600) { assertFalse(m.tickProduction()) }
        assertEquals(0, m.progress); assertEquals(4, m.getItem(0).count)
        m.removeItem(3, 1)
        repeat(599) { assertFalse(m.tickProduction()) }
        assertTrue(m.tickProduction())
        for (i in 1..3) assertEquals(64, m.getItem(i).count)
        assertEquals(3, m.getItem(0).count)
    }
    @Test fun `two machines do not share their progress and inventory`() {
        val a = machine(); val b = machine()
        repeat(600) { a.tickProduction() }
        assertEquals(20, a.getItem(1).count); assertTrue(b.getItem(1).isEmpty); assertEquals(0, b.progress)
    }
    @Test fun `two amethyst shards produce one crystal only at tick 160`() {
        val m = machine()
        m.setItem(0, ItemStack(Items.AMETHYST_SHARD, 2))
        assertTrue(m.canPlaceItem(0, ItemStack(Items.AMETHYST_SHARD)))
        assertTrue(m.canProcess())
        repeat(159) { assertFalse(m.tickProduction()) }
        assertEquals(2, m.getItem(0).count)
        assertTrue(m.tickProduction())
        assertTrue(m.getItem(0).isEmpty)
        assertTrue(m.getItem(1).`is`(Items.DIAMOND))
        assertEquals(1, m.getItem(1).count)
        assertFalse(m.canProcess())
        repeat(600) { assertFalse(m.tickProduction()) }
        assertEquals(1, m.getItem(1).count)
    }
    @Test fun `one amethyst or blocked outputs stop production and particle eligibility`() {
        val m = machine()
        m.setItem(0, ItemStack(Items.AMETHYST_SHARD, 1))
        assertFalse(m.canProcess())
        repeat(160) { assertFalse(m.tickProduction()) }
        assertEquals(0, m.progress)
        m.setItem(0, ItemStack(Items.AMETHYST_SHARD, 2))
        for (i in 1..3) m.setItem(i, ItemStack(Items.EMERALD, 64))
        assertFalse(m.canProcess())
        repeat(160) { assertFalse(m.tickProduction()) }
        assertEquals(2, m.getItem(0).count)
    }
    @Test fun `changing recipes cannot reuse progress`() {
        val m = machine()
        repeat(599) { m.tickProduction() }
        m.setItem(0, ItemStack(Items.AMETHYST_SHARD, 2))
        assertFalse(m.tickProduction()); assertEquals(1, m.progress)
        repeat(158) { assertFalse(m.tickProduction()) }
        assertTrue(m.tickProduction())
        m.setItem(0, ItemStack(Items.DIAMOND, 1))
        repeat(599) { assertFalse(m.tickProduction()) }
        assertTrue(m.tickProduction())
        assertEquals(20, m.getItem(2).count)
    }
    @Test fun `amethyst partial progress survives save and does not consume early`() {
        val m = machine()
        m.setItem(0, ItemStack(Items.AMETHYST_SHARD, 4))
        repeat(99) { m.tickProduction() }
        val registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
        val tag = CompoundTag(); m.save(tag, registries)
        val restored = machine()
        restored.load(tag, registries)
        repeat(60) { assertFalse(restored.tickProduction()) }
        assertTrue(restored.tickProduction())
        assertEquals(2, restored.getItem(0).count)
        assertEquals(1, restored.getItem(1).count)
    }
    @Test fun `white flow path matches narrow conduit and moves left to right`() {
        assertEquals(125f to 111f, LightCollectorUI.conduitPoint(0f))
        assertEquals(247f to 99f, LightCollectorUI.conduitPoint(1f))
        var previous = 124f
        for (i in 0..100) {
            val (x,y) = LightCollectorUI.conduitPoint(i/100f)
            assertTrue(x >= previous)
            assertTrue(y in 98f..130f)
            previous = x
        }
    }
    @Test fun `authored background is 360 by 288 and UI has persistent real slots`() {
        val root = Path.of("src/main")
        val png = ImageIO.read(root.resolve("resources/assets/destiny2-mod/textures/gui/light_collector.png").toFile())
        assertEquals(360, png.width); assertEquals(288, png.height); assertTrue(png.colorModel.hasAlpha())
        val ui = Files.readString(root.resolve("kotlin/atopos/destiny2/common/block/LightCollectorUI.kt"))
        assertTrue(ui.contains("entity?.inventory")); assertTrue(ui.contains("74f, 59f"))
        assertTrue(ui.contains("for (column in 0..8)")); assertFalse(ui.contains("onRemoved"))
        val block = Files.readString(root.resolve("kotlin/atopos/destiny2/common/block/LightCollectorBlock.kt"))
        assertTrue(block.contains("pos.below()")); assertTrue(block.contains("level.isClientSide"))
        assertTrue(block.contains("Containers.dropContents")); assertTrue(block.contains("entity.inventory.clearContent()"))
    }
}


