package atopos.destiny2.client.particle.bedrock

import com.google.gson.JsonParser
import de.tomalbrc.sandstorm.component.ParticleComponents
import de.tomalbrc.sandstorm.io.Json
import de.tomalbrc.sandstorm.io.ParticleEffectFile
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object BedrockParticleResources : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("DestinyBedrockParticles")
    private val effects = ConcurrentHashMap<ResourceLocation, ParticleEffectFile>()
    private val locators = ConcurrentHashMap<ResourceLocation, Map<String, Locator>>()

    data class Locator(
        val parentBone: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val rotationX: Float,
        val rotationY: Float,
        val rotationZ: Float
    )

    override fun getFabricId(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "bedrock_particle_resources")

    override fun onResourceManagerReload(manager: ResourceManager) {
        ParticleComponents.init()
        val loadedEffects = HashMap<ResourceLocation, ParticleEffectFile>()
        manager.listResources("bedrock_particles") { it.path.endsWith(".particle.json") }.forEach { (path, resource) ->
            runCatching {
                resource.openAsReader().use { Json.GSON.fromJson(it, ParticleEffectFile::class.java) }
            }.onSuccess { file ->
                val id = file.effect?.description?.identifier
                    ?: throw IllegalArgumentException("Missing particle_effect.description.identifier in $path")
                loadedEffects[id] = file
            }.onFailure { logger.error("Failed to load Bedrock particle {}", path, it) }
        }
        effects.clear()
        effects.putAll(loadedEffects)

        val loadedLocators = HashMap<ResourceLocation, Map<String, Locator>>()
        manager.listResources("geo") { it.path.endsWith(".geo.json") }.forEach { (path, resource) ->
            runCatching {
                val root = resource.openAsReader().use(JsonParser::parseReader).asJsonObject
                val result = LinkedHashMap<String, Locator>()
                root.getAsJsonArray("minecraft:geometry")?.forEach { geometry ->
                    geometry.asJsonObject.getAsJsonArray("bones")?.forEach { node ->
                        val bone = node.asJsonObject
                        val parentName = bone.get("name")?.asString ?: return@forEach
                        bone.getAsJsonObject("locators")?.entrySet()?.forEach { (name, value) ->
                            val obj = value.takeIf { it.isJsonObject }?.asJsonObject
                            val offset = obj?.getAsJsonArray("offset") ?: value.takeIf { it.isJsonArray }?.asJsonArray
                            if (offset != null && offset.size() >= 3) {
                                val rotation = obj?.getAsJsonArray("rotation")
                                // The current pose already contains prepMatrixForBone. Geo vertices
                                // remain in absolute model space, so locators must use their absolute
                                // exported coordinates too. Match GeckoLib BakedModelFactory exactly:
                                // position (X,Y,Z) -> (-X,Y,Z), rotation -> (-X,-Y,Z).
                                result[name] = Locator(
                                    parentName,
                                    -offset[0].asDouble / 16.0,
                                    offset[1].asDouble / 16.0,
                                    offset[2].asDouble / 16.0,
                                    -(rotation?.get(0)?.asFloat ?: 0f),
                                    -(rotation?.get(1)?.asFloat ?: 0f),
                                    rotation?.get(2)?.asFloat ?: 0f
                                )
                            }
                        }
                    }
                }
                result
            }.onSuccess { loadedLocators[path] = it }
                .onFailure { logger.error("Failed to load locators from {}", path, it) }
        }
        locators.clear()
        locators.putAll(loadedLocators)
        logger.info("Loaded {} Bedrock particle effects and {} geo locator sets", effects.size, locators.size)
    }

    fun effect(id: ResourceLocation): ParticleEffectFile? = effects[id]
    fun locator(model: ResourceLocation, name: String): Locator? = locators[model]?.get(name)
}
