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
import atopos.destiny2.common.weapon.WeaponAnimationTimingBridge
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.constant.DataTickets
import java.util.concurrent.atomic.AtomicInteger

class IzanagiBurdenItem(properties: Properties) : TaczGunPackWeaponItem(properties), DestinyRangedWeapon {
    override val gunPackId: ResourceLocation
        get() = WEAPON_ID
    fun requestFire(
        level: Level,
        shooter: ServerPlayer,
        hand: InteractionHand,
        clientDirection: Vec3 = shooter.lookAngle
    ): Boolean {
        if (level.isClientSide || level !is ServerLevel || shooter.cooldowns.isOnCooldown(this)) return false
        val stack = shooter.getItemInHand(hand)
        if (stack.item !== this) return false

        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (state.isReloading || state.isCycling) return false
        if (state.magazine <= 0) {
            requestReload(level, shooter, stack)
            return false
        }

        val honedRounds = honedRounds(stack)
        val multiplier = IzanagiBurdenRules.damageMultiplier(honedRounds)
        val rangeMultiplier = IzanagiBurdenRules.rangeMultiplier(honedRounds)
        val shotProfile = profile.copy(
            baseDamage = profile.baseDamage * multiplier,
            ballistics = profile.ballistics.copy(
                range = profile.ballistics.range * rangeMultiplier,
                distanceDamage = profile.ballistics.distanceDamage.map {
                    it.copy(distance = it.distance * rangeMultiplier, damage = it.damage * multiplier)
                }
            )
        )
        val direction = WeaponAccuracyRuntime.shotDirection(
            shooter,
            WEAPON_ID,
            profile.accuracy,
            TaczProjectileDirection.validated(shooter.lookAngle, clientDirection),
            level.random
        )
        val bullet = ForgottenNameBulletEntity(level, shooter, shotProfile, direction, forgottenTraitsEnabled = false)
        level.addFreshEntity(bullet)
        WeaponAmmoState.consumeRound(stack, profile.magazineSize, profile.boltTicks)
        clearHonedEdge(stack)
        triggerAnim<IzanagiBurdenItem>(
            shooter,
            GeoItem.getOrAssignId(stack, level),
            ACTION_CONTROLLER,
            alternatingTrigger(SHOOT_AND_BOLT_TRIGGER_A, SHOOT_AND_BOLT_TRIGGER_B)
        )

        val recoil = profile.recoil
        val recoilShot = WeaponRecoilMath.sampleShot(recoil, level.random.nextFloat(), level.random.nextFloat())
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
        shooter.cooldowns.addCooldown(this, fireCooldownTicks(profile.roundsPerMinute))
        DestinyNetworking.broadcastWeaponThirdPersonAction(shooter, WeaponThirdPersonAction.SHOOT, profile.boltTicks.coerceAtLeast(6))
        level.playSound(
            null,
            shooter.x,
            shooter.y,
            shooter.z,
            DestinySounds.FORGOTTEN_NAME_FIRE,
            SoundSource.PLAYERS,
            if (honedRounds >= 2) 1.0f else 0.82f,
            if (honedRounds >= 2) 0.78f else 0.9f
        )
        WeaponHudSync.syncNow(shooter)
        return true
    }

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide) return
        val player = entity as? ServerPlayer ?: return
        val profile = combatProfile(stack)
        if (isSelected) {
            WeaponAmmoState.tickReload(stack, player, profile.copy(reloadTicks = reloadTicks(player, profile.reloadTicks)))
        } else {
            WeaponAmmoState.cancelReload(stack, profile.magazineSize)
        }
    }

    override fun combatProfile(stack: ItemStack): WeaponCombatProfile =
        DestinyWeaponDataRegistry.profile(WEAPON_ID) ?: MISSING_PROFILE

    override fun aimProfile(stack: ItemStack): WeaponAimProfile = AIM_PROFILE

    override fun requestReload(level: Level, player: ServerPlayer, stack: ItemStack): Boolean {
        if (level.isClientSide || level !is ServerLevel) return false
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
        val duration = reloadTicks(player, profile.reloadTicks)
        val started = WeaponAmmoState.startReload(
            stack,
            profile.magazineSize,
            duration,
            profile.emptyReloadBonusTicks,
            profile.reloadFeedFraction
        )
        if (started) {
            val animationId = GeoItem.getOrAssignId(stack, level)
            val trigger = if (state.chamberEmpty) {
                alternatingTrigger(RELOAD_EMPTY_TRIGGER_A, RELOAD_EMPTY_TRIGGER_B)
            } else {
                alternatingTrigger(RELOAD_TACTICAL_TRIGGER_A, RELOAD_TACTICAL_TRIGGER_B)
            }
            triggerAnim<IzanagiBurdenItem>(player, animationId, ACTION_CONTROLLER, trigger)
            player.inventoryMenu.broadcastChanges()
            WeaponHudSync.syncNow(player)
            DestinyNetworking.broadcastWeaponThirdPersonAction(player, WeaponThirdPersonAction.RELOAD, duration)
        }
        return started
    }

    override fun requestInspect(level: Level, player: ServerPlayer, stack: ItemStack): Boolean {
        if (level.isClientSide || level !is ServerLevel) return false
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (state.isReloading || state.isCycling) return false
        triggerAnim<IzanagiBurdenItem>(
            player,
            GeoItem.getOrAssignId(stack, level),
            ACTION_CONTROLLER,
            alternatingTrigger(INSPECT_TRIGGER_A, INSPECT_TRIGGER_B)
        )
        DestinyNetworking.broadcastWeaponThirdPersonAction(player, WeaponThirdPersonAction.INSPECT, INSPECT_TICKS)
        return true
    }

    /**
     * The project's B key is the server-authoritative alternate weapon action.
     * Honed Edge compresses the currently loaded magazine into one stronger round.
     */
    override fun cycleFireMode(player: ServerPlayer, stack: ItemStack): WeaponFireMode {
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (
            !state.isReloading &&
            !state.isCycling &&
            IzanagiBurdenRules.canLoadHonedEdge(state.magazine) &&
            !player.cooldowns.isOnCooldown(this)
        ) {
            setHonedRounds(stack, state.magazine)
            WeaponAmmoState.setMagazine(stack, profile.magazineSize, 1)
            player.cooldowns.addCooldown(this, HONED_EDGE_LOAD_TICKS)
            val level = player.level() as? ServerLevel
            if (level != null) {
                triggerAnim<IzanagiBurdenItem>(
                    player,
                    GeoItem.getOrAssignId(stack, level),
                    ACTION_CONTROLLER,
                    alternatingTrigger(HONED_EDGE_TRIGGER_A, HONED_EDGE_TRIGGER_B)
                )
                DestinyNetworking.broadcastWeaponThirdPersonAction(
                    player,
                    WeaponThirdPersonAction.RELOAD,
                    HONED_EDGE_LOAD_TICKS
                )
            }
            player.inventoryMenu.broadcastChanges()
            WeaponHudSync.syncNow(player)
        }
        return WeaponFireModeState.current(stack, profile)
    }

    override fun weaponHudStatus(player: ServerPlayer, stack: ItemStack): WeaponHudStatus {
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        val honed = honedRounds(stack)
        return WeaponHudStatus(
            weaponId = if (honed >= 2) "伊邪那岐的重担 · 精磨利刃 ×$honed" else "伊邪那岐的重担",
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

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        val reloadTacticalA = RawAnimation.begin().thenPlay(RELOAD_TACTICAL)
        val reloadTacticalB = RawAnimation.begin().thenPlay(RELOAD_TACTICAL).thenWait(0)
        val reloadEmptyA = RawAnimation.begin().thenPlay(RELOAD_EMPTY)
        val reloadEmptyB = RawAnimation.begin().thenPlay(RELOAD_EMPTY).thenWait(0)
        val reloadHonedEdgeA = RawAnimation.begin().thenPlay(RELOAD_HONED_EDGE)
        val reloadHonedEdgeB = RawAnimation.begin().thenPlay(RELOAD_HONED_EDGE).thenWait(0)
        val inspectA = RawAnimation.begin().thenPlay(INSPECT)
        val inspectB = RawAnimation.begin().thenPlay(INSPECT).thenWait(0)
        val shootAndBoltA = RawAnimation.begin().thenPlay(SHOOT).thenPlay(BOLT)
        val shootAndBoltB = RawAnimation.begin().thenPlay(SHOOT).thenPlay(BOLT).thenWait(0)
        var lockedAction: RawAnimation? = null
        var lockedActionSpeed = 1.0f

        controllers.add(
            AnimationController(this, BASE_CONTROLLER, 4) { state ->
                state.setAndContinue(RawAnimation.begin().thenLoop(STATIC_IDLE))
            }
        )
        controllers.add(
            AnimationController(this, ACTION_CONTROLLER, 0) { state ->
                val controller = state.controller
                val current = controller.currentRawAnimation
                val authoredTicks = when {
                    current === reloadTacticalA || current === reloadTacticalB -> RELOAD_TACTICAL_AUTHORED_TICKS
                    current === reloadEmptyA || current === reloadEmptyB -> RELOAD_EMPTY_AUTHORED_TICKS
                    current === reloadHonedEdgeA || current === reloadHonedEdgeB -> HONED_EDGE_LOAD_TICKS.toFloat()
                    else -> 0.0f
                }
                if (controller.isPlayingTriggeredAnimation && authoredTicks > 0.0f) {
                    if (current !== lockedAction) {
                        val stack = state.getData(DataTickets.ITEMSTACK)
                        val stackReloadTotal = if (stack != null && stack.item === this) {
                            val profile = combatProfile(stack)
                            WeaponAmmoState.read(stack, profile.magazineSize).reloadTotal
                        } else {
                            0
                        }
                        val authoritativeTicks = when {
                            current === reloadHonedEdgeA || current === reloadHonedEdgeB -> HONED_EDGE_LOAD_TICKS
                            else -> stackReloadTotal.takeIf { it > 0 }
                                ?: WeaponAnimationTimingBridge.reloadTotalTicks(WEAPON_ID)
                        }
                        lockedActionSpeed = if (authoritativeTicks > 0) {
                            authoredTicks / authoritativeTicks.toFloat()
                        } else {
                            1.0f
                        }
                        lockedAction = current
                    }
                    state.setControllerSpeed(lockedActionSpeed)
                } else {
                    lockedAction = null
                    lockedActionSpeed = 1.0f
                    state.setControllerSpeed(1.0f)
                }
                PlayState.CONTINUE
            }
                .receiveTriggeredAnimations()
                .triggerableAnim(RELOAD_TACTICAL_TRIGGER_A, reloadTacticalA)
                .triggerableAnim(RELOAD_TACTICAL_TRIGGER_B, reloadTacticalB)
                .triggerableAnim(RELOAD_EMPTY_TRIGGER_A, reloadEmptyA)
                .triggerableAnim(RELOAD_EMPTY_TRIGGER_B, reloadEmptyB)
                .triggerableAnim(HONED_EDGE_TRIGGER_A, reloadHonedEdgeA)
                .triggerableAnim(HONED_EDGE_TRIGGER_B, reloadHonedEdgeB)
                .triggerableAnim(INSPECT_TRIGGER_A, inspectA)
                .triggerableAnim(INSPECT_TRIGGER_B, inspectB)
                .triggerableAnim(SHOOT_AND_BOLT_TRIGGER_A, shootAndBoltA)
                .triggerableAnim(SHOOT_AND_BOLT_TRIGGER_B, shootAndBoltB)
        )
    }

    private fun alternatingTrigger(first: String, second: String): String =
        if ((actionTriggerSequence.getAndIncrement() and 1) == 0) first else second

    private fun reloadTicks(player: ServerPlayer, baseTicks: Int): Int {
        val stats = DestinyStatsResolver.resolve(player)
        return (baseTicks * DestinyStatFormulas.weaponReloadTimeMultiplier(stats) * ArmorModRuntime.reloadMultiplier(player))
            .toInt()
            .coerceAtLeast(1)
    }

    private fun honedRounds(stack: ItemStack): Int =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getInt(HONED_EDGE_ROUNDS)
            ?.let(IzanagiBurdenRules::honedRounds)
            ?: 0

    private fun setHonedRounds(stack: ItemStack, rounds: Int) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
            data.update { tag -> tag.putInt(HONED_EDGE_ROUNDS, IzanagiBurdenRules.honedRounds(rounds)) }
        }
    }

    private fun clearHonedEdge(stack: ItemStack) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data ->
            data.update { tag -> tag.remove(HONED_EDGE_ROUNDS) }
        }
    }

    companion object {
        private val WEAPON_ID = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "izanagis_burden")
        private const val HONED_EDGE_ROUNDS = "IzanagiHonedEdgeRounds"
        private const val BASE_CONTROLLER = "base_controller"
        private const val ACTION_CONTROLLER = "action_controller"
        private const val STATIC_IDLE = "static_idle"
        private const val RELOAD_TACTICAL = "reload_tactical"
        private const val RELOAD_EMPTY = "reload_empty"
        private const val RELOAD_HONED_EDGE = "reload_honed_edge"
        private const val INSPECT = "inspect"
        private const val SHOOT = "shoot"
        private const val BOLT = "bolt"
        private const val RELOAD_TACTICAL_TRIGGER_A = "reload_tactical_a"
        private const val RELOAD_TACTICAL_TRIGGER_B = "reload_tactical_b"
        private const val RELOAD_EMPTY_TRIGGER_A = "reload_empty_a"
        private const val RELOAD_EMPTY_TRIGGER_B = "reload_empty_b"
        private const val HONED_EDGE_TRIGGER_A = "reload_honed_edge_a"
        private const val HONED_EDGE_TRIGGER_B = "reload_honed_edge_b"
        private const val INSPECT_TRIGGER_A = "inspect_a"
        private const val INSPECT_TRIGGER_B = "inspect_b"
        private const val SHOOT_AND_BOLT_TRIGGER_A = "shoot_and_bolt_a"
        private const val SHOOT_AND_BOLT_TRIGGER_B = "shoot_and_bolt_b"
        private const val HONED_EDGE_LOAD_TICKS = 48
        private const val INSPECT_TICKS = 88
        private const val RELOAD_TACTICAL_AUTHORED_TICKS = 51.666f
        private const val RELOAD_EMPTY_AUTHORED_TICKS = 61.666f
        private val actionTriggerSequence = AtomicInteger()

        private fun fireCooldownTicks(roundsPerMinute: Int): Int =
            (1200.0 / roundsPerMinute.coerceAtLeast(1)).toInt().coerceAtLeast(1)

        private val MISSING_PROFILE = WeaponCombatProfile(
            ammoType = DestinyAmmoType.SPECIAL,
            baseDamage = IzanagiBurdenRules.BASE_DAMAGE,
            precisionMultiplier = IzanagiBurdenRules.PRECISION_MULTIPLIER,
            magazineSize = IzanagiBurdenRules.MAGAZINE_SIZE,
            reloadTicks = 52,
            roundsPerMinute = 90,
            boltTicks = 13
        )
        private val AIM_PROFILE = WeaponAimProfile(
            aimTimeSeconds = 0.22f,
            zoom = 4.0f,
            modelOffsetX = -0.10,
            modelOffsetY = 0.08,
            modelOffsetZ = -0.24,
            modelRotationX = -1.0f,
            aimedCameraAnimationScale = 0.65f,
            scopeOverlay = true
        )
    }
}
