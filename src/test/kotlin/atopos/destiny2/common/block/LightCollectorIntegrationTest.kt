package atopos.destiny2.common.block

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.SharedConstants
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.MappedRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.BlockCollisions
import net.minecraft.world.level.CollisionGetter
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.VoxelShape
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class LightCollectorIntegrationTest {
    private val source = Path.of("src/test/resources/fixtures/light_collector")
    private val assets = Path.of("src/main/resources/assets/destiny2-mod")
    private fun json(path: Path): JsonObject = Files.newBufferedReader(path).use(JsonParser::parseReader).asJsonObject
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }

    @Test fun `runtime preserves current user export not the older 88 cube revision`() {
        for ((file, target) in listOf("light_collector.geo.json" to "geo", "light_collector.animation.json" to "animations", "light_collector.png" to "textures/block")) {
            assertArrayEquals(Files.readAllBytes(source.resolve(file)), Files.readAllBytes(assets.resolve("$target/$file")))
        }
        val m = json(source.resolve("user_current.bbmodel"))
        val geo = json(assets.resolve("geo/light_collector.geo.json")).getAsJsonArray("minecraft:geometry")[0].asJsonObject
        assertEquals(m.getAsJsonArray("elements").size(), geo.getAsJsonArray("bones").sumOf { it.asJsonObject.getAsJsonArray("cubes")?.size() ?: 0 })
        assertEquals(86, m.getAsJsonArray("elements").size())
        assertEquals(86, json(assets.resolve("models/item/light_collector.json")).getAsJsonArray("elements").size())
    }

    @Test fun `authored animation targets exist and timestamps stay ordered`() {
        val geo = json(assets.resolve("geo/light_collector.geo.json")).getAsJsonArray("minecraft:geometry")[0].asJsonObject
        val bones = geo.getAsJsonArray("bones").map { it.asJsonObject.get("name").asString }.toSet()
        val animations = json(assets.resolve("animations/light_collector.animation.json")).getAsJsonObject("animations")
        assertEquals(2, animations.size())
        assertEquals(1.5, animations.getAsJsonObject("animation.light_collector.place").get("animation_length").asDouble)
        assertTrue(animations.getAsJsonObject("animation.light_collector.operate").get("loop").asBoolean)
        animations.entrySet().forEach { (_, entry) ->
            entry.asJsonObject.getAsJsonObject("bones").entrySet().forEach { (bone, channels) ->
                assertTrue(bone in bones, bone)
                channels.asJsonObject.entrySet().forEach { (_, track) ->
                    if (track.isJsonObject) {
                        val times = track.asJsonObject.keySet().map(String::toDouble)
                        assertEquals(times.sorted(), times)
                        assertTrue(times.all { it >= 0 && it <= entry.asJsonObject.get("animation_length").asDouble })
                    }
                }
            }
        }
    }

    @Test fun `two vertical states per facing and only lower item drop`() {
        val variants = json(assets.resolve("blockstates/light_collector.json")).getAsJsonObject("variants")
        assertEquals(8, variants.size())
        for (f in listOf("north","east","south","west")) for(h in listOf("lower","upper")) assertTrue(variants.has("facing=$f,half=$h"))
        val loot = json(Path.of("src/main/resources/data/destiny2-mod/loot_table/blocks/light_collector.json"))
        val pool = loot.getAsJsonArray("pools").single().asJsonObject
        val c = pool.getAsJsonArray("conditions").map { it.asJsonObject }.single { it.get("condition").asString == "minecraft:block_state_property" }
        assertEquals("lower", c.getAsJsonObject("properties").get("half").asString)
        assertEquals(1, pool.get("rolls").asInt)
    }

    private fun block(): LightCollectorBlock {
        val registry = BuiltInRegistries.BLOCK
        val f = MappedRegistry::class.java.getDeclaredField("frozen").apply { isAccessible = true }
        val h = MappedRegistry::class.java.getDeclaredField("unregisteredIntrusiveHolders").apply { isAccessible = true }
        val oldF=f.get(registry); val oldH=h.get(registry)
        return try { f.set(registry,false); h.set(registry,java.util.IdentityHashMap<Any,Any>()); LightCollectorBlock(BlockBehaviour.Properties.of().noOcclusion()) }
        finally { f.set(registry,oldF); h.set(registry,oldH) }
    }
    private fun world(states: Map<BlockPos, BlockState>): CollisionGetter =
        Proxy.newProxyInstance(CollisionGetter::class.java.classLoader, arrayOf(CollisionGetter::class.java)) { proxy, method, args ->
            when(method.name) {
                "getChunkForCollisions" -> proxy
                "getBlockState" -> states[args!![0] as BlockPos] ?: Blocks.AIR.defaultBlockState()
                "getHeight" -> 384
                "getMinBuildHeight" -> -64
                else -> error(method.name)
            }
        } as CollisionGetter

    @Test fun `four facings keep collision local to each cell and stop falls on the top`() {
        val b=block()
        for(facing in Direction.Plane.HORIZONTAL) {
            val lower=b.defaultBlockState().setValue(LightCollectorBlock.FACING,facing)
            val upper=lower.setValue(LightCollectorBlock.HALF,DoubleBlockHalf.UPPER)
            lower.initCache();upper.initCache()
            val w=world(mapOf(BlockPos.ZERO to lower,BlockPos.ZERO.above() to upper))
            for((pos,state) in listOf(BlockPos.ZERO to lower,BlockPos.ZERO.above() to upper)) {
                val bounds=state.getCollisionShape(w,pos).bounds()
                assertTrue(bounds.minX>=0 && bounds.minY>=0 && bounds.minZ>=0)
                assertTrue(bounds.maxX<=1 && bounds.maxY<=1 && bounds.maxZ<=1)
            }
            val body=AABB(.2,2.1,.2,.8,3.9,.8)
            val shapes=BlockCollisions<VoxelShape>(w,null,body.expandTowards(0.0,-.4,0.0),false){_,s->s}.asSequence().toList()
            assertFalse(shapes.isEmpty())
            assertEquals(-.1,shapes.fold(-.4){delta,s->s.collide(Direction.Axis.Y,body,delta)},.000001)
            assertNull(b.newBlockEntity(BlockPos.ZERO.above(),upper))
        }
    }
}
