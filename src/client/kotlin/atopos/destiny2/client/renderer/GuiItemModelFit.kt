package atopos.destiny2.client.renderer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import kotlin.math.max
import kotlin.math.min

/** Measure the static GUI pose once, then center it within a 14-pixel item footprint. */
object GuiItemModelFit : SimpleSynchronousResourceReloadListener {
    data class Bounds(val x: Float, val y: Float, val z: Float, val scale: Float)
    private val cache = object : LinkedHashMap<String, Bounds>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bounds>?): Boolean = size > 128
    }
    fun register() = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this)
    override fun getFabricId(): ResourceLocation = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "gui_item_fit")
    override fun onResourceManagerReload(manager: ResourceManager) { cache.clear() }

    fun render(key: String, pose: PoseStack, buffers: MultiBufferSource, draw: (PoseStack, MultiBufferSource) -> Unit) {
        val bounds = cache[key] ?: run {
            val probe = Extents()
            val scratch = PoseStack()
            scratch.mulPose(Axis.ZP.rotationDegrees(-35f))
            draw(scratch, MultiBufferSource { probe })
            probe.result()?.also { cache[key] = it }
        } ?: return
        pose.pushPose()
        try {
            pose.translate(.5, .5, .5)
            pose.scale(bounds.scale, bounds.scale, bounds.scale)
            pose.translate(-bounds.x.toDouble(), -bounds.y.toDouble(), -bounds.z.toDouble())
            pose.mulPose(Axis.ZP.rotationDegrees(-35f))
            draw(pose, buffers)
        } finally { pose.popPose() }
    }

    class Extents : VertexConsumer {
        private var minX = Float.POSITIVE_INFINITY
        private var minY = Float.POSITIVE_INFINITY
        private var minZ = Float.POSITIVE_INFINITY
        private var maxX = Float.NEGATIVE_INFINITY
        private var maxY = Float.NEGATIVE_INFINITY
        private var maxZ = Float.NEGATIVE_INFINITY
        override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer {
            if (x.isFinite() && y.isFinite() && z.isFinite()) {
                minX=min(minX,x); minY=min(minY,y); minZ=min(minZ,z)
                maxX=max(maxX,x); maxY=max(maxY,y); maxZ=max(maxZ,z)
            }
            return this
        }
        fun result(): Bounds? {
            val span = max(maxX-minX, maxY-minY)
            return if (span.isFinite() && span > .00001f)
                Bounds((minX+maxX)/2f,(minY+maxY)/2f,(minZ+maxZ)/2f,.875f/span) else null
        }
        override fun setColor(r: Int,g: Int,b: Int,a: Int): VertexConsumer = this
        override fun setUv(u: Float,v: Float): VertexConsumer = this
        override fun setUv1(u: Int,v: Int): VertexConsumer = this
        override fun setUv2(u: Int,v: Int): VertexConsumer = this
        override fun setNormal(x: Float,y: Float,z: Float): VertexConsumer = this
    }
}


