package atopos.destiny2.common.entity

import atopos.destiny2.common.aspect.SolarWarlockAspectRules
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricEntityTypeBuilder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.resources.ResourceLocation
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry

/**
 * 实体注册表 (Entity Registry)
 */
object DestinyEntities {
    val FIRESPRITE: EntityType<FirespriteEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "firesprite"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> FirespriteEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.34f, 0.58f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val ORB_OF_POWER: EntityType<OrbOfPowerEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "orb_of_power"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> OrbOfPowerEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.42f, 0.42f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val THUNDERCLAP_PLAYER_PROXY: EntityType<ThunderclapPlayerProxyEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "thunderclap_player_proxy"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world ->
            ThunderclapPlayerProxyEntity(type, world)
        }
            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
            .trackRangeChunks(10)
            .trackedUpdateRate(1)
            .build()
    )
    
    // 注册烈日手雷实体
    val SOLAR_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "solar_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> SolarGrenadeEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .build()
    )

    // 注册烈日耀斑实体
    val SOLAR_FLARE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "solar_flare"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> SolarFlareEntity(type, world) }
            .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
            .build()
    )

    val HEALING_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "healing_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> HealingGrenadeEntity(type, world) }
            .dimensions(
                EntityDimensions.fixed(
                    SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_ENTITY_SIZE,
                    SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_ENTITY_SIZE
                )
            )
            .trackRangeChunks(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_TRACK_RANGE_CHUNKS)
            .trackedUpdateRate(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_TRACKED_UPDATE_RATE)
            .build()
    )

    val HEALING_GRENADE_ORB = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "healing_grenade_orb"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> HealingGrenadeOrbEntity(type, world) }
            .dimensions(
                EntityDimensions.fixed(
                    SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEALING_ORB_ENTITY_SIZE,
                    SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEALING_ORB_ENTITY_SIZE
                )
            )
            .trackRangeChunks(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_TRACK_RANGE_CHUNKS)
            .trackedUpdateRate(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEALING_ORB_TRACKED_UPDATE_RATE)
            .build()
    )

    val FIREBOLT_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "firebolt_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> FireboltGrenadeEntity(type, world) }
            .dimensions(
                EntityDimensions.fixed(
                    SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_ENTITY_SIZE,
                    SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_ENTITY_SIZE
                )
            )
            .trackRangeChunks(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_TRACK_RANGE_CHUNKS)
            .trackedUpdateRate(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_TRACKED_UPDATE_RATE)
            .build()
    )

    val FUSION_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "fusion_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> FusionGrenadeEntity(type, world) }
            .dimensions(
                EntityDimensions.fixed(
                    SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_ENTITY_SIZE,
                    SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_ENTITY_SIZE
                )
            )
            .trackRangeChunks(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_TRACK_RANGE_CHUNKS)
            .trackedUpdateRate(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_TRACKED_UPDATE_RATE)
            .build()
    )

    // 注册焚烧响指投掷物
    val INCINERATOR_SNAP_PROJECTILE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "incinerator_snap_projectile"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> IncineratorSnapProjectile(type, world) }
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .build()
    )

    // 注册治愈裂隙实体
    val HEALING_RIFT = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "healing_rift"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> HealingRiftEntity(type, world) }
            .dimensions(EntityDimensions.fixed(1.0f, 0.1f))
            .build()
    )

    // 注册光焰之井实体
    val WELL_OF_RADIANCE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "well_of_radiance"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> WellOfRadianceEntity(type, world) }
            .dimensions(EntityDimensions.fixed(1.0f, 0.1f))
            .build()
    )

    // 注册微型导弹实体
    val MICRO_MISSILE_PROJECTILE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "micro_missile_projectile"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> MicroMissileProjectile(type, world) }
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .build()
    )

    // --- 虚空猎人实体 ---

    val VOID_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> VoidGrenadeEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .build()
    )

    val VOID_VORTEX = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_vortex"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> VoidVortexEntity(type, world) }
            .dimensions(EntityDimensions.fixed(3.0f, 1.0f))
            .build()
    )

    val SNARE_BOMB = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "snare_bomb"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> SnareBombEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .build()
    )

    val SHADOWSHOT_ANCHOR = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "shadowshot_anchor"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ShadowshotAnchorEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
            .build()
    )

    val VOID_TETHER = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_tether"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> VoidTetherEntity(type, world) }
            .dimensions(EntityDimensions.fixed(1.0f, 2.0f))
            .build()
    )

    val ARC_PULSE_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_pulse_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ArcPulseGrenadeEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val ARC_FLASHBANG_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_flashbang_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ArcFlashbangGrenadeEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val ARC_LIGHTNING_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_lightning_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ArcLightningGrenadeEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val ARC_STORM_GRENADE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_storm_grenade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ArcStormGrenadeEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.35f, 0.35f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val ARC_ABILITY_DAMAGE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_ability_damage"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ArcAbilityDamageEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val ARC_TITAN_BARRICADE: EntityType<ArcTitanBarricadeEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "arc_titan_barricade"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ArcTitanBarricadeEntity(type, world) }
            .dimensions(EntityDimensions.fixed(4.2f, 2.6f))
            .trackRangeChunks(10)
            .trackedUpdateRate(1)
            .build()
    )

    val SOLAR_ERUPTION_PROJECTILE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "solar_eruption_projectile"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world ->
            SolarEruptionProjectileEntity(type, world)
        }
            .dimensions(EntityDimensions.fixed(0.32f, 0.32f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val FORGOTTEN_NAME_BULLET = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name_bullet"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ForgottenNameBulletEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.1f, 0.1f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val HUNTING_MARK = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "hunting_mark"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> HuntingMarkEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.9f, 0.9f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val FORGOTTEN_REMNANT = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_remnant"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> ForgottenRemnantEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.8f, 0.35f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val FALLEN_SOLDIER: EntityType<FallenSoldierEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "fallen_soldier"),
        FabricEntityTypeBuilder.create(MobCategory.MONSTER) { type, world -> FallenSoldierEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.7f, 2.15f))
            .trackRangeChunks(8).trackedUpdateRate(1).build()
    )

    val FALLEN_CAPTAIN: EntityType<FallenCaptainEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "fallen_captain"),
        FabricEntityTypeBuilder.create(MobCategory.MONSTER) { type, world -> FallenCaptainEntity(type, world) }
            .dimensions(EntityDimensions.fixed(1.15f, 2.85f))
            .trackRangeChunks(10)
            .trackedUpdateRate(1)
            .build()
    )

    val FALLEN_CAPTAIN_PELLET: EntityType<FallenCaptainPelletEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "fallen_captain_pellet"),
        FabricEntityTypeBuilder.create(MobCategory.MISC) { type, world -> FallenCaptainPelletEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.12f, 0.12f))
            .trackRangeChunks(8)
            .trackedUpdateRate(1)
            .build()
    )

    val JILING: EntityType<JilingEntity> = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "jiling"),
        FabricEntityTypeBuilder.create(MobCategory.CREATURE) { type, world -> JilingEntity(type, world) }
            .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
            .trackRangeChunks(8)
            .trackedUpdateRate(2)
            .build()
    )

    fun register() {
        FabricDefaultAttributeRegistry.register(FALLEN_SOLDIER, FallenSoldierEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(FALLEN_CAPTAIN, FallenCaptainEntity.createAttributes())
        FabricDefaultAttributeRegistry.register(JILING, JilingEntity.createAttributes())
    }
}
