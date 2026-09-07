package atopos.destiny2.client.renderer.cloth

import atopos.destiny2.common.entity.FallenSoldierEntity
import atopos.destiny2.common.physics.ClothGrid
import atopos.destiny2.common.physics.ClothGrid.Point
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import software.bernie.geckolib.cache.`object`.GeoBone
import software.bernie.geckolib.util.RenderUtil
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max

/** Three world-space control nets deform the authored UV surfaces, including the emblem. */
class FallenClothRenderer {
    private data class Patch(val x0: Float, val x1: Float, val top: Float, val bottom: Float, val z: Float)
    private data class Vertex(val p: Vector3f, val u: Float, val v: Float)
    private data class State(val grid: ClothGrid, var time: Double)
    private data class Frame(val entity: FallenSoldierEntity, val origin: Point, val base: Matrix4f,
        val inverse: Matrix4f, val normal: Matrix3f, val time: Double,
        val collisions: MutableList<ClothGrid.Collision>)
    private val patches = mapOf(
        "tabard_front" to Patch(-5f/16,5f/16,23f/16,10f/16,-3.6f/16),
        "tabard_back" to Patch(-4.5f/16,4.5f/16,23f/16,11f/16,3.6f/16),
        "cloth_shawl" to Patch(-5.5f/16,5.5f/16,38f/16,30f/16,3.6f/16)
    )
    private val states = WeakHashMap<FallenSoldierEntity, MutableMap<String, State>>()
    private val surfaces = WeakHashMap<GeoBone, List<Array<Vertex>>>()
    private var frame: Frame? = null

    fun begin(entity: FallenSoldierEntity, partial: Float, stack: PoseStack) {
        frame = null
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        if (entity.distanceToSqr(camera) > 48.0*48.0) { states.remove(entity); return }
        val origin = Point(Mth.lerp(partial.toDouble(),entity.xo,entity.x),
            Mth.lerp(partial.toDouble(),entity.yo,entity.y), Mth.lerp(partial.toDouble(),entity.zo,entity.z))
        val collisions = mutableListOf<ClothGrid.Collision>()
        for (shape in entity.level().getBlockCollisions(entity,entity.boundingBox.inflate(1.5))) {
            for (b in shape.toAabbs()) collisions += ClothGrid.Box(
                b.minX-.035,b.minY-.035,b.minZ-.035,b.maxX+.035,b.maxY+.035,b.maxZ+.035)
        }
        val base = Matrix4f(stack.last().pose())
        frame = Frame(entity,origin,base,Matrix4f(base).invert(),Matrix3f(stack.last().normal()),
            (entity.tickCount+partial.toDouble())/20.0,collisions)
    }
    fun end() { frame = null }

    private fun world(matrix: Matrix4f, p: Vector3f, f: Frame): Point {
        val v = matrix.transformPosition(Vector3f(p))
        return f.origin.add(Point(v.x.toDouble(),v.y.toDouble(),v.z.toDouble()))
    }
    /** Same animated transforms as the skin; no upright collision cylinder on a fallen corpse. */
    fun collectBody(stack: PoseStack, bone: GeoBone) {
        val f = frame ?: return
        stack.pushPose()
        try {
            RenderUtil.prepMatrixForBone(stack,bone)
            val endpoints = when (bone.name) {
                "torso" -> floatArrayOf(0f,27f,0f,0f,35f,0f,2.8f)
                "pelvis" -> floatArrayOf(0f,22f,0f,0f,25f,0f,2.8f)
                "leg_right" -> floatArrayOf(-2.9f,22f,0f,-6.6f,13.7f,-1.7f,1.5f)
                "leg_left" -> floatArrayOf(2.9f,22f,0f,4.5f,12.8f,-1.2f,1.5f)
                "leg_right_shin" -> floatArrayOf(-6.6f,13.7f,-1.7f,-7.8f,3.2f,.6f,1.2f)
                "leg_left_shin" -> floatArrayOf(4.5f,12.8f,-1.2f,7.4f,3.2f,1.7f,1.2f)
                else -> null
            }
            if (endpoints != null) {
                val m = Matrix4f(f.inverse).mul(stack.last().pose())
                val a = world(m,Vector3f(endpoints[0],endpoints[1],endpoints[2]).div(16f),f)
                val b = world(m,Vector3f(endpoints[3],endpoints[4],endpoints[5]).div(16f),f)
                f.collisions += ClothGrid.Capsule(a,b,endpoints[6]/16.0*.65+.035)
            }
            for (child in bone.childBones) collectBody(stack,child)
        } finally { stack.popPose() }
    }

    fun render(stack: PoseStack, bone: GeoBone, buffer: VertexConsumer, light: Int, overlay: Int, colour: Int): Boolean {
        val patch = patches[bone.name] ?: return false
        val f = frame ?: return false
        val matrix = Matrix4f(f.inverse).mul(stack.last().pose())
        val target = Array(63) { i ->
            val x = Mth.lerp((i%7)/6f,patch.x0,patch.x1)
            val y = Mth.lerp((i/7)/8f,patch.top,patch.bottom)
            world(matrix,Vector3f(x,y,patch.z),f)
        }
        val map = states.getOrPut(f.entity) { mutableMapOf() }
        val state = map.getOrPut(bone.name) { State(ClothGrid(7,9,target),f.time) }
        val elapsed = f.time-state.time
        if (elapsed < 0) state.grid.reset(target)
        else state.grid.advance(elapsed,target,f.collisions)
        state.time = f.time
        for (quad in surfaces.getOrPut(bone) { bake(bone) }) {
            val positions = quad.map { v ->
                val u = (v.p.x-patch.x0)/(patch.x1-patch.x0)
                val t = (patch.top-v.p.y)/(patch.top-patch.bottom)
                world(matrix,v.p,f).add(state.grid.displacement(u.toDouble(),t.toDouble(),target))
            }
            fun relative(p: Point) = p.sub(f.origin).let { Vector3f(it.x().toFloat(),it.y().toFloat(),it.z().toFloat()) }
            val q = positions.map(::relative)
            val normal = Vector3f(q[1]).sub(q[0]).cross(Vector3f(q[2]).sub(q[0]))
            if (normal.lengthSquared() < 1e-12f) continue
            f.normal.transform(normal.normalize()).normalize()
            for (i in 0..3) buffer.addVertex(f.base,q[i].x,q[i].y,q[i].z).setColor(colour)
                .setUv(quad[i].u,quad[i].v).setOverlay(overlay).setLight(light)
                .setNormal(normal.x,normal.y,normal.z)
        }
        return true
    }

    /** Subdivision keeps texture coordinates continuous; the rigid original faces are not drawn. */
    private fun bake(bone: GeoBone): List<Array<Vertex>> {
        val out = mutableListOf<Array<Vertex>>()
        for (cube in bone.cubes) {
            val s = PoseStack()
            RenderUtil.translateToPivotPoint(s,cube)
            RenderUtil.rotateMatrixAroundCube(s,cube)
            RenderUtil.translateAwayFromPivotPoint(s,cube)
            for (quad in cube.quads()) {
                if (quad == null) continue
                val v = quad.vertices().map { Vertex(s.last().pose().transformPosition(Vector3f(it.position())),it.texU(),it.texV()) }
                val nx = ceil(max(v[0].p.distance(v[1].p),v[3].p.distance(v[2].p))/.09).toInt().coerceIn(1,16)
                val ny = ceil(max(v[0].p.distance(v[3].p),v[1].p.distance(v[2].p))/.09).toInt().coerceIn(1,16)
                fun lerp(a: Vertex,b: Vertex,t: Float) = Vertex(Vector3f(a.p).lerp(b.p,t),Mth.lerp(t,a.u,b.u),Mth.lerp(t,a.v,b.v))
                fun at(x: Float,y: Float) = lerp(lerp(v[0],v[1],x),lerp(v[3],v[2],x),y)
                for (y in 0 until ny) for (x in 0 until nx) out += arrayOf(
                    at(x.toFloat()/nx,y.toFloat()/ny),at((x+1f)/nx,y.toFloat()/ny),
                    at((x+1f)/nx,(y+1f)/ny),at(x.toFloat()/nx,(y+1f)/ny))
            }
        }
        return out
    }
}
