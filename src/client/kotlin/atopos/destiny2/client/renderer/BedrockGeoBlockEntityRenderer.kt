package atopos.destiny2.client.renderer

import atopos.destiny2.common.block.SphereModelBlockEntity
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.block.entity.BlockEntity
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f

open class BedrockGeoBlockEntityRenderer<T>(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context,
    private val modelResource: (T) -> ResourceLocation,
    private val textureResource: (T) -> ResourceLocation
) : BlockEntityRenderer<T> where T : BlockEntity {
    override fun render(
        blockEntity: T,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val model = BedrockGeoModelCache.load(modelResource(blockEntity))
        val consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(textureResource(blockEntity)))
        model.faces.forEach { face ->
            emitFace(consumer, poseStack, face, packedLight, packedOverlay)
        }
    }

    override fun shouldRenderOffScreen(blockEntity: T): Boolean {
        return false
    }

    private fun emitFace(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        face: BedrockGeoFace,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val normal = face.normal()
        face.vertices.forEach { vertex ->
            val vertexNormal = vertex.normal ?: normal
            consumer.addVertex(poseStack.last(), vertex.position.x, vertex.position.y, vertex.position.z)
                .setColor(255, 255, 255, 255)
                .setUv(vertex.uv.x, vertex.uv.y)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(poseStack.last(), vertexNormal.x, vertexNormal.y, vertexNormal.z)
        }
    }
}

class SphereModelBlockRenderer(context: BlockEntityRendererProvider.Context) :
    BedrockGeoBlockEntityRenderer<SphereModelBlockEntity>(
        context,
        { SphereModelBlockEntity.MODEL },
        { SphereModelBlockEntity.TEXTURE }
    )

private object BedrockGeoModelCache {
    private val cache = mutableMapOf<ResourceLocation, BedrockGeoModel>()

    fun load(location: ResourceLocation): BedrockGeoModel {
        return cache.getOrPut(location) {
            val resource = Minecraft.getInstance().resourceManager.getResourceOrThrow(location)
            resource.open().bufferedReader().use { reader ->
                BedrockGeoParser.parse(JsonParser.parseReader(reader).asJsonObject)
            }
        }
    }
}

private object BedrockGeoParser {
    fun parse(root: JsonObject): BedrockGeoModel {
        val geometry = root.getAsJsonArray("minecraft:geometry").first().asJsonObject
        val bones = geometry.getAsJsonArray("bones").map { it.asJsonObject }
        val byName = bones.associateBy { it.get("name").asString }
        val transforms = mutableMapOf<String, Matrix4f>()
        val faces = mutableListOf<BedrockGeoFace>()

        bones.forEach { bone ->
            val name = bone.get("name").asString
            val transform = boneTransform(name, byName, transforms)
            bone.getAsJsonArray("cubes")?.forEach { cube ->
                faces += cubeFaces(cube.asJsonObject, transform)
            }
            bone.getAsJsonObject("poly_mesh")?.let { polyMesh ->
                faces += polyMeshFaces(polyMesh, transform)
            }
        }

        return BedrockGeoModel(faces)
    }

    private fun boneTransform(
        boneName: String,
        bones: Map<String, JsonObject>,
        cache: MutableMap<String, Matrix4f>
    ): Matrix4f {
        cache[boneName]?.let { return Matrix4f(it) }
        val bone = bones.getValue(boneName)
        val parentTransform = bone.get("parent")?.asString?.let { parent ->
            boneTransform(parent, bones, cache)
        } ?: Matrix4f()
        val pivot = parseVec3(bone.getAsJsonArray("pivot"), scale = 1.0f / 16.0f)
        val rotation = bone.getAsJsonArray("rotation")?.let { parseVec3(it, scale = 1.0f) } ?: Vector3f()
        val transform = Matrix4f(parentTransform)
        applyRotation(transform, pivot, rotation)
        cache[boneName] = Matrix4f(transform)
        return transform
    }

    private fun cubeFaces(cube: JsonObject, boneTransform: Matrix4f): List<BedrockGeoFace> {
        val origin = parseVec3(cube.getAsJsonArray("origin"), scale = 1.0f / 16.0f)
        val size = parseVec3(cube.getAsJsonArray("size"), scale = 1.0f / 16.0f)
        val pivot = cube.getAsJsonArray("pivot")?.let { parseVec3(it, scale = 1.0f / 16.0f) } ?: origin
        val rotation = cube.getAsJsonArray("rotation")?.let { parseVec3(it, scale = 1.0f) } ?: Vector3f()

        val cubeTransform = Matrix4f(boneTransform)
        applyRotation(cubeTransform, pivot, rotation)

        val min = origin
        val max = Vector3f(origin).add(size)
        val corners = mapOf(
            Corner.NNN to transform(Vector3f(min.x, min.y, min.z), cubeTransform),
            Corner.NNP to transform(Vector3f(min.x, min.y, max.z), cubeTransform),
            Corner.NPN to transform(Vector3f(min.x, max.y, min.z), cubeTransform),
            Corner.NPP to transform(Vector3f(min.x, max.y, max.z), cubeTransform),
            Corner.PNN to transform(Vector3f(max.x, min.y, min.z), cubeTransform),
            Corner.PNP to transform(Vector3f(max.x, min.y, max.z), cubeTransform),
            Corner.PPN to transform(Vector3f(max.x, max.y, min.z), cubeTransform),
            Corner.PPP to transform(Vector3f(max.x, max.y, max.z), cubeTransform)
        )

        return listOf(
            face(corners, Corner.NPN, Corner.PPN, Corner.PNN, Corner.NNN),
            face(corners, Corner.PPP, Corner.NPP, Corner.NNP, Corner.PNP),
            face(corners, Corner.PPP, Corner.PPN, Corner.PNN, Corner.PNP),
            face(corners, Corner.NPN, Corner.NPP, Corner.NNP, Corner.NNN),
            face(corners, Corner.NPN, Corner.PPN, Corner.PPP, Corner.NPP),
            face(corners, Corner.NNP, Corner.PNP, Corner.PNN, Corner.NNN)
        )
    }

    private fun polyMeshFaces(polyMesh: JsonObject, boneTransform: Matrix4f): List<BedrockGeoFace> {
        val positions = polyMesh.getAsJsonArray("positions").map { parseVec3(it.asJsonArray, scale = 1.0f / 16.0f) }
        val uvs = polyMesh.getAsJsonArray("uvs")?.map { parseUv(it.asJsonArray, polyMesh.normalizedUvs()) }.orEmpty()
        if (positions.isEmpty()) {
            return emptyList()
        }
        val smoothNormals = polyMesh.get("smooth_normals")?.asBoolean ?: false
        val meshCenter = positions.fold(Vector3f()) { center, position -> center.add(position) }.div(positions.size.toFloat())
        val result = mutableListOf<BedrockGeoFace>()

        polyMesh.getAsJsonArray("polys").forEach { polygon ->
            val vertices = polygon.asJsonArray.mapNotNull { point ->
                val indices = point.asJsonArray.map { it.asInt }
                val positionIndex = indices.getOrNull(0) ?: return@mapNotNull null
                val uvIndex = when {
                    indices.size >= 3 && indices[2] in uvs.indices -> indices[2]
                    indices.size >= 2 && indices[1] in uvs.indices -> indices[1]
                    else -> -1
                }
                val uv = if (uvIndex >= 0) uvs[uvIndex] else Vector2f(0.0f, 0.0f)
                val position = positions[positionIndex]
                val normal = if (smoothNormals) transformDirection(Vector3f(position).sub(meshCenter), boneTransform) else null
                BedrockGeoVertex(transform(Vector3f(position), boneTransform), uv, normal)
            }
            if (vertices.size >= 3) {
                for (index in 1 until vertices.lastIndex) {
                    result += quadCompatibleFace(vertices[0], vertices[index], vertices[index + 1])
                }
            }
        }

        return result
    }

    private fun quadCompatibleFace(
        first: BedrockGeoVertex,
        second: BedrockGeoVertex,
        third: BedrockGeoVertex
    ): BedrockGeoFace {
        return BedrockGeoFace(listOf(first, second, third, third))
    }

    private fun JsonObject.normalizedUvs(): Boolean {
        return get("normalized_uvs")?.asBoolean ?: true
    }

    private fun face(corners: Map<Corner, Vector3f>, vararg order: Corner): BedrockGeoFace {
        val uvs = listOf(
            Vector2f(0.0f, 0.0f),
            Vector2f(1.0f, 0.0f),
            Vector2f(1.0f, 1.0f),
            Vector2f(0.0f, 1.0f)
        )
        return BedrockGeoFace(order.mapIndexed { index, corner ->
            BedrockGeoVertex(Vector3f(corners.getValue(corner)), uvs[index])
        })
    }

    private fun applyRotation(matrix: Matrix4f, pivot: Vector3f, rotation: Vector3f) {
        if (rotation.x == 0.0f && rotation.y == 0.0f && rotation.z == 0.0f) {
            return
        }
        matrix.translate(pivot)
        matrix.rotateX(Math.toRadians(rotation.x.toDouble()).toFloat())
        matrix.rotateY(Math.toRadians(rotation.y.toDouble()).toFloat())
        matrix.rotateZ(Math.toRadians(rotation.z.toDouble()).toFloat())
        matrix.translate(Vector3f(pivot).negate())
    }

    private fun transform(vector: Vector3f, matrix: Matrix4f): Vector3f {
        return matrix.transformPosition(vector)
    }

    private fun transformDirection(vector: Vector3f, matrix: Matrix4f): Vector3f {
        return matrix.transformDirection(vector.normalize())
    }

    private fun parseVec3(array: JsonArray?, scale: Float): Vector3f {
        if (array == null || array.size() < 3) {
            return Vector3f()
        }
        return Vector3f(array[0].asFloat * scale, array[1].asFloat * scale, array[2].asFloat * scale)
    }

    private fun parseUv(array: JsonArray, normalized: Boolean): Vector2f {
        val divisor = if (normalized) 1.0f else 16.0f
        return Vector2f(array[0].asFloat / divisor, array[1].asFloat / divisor)
    }

    private enum class Corner {
        NNN, NNP, NPN, NPP, PNN, PNP, PPN, PPP
    }
}

private data class BedrockGeoModel(val faces: List<BedrockGeoFace>)

private data class BedrockGeoFace(val vertices: List<BedrockGeoVertex>) {
    fun normal(): Vector3f {
        if (vertices.size < 3) {
            return Vector3f(0.0f, 1.0f, 0.0f)
        }
        val a = Vector3f(vertices[1].position).sub(vertices[0].position)
        val b = Vector3f(vertices[2].position).sub(vertices[0].position)
        return a.cross(b).normalize()
    }
}

private data class BedrockGeoVertex(val position: Vector3f, val uv: Vector2f, val normal: Vector3f? = null)
