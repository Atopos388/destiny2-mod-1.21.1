package atopos.destiny2.client

import atopos.destiny2.Destiny2MODClient
import atopos.destiny2.client.gui.DestinyHUDState
import atopos.destiny2.client.gui.DestinyPerkBuffState
import atopos.destiny2.client.gui.DestinyWeaponHUDState
import atopos.destiny2.client.gui.DestinyDamageNumbers
import atopos.destiny2.client.gui.DestinyNavigationState
import atopos.destiny2.client.action.DestinyActionClient
import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.client.combat.QuickMeleeAimClient
import atopos.destiny2.client.combat.HunterMeleeFirstPersonClient
import atopos.destiny2.client.particle.bedrock.BedrockParticleEngine
import atopos.destiny2.client.renderer.VoidHunterSuperAuraClient
import atopos.destiny2.client.renderer.ThunderclapGroundLiftRenderer
import atopos.destiny2.client.renderer.ThunderclapBlastRenderer
import atopos.destiny2.client.weapon.DestinyWeaponFeedbackClient
import atopos.destiny2.client.weapon.DestinyWeaponThirdPersonClient
import atopos.destiny2.client.weapon.GenericGunAnimationClient
import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

object ClientNetworking {
    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.SyncCooldownPayload.ID) { payload, context ->
            val remainingMs = payload.remainingTicks * 50L
            val durationMs = payload.totalDurationTicks.coerceAtLeast(payload.remainingTicks) * 50L
            val endTime = System.currentTimeMillis() + remainingMs
            context.client().execute {
                if (payload.remainingTicks <= 0) {
                    Destiny2MODClient.clientCooldowns.remove(payload.abilityType)
                } else {
                    Destiny2MODClient.clientCooldowns[payload.abilityType] = Pair(endTime, durationMs)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.QuickMeleeAssistPayload.ID) { payload, context ->
            context.client().execute {
                if (payload.hunterMelee) {
                    HunterMeleeFirstPersonClient.play()
                }
                if (payload.targetEntityId >= 0) {
                    QuickMeleeAimClient.start(payload.targetEntityId)
                }
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.PlayHunterGrenadeThrowPayload.ID) { _, context ->
            context.client().execute {
                HunterMeleeFirstPersonClient.playGrenadeThrow()
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.PlayHunterChargedMeleePayload.ID) { _, context ->
            context.client().execute {
                HunterMeleeFirstPersonClient.playChargedMelee()
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.SyncPerkBuffPayload.ID) { payload, context ->
            context.client().execute {
                DestinyPerkBuffState.activate(payload.id, payload.name, payload.durationTicks, payload.stacks)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.EagerEdgeDashPayload.ID) { payload, context ->
            context.client().execute {
                DestinyInputHandler.applyEagerEdgePush(payload.directionX, payload.directionZ)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.SyncPlayerDataPayload.ID) { payload, context ->
            context.client().execute {
                DestinyHUDState.update(payload)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.SyncNavigationStatePayload.ID) { payload, context ->
            context.client().execute {
                DestinyNavigationState.update(
                    payload.journeyStage,
                    payload.currentPower,
                    payload.highestAvailablePower,
                    payload.recommendedPower,
                    payload.powerDeficit,
                    payload.suppressionPercent
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.SyncStatStatePayload.ID) { payload, context ->
            context.client().execute {
                Destiny2MODClient.clientCooldowns.remove(DestinyNetworking.ABILITY_SUPER)
                DestinyHUDState.update(payload)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.SyncWeaponStatePayload.ID) { payload, context ->
            context.client().execute { DestinyWeaponHUDState.update(payload) }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.PrecisionHitPayload.ID) { payload, context ->
            context.client().execute { DestinyWeaponHUDState.precisionHit(payload) }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.DamageNumberPayload.ID) { payload, context ->
            context.client().execute { DestinyDamageNumbers.spawn(payload) }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.WeaponShotFeedbackPayload.ID) { payload, context ->
            context.client().execute { DestinyWeaponFeedbackClient.onShot(payload) }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.WeaponAimStatePayload.ID) { payload, context ->
            context.client().execute {
                DestinyWeaponThirdPersonClient.setRemoteAim(payload.playerId, payload.aiming)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.WeaponThirdPersonActionPayload.ID) { payload, context ->
            context.client().execute {
                DestinyWeaponThirdPersonClient.onAction(payload)
                GenericGunAnimationClient.onAction(payload)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.PlayDestinyActionPayload.ID) { payload, context ->
            context.client().execute {
                DestinyActionClient.play(payload.playerId, payload.actionId)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.VoidHunterSuperAuraPayload.ID) { payload, context ->
            context.client().execute {
                VoidHunterSuperAuraClient.activate(payload.playerId, payload.durationTicks)
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.PlayWorldVfxPayload.ID) { payload, context ->
            context.client().execute {
                val effectId = ResourceLocation.tryParse(payload.effectId)
                    ?: return@execute
                if (effectId == THUNDERCLAP_GROUND_LIFT_EFFECT) {
                    val origin = Vec3(payload.x, payload.y, payload.z)
                    ThunderclapBlastRenderer.activate(
                        origin = origin,
                        yawDegrees = payload.yaw
                    )
                    ThunderclapGroundLiftRenderer.activate(
                        origin = origin,
                        yawDegrees = payload.yaw
                    )
                    return@execute
                }
                BedrockParticleEngine.queueWorld(
                    effect = effectId,
                    position = Vec3(payload.x, payload.y, payload.z),
                    yaw = payload.yaw,
                    pitch = payload.pitch,
                    delayTicks = payload.delayTicks
                )
            }
        }

        ClientPlayNetworking.registerGlobalReceiver(DestinyNetworking.StartCinematicPayload.ID) { payload, context ->
            context.client().execute {
                CinematicCameraClient.requestStart(payload)
            }
        }

    }

    private val THUNDERCLAP_GROUND_LIFT_EFFECT = ResourceLocation.fromNamespaceAndPath(
        "destiny2-mod",
        "vfx/thunderclap_ground_lift"
    )
}
