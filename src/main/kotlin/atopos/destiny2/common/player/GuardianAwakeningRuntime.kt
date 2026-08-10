package atopos.destiny2.common.player

import atopos.destiny2.common.cinematic.CinematicSessionTracker
import atopos.destiny2.common.entity.DestinyEntities
import atopos.destiny2.common.entity.JilingEntity
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID

object GuardianAwakeningRuntime {
    private val cinematicSessions = CinematicSessionTracker()
    private val frozenPlayers = mutableMapOf<UUID, FrozenPose>()
    private val awakeningActors = mutableMapOf<UUID, ActorSession>()

    private data class FrozenPose(
        val level: ServerLevel,
        val position: Vec3,
        val yaw: Float,
        val pitch: Float
    )

    private data class ActorSession(
        val sessionId: UUID,
        val actor: JilingEntity
    )

    fun register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register { entity, _, _ ->
            val player = entity as? ServerPlayer
            player == null || allowDeathOrBeginFirstResurrection(player)
        }
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            val player = entity as? ServerPlayer
            player == null || !cinematicSessions.isActive(player.uuid)
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer, alive ->
            if (!alive) presentAwakeningIfPending(newPlayer)
        }
        ServerPlayerEvents.JOIN.register(::presentAwakeningIfPending)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            cinematicSessions.clear(handler.player.uuid)
            cleanupPresentation(handler.player.uuid)
        }
        ServerTickEvents.START_SERVER_TICK.register { server ->
            server.playerList.players.forEach { player ->
                if (cinematicSessions.isActive(player.uuid)) {
                    freezePlayer(player)
                    suppressMonsterAggression(player)
                }
            }
        }
        ServerTickEvents.END_SERVER_TICK.register {
            cinematicSessions.tick().forEach(::cleanupPresentation)
        }
    }

    private fun allowDeathOrBeginFirstResurrection(player: ServerPlayer): Boolean {
        val data = PlayerDestinyDataApi.get(player)
        if (!GuardianAwakeningRules.canAwaken(data.journeyStage, player.isCreative, player.isSpectator)) {
            return true
        }

        val level = player.serverLevel()
        val deathOrigin = player.blockPosition()
        val encounterBox = player.boundingBox.inflate(MONSTER_CLEAR_RADIUS, MONSTER_CLEAR_VERTICAL_RADIUS, MONSTER_CLEAR_RADIUS)
        val nearbyMobs = level.getEntitiesOfClass(Mob::class.java, encounterBox)
        val nearbyElites = nearbyMobs.filter(::isElite)
        val mustEvacuateFluid = isInWaterOrLava(player, level, deathOrigin)
        clearOrdinaryMonsters(level, encounterBox)

        val safeDestination = if (nearbyElites.isNotEmpty() || mustEvacuateFluid) {
            findSafeDestination(
                level,
                deathOrigin,
                nearbyElites,
                minimumRadius = if (mustEvacuateFluid) FLUID_EVACUATION_MINIMUM_RADIUS else SAFE_SEARCH_MINIMUM_RADIUS,
                step = if (mustEvacuateFluid) FLUID_EVACUATION_STEP else SAFE_SEARCH_STEP
            )
        } else {
            null
        }
        data.journeyStage = GuardianJourneyStage.AWAKENED
        data.awakeningPresentationPending = true
        stabilizeResurrectedPlayer(player)
        if (safeDestination != null) {
            clearOrdinaryMonsters(
                level,
                AABB(safeDestination).inflate(
                    MONSTER_CLEAR_RADIUS,
                    MONSTER_CLEAR_VERTICAL_RADIUS,
                    MONSTER_CLEAR_RADIUS
                )
            )
            player.teleportTo(
                safeDestination.x + 0.5,
                safeDestination.y.toDouble(),
                safeDestination.z + 0.5
            )
        }
        presentAwakeningIfPending(player)
        return false
    }

    private fun presentAwakeningIfPending(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        if (!data.awakeningPresentationPending || data.journeyStage != GuardianJourneyStage.AWAKENED) return
        if (player.isSpectator || cinematicSessions.isActive(player.uuid)) return

        val sessionId = UUID.randomUUID()
        cleanupPresentation(player.uuid)
        val actor = JilingEntity(DestinyEntities.JILING, player.serverLevel()).apply {
            prepareAwakeningActor()
            moveTo(player.x, player.y, player.z, player.yRot, 0.0f)
            yBodyRot = player.yRot
            yHeadRot = player.yRot
        }
        if (!player.serverLevel().addFreshEntity(actor)) return

        cinematicSessions.start(player.uuid, sessionId, CINEMATIC_SAFETY_TIMEOUT_TICKS)
        frozenPlayers[player.uuid] = FrozenPose(
            level = player.serverLevel(),
            position = player.position(),
            yaw = player.yRot,
            pitch = player.xRot
        )
        awakeningActors[player.uuid] = ActorSession(sessionId, actor)
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.StartCinematicPayload(
                sessionId = sessionId,
                cinematicId = AWAKENING_CINEMATIC_ID,
                anchorX = player.x,
                anchorY = player.y,
                anchorZ = player.z,
                anchorYaw = player.yRot,
                actorEntityId = actor.id
            )
        )
    }

    fun beginAnimation(player: ServerPlayer, sessionId: UUID) {
        if (!cinematicSessions.owns(player.uuid, sessionId)) return
        val actorSession = awakeningActors[player.uuid] ?: return
        if (actorSession.sessionId != sessionId || actorSession.actor.isRemoved) return
        if (actorSession.actor.isAwakeningStarted()) return

        actorSession.actor.startAwakeningAnimation()
        val level = player.serverLevel()
        level.sendParticles(ParticleTypes.END_ROD, player.x, player.y + 1.0, player.z, 48, 0.8, 1.0, 0.8, 0.035)
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.x, player.y + 1.0, player.z, 32, 0.65, 0.9, 0.65, 0.025)
    }

    fun finishCinematic(player: ServerPlayer, sessionId: UUID) {
        if (!cinematicSessions.finish(player.uuid, sessionId)) return
        cleanupPresentation(player.uuid)

        val data = PlayerDestinyDataApi.get(player)
        if (!data.awakeningPresentationPending || data.journeyStage != GuardianJourneyStage.AWAKENED) return

        // Consume before granting the unique item so duplicate acknowledgements
        // and callback overlap can never duplicate the reward.
        data.awakeningPresentationPending = false
        giveGhostCore(player)
        player.connection.send(ClientboundSetTitlesAnimationPacket(10, 70, 20))
        player.connection.send(ClientboundSetTitleTextPacket(Component.translatable("journey.destiny2-mod.awakening.title")))
        player.connection.send(ClientboundSetSubtitleTextPacket(Component.translatable("journey.destiny2-mod.awakening.subtitle")))
        player.sendSystemMessage(Component.translatable("journey.destiny2-mod.awakening.message"))
        player.sendSystemMessage(Component.translatable("journey.destiny2-mod.awakening.next_objective"))
        DestinyNetworking.syncPlayerData(player)
    }

    fun isProtected(player: ServerPlayer): Boolean = cinematicSessions.isActive(player.uuid)

    private fun stabilizeResurrectedPlayer(player: ServerPlayer) {
        player.health = player.maxHealth
        player.foodData.foodLevel = 20
        player.foodData.setSaturation(5.0f)
        player.airSupply = player.maxAirSupply
        player.remainingFireTicks = 0
        player.ticksFrozen = 0
        player.fallDistance = 0.0f
        player.hurtTime = 0
        player.invulnerableTime = RESURRECTION_INVULNERABLE_TICKS
    }

    private fun freezePlayer(player: ServerPlayer) {
        val pose = frozenPlayers[player.uuid] ?: return
        if (player.serverLevel() !== pose.level) return
        if (player.position().distanceToSqr(pose.position) > FREEZE_POSITION_EPSILON_SQUARED) {
            player.teleportTo(pose.position.x, pose.position.y, pose.position.z)
        }
        player.setDeltaMovement(Vec3.ZERO)
        player.yRot = pose.yaw
        player.yHeadRot = pose.yaw
        player.yBodyRot = pose.yaw
        player.xRot = pose.pitch
        player.fallDistance = 0.0f
    }

    private fun cleanupPresentation(playerId: UUID) {
        frozenPlayers.remove(playerId)
        awakeningActors.remove(playerId)?.actor?.takeUnless { it.isRemoved }?.discard()
    }

    private fun isInWaterOrLava(player: ServerPlayer, level: ServerLevel, origin: BlockPos): Boolean =
        player.isInWaterOrBubble ||
            player.isInLava ||
            level.getFluidState(origin).`is`(FluidTags.WATER) ||
            level.getFluidState(origin).`is`(FluidTags.LAVA) ||
            level.getFluidState(origin.above()).`is`(FluidTags.WATER) ||
            level.getFluidState(origin.above()).`is`(FluidTags.LAVA)

    private fun suppressMonsterAggression(player: ServerPlayer) {
        val box = player.boundingBox.inflate(PROTECTION_TARGET_RADIUS)
        player.serverLevel()
            .getEntitiesOfClass(Mob::class.java, box) { it.target === player }
            .forEach { it.target = null }
    }

    private fun clearOrdinaryMonsters(level: ServerLevel, area: AABB) {
        level.getEntitiesOfClass(Monster::class.java, area)
            .filter {
                GuardianAwakeningSafetyRules.shouldRemoveOrdinaryMonster(
                    hasCustomName = it.hasCustomName(),
                    elite = isElite(it)
                )
            }
            .forEach(Mob::discard)
    }

    private fun isElite(mob: Mob): Boolean =
        mob.type.`is`(GuardianAwakeningEntityTags.ELITES)

    private fun findSafeDestination(
        level: ServerLevel,
        origin: BlockPos,
        nearbyElites: List<Mob>,
        minimumRadius: Int,
        step: Int
    ): BlockPos? {
        val local = GuardianAwakeningSafetyRules.safeSearchOffsets(
            minimumRadius = minimumRadius,
            maximumRadius = SAFE_SEARCH_MAXIMUM_RADIUS,
            step = step
        )
            .asSequence()
            .mapNotNull { offset ->
                safeSurfaceAt(
                    level,
                    origin.x + offset.x,
                    origin.z + offset.z,
                    preferredY = origin.y
                )
            }
            .firstOrNull { candidate ->
                isClearOfEliteEncounter(candidate, nearbyElites) &&
                    hasNoPersistentHostileMobNear(level, candidate)
            }
        if (local != null) return local

        val spawn = level.sharedSpawnPos
        return sequenceOf(GuardianAwakeningSafetyRules.HorizontalOffset(0, 0))
            .plus(GuardianAwakeningSafetyRules.safeSearchOffsets(8, 40, 8))
            .mapNotNull { offset ->
                safeSurfaceAt(
                    level,
                    spawn.x + offset.x,
                    spawn.z + offset.z,
                    preferredY = spawn.y,
                    allowChunkLoad = true
                )
            }
            .firstOrNull { candidate -> hasNoPersistentHostileMobNear(level, candidate) }
    }

    private fun safeSurfaceAt(
        level: ServerLevel,
        x: Int,
        z: Int,
        preferredY: Int,
        allowChunkLoad: Boolean = false
    ): BlockPos? {
        if (!allowChunkLoad && !level.chunkSource.hasChunk(x shr 4, z shr 4)) return null
        val heights = if (level.dimensionType().hasCeiling()) {
            buildList {
                add(preferredY)
                for (distance in 1..SAFE_VERTICAL_SEARCH_RADIUS) {
                    add(preferredY + distance)
                    add(preferredY - distance)
                }
            }
        } else {
            listOf(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z))
        }
        return heights
            .asSequence()
            .filter { it > level.minBuildHeight && it < level.maxBuildHeight - 1 }
            .map { BlockPos(x, it, z) }
            .firstOrNull { isSafeStandingPosition(level, it) }
    }

    private fun isSafeStandingPosition(level: ServerLevel, feet: BlockPos): Boolean {
        if (!level.worldBorder.isWithinBounds(feet)) return false

        val floor = feet.below()
        val floorState = level.getBlockState(floor)
        val feetState = level.getBlockState(feet)
        val headState = level.getBlockState(feet.above())
        if (!floorState.isFaceSturdy(level, floor, Direction.UP)) return false
        if (!feetState.getCollisionShape(level, feet).isEmpty) return false
        if (!headState.getCollisionShape(level, feet.above()).isEmpty) return false
        if (!level.getFluidState(feet).isEmpty || !level.getFluidState(feet.above()).isEmpty) return false
        if (
            floorState.`is`(Blocks.MAGMA_BLOCK) ||
            floorState.`is`(Blocks.CACTUS) ||
            feetState.`is`(Blocks.FIRE) ||
            feetState.`is`(Blocks.SOUL_FIRE) ||
            feetState.`is`(Blocks.POWDER_SNOW)
        ) {
            return false
        }
        return true
    }

    private fun isClearOfEliteEncounter(candidate: BlockPos, elites: List<Mob>): Boolean {
        val center = Vec3.atBottomCenterOf(candidate)
        return elites.none { it.distanceToSqr(center) < SAFE_ELITE_CLEARANCE_SQUARED }
    }

    private fun hasNoPersistentHostileMobNear(level: ServerLevel, candidate: BlockPos): Boolean {
        val area = AABB(candidate).inflate(SAFE_MONSTER_CLEARANCE, SAFE_MONSTER_VERTICAL_CLEARANCE, SAFE_MONSTER_CLEARANCE)
        return level.getEntitiesOfClass(Mob::class.java, area) {
            isElite(it) || (it is Monster && it.hasCustomName())
        }.isEmpty()
    }

    private fun giveGhostCore(player: ServerPlayer) {
        val stack = ItemStack(DestinyItems.GHOST_CORE)
        if (!player.inventory.add(stack)) {
            player.drop(stack, false)
        }
    }

    private const val AWAKENING_CINEMATIC_ID = "guardian_awakening"
    private const val CINEMATIC_SAFETY_TIMEOUT_TICKS = 20 * 90
    private const val RESURRECTION_INVULNERABLE_TICKS = 40
    private const val MONSTER_CLEAR_RADIUS = 32.0
    private const val MONSTER_CLEAR_VERTICAL_RADIUS = 16.0
    private const val PROTECTION_TARGET_RADIUS = 48.0
    private const val SAFE_MONSTER_CLEARANCE = 14.0
    private const val SAFE_MONSTER_VERTICAL_CLEARANCE = 8.0
    private const val SAFE_VERTICAL_SEARCH_RADIUS = 32
    private const val SAFE_SEARCH_MINIMUM_RADIUS = 24
    private const val SAFE_SEARCH_MAXIMUM_RADIUS = 72
    private const val SAFE_SEARCH_STEP = 8
    private const val FLUID_EVACUATION_MINIMUM_RADIUS = 4
    private const val FLUID_EVACUATION_STEP = 4
    private const val SAFE_ELITE_CLEARANCE = 28.0
    private const val SAFE_ELITE_CLEARANCE_SQUARED = SAFE_ELITE_CLEARANCE * SAFE_ELITE_CLEARANCE
    private const val FREEZE_POSITION_EPSILON_SQUARED = 0.0001
}
