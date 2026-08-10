package atopos.destiny2.common.entity

import atopos.destiny2.common.aspect.SolarWarlockFragmentRuntime
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.effect.SolarIgnitionRuntime
import atopos.destiny2.common.effect.SolarScorchContext
import atopos.destiny2.common.particle.BedrockWorldParticleBridge
import atopos.destiny2.common.sound.DestinySounds
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import software.bernie.geckolib.animatable.GeoEntity
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.util.GeckoLibUtil
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class WellOfRadianceEntity(
    entityType: EntityType<*>,
    level: Level
) : Entity(entityType, level), GeoEntity {
    private val cache: AnimatableInstanceCache = GeckoLibUtil.createInstanceCache(this)

    constructor(entityType: EntityType<*>, level: Level, x: Double, y: Double, z: Double, owner: LivingEntity?) : this(entityType, level) {
        setPos(x, y, z)
        this.owner = owner
    }

    private var duration = 30 * 20
    private var initialized = false
    var owner: LivingEntity? = null
    private var ownerUuid: UUID? = null

    override fun tick() {
        super.tick()

        if (!initialized && !level().isClientSide) {
            performInitialImpact()
            initialized = true
        }

        if (level().isClientSide) {
            if (LEGACY_PARTICLES_VISIBLE) {
                spawnGuangjinSparks()
            }
            return
        }

        val savedOwner = ownerUuid
        if (owner == null && savedOwner != null) {
            owner = (level() as? ServerLevel)?.getEntity(savedOwner) as? LivingEntity
        }

        if (duration-- <= 0) {
            discard()
            return
        }

        if (tickCount == LOOP_SOUND_START_TICK) {
            level().playSound(
                null,
                blockPosition(),
                DestinySounds.WELL_OF_RADIANCE_LOOP,
                SoundSource.PLAYERS,
                1.0f,
                1.0f
            )
        }

        applyAuraEffects()
    }

    private fun spawnGuangjinSparks() {
        if (tickCount % 4 != 0) return

        repeat(2) { index ->
            val phase = tickCount * 0.31 + id * 0.47 + index * PI
            val radiusStep = ((tickCount + index * 7) % 17) / 16.0
            val radius = 0.75 + radiusStep * 2.55
            val heightStep = ((tickCount * 3 + index * 11) % 29) / 28.0
            val height = 0.35 + heightStep * 3.95
            val sparkPosition = position().add(
                cos(phase) * radius,
                height,
                sin(phase) * radius
            )
            BedrockWorldParticleBridge.emit(GUANGJIN_SPARK_EFFECT, sparkPosition, 0f, 0f)
        }
    }

    private fun performInitialImpact() {
        val radius = 5.0
        val damage = 10.0f
        val sourcePlayer = owner as? ServerPlayer
        val context = sourcePlayer?.let { SolarScorchContext(it.uuid, SolarDamageKind.SUPER, uuid) }

        val entities = level().getEntities(this, AABB(x - radius, y - 2, z - radius, x + radius, y + 4, z + radius))
        entities.forEach { entity ->
            if (entity is LivingEntity && entity != owner && isEnemy(entity)) {
                val source = if (sourcePlayer != null) damageSources().indirectMagic(this, sourcePlayer) else damageSources().magic()
                val wasAlive = entity.isAlive
                if (context != null) {
                    SolarIgnitionRuntime.withAttributedSolarDamage(entity, context, ignition = false) {
                        entity.hurt(source, damage)
                    }
                } else {
                    entity.hurt(source, damage)
                }
                if (wasAlive && !entity.isAlive && sourcePlayer != null && context != null) {
                    SolarWarlockFragmentRuntime.onAttributedFinalBlow(sourcePlayer, entity, context, wasScorched = false)
                }
            }
        }

    }

    private fun applyAuraEffects() {
        val radius = 8.0
        val source = owner as? ServerPlayer
        val entities = level().getEntities(this, AABB(x - radius, y - 2, z - radius, x + radius, y + 4, z + radius))
        entities.forEach { entity ->
            if (entity is LivingEntity && (entity == owner || isAlly(entity))) {
                DestinyStatusRules.applyRadiant(entity, 120, source)
                DestinyStatusRules.applyRestoration(entity, 80, level = 2, source = source)
                if (entity is Player && tickCount % 20 == 0) {
                    DestinyStatusRules.applyCure(entity, 5.0f, source)
                }
            }
        }
    }

    private fun isEnemy(entity: LivingEntity): Boolean {
        val currentOwner = owner
        return entity !is Player || currentOwner !is Player || !currentOwner.isAlliedTo(entity)
    }

    private fun isAlly(entity: LivingEntity): Boolean {
        val currentOwner = owner
        return entity is Player && (currentOwner !is Player || currentOwner.isAlliedTo(entity) || entity == currentOwner)
    }

    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) {}
    override fun readAdditionalSaveData(compound: CompoundTag) {
        duration = compound.getInt("Duration").takeIf { it > 0 } ?: 30 * 20
        initialized = compound.getBoolean("Initialized")
        ownerUuid = if (compound.hasUUID("Owner")) compound.getUUID("Owner") else null
    }

    override fun addAdditionalSaveData(compound: CompoundTag) {
        compound.putInt("Duration", duration)
        compound.putBoolean("Initialized", initialized)
        (owner?.uuid ?: ownerUuid)?.let { compound.putUUID("Owner", it) }
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        controllers.add(
            AnimationController(this, "well_controller", 0) { state ->
                state.controller.setAnimation(RawAnimation.begin().thenLoop("animation.well_of_radiance.idle"))
                PlayState.CONTINUE
            }.setParticleKeyframeHandler { event ->
                if (LEGACY_PARTICLES_VISIBLE && event.keyframeData.effect == "particles") {
                    BedrockWorldParticleBridge.emit(
                        AUTHORED_PARTICLE_EFFECT,
                        position(),
                        yRot,
                        xRot
                    )
                }
            }
        )
    }

    override fun getAnimatableInstanceCache(): AnimatableInstanceCache = cache

    private companion object {
        const val LOOP_SOUND_START_TICK = 35

        // Temporarily hide both authored Well particle emitters without touching
        // their JSON, textures, animation keyframes, or bridge registrations.
        const val LEGACY_PARTICLES_VISIBLE = false

        val GUANGJIN_SPARK_EFFECT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "well_guangjin_spark"
        )
        val AUTHORED_PARTICLE_EFFECT: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            "destiny2-mod",
            "well_authored_particles"
        )
    }
}
