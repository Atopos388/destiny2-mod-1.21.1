package atopos.destiny2.client

import atopos.destiny2.Destiny2MODClient
import atopos.destiny2.client.gui.DestinyHUDState
import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.client.combat.QuickMeleeAimClient
import atopos.destiny2.client.gui.DestinyLDLibEditor
import atopos.destiny2.client.gui.DestinyAspectScreen
import atopos.destiny2.client.gui.DestinyPerkBuffState
import atopos.destiny2.client.gui.DestinyNavigationOverlay
import atopos.destiny2.client.weapon.DestinyWeaponAimClient
import atopos.destiny2.client.weapon.DestinyWeaponThirdPersonClient
import atopos.destiny2.client.weapon.DestinyWeaponFeedbackClient
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponFireMode
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.world.phys.Vec3

object DestinyInputHandler {
    private const val GAMBLER_DODGE_ABILITY_ID = "destiny2-mod:void_hunter_gambler_dodge"
    private const val THUNDERCLAP_ABILITY_ID = "destiny2-mod:arc_titan_thunderclap"
    private const val THRUSTER_ABILITY_ID = "destiny2-mod:arc_titan_thruster"
    private const val EAGER_EDGE_ID = "destiny2-mod:eager_edge"
    private const val HEAT_RISES_ASPECT_ID = "destiny2-mod:aspect_heat_rises"
    private const val ICARUS_DASH_ASPECT_ID = "destiny2-mod:aspect_icarus_dash"
    private const val TRAPPERS_AMBUSH_ASPECT_ID = "destiny2-mod:aspect_trappers_ambush"
    private const val HEAT_RISES_BUFF_ID = "destiny2-mod:aspect/heat_rises"
    private const val DAYBREAK_BUFF_ID = "destiny2-mod:super/daybreak"
    private const val HEAT_RISES_CHARGE_TICKS = 40
    private const val HEAT_RISES_DOUBLE_TAP_WINDOW_TICKS = 10L
    private const val HEAT_RISES_ASCENT_DISTANCE = 6.0
    private const val HEAT_RISES_ASCENT_VELOCITY = 0.24
    private const val HEAT_RISES_HOVER_VELOCITY = 0.08
    private var jumpWasDown = false
    private var eagerEdgeExtraJumpUsed = false
    private var heatRisesExtraJumpUsed = false
    private var heatRisesTargetY: Double? = null
    private var lastHeatRisesJumpPressTick = Long.MIN_VALUE
    private var clientTick = 0L
    private var grenadeWasDown = false
    private var grenadeHoldTicks = 0
    private var grenadeConsumedForHeatRises = false
    private var crouchWasDown = false
    private var meleeWasDown = false
    private var thunderclapChargeStarted = false

    fun applyEagerEdgePush(directionX: Double, directionZ: Double) {
        val player = net.minecraft.client.Minecraft.getInstance().player ?: return
        val motion = player.deltaMovement
        player.deltaMovement = motion.add(directionX * 2.5, 0.12, directionZ * 2.5)
        player.hasImpulse = true
    }

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            DestinyWeaponAimClient.tick(client)
            DestinyWeaponThirdPersonClient.tick(client)
            DestinyWeaponFeedbackClient.tick(client)
            DestinyNavigationOverlay.update(client)
            val player = client.player ?: return@register
            clientTick++

            if (CinematicCameraClient.isActive()) {
                drainGameplayClicks()
                jumpWasDown = client.options.keyJump.isDown
                crouchWasDown = client.options.keyShift.isDown
                grenadeWasDown = DestinyKeybindings.GRENADE_KEY.isDown
                meleeWasDown = DestinyKeybindings.MELEE_KEY.isDown
                return@register
            }

            DestinyLDLibEditor.openIfRequested(client)
            DestinyAspectScreen.openIfRequested(client)

            val heatRisesSelected = DestinyHUDState.selectedAspectIds.contains(HEAT_RISES_ASPECT_ID)
            val icarusDashSelected = DestinyHUDState.selectedAspectIds.contains(ICARUS_DASH_ASPECT_ID)
            val trappersAmbushSelected = DestinyHUDState.selectedAspectIds.contains(TRAPPERS_AMBUSH_ASPECT_ID)
            GuardianJumpClient.tick(player, DestinyHUDState.classId)
            val heatRisesActive = heatRisesSelected && DestinyPerkBuffState.isActive(HEAT_RISES_BUFF_ID, "炙热升腾")
            if (player.onGround()) {
                eagerEdgeExtraJumpUsed = false
                heatRisesExtraJumpUsed = false
                heatRisesTargetY = null
            } else if (!heatRisesSelected) {
                heatRisesExtraJumpUsed = false
                heatRisesTargetY = null
            }
            if (!heatRisesActive && heatRisesTargetY != null) {
                stopLocalHeatRisesFlight(player)
            }
            if (!heatRisesActive) {
                lastHeatRisesJumpPressTick = Long.MIN_VALUE
            }

            val crouchDown = client.options.keyShift.isDown
            if (
                crouchDown &&
                !crouchWasDown &&
                client.screen == null &&
                trappersAmbushSelected &&
                !player.onGround() &&
                !player.abilities.flying &&
                !player.isFallFlying &&
                !player.isPassenger
            ) {
                ClientPlayNetworking.send(DestinyNetworking.TrappersAmbushPayload())
            }
            crouchWasDown = crouchDown

            while (DestinyKeybindings.HUD_EDITOR_KEY.consumeClick()) {
                if (client.screen == null) {
                    DestinyLDLibEditor.requestOpen(DestinyLDLibEditor.Target.ABILITY_HUD)
                }
            }

            while (DestinyKeybindings.LDLIB_EDITOR_KEY.consumeClick()) {
                if (client.screen == null) {
                    DestinyLDLibEditor.requestOpen()
                }
            }


            while (DestinyKeybindings.RELOAD_WEAPON_KEY.consumeClick()) {
                if (client.screen == null) {
                    ClientPlayNetworking.send(DestinyNetworking.ReloadWeaponPayload())
                }
            }

            while (DestinyKeybindings.FIRE_MODE_KEY.consumeClick()) {
                if (client.screen == null && player.mainHandItem.item is DestinyRangedWeapon) {
                    ClientPlayNetworking.send(DestinyNetworking.CycleFireModePayload())
                }
            }

            while (DestinyKeybindings.INSPECT_WEAPON_KEY.consumeClick()) {
                if (client.screen == null && player.mainHandItem.item is DestinyRangedWeapon) {
                    ClientPlayNetworking.send(DestinyNetworking.InspectWeaponPayload())
                }
            }

            val weaponState = atopos.destiny2.client.gui.DestinyWeaponHUDState.snapshot
            val daybreakActive = DestinyPerkBuffState.isActive(DAYBREAK_BUFF_ID, "破晓")
            if (
                client.screen == null &&
                client.options.keyAttack.isDown &&
                daybreakActive
            ) {
                val look = player.lookAngle
                ClientPlayNetworking.send(
                    DestinyNetworking.DaybreakFirePayload(look.x, look.y, look.z)
                )
            } else if (
                client.screen == null &&
                client.options.keyAttack.isDown &&
                player.mainHandItem.item is DestinyRangedWeapon &&
                weaponState?.fireMode == WeaponFireMode.AUTO &&
                weaponState.reloadRemaining <= 0
            ) {
                val look = player.lookAngle
                ClientPlayNetworking.send(
                    DestinyNetworking.FireWeaponPayload(net.minecraft.world.InteractionHand.MAIN_HAND, look.x, look.y, look.z)
                )
            }

            val jumpDown = client.options.keyJump.isDown
            if (jumpDown && !jumpWasDown && client.screen == null) {
                var jumpHandled = false
                if (!player.onGround() && !eagerEdgeExtraJumpUsed && DestinyPerkBuffState.isActive(EAGER_EDGE_ID, "二段跳")) {
                    val motion = player.deltaMovement
                    player.deltaMovement = Vec3(motion.x, 0.56, motion.z)
                    player.fallDistance = 0.0f
                    player.hasImpulse = true
                    eagerEdgeExtraJumpUsed = true
                    DestinyPerkBuffState.remove(EAGER_EDGE_ID)
                    ClientPlayNetworking.send(DestinyNetworking.EagerEdgeJumpPayload())
                    jumpHandled = true
                }
                if (!jumpHandled && heatRisesActive && canUseHeatRises(player)) {
                    val isSecondPress = lastHeatRisesJumpPressTick != Long.MIN_VALUE &&
                        clientTick - lastHeatRisesJumpPressTick <= HEAT_RISES_DOUBLE_TAP_WINDOW_TICKS
                    if (!player.onGround() && !heatRisesExtraJumpUsed && isSecondPress) {
                        heatRisesExtraJumpUsed = true
                        heatRisesTargetY = player.y + HEAT_RISES_ASCENT_DISTANCE
                        applyLocalHeatRisesFlight(player)
                        ClientPlayNetworking.send(
                            DestinyNetworking.HeatRisesMovementPayload(DestinyNetworking.HeatRisesMovementPayload.DOUBLE_JUMP)
                        )
                        lastHeatRisesJumpPressTick = Long.MIN_VALUE
                    } else {
                        lastHeatRisesJumpPressTick = clientTick
                    }
                }
                if (!jumpHandled && !heatRisesActive && GuardianJumpClient.press(player, DestinyHUDState.classId)) {
                    ClientPlayNetworking.send(
                        GuardianJumpClient.payload()
                    )
                }
            }
            if (jumpDown && heatRisesExtraJumpUsed && heatRisesActive && client.screen == null && canUseHeatRises(player)) {
                applyLocalHeatRisesFlight(player)
                ClientPlayNetworking.send(
                    DestinyNetworking.HeatRisesMovementPayload(DestinyNetworking.HeatRisesMovementPayload.HOLD)
                )
            }
            if (!jumpDown && jumpWasDown && heatRisesTargetY != null) {
                stopLocalHeatRisesFlight(player)
                ClientPlayNetworking.send(
                    DestinyNetworking.HeatRisesMovementPayload(DestinyNetworking.HeatRisesMovementPayload.RELEASE)
                )
            }
            if (jumpDown && !heatRisesActive && client.screen == null && GuardianJumpClient.hold(player, DestinyHUDState.classId)) {
                ClientPlayNetworking.send(
                    GuardianJumpClient.payload(DestinyNetworking.GuardianJumpPayload.HOLD)
                )
            }
            if (!jumpDown && jumpWasDown && GuardianJumpClient.release()) {
                ClientPlayNetworking.send(
                    GuardianJumpClient.payload(DestinyNetworking.GuardianJumpPayload.RELEASE)
                )
            }
            jumpWasDown = jumpDown

            handleGrenadeInput(client, heatRisesSelected)

            handleMeleeInput(client)

            while (DestinyKeybindings.ICARUS_DASH_KEY.consumeClick()) {
                if (client.screen == null && icarusDashSelected && !player.onGround()) {
                    ClientPlayNetworking.send(DestinyNetworking.IcarusDashPayload(dodgeDirection()))
                }
            }

            while (DestinyKeybindings.CLASS_ABILITY_KEY.consumeClick()) {
                val selectedClassAbility = DestinyHUDState.selectedAbilityId(DestinyNetworking.ABILITY_CLASS)
                val isDirectionalClassAbility =
                    selectedClassAbility == GAMBLER_DODGE_ABILITY_ID || selectedClassAbility == THRUSTER_ABILITY_ID
                if (isDirectionalClassAbility || checkCooldown(DestinyNetworking.ABILITY_CLASS)) {
                    sendCastPacket(DestinyNetworking.ABILITY_CLASS, dodgeDirection())
                }
            }

            while (DestinyKeybindings.SUPER_ABILITY_KEY.consumeClick()) {
                if (checkCooldown(DestinyNetworking.ABILITY_SUPER)) {
                    sendCastPacket(DestinyNetworking.ABILITY_SUPER)
                }
            }
        }
    }

    private fun drainGameplayClicks() {
        val keys = arrayOf(
            DestinyKeybindings.RELOAD_WEAPON_KEY,
            DestinyKeybindings.FIRE_MODE_KEY,
            DestinyKeybindings.INSPECT_WEAPON_KEY,
            DestinyKeybindings.GRENADE_KEY,
            DestinyKeybindings.MELEE_KEY,
            DestinyKeybindings.CLASS_ABILITY_KEY,
            DestinyKeybindings.SUPER_ABILITY_KEY,
            DestinyKeybindings.ICARUS_DASH_KEY
        )
        keys.forEach { key -> while (key.consumeClick()) { } }
    }

    private fun checkCooldown(abilityType: Int): Boolean {
        val cooldownData = Destiny2MODClient.clientCooldowns[abilityType]
        if (cooldownData != null) {
            val endTime = cooldownData.first
            if (System.currentTimeMillis() < endTime) {
                return false
            }
        }
        return true
    }

    private fun sendCastPacket(abilityType: Int, extraData: Int = 0) {
        val payload = DestinyNetworking.CastAbilityPayload(abilityType, extraData)
        ClientPlayNetworking.send(payload)
    }

    private fun handleMeleeInput(client: net.minecraft.client.Minecraft) {
        val key = DestinyKeybindings.MELEE_KEY
        var pressedThisTick = false
        while (key.consumeClick()) {
            pressedThisTick = true
        }
        val meleeDown = key.isDown
        val thunderclapSelected =
            DestinyHUDState.selectedAbilityId(DestinyNetworking.ABILITY_MELEE) == THUNDERCLAP_ABILITY_ID

        if (!thunderclapSelected) {
            if (thunderclapChargeStarted) {
                ClientPlayNetworking.send(DestinyNetworking.ReleaseArcTitanThunderclapPayload())
                thunderclapChargeStarted = false
            }
            if (pressedThisTick) {
                if (!checkCooldown(DestinyNetworking.ABILITY_MELEE)) {
                    QuickMeleeAimClient.predict(client)
                }
                sendCastPacket(DestinyNetworking.ABILITY_MELEE)
            }
            meleeWasDown = meleeDown
            return
        }

        if (pressedThisTick && !meleeWasDown) {
            val chargedMeleeReady = checkCooldown(DestinyNetworking.ABILITY_MELEE)
            if (!chargedMeleeReady) {
                QuickMeleeAimClient.predict(client)
            }
            sendCastPacket(DestinyNetworking.ABILITY_MELEE)
            thunderclapChargeStarted = chargedMeleeReady
        }

        if (!meleeDown && (meleeWasDown || pressedThisTick) && thunderclapChargeStarted) {
            ClientPlayNetworking.send(DestinyNetworking.ReleaseArcTitanThunderclapPayload())
            thunderclapChargeStarted = false
        }
        meleeWasDown = meleeDown
    }

    private fun handleGrenadeInput(client: net.minecraft.client.Minecraft, heatRisesSelected: Boolean) {
        val key = DestinyKeybindings.GRENADE_KEY
        if (client.screen != null) {
            if (grenadeWasDown) sendHeatRisesHoldAction(DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.RELEASE)
            while (key.consumeClick()) { }
            grenadeWasDown = false
            grenadeHoldTicks = 0
            grenadeConsumedForHeatRises = false
            return
        }

        if (!heatRisesSelected) {
            if (grenadeWasDown) sendHeatRisesHoldAction(DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.RELEASE)
            grenadeWasDown = key.isDown
            grenadeHoldTicks = 0
            grenadeConsumedForHeatRises = false
            while (key.consumeClick()) {
                if (checkCooldown(DestinyNetworking.ABILITY_GRENADE)) {
                    sendCastPacket(DestinyNetworking.ABILITY_GRENADE)
                }
            }
            return
        }

        // Drain KeyMapping's click queue: with Heat Rises equipped, release is
        // what distinguishes a normal throw from a two-second consumption.
        var pressedThisTick = false
        while (key.consumeClick()) {
            pressedThisTick = true
        }
        val grenadeDown = key.isDown
        if ((grenadeDown || pressedThisTick) && !grenadeWasDown) {
            grenadeHoldTicks = 0
            grenadeConsumedForHeatRises = false
            sendHeatRisesHoldAction(DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.START)
        }

        // Preserve very short taps whose press and release both occur between
        // two client ticks; they are normal grenade throws, never a charge.
        if (pressedThisTick && !grenadeDown && !grenadeWasDown) {
            sendHeatRisesHoldAction(DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.RELEASE)
            if (checkCooldown(DestinyNetworking.ABILITY_GRENADE)) {
                sendCastPacket(DestinyNetworking.ABILITY_GRENADE)
            }
            return
        }

        if (grenadeDown && !grenadeConsumedForHeatRises && checkCooldown(DestinyNetworking.ABILITY_GRENADE)) {
            grenadeHoldTicks++
            if (grenadeHoldTicks >= HEAT_RISES_CHARGE_TICKS) {
                sendHeatRisesHoldAction(DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.COMPLETE)
                grenadeConsumedForHeatRises = true
            }
        } else if (!grenadeDown && grenadeWasDown) {
            sendHeatRisesHoldAction(DestinyNetworking.ConsumeGrenadeForHeatRisesPayload.RELEASE)
            if (!grenadeConsumedForHeatRises && checkCooldown(DestinyNetworking.ABILITY_GRENADE)) {
                sendCastPacket(DestinyNetworking.ABILITY_GRENADE)
            }
            grenadeHoldTicks = 0
            grenadeConsumedForHeatRises = false
        }
        grenadeWasDown = grenadeDown
    }

    private fun sendHeatRisesHoldAction(action: Int) {
        ClientPlayNetworking.send(DestinyNetworking.ConsumeGrenadeForHeatRisesPayload(action))
    }

    private fun applyLocalHeatRisesFlight(player: net.minecraft.client.player.LocalPlayer) {
        val targetY = heatRisesTargetY ?: return
        val motion = player.deltaMovement
        val verticalVelocity = if (player.y < targetY) {
            HEAT_RISES_ASCENT_VELOCITY
        } else {
            HEAT_RISES_HOVER_VELOCITY
        }
        player.deltaMovement = Vec3(motion.x, verticalVelocity, motion.z)
        player.fallDistance = 0.0f
        player.hasImpulse = true
    }

    private fun stopLocalHeatRisesFlight(player: net.minecraft.client.player.LocalPlayer) {
        heatRisesTargetY = null
        val motion = player.deltaMovement
        player.deltaMovement = Vec3(motion.x, motion.y.coerceAtMost(0.0), motion.z)
        player.hasImpulse = true
    }

    private fun canUseHeatRises(player: net.minecraft.client.player.LocalPlayer): Boolean {
        return !player.isSpectator &&
            !player.abilities.flying &&
            !player.isInWater &&
            !player.isInLava &&
            !player.isFallFlying &&
            !player.isPassenger
    }

    private fun dodgeDirection(): Int {
        val options = net.minecraft.client.Minecraft.getInstance().options
        return when {
            options.keyDown.isDown -> 1
            options.keyLeft.isDown -> 2
            options.keyRight.isDown -> 3
            else -> 0
        }
    }
}
