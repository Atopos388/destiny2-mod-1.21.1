package atopos.destiny2.client.renderer

import atopos.destiny2.common.block.ShimmeringCrystalOreBlockEntity
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

class ShimmeringCrystalOreRenderer(@Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context) :
    BlockEntityRenderer<ShimmeringCrystalOreBlockEntity> {

    override fun render(
        blockEntity: ShimmeringCrystalOreBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val model = ShimmeringCrystalOreModel.model
        for (element in model.elements) {
            renderElement(element, poseStack, bufferSource, packedLight, packedOverlay)
        }
    }

    override fun shouldRenderOffScreen(blockEntity: ShimmeringCrystalOreBlockEntity): Boolean {
        return false
    }

    private fun renderElement(
        element: ModelElement,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val min = element.from
        val max = element.to
        val corners = mapOf(
            Corner.NNN to Vector3f(min.x, min.y, min.z),
            Corner.NNP to Vector3f(min.x, min.y, max.z),
            Corner.NPN to Vector3f(min.x, max.y, min.z),
            Corner.NPP to Vector3f(min.x, max.y, max.z),
            Corner.PNN to Vector3f(max.x, min.y, min.z),
            Corner.PNP to Vector3f(max.x, min.y, max.z),
            Corner.PPN to Vector3f(max.x, max.y, min.z),
            Corner.PPP to Vector3f(max.x, max.y, max.z)
        ).mapValues { (_, value) -> element.rotation?.apply(value) ?: value }

        for ((direction, face) in element.faces) {
            val vertices = faceCorners(direction).map { corners.getValue(it) }
            val normal = normal(vertices)
            val texture = ShimmeringCrystalOreModel.texture(face.textureKey)
            val light = if (face.textureKey == "1") LightTexture.FULL_BRIGHT else packedLight
            val consumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture))
            emitFace(consumer, poseStack, vertices, normal, face.uv, light, packedOverlay)
        }
    }

    private fun emitFace(
        consumer: VertexConsumer,
        poseStack: PoseStack,
        vertices: List<Vector3f>,
        normal: Vector3f,
        uv: UvRect,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val u1 = uv.u1 / 16.0f
        val v1 = uv.v1 / 16.0f
        val u2 = uv.u2 / 16.0f
        val v2 = uv.v2 / 16.0f
        val uvs = listOf(u1 to v1, u2 to v1, u2 to v2, u1 to v2)
        for (i in vertices.indices) {
            val vertex = vertices[i]
            val (u, v) = uvs[i]
            consumer.addVertex(poseStack.last(), vertex.x, vertex.y, vertex.z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(poseStack.last(), normal.x, normal.y, normal.z)
        }
    }

    private fun faceCorners(direction: String): List<Corner> {
        return when (direction) {
            "north" -> listOf(Corner.NPN, Corner.PPN, Corner.PNN, Corner.NNN)
            "south" -> listOf(Corner.PPP, Corner.NPP, Corner.NNP, Corner.PNP)
            "east" -> listOf(Corner.PPP, Corner.PPN, Corner.PNN, Corner.PNP)
            "west" -> listOf(Corner.NPN, Corner.NPP, Corner.NNP, Corner.NNN)
            "up" -> listOf(Corner.NPN, Corner.PPN, Corner.PPP, Corner.NPP)
            "down" -> listOf(Corner.NNP, Corner.PNP, Corner.PNN, Corner.NNN)
            else -> listOf(Corner.NNN, Corner.PNN, Corner.PPN, Corner.NPN)
        }
    }

    private fun normal(vertices: List<Vector3f>): Vector3f {
        val a = Vector3f(vertices[1]).sub(vertices[0])
        val b = Vector3f(vertices[2]).sub(vertices[0])
        return a.cross(b).normalize()
    }

    private enum class Corner {
        NNN, NNP, NPN, NPP, PNN, PNP, PPN, PPP
    }
}

private object ShimmeringCrystalOreModel {
    private val modelLocation = ResourceLocation.fromNamespaceAndPath(
        "destiny2-mod",
        "blockbench/shimmering_crystal_ore_original.json"
    )
    private val stoneTexture = ResourceLocation.fromNamespaceAndPath(
        "destiny2-mod",
        "textures/block/shimmering_crystal_ore_stone.png"
    )
    private val crystalTexture = ResourceLocation.fromNamespaceAndPath(
        "destiny2-mod",
        "textures/block/shimmering_crystal_ore_crystal.png"
    )

    val model: ModelDefinition by lazy { loadModel() }

    fun texture(key: String): ResourceLocation {
        return if (key == "1") crystalTexture else stoneTexture
    }

    private fun loadModel(): ModelDefinition {
        val resource = Minecraft.getInstance().resourceManager.getResourceOrThrow(modelLocation)
        resource.open().bufferedReader().use { reader ->
            val root = JsonParser.parseReader(reader).asJsonObject
            val elements = root.getAsJsonArray("elements").map { parseElement(it.asJsonObject) }
            return ModelDefinition(elements)
        }
    }

    private fun parseElement(json: JsonObject): ModelElement {
        return ModelElement(
            from = parseVector(json.getAsJsonArray("from")),
            to = parseVector(json.getAsJsonArray("to")),
            rotation = json.get("rotation")?.asJsonObject?.let(::parseRotation),
            faces = json.getAsJsonObject("faces").entrySet().associate { (direction, faceJson) ->
                direction to parseFace(faceJson.asJsonObject)
            }
        )
    }

    private fun parseFace(json: JsonObject): ModelFace {
        val texture = json.get("texture").asString.removePrefix("#")
        val uv = json.getAsJsonArray("uv")
        return ModelFace(texture, UvRect(uv[0].asFloat, uv[1].asFloat, uv[2].asFloat, uv[3].asFloat))
    }

    private fun parseRotation(json: JsonObject): ModelRotation {
        val origin = parseVector(json.getAsJsonArray("origin"))
        if (json.has("angle") && json.has("axis")) {
            return ModelRotation(origin, json.get("angle").asFloat, json.get("axis").asString)
        }
        return ModelRotation(
            origin,
            json.get("x")?.asFloat ?: 0.0f,
            json.get("y")?.asFloat ?: 0.0f,
            json.get("z")?.asFloat ?: 0.0f
        )
    }

    private fun parseVector(array: JsonArray): Vector3f {
        return Vector3f(array[0].asFloat / 16.0f, array[1].asFloat / 16.0f, array[2].asFloat / 16.0f)
    }
}

private data class ModelDefinition(val elements: List<ModelElement>)

private data class ModelElement(
    val from: Vector3f,
    val to: Vector3f,
    val rotation: ModelRotation?,
    val faces: Map<String, ModelFace>
)

private data class ModelFace(val textureKey: String, val uv: UvRect)

private data class UvRect(val u1: Float, val v1: Float, val u2: Float, val v2: Float)

private data class ModelRotation(
    val origin: Vector3f,
    val x: Float,
    val y: Float,
    val z: Float
) {
    constructor(origin: Vector3f, angle: Float, axis: String) : this(
        origin,
        if (axis == "x") angle else 0.0f,
        if (axis == "y") angle else 0.0f,
        if (axis == "z") angle else 0.0f
    )

    fun apply(input: Vector3f): Vector3f {
        val output = Vector3f(input).sub(origin)
        rotateX(output, x)
        rotateY(output, y)
        rotateZ(output, z)
        return output.add(origin)
    }

    private fun rotateX(vector: Vector3f, degrees: Float) {
        if (degrees == 0.0f) return
        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val y = vector.y * cos - vector.z * sin
        val z = vector.y * sin + vector.z * cos
        vector.y = y
        vector.z = z
    }

    private fun rotateY(vector: Vector3f, degrees: Float) {
        if (degrees == 0.0f) return
        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val x = vector.x * cos + vector.z * sin
        val z = -vector.x * sin + vector.z * cos
        vector.x = x
        vector.z = z
    }

    private fun rotateZ(vector: Vector3f, degrees: Float) {
        if (degrees == 0.0f) return
        val radians = Math.toRadians(degrees.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        val x = vector.x * cos - vector.y * sin
        val y = vector.x * sin + vector.y * cos
        vector.x = x
        vector.y = y
    }
}
