package atopos.destiny2.common.block

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.MappedRegistry
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.BlockCollisions
import net.minecraft.world.level.CollisionGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class TowerWorkbenchCollisionTest {
    companion object {
        @JvmStatic @BeforeAll fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `both halves normalize to the same menu anchor for every facing`() {
        val block = testBlock { TowerWorkbenchBlock(BlockBehaviour.Properties.of()) }
        val anchor = BlockPos(11, 70, -9)
        for (facing in listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            val lower = block.defaultBlockState().setValue(TowerWorkbenchBlock.FACING, facing)
            val upper = lower.setValue(TowerWorkbenchBlock.PART, 1)
            val other = anchor.relative(facing.clockWise)
            assertEquals(anchor, block.menuAnchor(lower, anchor))
            assertEquals(anchor, block.menuAnchor(upper, other))
            assertTrue(block.isMenuStructureValid(world(mapOf(anchor to lower, other to upper)), anchor))
            assertFalse(block.isMenuStructureValid(world(mapOf(anchor to lower)), anchor))
            assertFalse(block.isMenuStructureValid(world(mapOf(anchor to lower, other to lower)), anchor))
            assertFalse(block.isMenuStructureValid(world(mapOf(anchor to lower,
                other to upper.setValue(TowerWorkbenchBlock.FACING, facing.opposite))), anchor))
            assertFalse(block.isMenuStructureValid(world(mapOf(anchor to lower, other to upper)), other))
        }
    }

    // Bootstrap freezes vanilla registries. Permit only construction of unregistered test blocks,
    // then restore both fields before any collision query; production registration is untouched.
    private fun <T : Block> testBlock(factory: () -> T): T {
        val registry = BuiltInRegistries.BLOCK
        val frozen = MappedRegistry::class.java.getDeclaredField("frozen").apply { isAccessible = true }
        val holders = MappedRegistry::class.java.getDeclaredField("unregisteredIntrusiveHolders").apply { isAccessible = true }
        val oldFrozen = frozen.get(registry)
        val oldHolders = holders.get(registry)
        return try {
            frozen.set(registry, false)
            holders.set(registry, java.util.IdentityHashMap<Any, Any>())
            factory()
        } finally {
            frozen.set(registry, oldFrozen)
            holders.set(registry, oldHolders)
        }
    }

    private fun world(states: Map<BlockPos, BlockState>): CollisionGetter {
        return Proxy.newProxyInstance(CollisionGetter::class.java.classLoader, arrayOf(CollisionGetter::class.java)) { proxy, method, args ->
            when (method.name) {
                "getChunkForCollisions" -> proxy
                "getBlockState" -> states[args!![0] as BlockPos] ?: Blocks.AIR.defaultBlockState()
                "getHeight" -> 384
                "getMinBuildHeight" -> -64
                else -> error("Unexpected collision query: " + method.name)
            }
        } as CollisionGetter
    }

    private fun collisions(states: Map<BlockPos, BlockState>, query: AABB): List<VoxelShape> {
        states.values.forEach { it.initCache() }
        return BlockCollisions<VoxelShape>(world(states), null, query, false) { _, shape -> shape }.asSequence().toList()
    }

    @Test fun `vanilla broad phase reproduces old side landing miss and finds the new second cell`() {
        val old = testBlock { object : Block(BlockBehaviour.Properties.of().noOcclusion()) {
            override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
                box(-8.0, 0.0, -0.25, 24.0, 28.0, 16.0)
        }
        }
        val query = AABB(1.05, 1.1, 0.2, 1.45, 2.9, 0.8)
        assertTrue(old.defaultBlockState().getShape(world(emptyMap()), BlockPos.ZERO, CollisionContext.empty()).bounds().intersects(query))
        assertTrue(collisions(mapOf(BlockPos.ZERO to old.defaultBlockState()), query).isEmpty(), "Old box is geometrically overlapping but skipped by vanilla broad phase")
        val block = testBlock { TowerWorkbenchBlock(BlockBehaviour.Properties.of().noOcclusion()) }
        val state = block.defaultBlockState()
        assertFalse(collisions(mapOf(BlockPos.ZERO to state, BlockPos(1, 0, 0) to state.setValue(TowerWorkbenchBlock.PART, 1)), query).isEmpty())
    }


    @Test fun `four facings land on the open tabletop instead of floating at backboard height`() {
        val block = testBlock { TowerWorkbenchBlock(BlockBehaviour.Properties.of().noOcclusion()) }
        for ((turns, facing) in listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST).withIndex()) {
            val state = block.defaultBlockState().setValue(TowerWorkbenchBlock.FACING, facing)
            val states = mapOf(BlockPos.ZERO to state, BlockPos.ZERO.relative(facing.clockWise) to state.setValue(TowerWorkbenchBlock.PART, 1))
            // A player-width body fits in the unobstructed center, spanning the two-cell seam.
            var x = 1.0
            var z = 0.45
            repeat(turns) { val oldX = x; x = 1.0 - z; z = oldX }
            val body = AABB(x - 0.3, 1.85, z - 0.3, x + 0.3, 3.65, z + 0.3)
            val shapes = collisions(states, body.expandTowards(0.0, -0.8, 0.0))
            assertFalse(shapes.isEmpty())
            val allowed = shapes.fold(-0.8) { delta, shape -> shape.collide(Direction.Axis.Y, body, delta) }
            assertEquals(-0.6, allowed, 0.000001, "Feet should land at y=1.25 for $facing")
        }
    }

    @Test fun `outer sides and the seam have collision above the anchor cell height`() {
        val block = testBlock { TowerWorkbenchBlock(BlockBehaviour.Properties.of().noOcclusion()) }
        for (facing in Direction.Plane.HORIZONTAL) {
            val state = block.defaultBlockState().setValue(TowerWorkbenchBlock.FACING, facing)
            val next = BlockPos.ZERO.relative(facing.clockWise)
            val states = mapOf(BlockPos.ZERO to state, next to state.setValue(TowerWorkbenchBlock.PART, 1))
            for (pos in states.keys) for (side in Direction.Plane.HORIZONTAL) {
                val x = pos.x + 0.5 + side.stepX * 0.55
                val z = pos.z + 0.5 + side.stepZ * 0.55
                assertFalse(collisions(states, AABB(x - 0.3, 1.1, z - 0.3, x + 0.3, 2.9, z + 0.3)).isEmpty(), "$facing $pos $side")
            }
        }
    }

    @Test fun `open tabletop is empty above twenty units but major props stay solid in all facings`() {
        val block = testBlock { TowerWorkbenchBlock(BlockBehaviour.Properties.of().noOcclusion()) }
        for ((turns, facing) in listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST).withIndex()) {
            val state = block.defaultBlockState().setValue(TowerWorkbenchBlock.FACING, facing)
            val states = mapOf(BlockPos.ZERO to state, BlockPos.ZERO.relative(facing.clockWise) to state.setValue(TowerWorkbenchBlock.PART, 1))
            fun query(x: Double, y: Double, z: Double): List<VoxelShape> {
                var rx = x / 16.0
                var rz = z / 16.0
                repeat(turns) { val oldX = rx; rx = 1.0 - rz; rz = oldX }
                return collisions(states, AABB(rx - 0.02, y / 16.0, rz - 0.02, rx + 0.02, y / 16.0 + 0.02, rz + 0.02))
            }
            assertTrue(query(12.0, 22.0, 6.0).isEmpty(), "Air over left desk $facing")
            assertTrue(query(20.0, 22.0, 6.0).isEmpty(), "Air over right desk $facing")
            assertFalse(query(16.0, 27.0, 14.5).isEmpty(), "Backboard $facing")
            assertFalse(query(4.0, 23.0, 7.0).isEmpty(), "Toolbox $facing")
            assertFalse(query(27.0, 24.0, 6.0).isEmpty(), "Vise $facing")
            assertTrue(query(4.0, 25.0, 7.0).isEmpty(), "Air above toolbox $facing")
            assertTrue(query(27.0, 26.0, 6.0).isEmpty(), "Air above vise $facing")
        }
    }
}



