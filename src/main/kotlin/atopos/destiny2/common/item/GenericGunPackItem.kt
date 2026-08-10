package atopos.destiny2.common.item

import atopos.destiny2.common.entity.ForgottenNameBulletEntity
import atopos.destiny2.common.gear.ArmorModRuntime
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.sound.DestinySounds
import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.DestinyWeaponDataRegistry
import atopos.destiny2.common.weapon.IzanagiBurdenRules
import atopos.destiny2.common.weapon.MonteCarloExoticRuntime
import atopos.destiny2.common.weapon.TaczGunPackItem
import atopos.destiny2.common.weapon.TaczProjectileDirection
import atopos.destiny2.common.weapon.WeaponAccuracyRuntime
import atopos.destiny2.common.weapon.WeaponRecoilMath
import atopos.destiny2.common.weapon.WeaponAimProfile
import atopos.destiny2.common.weapon.WeaponAmmoState
import atopos.destiny2.common.weapon.WeaponCombatProfile
import atopos.destiny2.common.weapon.WeaponFireMode
import atopos.destiny2.common.weapon.WeaponFireModeState
import atopos.destiny2.common.weapon.WeaponHudStatus
import atopos.destiny2.common.weapon.WeaponHudSync
import atopos.destiny2.common.weapon.WeaponThirdPersonAction
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * A single registered item for every data-driven gun definition.
 *
 * The stack stores only the gun id and runtime state. Combat values remain
 * server-authoritative in destiny_weapons JSON, while client model/display
 * data is resolved independently by the gun-pack renderer.
 */
class GenericGunPackItem(properties: Properties) : Item(properties), TaczGunPackItem, DestinyRangedWeapon {
    override val gunPackId: ResourceLocation
        get() = FALLBACK_ID

    override fun gunPackId(stack: ItemStack): ResourceLocation = id(stack)

    fun requestFire(
        level: Level,
        shooter: ServerPlayer,
        hand: InteractionHand,
        clientDirection: Vec3 = shooter.lookAngle
    ): Boolean {
        if (level.isClientSide || level !is ServerLevel) return false
        val stack = shooter.getItemInHand(hand)
        if (stack.item !== this || level.gameTime < nextShotTick(stack)) return false

        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (state.isReloading || state.isCycling) return false
        if (state.magazine <= 0) {
            requestReload(level, shooter, stack)
            return false
        }

        val weaponId = id(stack)
        val honedRounds = if (weaponId == IZANAGI_ID) honedRounds(stack) else 0
        val damageMultiplier = when (weaponId) {
            IZANAGI_ID -> IzanagiBurdenRules.damageMultiplier(honedRounds)
            MonteCarloExoticRuntime.ID -> MonteCarloExoticRuntime.damageMultiplier(shooter)
            else -> 1.0f
        }
        val rangeMultiplier = if (weaponId == IZANAGI_ID) {
            IzanagiBurdenRules.rangeMultiplier(honedRounds)
        } else {
            1.0
        }
        val shotProfile = profile.copy(
            baseDamage = profile.baseDamage * damageMultiplier,
            ballistics = profile.ballistics.copy(
                range = profile.ballistics.range * rangeMultiplier,
                distanceDamage = profile.ballistics.distanceDamage.map {
                    it.copy(
                        distance = it.distance * rangeMultiplier,
                        damage = it.damage * damageMultiplier
                    )
                }
            )
        )
        val centerDirection = WeaponAccuracyRuntime.shotDirection(
            shooter,
            weaponId,
            profile.accuracy,
            TaczProjectileDirection.validated(shooter.lookAngle, clientDirection),
            level.random
        )
        val bullets = List(profile.projectilesPerShot) {
            val direction = if (profile.projectilesPerShot == 1) {
                centerDirection
            } else {
                WeaponAccuracyRuntime.spread(
                    centerDirection,
                    profile.projectileSpreadDegrees,
                    level.random.nextFloat(),
                    level.random.nextFloat()
                )
            }
            ForgottenNameBulletEntity(
                level,
                shooter,
                shotProfile,
                direction,
                forgottenTraitsEnabled = false,
                sourceWeaponId = weaponId
            ).also(level::addFreshEntity)
        }
        val bullet = bullets.first()
        WeaponAmmoState.consumeRound(stack, profile.magazineSize, profile.boltTicks)
        clearHonedEdge(stack)
        setNextShotTick(stack, level.gameTime + fireCooldownTicks(profile.roundsPerMinute))

        val recoil = profile.recoil
        val recoilSequence = advanceRecoilSequence(
            stack,
            level.gameTime,
            recoilResetTicks(profile)
        )
        val recoilShot = WeaponRecoilMath.sampleShot(
            recoil,
            level.random.nextFloat(),
            level.random.nextFloat(),
            recoilSequence
        )
        val feedback = DestinyNetworking.WeaponShotFeedbackPayload(
            shooter.uuid,
            bullet.x, bullet.y, bullet.z,
            bullet.x, bullet.y, bullet.z,
            ForgottenNameBulletEntity.IMPACT_MISS, false, false, false,
            true,
            recoilShot.pitch, recoilShot.yaw,
            recoilShot.kickDurationMs, recoilShot.recoverDurationMs, recoil.aimedMultiplier,
            0.0,
            false
        )
        PlayerLookup.world(level).forEach { ServerPlayNetworking.send(it, feedback) }
        DestinyNetworking.broadcastWeaponThirdPersonAction(
            shooter,
            WeaponThirdPersonAction.SHOOT,
            profile.boltTicks.coerceAtLeast(6)
        )
        val fireSound = if (weaponId == THE_DEICIDE_ID) {
            DestinySounds.THE_DEICIDE_FIRE
        } else {
            DestinySounds.FORGOTTEN_NAME_FIRE
        }
        level.playSound(
            null,
            shooter.x,
            shooter.y,
            shooter.z,
            fireSound,
            SoundSource.PLAYERS,
            if (weaponId == THE_DEICIDE_ID || honedRounds >= 2) 1.0f else 0.82f,
            if (weaponId == THE_DEICIDE_ID) 1.0f else if (honedRounds >= 2) 0.78f else 0.9f
        )
        WeaponHudSync.syncNow(shooter)
        return true
    }

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide) return
        val player = entity as? ServerPlayer ?: return
        val profile = combatProfile(stack)
        if (isSelected) {
            WeaponAmmoState.tickReload(
                stack,
                player,
                profile.copy(reloadTicks = reloadTicks(player, profile.reloadTicks))
            )
        } else {
            WeaponAmmoState.cancelReload(stack, profile.magazineSize)
        }
    }

    override fun combatProfile(stack: ItemStack): WeaponCombatProfile =
        DestinyWeaponDataRegistry.profile(id(stack)) ?: MISSING_PROFILE

    override fun aimProfile(stack: ItemStack): WeaponAimProfile =
        if (id(stack) == IZANAGI_ID) IZANAGI_AIM_PROFILE else DEFAULT_AIM_PROFILE

    override fun requestReload(level: Level, player: ServerPlayer, stack: ItemStack): Boolean {
        if (level.isClientSide || level !is ServerLevel || stack.item !== this) return false
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (
            state.isReloading ||
            state.isCycling ||
            state.magazine >= profile.magazineSize ||
            WeaponAmmoState.isReloadBlocked(state, level.gameTime)
        ) return false
        if (!player.abilities.instabuild && WeaponAmmoState.reserveCount(player, profile.ammoType) <= 0) return false

        clearHonedEdge(stack)
        val missingRounds = (profile.magazineSize - state.magazine).coerceAtLeast(1)
        val availableRounds = if (player.abilities.instabuild) {
            missingRounds
        } else {
            WeaponAmmoState.reserveCount(player, profile.ammoType).coerceAtLeast(1)
        }
        val authoredReloadTicks = if (id(stack) == THE_DEICIDE_ID) {
            theDeicideReloadTicks(minOf(missingRounds, availableRounds))
        } else {
            profile.reloadTicks
        }
        val duration = reloadTicks(player, authoredReloadTicks)
        val started = WeaponAmmoState.startReload(
            stack,
            profile.magazineSize,
            duration,
            profile.emptyReloadBonusTicks,
            profile.reloadFeedFraction
        )
        if (started) {
            clearRecoilSequence(stack)
            player.inventoryMenu.broadcastChanges()
            WeaponHudSync.syncNow(player)
            DestinyNetworking.broadcastWeaponThirdPersonAction(player, WeaponThirdPersonAction.RELOAD, duration)
        }
        return started
    }

    override fun requestInspect(level: Level, player: ServerPlayer, stack: ItemStack): Boolean {
        if (level.isClientSide || level !is ServerLevel || stack.item !== this) return false
        val state = WeaponAmmoState.read(stack, combatProfile(stack).magazineSize)
        if (state.isReloading || state.isCycling) return false
        val inspectTicks = if (id(stack) == IZANAGI_ID) IZANAGI_INSPECT_TICKS else DEFAULT_INSPECT_TICKS
        DestinyNetworking.broadcastWeaponThirdPersonAction(
            player,
            WeaponThirdPersonAction.INSPECT,
            inspectTicks
        )
        return true
    }

    override fun cycleFireMode(player: ServerPlayer, stack: ItemStack): WeaponFireMode {
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (
            id(stack) == IZANAGI_ID &&
            !state.isReloading &&
            !state.isCycling &&
            IzanagiBurdenRules.canLoadHonedEdge(state.magazine) &&
            player.level().gameTime >= nextShotTick(stack)
        ) {
            setHonedRounds(stack, state.magazine)
            WeaponAmmoState.setMagazine(stack, profile.magazineSize, 1)
            setNextShotTick(stack, player.level().gameTime + HONED_EDGE_LOAD_TICKS)
            DestinyNetworking.broadcastWeaponThirdPersonAction(
                player,
                WeaponThirdPersonAction.RELOAD,
                HONED_EDGE_LOAD_TICKS
            )
            player.inventoryMenu.broadcastChanges()
            WeaponHudSync.syncNow(player)
        }
        return WeaponFireModeState.current(stack, profile)
    }

    override fun weaponHudStatus(player: ServerPlayer, stack: ItemStack): WeaponHudStatus {
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        val honed = honedRounds(stack)
        val weaponName = id(stack).path.replace('_', ' ')
        return WeaponHudStatus(
            weaponId = if (honed >= 2) "$weaponName x$honed" else weaponName,
            ammoType = profile.ammoType,
            magazine = state.magazine,
            capacity = profile.magazineSize,
            reserve = WeaponAmmoState.reserveCount(player, profile.ammoType),
            reloadRemaining = state.reloadRemaining,
            reloadTotal = state.reloadTotal,
            precisionMultiplier = profile.precisionMultiplier,
            reloadPhase = state.reloadPhase,
            fireMode = WeaponFireModeState.current(stack, profile),
            chamberEmpty = state.chamberEmpty,
            boltRemaining = state.boltRemaining,
            crosshair = profile.crosshair
        )
    }

    override fun getName(stack: ItemStack): Component {
        val weaponId = id(stack)
        return Component.translatable("gun.${weaponId.namespace}.${weaponId.path}")
    }

    private fun reloadTicks(player: ServerPlayer, baseTicks: Int): Int {
        val stats = DestinyStatsResolver.resolve(player)
        return (baseTicks * DestinyStatFormulas.weaponReloadTimeMultiplier(stats) * ArmorModRuntime.reloadMultiplier(player))
            .toInt()
            .coerceAtLeast(1)
    }

    private fun theDeicideReloadTicks(insertCount: Int): Int =
        kotlin.math.ceil(
            THE_DEICIDE_RELOAD_FIXED_SECONDS * TICKS_PER_SECOND +
                insertCount.coerceIn(1, THE_DEICIDE_MAGAZINE_SIZE) * THE_DEICIDE_INSERT_SECONDS * TICKS_PER_SECOND
        ).toInt()

    companion object {
        private val FALLBACK_ID = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name")
        val IZANAGI_ID: ResourceLocation =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "izanagis_burden")
        private val THE_DEICIDE_ID =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "the_deicide")
        private const val GUN_ID = "DestinyGunId"
        private const val NEXT_SHOT_TICK = "DestinyGunNextShotTick"
        private const val RECOIL_SEQUENCE = "DestinyGunRecoilSequence"
        private const val LAST_RECOIL_SHOT_TICK = "DestinyGunLastRecoilShotTick"
        private const val HONED_EDGE_ROUNDS = "IzanagiHonedEdgeRounds"
        private const val HONED_EDGE_LOAD_TICKS = 48
        private const val IZANAGI_INSPECT_TICKS = 88
        private const val DEFAULT_INSPECT_TICKS = 50
        private const val THE_DEICIDE_MAGAZINE_SIZE = 7
        private const val THE_DEICIDE_RELOAD_FIXED_SECONDS = 1.8166667
        private const val THE_DEICIDE_INSERT_SECONDS = 0.5666667
        private const val TICKS_PER_SECOND = 20.0

        fun stack(id: ResourceLocation): ItemStack =
            ItemStack(DestinyItems.GENERIC_GUN).also { stack ->
                stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
                    data.update { tag -> tag.putString(GUN_ID, id.toString()) }
                }
            }

        fun id(stack: ItemStack): ResourceLocation {
            val value = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getString(GUN_ID)
            return value?.takeIf(String::isNotBlank)
                ?.let { runCatching { ResourceLocation.parse(it) }.getOrNull() }
                ?: FALLBACK_ID
        }

        private fun nextShotTick(stack: ItemStack): Long =
            stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getLong(NEXT_SHOT_TICK) ?: 0L

        private fun setNextShotTick(stack: ItemStack, tick: Long) {
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
                data.update { tag -> tag.putLong(NEXT_SHOT_TICK, tick) }
            }
        }

        private fun advanceRecoilSequence(stack: ItemStack, shotTick: Long, resetAfterTicks: Long): Int {
            val snapshot = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()
            val hasPreviousShot = snapshot?.contains(LAST_RECOIL_SHOT_TICK) == true
            val lastShotTick = snapshot?.getLong(LAST_RECOIL_SHOT_TICK) ?: 0L
            val settled = !hasPreviousShot || shotTick - lastShotTick > resetAfterTicks
            val sequence = if (settled) 0 else snapshot?.getInt(RECOIL_SEQUENCE) ?: 0
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
                data.update { tag ->
                    tag.putInt(RECOIL_SEQUENCE, if (sequence == Int.MAX_VALUE) 0 else sequence + 1)
                    tag.putLong(LAST_RECOIL_SHOT_TICK, shotTick)
                }
            }
            return sequence
        }

        private fun clearRecoilSequence(stack: ItemStack) {
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
                data.update { tag ->
                    tag.remove(RECOIL_SEQUENCE)
                    tag.remove(LAST_RECOIL_SHOT_TICK)
                }
            }
        }

        private fun recoilResetTicks(profile: WeaponCombatProfile): Long {
            val cadenceTicks = fireCooldownTicks(profile.roundsPerMinute) * 3L
            val settleTicks = (profile.recoil.recoverDurationMs + 49L) / 50L +
                profile.accuracy.settleDelayTicks
            return maxOf(cadenceTicks, settleTicks, 6L)
        }

        private fun honedRounds(stack: ItemStack): Int =
            stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getInt(HONED_EDGE_ROUNDS)
                ?.let(IzanagiBurdenRules::honedRounds)
                ?: 0

        private fun setHonedRounds(stack: ItemStack, rounds: Int) {
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
                data.update { tag ->
                    tag.putInt(HONED_EDGE_ROUNDS, IzanagiBurdenRules.honedRounds(rounds))
                }
            }
        }

        private fun clearHonedEdge(stack: ItemStack) {
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
                data.update { tag -> tag.remove(HONED_EDGE_ROUNDS) }
            }
        }

        private fun fireCooldownTicks(roundsPerMinute: Int): Long =
            (1200.0 / roundsPerMinute.coerceAtLeast(1)).toLong().coerceAtLeast(1L)

        private val MISSING_PROFILE = WeaponCombatProfile(
            ammoType = DestinyAmmoType.PRIMARY,
            baseDamage = 1.0f,
            magazineSize = 1,
            reloadTicks = 20
        )
        private val DEFAULT_AIM_PROFILE = WeaponAimProfile(
            aimTimeSeconds = 0.25f,
            zoom = 1.5f
        )
        private val IZANAGI_AIM_PROFILE = WeaponAimProfile(
            aimTimeSeconds = 0.22f,
            zoom = 4.0f,
            aimedCameraAnimationScale = 0.65f,
            scopeOverlay = true
        )
    }
}
