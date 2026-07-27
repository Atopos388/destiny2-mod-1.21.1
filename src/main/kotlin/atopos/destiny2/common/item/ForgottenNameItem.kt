package atopos.destiny2.common.item

import atopos.destiny2.common.sound.DestinySounds
import atopos.destiny2.common.sound.WeaponSoundKeyframeBridge
import atopos.destiny2.common.particle.BedrockParticleKeyframeBridge
import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponAmmoState
import atopos.destiny2.common.weapon.WeaponAnimationTimingBridge
import atopos.destiny2.common.weapon.WeaponCombatProfile
import atopos.destiny2.common.weapon.WeaponAimProfile
import atopos.destiny2.common.weapon.WeaponHudStatus
import atopos.destiny2.common.weapon.ForgottenNameExoticRuntime
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.gear.ArmorModRuntime
import atopos.destiny2.common.weapon.DestinyWeaponDataRegistry
import atopos.destiny2.common.weapon.TaczProjectileDirection
import atopos.destiny2.common.weapon.TaczWeaponAnimationBridge
import atopos.destiny2.common.weapon.TaczWeaponAnimationContract
import atopos.destiny2.common.weapon.WeaponFireModeState
import atopos.destiny2.common.weapon.WeaponHudSync
import atopos.destiny2.common.weapon.WeaponThirdPersonAction
import atopos.destiny2.common.entity.ForgottenNameBulletEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import atopos.destiny2.common.network.DestinyNetworking
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.animatable.GeoItem
import software.bernie.geckolib.animation.AnimatableManager
import software.bernie.geckolib.animation.AnimationController
import software.bernie.geckolib.animation.PlayState
import software.bernie.geckolib.animation.RawAnimation
import software.bernie.geckolib.constant.DataTickets

class ForgottenNameItem(properties: Properties) : TaczGunPackWeaponItem(properties), DestinyRangedWeapon {
    override val gunPackId: ResourceLocation
        get() = WEAPON_ID
    fun requestFire(
        level: Level,
        shooter: ServerPlayer,
        hand: InteractionHand,
        clientDirection: Vec3 = shooter.lookAngle
    ): Boolean {
        if (level.isClientSide || level !is ServerLevel || shooter.cooldowns.isOnCooldown(this)) {
            return false
        }

        val stack = shooter.getItemInHand(hand)
        if (stack.item !== this) return false
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (state.isReloading || state.isCycling) return false
        if (state.magazine <= 0) {
            requestReload(level, shooter, stack)
            return false
        }

        val look = ForgottenNameExoticRuntime.adjustAim(
            shooter,
            TaczProjectileDirection.validated(shooter.lookAngle, clientDirection)
        )
        val bullet = ForgottenNameBulletEntity(level, shooter, profile, look)
        level.addFreshEntity(bullet)
        WeaponAmmoState.consumeRound(stack, profile.magazineSize, profile.boltTicks)

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
        triggerWeaponAnimation(
            shooter,
            stack,
            level,
            TaczWeaponAnimationContract.ACTION_CONTROLLER,
            nextShootTrigger(shooter.uuid, profile.boltTicks > 0)
        )
        DestinyNetworking.broadcastWeaponThirdPersonAction(
            shooter,
            WeaponThirdPersonAction.SHOOT,
            profile.boltTicks.coerceAtLeast(4)
        )

        level.playSound(
            null,
            shooter.x,
            shooter.y,
            shooter.z,
            DestinySounds.FORGOTTEN_NAME_FIRE,
            SoundSource.PLAYERS,
            0.72f,
            1.0f
        )

        return true
    }

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        val profile = combatProfile(stack)
        if (!level.isClientSide && isSelected && entity is ServerPlayer) {
            val animationId = GeoItem.getOrAssignId(stack, level as ServerLevel)
            if (selectedAnimationIds.put(entity.uuid, animationId) != animationId) {
                triggerWeaponAnimation(
                    entity,
                    stack,
                    level,
                    TaczWeaponAnimationContract.ACTION_CONTROLLER,
                    nextDrawTrigger(entity.uuid)
                )
                DestinyNetworking.broadcastWeaponThirdPersonAction(
                    entity,
                    WeaponThirdPersonAction.DRAW,
                    DRAW_THIRD_PERSON_TICKS
                )
            }
            WeaponAmmoState.tickReload(
                stack,
                entity,
                profile.copy(reloadTicks = armorThreeReloadTicks(entity, profile.reloadTicks))
            )
        } else if (!level.isClientSide && !isSelected) {
            if (entity is ServerPlayer) {
                val serverLevel = level as? ServerLevel
                if (serverLevel != null) {
                    val animationId = GeoItem.getOrAssignId(stack, serverLevel)
                    if (selectedAnimationIds.remove(entity.uuid, animationId)) {
                        triggerWeaponAnimation(
                            entity,
                            stack,
                            level,
                            TaczWeaponAnimationContract.ACTION_CONTROLLER,
                            TaczWeaponAnimationContract.PUT_AWAY
                        )
                        DestinyNetworking.broadcastWeaponThirdPersonAction(
                            entity,
                            WeaponThirdPersonAction.PUT_AWAY,
                            PUT_AWAY_THIRD_PERSON_TICKS
                        )
                    }
                }
            }
            WeaponAmmoState.cancelReload(stack, profile.magazineSize)
        }
    }

    override fun combatProfile(stack: ItemStack): WeaponCombatProfile =
        DestinyWeaponDataRegistry.profile(WEAPON_ID) ?: MISSING_PROFILE

    override fun aimProfile(stack: ItemStack): WeaponAimProfile = AIM_PROFILE

    override fun requestReload(level: Level, player: ServerPlayer, stack: ItemStack): Boolean {
        if (level.isClientSide) return false
        val serverLevel = level as? ServerLevel ?: return false
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (
            state.isReloading ||
            state.isCycling ||
            state.magazine >= profile.magazineSize ||
            WeaponAmmoState.isReloadBlocked(state, level.gameTime)
        ) return false
        if (!player.abilities.instabuild && WeaponAmmoState.reserveCount(player, profile.ammoType) <= 0) return false

        // Assign the GeckoLib stack identity before mutating and synchronising
        // reload state. Equipment swaps can produce a fresh stack without an
        // animatable id; broadcasting first would leave the client unable to
        // resolve the animation packet that follows, so none of its sound or
        // particle keyframes would run either.
        val animationId = GeoItem.getOrAssignId(stack, serverLevel)
        val started = WeaponAmmoState.startReload(
            stack,
            profile.magazineSize,
            armorThreeReloadTicks(player, profile.reloadTicks),
            profile.emptyReloadBonusTicks,
            profile.reloadFeedFraction
        )
        if (started) {
            // Make the authoritative reload duration available to the client before
            // GeckoLib evaluates the trigger and chooses its playback speed.
            player.inventoryMenu.broadcastChanges()
            WeaponHudSync.syncNow(player)
            triggerWeaponAnimation(
                player,
                stack,
                level,
                TaczWeaponAnimationContract.ACTION_CONTROLLER,
                nextReloadTrigger(player.uuid),
                animationId
            )
            val reloadDuration = WeaponAmmoState.read(stack, profile.magazineSize).reloadTotal
            DestinyNetworking.broadcastWeaponThirdPersonAction(
                player,
                WeaponThirdPersonAction.RELOAD,
                reloadDuration
            )
        }
        return started
    }

    override fun requestInspect(level: Level, player: ServerPlayer, stack: ItemStack): Boolean {
        if (level.isClientSide) return false
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        if (state.isReloading || state.isCycling) return false
        triggerWeaponAnimation(
            player,
            stack,
            level,
            TaczWeaponAnimationContract.ACTION_CONTROLLER,
            nextInspectTrigger(player.uuid)
        )
        DestinyNetworking.broadcastWeaponThirdPersonAction(
            player,
            WeaponThirdPersonAction.INSPECT,
            INSPECT_THIRD_PERSON_TICKS
        )
        return true
    }

    private fun armorThreeReloadTicks(player: ServerPlayer, baseTicks: Int): Int {
        val exoticTicks = ForgottenNameExoticRuntime.reloadTicks(player, baseTicks)
        val stats = DestinyStatsResolver.resolve(player)
        return (exoticTicks * DestinyStatFormulas.weaponReloadTimeMultiplier(stats) * ArmorModRuntime.reloadMultiplier(player)).toInt().coerceAtLeast(1)
    }

    override fun weaponHudStatus(player: ServerPlayer, stack: ItemStack): WeaponHudStatus {
        val profile = combatProfile(stack)
        val state = WeaponAmmoState.read(stack, profile.magazineSize)
        return WeaponHudStatus(
            "", profile.ammoType, state.magazine, profile.magazineSize,
            WeaponAmmoState.reserveCount(player, profile.ammoType), state.reloadRemaining,
            state.reloadTotal, profile.precisionMultiplier,
            state.reloadPhase, WeaponFireModeState.current(stack, profile), state.chamberEmpty,
            state.boltRemaining, profile.crosshair
        )
    }

    override fun registerControllers(controllers: AnimatableManager.ControllerRegistrar) {
        val drawAnimationA = RawAnimation.begin().thenPlayAndHold(TaczWeaponAnimationContract.DRAW)
        val drawAnimationB = RawAnimation.begin()
            .thenPlayAndHold(TaczWeaponAnimationContract.DRAW)
            .thenWait(0)
        val inspectAnimationA = RawAnimation.begin().thenPlayAndHold(TaczWeaponAnimationContract.INSPECT)
        val inspectAnimationB = RawAnimation.begin()
            .thenPlayAndHold(TaczWeaponAnimationContract.INSPECT)
            .thenWait(0)
        val reloadAnimationA = RawAnimation.begin().thenPlayAndHold(TaczWeaponAnimationContract.RELOAD_TACTICAL)
        val reloadAnimationB = RawAnimation.begin()
            .thenPlayAndHold(TaczWeaponAnimationContract.RELOAD_TACTICAL)
            .thenWait(0)
        var lockedReloadSpeed = 1.0f
        var reloadSpeedLocked = false

        controllers.add(
            AnimationController(this, TaczWeaponAnimationContract.BASE_CONTROLLER, 4) { state ->
                val animation = TaczWeaponAnimationBridge.locomotion(WEAPON_ID)
                state.setAndContinue(RawAnimation.begin().thenLoop(animation))
            }
        )
        controllers.add(
            AnimationController(this, TaczWeaponAnimationContract.ACTION_CONTROLLER, 0) { state ->
                val controller = state.controller
                val stack = state.getData(DataTickets.ITEMSTACK)
                val stackReloadTotal = if (stack != null && stack.item === this) {
                    val profile = combatProfile(stack)
                    WeaponAmmoState.read(stack, profile.magazineSize).reloadTotal
                } else {
                    0
                }
                val reloadTotal = stackReloadTotal.takeIf { it > 0 }
                    ?: WeaponAnimationTimingBridge.reloadTotalTicks(WEAPON_ID)
                val playingReload = controller.isPlayingTriggeredAnimation &&
                    (controller.currentRawAnimation === reloadAnimationA ||
                        controller.currentRawAnimation === reloadAnimationB)

                if (playingReload) {
                    if (!reloadSpeedLocked && reloadTotal > 0) {
                        lockedReloadSpeed = reloadAnimationSpeed(reloadTotal)
                        reloadSpeedLocked = true
                    } else if (!reloadSpeedLocked) {
                        // A selected hotbar stack is not guaranteed to receive CUSTOM_DATA
                        // before GeckoLib evaluates the trigger. Never freeze the authored
                        // reload on frame zero or restart an animation that is already playing;
                        // keep native timing until the authoritative duration becomes available,
                        // then accelerate the remaining keyframes continuously.
                        lockedReloadSpeed = reloadAnimationSpeed(reloadTotal)
                    }
                    state.setControllerSpeed(lockedReloadSpeed)
                } else {
                    lockedReloadSpeed = 1.0f
                    reloadSpeedLocked = false
                    state.setControllerSpeed(1.0f)
                }
                PlayState.CONTINUE
            }
                .receiveTriggeredAnimations()
                .triggerableAnim(
                    TaczWeaponAnimationContract.DRAW,
                    drawAnimationA
                )
                .triggerableAnim(
                    DRAW_TRIGGER_A,
                    drawAnimationA
                )
                .triggerableAnim(
                    DRAW_TRIGGER_B,
                    drawAnimationB
                )
                .triggerableAnim(
                    TaczWeaponAnimationContract.PUT_AWAY,
                    RawAnimation.begin().thenPlay(TaczWeaponAnimationContract.PUT_AWAY)
                )
                .triggerableAnim(
                    TaczWeaponAnimationContract.RELOAD_TACTICAL,
                    reloadAnimationA
                )
                .triggerableAnim(
                    TaczWeaponAnimationContract.RELOAD_EMPTY,
                    // The authored rig currently has one complete reload action.
                    // Keep the empty-reload gameplay trigger, but intentionally
                    // play the same visible action and keyframed sound.
                    reloadAnimationB
                )
                .triggerableAnim(
                    RELOAD_TRIGGER_A,
                    reloadAnimationA
                )
                .triggerableAnim(
                    RELOAD_TRIGGER_B,
                    // Reload gameplay can finish before a previous visual trigger.
                    // Alternate unequal RawAnimations so GeckoLib always resets to frame zero.
                    reloadAnimationB
                )
                .triggerableAnim(
                    TaczWeaponAnimationContract.INSPECT,
                    inspectAnimationA
                )
                .triggerableAnim(
                    TaczWeaponAnimationContract.INSPECT_EMPTY,
                    // The updated authored set has one complete inspect action.
                    inspectAnimationB
                )
                .triggerableAnim(
                    INSPECT_TRIGGER_A,
                    inspectAnimationA
                )
                .triggerableAnim(
                    INSPECT_TRIGGER_B,
                    inspectAnimationB
                )
                .triggerableAnim(
                    SHOOT_TRIGGER_A,
                    RawAnimation.begin().thenPlay(TaczWeaponAnimationContract.SHOOT)
                )
                .triggerableAnim(
                    SHOOT_TRIGGER_B,
                    // GeckoLib does not restart an in-flight trigger when the next
                    // RawAnimation compares equal. The zero-tick stage makes the
                    // alternating trigger distinct without changing visible timing.
                    RawAnimation.begin()
                        .thenPlay(TaczWeaponAnimationContract.SHOOT)
                        .thenWait(0)
                )
                .triggerableAnim(
                    SHOOT_AND_BOLT_TRIGGER_A,
                    RawAnimation.begin()
                        .thenPlay(TaczWeaponAnimationContract.SHOOT)
                        .thenPlay(TaczWeaponAnimationContract.BOLT)
                )
                .triggerableAnim(
                    SHOOT_AND_BOLT_TRIGGER_B,
                    RawAnimation.begin()
                        .thenPlay(TaczWeaponAnimationContract.SHOOT)
                        .thenPlay(TaczWeaponAnimationContract.BOLT)
                        .thenWait(0)
                )
                .setSoundKeyframeHandler { event ->
                    WeaponSoundKeyframeBridge.emit(event.keyframeData.sound)
                }
                .setParticleKeyframeHandler { event ->
                    val data = event.keyframeData
                    BedrockParticleKeyframeBridge.emit(data.effect, data.locator)
                }
        )
    }

    private fun triggerWeaponAnimation(
        player: ServerPlayer,
        stack: ItemStack,
        level: Level,
        controller: String,
        trigger: String,
        knownAnimationId: Long? = null
    ) {
        if (!level.isClientSide) {
            val serverLevel = level as? ServerLevel ?: return
            triggerAnim<ForgottenNameItem>(
                player,
                knownAnimationId ?: GeoItem.getOrAssignId(stack, serverLevel),
                controller,
                trigger
            )
        }
    }

    companion object {
        private fun fireCooldownTicks(roundsPerMinute: Int): Int =
            (1200.0 / roundsPerMinute.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        private val WEAPON_ID =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "forgotten_name")
        private val selectedAnimationIds = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Long>()
        private val lastShootTriggerSlot = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean>()
        private val lastReloadTriggerSlot = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean>()
        private val lastDrawTriggerSlot = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean>()
        private val lastInspectTriggerSlot = java.util.concurrent.ConcurrentHashMap<java.util.UUID, Boolean>()

        private fun nextShootTrigger(playerId: java.util.UUID, includesBolt: Boolean): String {
            val useFirstSlot = lastShootTriggerSlot.compute(playerId) { _, previous -> previous != true } == true
            return when {
                includesBolt && useFirstSlot -> SHOOT_AND_BOLT_TRIGGER_A
                includesBolt -> SHOOT_AND_BOLT_TRIGGER_B
                useFirstSlot -> SHOOT_TRIGGER_A
                else -> SHOOT_TRIGGER_B
            }
        }

        private fun nextReloadTrigger(playerId: java.util.UUID): String {
            val useFirstSlot = lastReloadTriggerSlot.compute(playerId) { _, previous -> previous != true } == true
            return if (useFirstSlot) RELOAD_TRIGGER_A else RELOAD_TRIGGER_B
        }

        private fun nextDrawTrigger(playerId: java.util.UUID): String {
            val useFirstSlot = lastDrawTriggerSlot.compute(playerId) { _, previous -> previous != true } == true
            return if (useFirstSlot) DRAW_TRIGGER_A else DRAW_TRIGGER_B
        }

        private fun nextInspectTrigger(playerId: java.util.UUID): String {
            val useFirstSlot = lastInspectTriggerSlot.compute(playerId) { _, previous -> previous != true } == true
            return if (useFirstSlot) INSPECT_TRIGGER_A else INSPECT_TRIGGER_B
        }

        internal fun reloadAnimationSpeed(reloadTotalTicks: Int): Float =
            if (reloadTotalTicks > 0) RELOAD_BASE_TICKS.toFloat() / reloadTotalTicks else 1.0f

        private const val SHOOT_TRIGGER_A = "shoot_a"
        private const val SHOOT_TRIGGER_B = "shoot_b"
        private const val SHOOT_AND_BOLT_TRIGGER_A = "shoot_and_bolt_a"
        private const val SHOOT_AND_BOLT_TRIGGER_B = "shoot_and_bolt_b"
        private const val RELOAD_TRIGGER_A = "reload_a"
        private const val RELOAD_TRIGGER_B = "reload_b"
        private const val DRAW_TRIGGER_A = "draw_a"
        private const val DRAW_TRIGGER_B = "draw_b"
        private const val INSPECT_TRIGGER_A = "inspect_a"
        private const val INSPECT_TRIGGER_B = "inspect_b"
        private const val RELOAD_BASE_TICKS = 34
        private const val DRAW_THIRD_PERSON_TICKS = 12
        private const val PUT_AWAY_THIRD_PERSON_TICKS = 8
        private const val INSPECT_THIRD_PERSON_TICKS = 48
        private val MISSING_PROFILE = WeaponCombatProfile(
            ammoType = DestinyAmmoType.PRIMARY,
            baseDamage = 0.0f,
            precisionMultiplier = 1.0f,
            magazineSize = 1,
            reloadTicks = 20
        )
        private val AIM_PROFILE = WeaponAimProfile(
            aimTimeSeconds = 0.25f,
            zoom = 1.35f,
            modelOffsetX = -0.18,
            modelOffsetY = 0.10,
            modelOffsetZ = -0.28,
            modelRotationX = -2.0f,
            aimedCameraAnimationScale = 0.72f
        )
    }
}
