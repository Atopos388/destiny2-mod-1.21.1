package atopos.destiny2.common.block
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
class RetiredMachinesTest {
    private val ids=listOf("light_capacitor","glimmer_refinery","memory_foundry")
    @Test fun `retired machines have no packaged resources`() {
        for(id in ids) for(p in listOf("assets/destiny2-mod/blockstates/$id.json",
            "assets/destiny2-mod/models/item/$id.json","assets/destiny2-mod/models/block/$id.json",
            "assets/destiny2-mod/models/block/${id}_on.json","data/destiny2-mod/recipe/$id.json",
            "data/destiny2-mod/loot_table/blocks/$id.json")) assertFalse(Files.exists(Path.of("src/main/resources",p)),p)
    }
    @Test fun `retired registrations and editor entries are removed`() {
        val blocks=Files.readString(Path.of("src/main/kotlin/atopos/destiny2/common/block/DestinyBlocks.kt"))
        assertFalse(blocks.contains("IndustrialMachine"));ids.forEach { assertFalse(blocks.contains(it)) }
        for(name in listOf("DestinyLDLibEditor","DestinyHUDClientCommands")) {
            val s=Files.readString(Path.of("src/client/kotlin/atopos/destiny2/client/gui/$name.kt"))
            assertFalse(s.contains("IndustrialMachine"));assertFalse(s.contains("Target.MACHINE"))
        }
    }
    @Test fun `modern workbench collector and shared framework are retained`() {
        for(name in listOf("TowerWorkbenchBlock","TowerWorkbenchUI","TowerWorkbenchCrafting","LightCollectorBlock","LightCollectorBlockEntity"))
            assertTrue(Files.exists(Path.of("src/main/kotlin/atopos/destiny2/common/block/$name.kt")))
        val editor=Files.readString(Path.of("src/client/kotlin/atopos/destiny2/client/gui/DestinyLDLibEditor.kt"))
        assertTrue(editor.contains("Target.ASPECT"));assertTrue(editor.contains("Target.NAVIGATION"))
    }
}
