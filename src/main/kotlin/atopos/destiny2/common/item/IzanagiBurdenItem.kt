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
        val direction = TaczProjectileDirection.validated(shooter.lookAngle, clientDirection)
        val bullet = ForgottenNameBulletEntity(level, shooter, shotProfile, direction, forgottenTraitsEnabled = false)
        level.addFreshEntity(bullet)
        WeaponAmmoState.consumeRound(stack, profile.magazineSize, profile.boltTicks)
        clearHonedEdge(stack)

        val recoil = profile.recoil
        val recoilPitch = recoil.pitchMin + level.random.nextFloat() * (recoil.pitchMax - recoil.pitchMin)
        val recoilYaw = recoil.yawMin + level.random.nextFloat() * (recoil.yawMax - recoil.yawMin)
        val feedback = DestinyNetworking.WeaponShotFeedbackPayload(
            shooter.uuid,
            bullet.x, bullet.y, bullet.z,
            bullet.x, bullet.y, bullet.z,
            ForgottenNameBulletEntity.IMPACT_MISS, false, false, false,
            true,
            recoilPitch, recoilYaw,
            recoil.kickDurationMs, recoil.recoverDurationMs, recoil.aimedMultiplier,
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
            val trigger = if (state.chamberEmpty) RELOAD_EMPTY else RELOAD_TACTICAL
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
            INSPECT
        )
        DestinyNetworking.broadcastWeaponThirdPersonAction(player, WeaponThirdPersonAction.INSPECT, 50)
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
                    RELOAD_EMPTY
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
        controllers.add(
            AnimationController(this, BASE_CONTROLLER, 4) { state ->
                state.setAndContinue(RawAnimation.begin().thenLoop(STATIC_IDLE))
            }
        )
        controllers.add(
            AnimationController(this, ACTION_CONTROLLER, 0) { PlayState.CONTINUE }
                .receiveTriggeredAnimations()
                .triggerableAnim(RELOAD_TACTICAL, RawAnimation.begin().thenPlayAndHold(RELOAD_TACTICAL))
                .triggerableAnim(RELOAD_EMPTY, RawAnimation.begin().thenPlayAndHold(RELOAD_EMPTY))
                .triggerableAnim(INSPECT, RawAnimation.begin().thenPlayAndHold(INSPECT))
        )
    }

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
        private const val INSPECT = "inspect"
        private const val HONED_EDGE_LOAD_TICKS = 60

        private fun fireCooldownTicks(roundsPerMinute: Int): Int =
            (1200.0 / roundsPerMinute.coerceAtLeast(1)).toInt().coerceAtLeast(1)

        private val MISSING_PROFILE = WeaponCombatProfile(
            ammoType = DestinyAmmoType.SPECIAL,
            baseDamage = IzanagiBurdenRules.BASE_DAMAGE,
            precisionMultiplier = IzanagiBurdenRules.PRECISION_MULTIPLIER,
            magazineSize = IzanagiBurdenRules.MAGAZINE_SIZE,
            reloadTicks = 64,
            roundsPerMinute = 90,
            boltTicks = 10
        )
        private val AIM_PROFILE = WeaponAimProfile(
            aimTimeSeconds = 0.35f,
            zoom = 2.0f,
            modelOffsetX = -0.10,
            modelOffsetY = 0.08,
            modelOffsetZ = -0.24,
            modelRotationX = -1.0f,
            aimedCameraAnimationScale = 0.65f
        )
    }
}
