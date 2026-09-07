// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.weapon

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * TaCZ-style data-driven gun values. Code owns behavior; server data packs own numbers.
 */
object DestinyWeaponDataRegistry : SimpleSynchronousResourceReloadListener {
    private val logger = LoggerFactory.getLogger("DestinyWeaponData")
    private val profiles = ConcurrentHashMap<ResourceLocation, WeaponCombatProfile>()

    fun register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(this)
    }

    override fun getFabricId(): ResourceLocation =
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "weapon_data")

    override fun onResourceManagerReload(manager: ResourceManager) {
        val loaded = HashMap<ResourceLocation, WeaponCombatProfile>()
        manager.listResources(ROOT) { it.path.endsWith(".json") }.forEach { (path, resource) ->
            runCatching {
                val root = resource.openAsReader().use(JsonParser::parseReader).asJsonObject
                val relative = path.path.removePrefix("$ROOT/").removeSuffix(".json")
                val id = ResourceLocation.fromNamespaceAndPath(path.namespace, relative)
                loaded[id] = parse(root)
            }.onFailure {
                logger.error("Failed to load weapon data {}", path, it)
            }
        }
        DestinyGunPackFiles.serverDefinitions().forEach { entry ->
            runCatching {
                val root = entry.bytes.inputStream().reader(Charsets.UTF_8)
                    .use(JsonParser::parseReader).asJsonObject
                val id = DestinyGunPackFiles.idFromDefinitionPath(entry.path, ROOT)
                    ?: error("invalid weapon definition path ${entry.path}")
                loaded[id] = parse(root)
            }.onFailure {
                logger.error(
                    "Failed to load external weapon data {} from {}",
                    entry.path,
                    entry.packName,
                    it
                )
            }
        }
        profiles.clear()
        profiles.putAll(loaded)
        logger.info("Loaded {} Destiny weapon definitions", profiles.size)
    }

    fun profile(id: ResourceLocation): WeaponCombatProfile? = profiles[id]

    fun ids(): List<ResourceLocation> = profiles.keys.sortedBy(ResourceLocation::toString)

    private fun parse(root: JsonObject): WeaponCombatProfile {
        val recoilJson = root.obj("recoil")
        val accuracyJson = root.obj("accuracy")
        val crosshairJson = root.obj("crosshair")
        val ballisticsJson = root.obj("ballistics")
        return WeaponCombatProfile(
            ammoType = DestinyAmmoType.valueOf(root.string("ammo_type", "PRIMARY").uppercase()),
            baseDamage = root.float("damage", 0.0f).coerceAtLeast(0.0f),
            precisionMultiplier = root.float("head_shot", 1.0f).coerceAtLeast(0.0f),
            magazineSize = root.int("magazine_size", 1).coerceAtLeast(1),
            reloadTicks = root.int("reload_ticks", 20).coerceAtLeast(1),
            reserveCapacity = when (val type = DestinyAmmoType.valueOf(root.string("ammo_type", "PRIMARY").uppercase())) {
                DestinyAmmoType.PRIMARY -> Int.MAX_VALUE
                DestinyAmmoType.SPECIAL -> root.int("reserve_capacity", root.int("magazine_size", 1) * 4).coerceAtLeast(0)
                DestinyAmmoType.HEAVY -> root.int("reserve_capacity", root.int("magazine_size", 1) * 3).coerceAtLeast(0)
            },
            emptyReloadBonusTicks = root.int("empty_reload_bonus_ticks", 0).coerceAtLeast(0),
            reloadFeedFraction = root.float("reload_feed_fraction", 0.72f).coerceIn(0.05f, 0.95f),
            fireMode = WeaponFireMode.valueOf(root.string("fire_mode", "SEMI").uppercase()),
            supportedFireModes = root.getAsJsonArray("supported_fire_modes")?.map {
                WeaponFireMode.valueOf(it.asString.uppercase())
            } ?: listOf(WeaponFireMode.valueOf(root.string("fire_mode", "SEMI").uppercase())),
            roundsPerMinute = root.int("rounds_per_minute", 80).coerceAtLeast(1),
            boltTicks = root.int("bolt_ticks", 0).coerceAtLeast(0),
            recoil = WeaponRecoilProfile(
                pitchMin = recoilJson.float("pitch_min", 0.0f),
                pitchMax = recoilJson.float("pitch_max", 0.0f),
                yawMin = recoilJson.float("yaw_min", 0.0f),
                yawMax = recoilJson.float("yaw_max", 0.0f),
                yawPattern = recoilJson.recoilPattern("yaw_pattern"),
                kickDurationMs = recoilJson.int("kick_ms", 70).coerceAtLeast(1),
                recoverDurationMs = recoilJson.int("recover_ms", 310).coerceAtLeast(1),
                aimedMultiplier = recoilJson.float("aimed_multiplier", 0.68f).coerceIn(0.0f, 1.5f),
                stability = recoilJson.float("stability", 60.0f).coerceIn(0.0f, 100.0f),
                recoilDirection = recoilJson.float("recoil_direction", 70.0f).coerceIn(0.0f, 100.0f)
            ),
            accuracy = WeaponAccuracyProfile(
                hipBaseDegrees = accuracyJson.float("hip_base_degrees", 0.45f).coerceAtLeast(0.0f),
                aimedBaseDegrees = accuracyJson.float("aimed_base_degrees", 0.04f).coerceAtLeast(0.0f),
                movingPenaltyDegrees = accuracyJson.float("moving_penalty_degrees", 0.15f).coerceAtLeast(0.0f),
                airbornePenaltyDegrees = accuracyJson.float("airborne_penalty_degrees", 1.25f).coerceAtLeast(0.0f),
                bloomPerShotDegrees = accuracyJson.float("bloom_per_shot_degrees", 0.08f).coerceAtLeast(0.0f),
                maxBloomDegrees = accuracyJson.float("max_bloom_degrees", 0.5f).coerceAtLeast(0.0f),
                settleDelayTicks = accuracyJson.int("settle_delay_ticks", 3).coerceAtLeast(0),
                bloomDecayPerTick = accuracyJson.float("bloom_decay_per_tick", 0.06f).coerceAtLeast(0.0f)
            ),
            crosshair = WeaponCrosshairProfile(
                baseGap = crosshairJson.float("base_gap", 5.0f),
                movingPenalty = crosshairJson.float("moving_penalty", 4.0f),
                airbornePenalty = crosshairJson.float("airborne_penalty", 7.0f),
                shotPenalty = crosshairJson.float("shot_penalty", 5.0f),
                shotDecayPerTick = crosshairJson.float("shot_decay_per_tick", 0.55f),
                hideAimProgress = crosshairJson.float("hide_aim_progress", 0.92f)
            ),
            ballistics = WeaponBallisticsProfile(
                range = ballisticsJson.double("range", 128.0).coerceAtLeast(1.0),
                entityTolerance = ballisticsJson.double("entity_tolerance", 0.08).coerceAtLeast(0.0),
                tracerStep = ballisticsJson.double("tracer_step", 1.35).coerceAtLeast(0.1),
                showBulletImpact = ballisticsJson.boolean("bullet_impact", true),
                speed = ballisticsJson.float("speed", 8.0f).coerceAtLeast(0.01f),
                gravity = ballisticsJson.float("gravity", 0.0f).coerceAtLeast(0.0f),
                friction = ballisticsJson.float("friction", 0.01f).coerceIn(0.0f, 1.0f),
                lifeTicks = ballisticsJson.int("life_ticks", 40).coerceAtLeast(1),
                pierce = ballisticsJson.int("pierce", 1).coerceAtLeast(1),
                distanceDamage = ballisticsJson.getAsJsonArray("distance_damage")?.map { node ->
                    val value = node.asJsonObject
                    WeaponDistanceDamage(
                        value.double("distance", 128.0).coerceAtLeast(0.01),
                        value.float("damage", 0.0f).coerceAtLeast(0.0f)
                    )
                }?.sortedBy(WeaponDistanceDamage::distance) ?: emptyList()
            ),
            projectilesPerShot = root.int("projectiles_per_shot", 1).coerceIn(1, 32),
            projectileSpreadDegrees = root.float("projectile_spread_degrees", 0.0f).coerceIn(0.0f, 30.0f),
            damageElement = DestinyDamageElement.fromSerializedName(root.string("damage_element", "KINETIC"))
        )
    }

    private fun JsonObject.obj(name: String): JsonObject =
        get(name)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()

    private fun JsonObject.string(name: String, fallback: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: fallback

    private fun JsonObject.int(name: String, fallback: Int): Int =
        get(name)?.takeIf { it.isJsonPrimitive }?.asInt ?: fallback

    private fun JsonObject.float(name: String, fallback: Float): Float =
        get(name)?.takeIf { it.isJsonPrimitive }?.asFloat ?: fallback

    private fun JsonObject.recoilPattern(name: String): List<Float> {
        val array = get(name)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        if (array.size() > MAX_RECOIL_PATTERN_LENGTH) return emptyList()
        return runCatching {
            array.map { node ->
                require(node.isJsonPrimitive)
                node.asFloat.also { require(it.isFinite()) }.coerceIn(-1.0f, 1.0f)
            }
        }.getOrDefault(emptyList())
    }

    private fun JsonObject.double(name: String, fallback: Double): Double =
        get(name)?.takeIf { it.isJsonPrimitive }?.asDouble ?: fallback

    private fun JsonObject.boolean(name: String, fallback: Boolean): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: fallback

    private const val ROOT = "destiny_weapons"
    private const val MAX_RECOIL_PATTERN_LENGTH = 128
}
