package atopos.destiny2.client.renderer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
class CreativeInventoryPolishTest {
    @Test fun `long models are centered within fourteen pixels`() {
        val e=GuiItemModelFit.Extents();e.addVertex(-10f,-2f,0f);e.addVertex(22f,6f,4f)
        val b=e.result()!!
        assertEquals(6f,b.x);assertEquals(2f,b.y)
        assertEquals(.875f,32f*b.scale,.00001f)
        assertEquals(.0625f,.5f+(-10f-b.x)*b.scale,.00001f)
        assertEquals(.9375f,.5f+(22f-b.x)*b.scale,.00001f)
    }
    @Test fun `invalid and tall geometry bounds`() {
        val e=GuiItemModelFit.Extents();assertNull(e.result())
        e.addVertex(Float.NaN,0f,0f);assertNull(e.result())
        e.addVertex(0f,-5f,0f);e.addVertex(1f,15f,0f)
        assertEquals(.875f,20f*e.result()!!.scale,.00001f)
    }
    @Test fun `item textures resolve in stitched paths`() {
        val root=Path.of("src/main/resources/assets/destiny2-mod")
        Files.list(root.resolve("models/item")).use { files -> files.filter { it.toString().endsWith(".json") }.forEach { p ->
            val j=JsonParser.parseString(Files.readString(p).removePrefix("\uFEFF")).asJsonObject
            j.getAsJsonObject("textures")?.entrySet()?.forEach { (_,v) ->
                val ref=v.asString
                assertFalse(ref.startsWith("destiny2-mod:gui/"),p.toString())
                if(ref.startsWith("destiny2-mod:item/")) assertTrue(Files.exists(root.resolve("textures/"+ref.substringAfter(':')+".png")),ref)
            }
        } }
    }
    @Test fun `nine hotbar bindings fit extended artwork`() {
        val source=Files.readString(Path.of("src/main/kotlin/atopos/destiny2/common/block/TowerWorkbenchUI.kt"))
        assertTrue(source.contains("Slot(holder.player.inventory, column, 0, 0)"))
        assertTrue(source.contains("360f, 296f"))
        val im=javax.imageio.ImageIO.read(Path.of("src/main/resources/assets/destiny2-mod/textures/gui/tower_workbench.png").toFile())
        assertEquals(360,im.width);assertEquals(296,im.height)
    }
}
