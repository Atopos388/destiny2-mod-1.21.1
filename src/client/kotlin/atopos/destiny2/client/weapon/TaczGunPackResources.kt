package atopos.destiny2.client.weapon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.joml.Matrix4f
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Resource-pack loader for TaCZ-style GeckoLib guns.
 *
 * Positioning matrices intentionally follow TaCZ's BedrockModel.convertPivot
 * and FirstPersonRenderGunEvent.getPositioningNodeInverse implementation.
 */
object TaczGunPackResources : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("DestinyTaCZGunPack")
    private val packs = ConcurrentHashMap<ResourceLocation, GunPackDefinition>()

    data class GunPackDefinition(
        val id: ResourceLocation,
        val model: ResourceLocation,
        val texture: ResourceLocation,
        val animation: ResourceLocation,
        val skinBones: Set<String>,
        val firstPersonOnlyBones: Set<String>,
        val boneNames: Set<String>,
        val positioning: Map<String, Matrix4f>,
        val loadErrors: List<String>
    ) {
        val usesStandardPositioning: Boolean =
            positioning.containsKey(IDLE_VIEW) && positioning.containsKey(IRON_VIEW)

        fun matrix(name: String): Matrix4f? = positioning[name]?.let(::Matrix4f)
    }

    private data class RawBone(
        val name: String,
        val parent: String?,
        val pivotX: Float,
        val pivotY: Float,
        val pivotZ: Float,
        val rotationX: Float,
        val rotationY: Float,
        val rotationZ: Float
    )

    private data class PositionNode(
        val x: Float,
        val y: Float,
        val z: Float,
        val xRot: Float,
        val yRot: Float,
        val zRot: Float,
        val hasParent: Boolean
    )

    fun register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this)
    }

    override fun getFabricId(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "tacz_gun_packs")

    override fun onResourceManagerReload(manager: ResourceManager) {
        val loaded = linkedMapOf<ResourceLocation, GunPackDefinition>()
        manager.listResources(PACK_DIRECTORY) { it.path.endsWith(".json") }
            .toSortedMap(compareBy(ResourceLocation::toString))
            .forEach { (_, resource) ->
                runCatching {
                    resource.openAsReader().use(JsonParser::parseReader).asJsonObject
                }.onSuccess { root ->
                    val definition = loadDefinition(manager, root)
                    loaded[definition.id] = definition
                }.onFailure {
                    logger.error("Failed to read TaCZ gun-pack definition from {}", resource.sourcePackId(), it)
                }
            }

        packs.clear()
        packs.putAll(loaded)
        loaded.values.forEach { definition ->
            if (definition.loadErrors.isEmpty()) {
                logger.info(
                    "Loaded gun pack {} (standard positioning: {}, bones: {})",
                    definition.id,
                    definition.usesStandardPositioning,
                    definition.boneNames.size
                )
            } else {
                logger.warn("Gun pack {} has errors: {}", definition.id, definition.loadErrors.joinToString(" | "))
            }
        }
    }

    fun definition(id: ResourceLocation): GunPackDefinition? = packs[id]

    fun definitions(): Collection<GunPackDefinition> = packs.values.toList()

    fun model(id: ResourceLocation): ResourceLocation =
        packs[id]?.model ?: conventional(id, "geo", ".geo.json")

    fun texture(id: ResourceLocation): ResourceLocation =
        packs[id]?.texture ?: conventional(id, "textures/item", ".png")

    fun animation(id: ResourceLocation): ResourceLocation =
        packs[id]?.animation ?: conventional(id, "animations", ".animation.json")

    private fun loadDefinition(manager: ResourceManager, root: JsonObject): GunPackDefinition {
        val id = ResourceLocation.parse(root.requiredString("id"))
        val model = root.resource("model", conventional(id, "geo", ".geo.json"))
        val texture = root.resource("texture", conventional(id, "textures/item", ".png"))
        val animation = root.resource("animation", conventional(id, "animations", ".animation.json"))
        val skinBones = root.stringSet("skin_bones").ifEmpty { DEFAULT_SKIN_BONES }
        val firstPersonOnlyBones = root.stringSet("first_person_only_bones") + skinBones
        val errors = mutableListOf<String>()

        val rawBones = runCatching {
            val resource = manager.getResource(model).orElseThrow()
            resource.openAsReader().use(JsonParser::parseReader).asJsonObject
                .getAsJsonArray("minecraft:geometry")
                ?.firstOrNull()
                ?.asJsonObject
                ?.getAsJsonArray("bones")
                ?.mapNotNull { parseBone(it.asJsonObject) }
                .orEmpty()
        }.onFailure { errors += "model $model: ${it.message}" }.getOrDefault(emptyList())

        if (manager.getResource(texture).isEmpty) errors += "missing texture $texture"
        if (manager.getResource(animation).isEmpty) errors += "missing animation $animation"

        val indexed = rawBones.associateBy(RawBone::name)
        val positioning = POSITIONING_BONES.mapNotNull { name ->
            buildPositioningInverse(name, indexed)?.let { name to it }
        }.toMap()

        return GunPackDefinition(
            id,
            model,
            texture,
            animation,
            skinBones,
            firstPersonOnlyBones,
            indexed.keys,
            positioning,
            errors
        )
    }

    private fun parseBone(root: JsonObject): RawBone? {
        val name = root.get("name")?.asString ?: return null
        val pivot = root.getAsJsonArray("pivot")
        val rotation = root.getAsJsonArray("rotation")
        return RawBone(
            name = name,
            parent = root.get("parent")?.asString,
            pivotX = pivot.floatOrZero(0),
            pivotY = pivot.floatOrZero(1),
            pivotZ = pivot.floatOrZero(2),
            rotationX = rotation.floatOrZero(0),
            rotationY = rotation.floatOrZero(1),
            rotationZ = rotation.floatOrZero(2)
        )
    }

    /**
     * Direct port of TaCZ's path conversion and inverse positioning matrix.
     */
    private fun buildPositioningInverse(name: String, bones: Map<String, RawBone>): Matrix4f? {
        val path = mutableListOf<RawBone>()
        var current = bones[name] ?: return null
        val visited = mutableSetOf<String>()
        while (visited.add(current.name)) {
            path += current
            current = current.parent?.let(bones::get) ?: break
        }
        path.reverse()

        val nodes = path.map { bone ->
            val parent = bone.parent?.let(bones::get)
            PositionNode(
                x = if (parent == null) bone.pivotX else bone.pivotX - parent.pivotX,
                y = if (parent == null) 24f - bone.pivotY else parent.pivotY - bone.pivotY,
                z = if (parent == null) bone.pivotZ else bone.pivotZ - parent.pivotZ,
                xRot = Math.toRadians(bone.rotationX.toDouble()).toFloat(),
                yRot = Math.toRadians(bone.rotationY.toDouble()).toFloat(),
                zRot = Math.toRadians(bone.rotationZ.toDouble()).toFloat(),
                hasParent = parent != null
            )
        }

        return Matrix4f().identity().also { matrix ->
            nodes.asReversed().forEach { node ->
                matrix.rotateX(-node.xRot)
                matrix.rotateY(-node.yRot)
                matrix.rotateZ(-node.zRot)
                if (node.hasParent) {
                    matrix.translate(-node.x / 16f, -node.y / 16f, -node.z / 16f)
                } else {
                    matrix.translate(-node.x / 16f, 1.5f - node.y / 16f, -node.z / 16f)
                }
            }
        }
    }

    private fun conventional(id: ResourceLocation, directory: String, suffix: String): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(id.namespace, "$directory/${id.path}$suffix")

    private fun JsonObject.requiredString(key: String): String =
        get(key)?.asString ?: error("missing '$key'")

    private fun JsonObject.resource(key: String, fallback: ResourceLocation): ResourceLocation =
        get(key)?.asString?.let(ResourceLocation::parse) ?: fallback

    private fun JsonObject.stringSet(key: String): Set<String> =
        getAsJsonArray(key)?.mapTo(linkedSetOf()) { it.asString }.orEmpty()

    private fun com.google.gson.JsonArray?.floatOrZero(index: Int): Float =
        if (this != null && index in 0 until size()) get(index).asFloat else 0f

    private const val PACK_DIRECTORY = "destiny_gunpacks"
    const val IDLE_VIEW = "idle_view"
    const val IRON_VIEW = "iron_view"
    const val THIRD_PERSON_HAND = "thirdperson_hand"
    const val FIXED = "fixed"
    const val GROUND = "ground"
    const val CAMERA = "camera"
    const val CONSTRAINT = "constraint"

    private val DEFAULT_SKIN_BONES = setOf("lefthand_pos", "righthand_pos")
    private val POSITIONING_BONES = setOf(
        IDLE_VIEW,
        IRON_VIEW,
        THIRD_PERSON_HAND,
        FIXED,
        GROUND
    )
}
