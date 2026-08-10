// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.renderer

import com.google.gson.JsonParser
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory

/** Loads the bone-to-texture manifests exported by GeckoLib Multi-Texture Bridge. */
object GeckoLibMultiTextureResources : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("DestinyGeckoMultiTexture")

    data class BoneMaterial(
        val texture: ResourceLocation,
        val translucent: Boolean
    )

    data class Definition(
        val defaultTexture: ResourceLocation,
        val bones: Map<String, BoneMaterial>
    )

    @Volatile
    private var definitions: Map<ResourceLocation, Definition> = emptyMap()

    fun register() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(this)
    }

    fun definition(location: ResourceLocation): Definition? = definitions[location]

    override fun getFabricId(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "geckolib_multi_textures")

    override fun onResourceManagerReload(manager: ResourceManager) {
        val loaded = linkedMapOf<ResourceLocation, Definition>()
        manager.listResources(DIRECTORY) { it.path.endsWith(SUFFIX) }
            .toSortedMap(compareBy(ResourceLocation::toString))
            .forEach { (location, resource) ->
                runCatching {
                    resource.openAsReader().use(JsonParser::parseReader).asJsonObject.let(::parseDefinition)
                }.onSuccess { definition ->
                    loaded[location] = definition
                }.onFailure { error ->
                    logger.error("Failed to load GeckoLib multi-texture manifest {}", location, error)
                }
            }
        definitions = loaded.toMap()
        logger.info("Loaded {} GeckoLib multi-texture manifest(s)", loaded.size)
    }

    private fun parseDefinition(root: com.google.gson.JsonObject): Definition {
        require(root.get("format_version")?.asInt == 1) { "Unsupported format_version" }
        val defaultTexture = ResourceLocation.parse(root.get("default_texture").asString)
        val bones = linkedMapOf<String, BoneMaterial>()
        root.getAsJsonObject("bones")?.entrySet()?.forEach { (boneName, element) ->
            val material = element.asJsonObject
            val texture = ResourceLocation.parse(material.get("texture").asString)
            val renderType = material.get("render_type")?.asString ?: "cutout"
            require(renderType == "cutout" || renderType == "translucent") {
                "Unsupported render_type '$renderType' for bone '$boneName'"
            }
            bones[boneName] = BoneMaterial(texture, renderType == "translucent")
        }
        return Definition(defaultTexture, bones.toMap())
    }

    private const val DIRECTORY = "geckolib_multitexture"
    private const val SUFFIX = ".bone_textures.json"
}
