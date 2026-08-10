package atopos.destiny2.common.ability

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.aspect.SolarWarlockAspectRules
import atopos.destiny2.common.aspect.DaybreakRuntime
import atopos.destiny2.common.aspect.SolarReviveRuntime
import atopos.destiny2.common.entity.DestinyEntities
import atopos.destiny2.common.entity.FireboltGrenadeEntity
import atopos.destiny2.common.entity.FusionGrenadeEntity
import atopos.destiny2.common.entity.HealingGrenadeEntity
import atopos.destiny2.common.entity.HealingRiftEntity
import atopos.destiny2.common.entity.IncineratorSnapProjectile
import atopos.destiny2.common.entity.SolarGrenadeEntity
import atopos.destiny2.common.entity.WellOfRadianceEntity
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.sound.DestinySounds
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

object SolarWarlockAbilities {
    private const val MOD_ID = "destiny2-mod"
    private const val INCINERATOR_SNAP_RELEASE_DELAY_TICKS = 18
    private val pendingSnaps = ConcurrentLinkedQueue<PendingSnap>()
    private val INCINERATOR_SNAP_CAST_VFX = listOf(
        "solar_snap__snap_flash" to 0,
        "solar_snap__launch_embers" to 1
    )

    private data class PendingSnap(
        val playerId: UUID,
        var remainingTicks: Int
    )

    fun register() {
        DaybreakRuntime.register()
        SolarReviveRuntime.register()
        ServerTickEvents.END_SERVER_TICK.register { server ->
            val continuing = mutableListOf<PendingSnap>()
            while (true) {
                val pending = pendingSnaps.poll() ?: break
                val player = server.playerList.getPlayer(pending.playerId) ?: continue
                pending.remainingTicks--
                if (pending.remainingTicks <= 0) {
                    releaseIncineratorSnap(player)
                } else {
                    continuing.add(pending)
                }
            }
            continuing.forEach(pendingSnaps::add)
        }
    }

    val SOLAR_GRENADE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "solar_warlock_solar_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "烈日手雷",
        baseCooldownTicks = 152 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val level = context.player.serverLevel()
            val grenade = SolarGrenadeEntity(level, context.player)
            level.addFreshEntity(grenade)
            level.playSound(null, context.player.blockPosition(), DestinySounds.SOLAR_GRENADE_CAST, SoundSource.PLAYERS, 0.75f, 1.0f)
            return true
        }
    }

    val HEALING_GRENADE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "solar_warlock_healing_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "治愈手雷",
        baseCooldownTicks = SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEALING_GRENADE_COOLDOWN_TICKS
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            val grenade = HealingGrenadeEntity(player.serverLevel(), player)
            grenade.shootFromRotation(
                player,
                player.xRot,
                player.yRot,
                0.0f,
                SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_SPEED,
                SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_INACCURACY
            )
            player.serverLevel().addFreshEntity(grenade)
            player.serverLevel().playSound(
                null,
                player.blockPosition(),
                DestinySounds.SOLAR_GRENADE_CAST,
                SoundSource.PLAYERS,
                MINECRAFT_CALIBRATION_GRENADE_CAST_SOUND_VOLUME,
                MINECRAFT_CALIBRATION_HEALING_GRENADE_CAST_SOUND_PITCH
            )
            return true
        }
    }

    val FIREBOLT_GRENADE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "solar_warlock_firebolt_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "火焰弹手雷",
        baseCooldownTicks = SolarWarlockAspectRules.MINECRAFT_CALIBRATION_FIREBOLT_GRENADE_COOLDOWN_TICKS
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            val grenade = FireboltGrenadeEntity(player.serverLevel(), player)
            grenade.shootFromRotation(
                player,
                player.xRot,
                player.yRot,
                0.0f,
                SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_SPEED,
                SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_INACCURACY
            )
            player.serverLevel().addFreshEntity(grenade)
            player.serverLevel().playSound(
                null,
                player.blockPosition(),
                DestinySounds.SOLAR_GRENADE_CAST,
                SoundSource.PLAYERS,
                MINECRAFT_CALIBRATION_GRENADE_CAST_SOUND_VOLUME,
                MINECRAFT_CALIBRATION_FIREBOLT_GRENADE_CAST_SOUND_PITCH
            )
            return true
        }
    }

    val FUSION_GRENADE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "solar_warlock_fusion_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "融合手雷",
        baseCooldownTicks = SolarWarlockAspectRules.MINECRAFT_CALIBRATION_FUSION_GRENADE_COOLDOWN_TICKS
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            val grenade = FusionGrenadeEntity(player.serverLevel(), player)
            grenade.shootFromRotation(
                player,
                player.xRot,
                player.yRot,
                0.0f,
                SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_SPEED,
                SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_INACCURACY
            )
            player.serverLevel().addFreshEntity(grenade)
            player.serverLevel().playSound(
                null,
                player.blockPosition(),
                DestinySounds.SOLAR_GRENADE_CAST,
                SoundSource.PLAYERS,
                MINECRAFT_CALIBRATION_GRENADE_CAST_SOUND_VOLUME,
                MINECRAFT_CALIBRATION_FUSION_GRENADE_CAST_SOUND_PITCH
            )
            return true
        }
    }

    val INCINERATOR_SNAP = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "solar_warlock_incinerator_snap"),
        slot = AbilitySlot.MELEE,
        displayName = "焚烧响指",
        baseCooldownTicks = 25 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            pendingSnaps.add(
                PendingSnap(
                    playerId = context.player.uuid,
                    remainingTicks = INCINERATOR_SNAP_RELEASE_DELAY_TICKS
                )
            )
            return true
        }
    }

    val HEALING_RIFT = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "solar_warlock_healing_rift"),
        slot = AbilitySlot.CLASS_ABILITY,
        displayName = "治疗裂隙",
        baseCooldownTicks = 82 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            val rift = HealingRiftEntity(
                DestinyEntities.HEALING_RIFT,
                player.serverLevel(),
                player.x,
                player.y,
                player.z,
                player
            )
            player.serverLevel().addFreshEntity(rift)
            DestinyStatusRules.applyRestoration(player, 80)
            player.serverLevel().playSound(null, player.blockPosition(), DestinySounds.HEALING_RIFT_CAST, SoundSource.PLAYERS, 0.65f, 1.0f)
            return true
        }
    }

    val WELL_OF_RADIANCE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "solar_warlock_well_of_radiance"),
        slot = AbilitySlot.SUPER,
        displayName = "光焰之井",
        baseCooldownTicks = 455 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val player = context.player
            val well = WellOfRadianceEntity(
                DestinyEntities.WELL_OF_RADIANCE,
                player.serverLevel(),
                player.x,
                player.y,
                player.z,
                player
            )
            player.serverLevel().addFreshEntity(well)
            DestinyStatusRules.applyRadiant(player, 220)
            DestinyStatusRules.applyRestoration(player, 120, level = 2)
            player.serverLevel().playSound(null, player.blockPosition(), DestinySounds.WELL_OF_RADIANCE_CAST, SoundSource.PLAYERS, 1.0f, 1.0f)
            return true
        }
    }

    val DAYBREAK = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "solar_warlock_daybreak"),
        slot = AbilitySlot.SUPER,
        displayName = "破晓",
        baseCooldownTicks = 455 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean = DaybreakRuntime.activate(context.player)
    }

    // Keep Solar Grenade last among grenade entries because the legacy fallback loadout
    // uses the final ability per slot; explicit subclass selections can choose all four.
    val ALL = listOf(
        HEALING_GRENADE,
        FIREBOLT_GRENADE,
        FUSION_GRENADE,
        SOLAR_GRENADE,
        INCINERATOR_SNAP,
        HEALING_RIFT,
        DAYBREAK,
        WELL_OF_RADIANCE
    )

    const val MINECRAFT_CALIBRATION_GRENADE_CAST_SOUND_VOLUME = 0.75f
    const val MINECRAFT_CALIBRATION_HEALING_GRENADE_CAST_SOUND_PITCH = 1.2f
    const val MINECRAFT_CALIBRATION_FIREBOLT_GRENADE_CAST_SOUND_PITCH = 1.05f
    const val MINECRAFT_CALIBRATION_FUSION_GRENADE_CAST_SOUND_PITCH = 0.9f

    /**
     * Server-side completion hook for Touch of Flame's Healing Grenade + Heat Rises clause.
     * The Heat Rises consumer calls this only after its grenade consumption succeeds.
     */
    fun applyHealingGrenadeHeatRisesTouchOfFlameBonus(player: ServerPlayer): Int {
        val selectedGrenade = PlayerDestinyDataApi.get(player)
            .subclassConfig.selectedAbilities[AbilitySlot.GRENADE]
        if (selectedGrenade != HEALING_GRENADE.id.toString() || !DestinyAspectRuntime.hasTouchOfFlame(player)) {
            return 0
        }

        val profile = SolarWarlockAspectRules.healingGrenadeProfile(hasTouchOfFlame = true)
        val radius = SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEALING_GRENADE_HEAT_RISES_ALLY_RADIUS
        val allies = player.serverLevel().getEntitiesOfClass(
            ServerPlayer::class.java,
            player.boundingBox.inflate(radius)
        ) { candidate ->
            candidate.isAlive && !candidate.isSpectator &&
                (candidate === player || player.isAlliedTo(candidate)) &&
                candidate.distanceToSqr(player) <= radius * radius
        }
        allies.forEach { ally ->
            DestinyStatusRules.applyRestoration(
                ally,
                profile.restorationDurationTicks,
                profile.restorationLevel,
                player
            )
        }
        return allies.size
    }

    private fun releaseIncineratorSnap(player: ServerPlayer) {
        val level = player.serverLevel()
        val yawOffsets = listOf(-22.0f, -11.0f, 0.0f, 11.0f, 22.0f)
        yawOffsets.forEach { offset ->
            val projectile = IncineratorSnapProjectile(level, player)
            projectile.shootFromRotation(
                player,
                player.xRot,
                player.yRot + offset,
                0.0f,
                1.65f,
                0.75f
            )
            level.addFreshEntity(projectile)
        }
        val look = player.lookAngle.normalize()
        val right = look.cross(Vec3(0.0, 1.0, 0.0)).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(-1.0, 0.0, 0.0)
        }
        val snapOrigin = Vec3(
            player.x,
            player.eyeY - 0.24,
            player.z
        ).add(look.scale(0.82)).add(right.scale(0.30))
        INCINERATOR_SNAP_CAST_VFX.forEach { (path, delayTicks) ->
            DestinyNetworking.broadcastWorldVfx(
                player = player,
                effectId = ResourceLocation.fromNamespaceAndPath(MOD_ID, path),
                origin = snapOrigin,
                yaw = player.yRot,
                pitch = player.xRot,
                delayTicks = delayTicks
            )
        }
        level.playSound(
            null,
            player.blockPosition(),
            DestinySounds.INCINERATOR_SNAP,
            SoundSource.PLAYERS,
            0.7f,
            1.0f
        )
    }
}
