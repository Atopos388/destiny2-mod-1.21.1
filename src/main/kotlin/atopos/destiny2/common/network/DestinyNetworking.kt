package atopos.destiny2.common.network

import atopos.destiny2.common.ability.DestinyAbilityContext
import atopos.destiny2.common.ability.DestinyAbilityRegistry
import atopos.destiny2.common.ability.ArcTitanAbilities
import atopos.destiny2.common.aspect.ArcTitanAspectRuntime
import atopos.destiny2.common.ability.VoidHunterAbilities
import atopos.destiny2.common.action.DestinyActionRegistry
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.aspect.SolarWarlockAspectRuntime
import atopos.destiny2.common.aspect.DaybreakRuntime
import atopos.destiny2.common.aspect.VoidHunterAspectRuntime
import atopos.destiny2.common.combat.QuickMeleeRuntime
import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.item.MicroMissileBurstWeaponItem
import atopos.destiny2.common.item.ForgottenNameItem
import atopos.destiny2.common.item.IzanagiBurdenItem
import atopos.destiny2.common.item.GenericGunPackItem
import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.DestinyWeaponReserves
import atopos.destiny2.common.weapon.DestinyWeaponSlot
import atopos.destiny2.common.weapon.WeaponLoadoutRuntime
import atopos.destiny2.common.weapon.WeaponHudStatus
import atopos.destiny2.common.weapon.WeaponAimRuntime
import atopos.destiny2.common.weapon.WeaponFireMode
import atopos.destiny2.common.weapon.WeaponReloadPhase
import atopos.destiny2.common.weapon.WeaponCrosshairProfile
import atopos.destiny2.common.weapon.WeaponThirdPersonAction
import atopos.destiny2.common.gear.GearPerkRuntime
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.gear.GearRolls
import atopos.destiny2.common.gear.ArmorModRuntime
import atopos.destiny2.common.item.DestinyClassItem
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyClassType
import atopos.destiny2.common.player.ClassResonanceRuntime
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyCombatRuntime
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.DestinySubclassConfigRegistry
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.player.GuardianAwakeningRuntime
import atopos.destiny2.common.player.GuardianJumpRuntime
import atopos.destiny2.common.player.GuardianPowerRuntime
import atopos.destiny2.common.player.GuardianPowerSnapshot
import atopos.destiny2.common.player.PlayerDestinyDataApi
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import java.util.UUID

object DestinyNetworking {
    const val ABILITY_GRENADE = 0
    const val ABILITY_MELEE = 1
    const val ABILITY_CLASS = 2
    const val ABILITY_SUPER = 3

    const val ABILITY_VOID_GRENADE = 10
    const val ABILITY_VOID_MELEE = 11
    const val ABILITY_VOID_CLASS = 12
    const val ABILITY_VOID_SUPER = 13
    data class CastAbilityPayload(val abilityType: Int, val extraData: Int = 0) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<CastAbilityPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "cast_ability")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, CastAbilityPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeInt(payload.abilityType)
                    buf.writeInt(payload.extraData)
                },
                { buf ->
                    val type = buf.readInt()
                    val data = if (buf.readableBytes() > 0) buf.readInt() else 0
                    CastAbilityPayload(type, data)
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<CastAbilityPayload> = ID
    }

    class ReleaseArcTitanThunderclapPayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ReleaseArcTitanThunderclapPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "release_arc_titan_thunderclap")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ReleaseArcTitanThunderclapPayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { ReleaseArcTitanThunderclapPayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<ReleaseArcTitanThunderclapPayload> = ID
    }

    class EagerEdgeJumpPayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<EagerEdgeJumpPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "eager_edge_jump")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, EagerEdgeJumpPayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { EagerEdgeJumpPayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<EagerEdgeJumpPayload> = ID
    }

    data class HeatRisesMovementPayload(val action: Int) : CustomPacketPayload {
        companion object {
            const val DOUBLE_JUMP = 0
            const val HOLD = 1
            const val RELEASE = 2

            val ID = CustomPacketPayload.Type<HeatRisesMovementPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "heat_rises_movement")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, HeatRisesMovementPayload> = CustomPacketPayload.codec(
                { payload, buf -> buf.writeInt(payload.action) },
                { buf -> HeatRisesMovementPayload(buf.readInt()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<HeatRisesMovementPayload> = ID
    }

    data class GuardianJumpPayload(
        val action: Int,
        val inputX: Float = 0.0f,
        val inputZ: Float = 0.0f
    ) : CustomPacketPayload {
        companion object {
            const val PRESS = 0
            const val HOLD = 1
            const val RELEASE = 2
            const val ARM = 3

            val ID = CustomPacketPayload.Type<GuardianJumpPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "guardian_jump")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, GuardianJumpPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeInt(payload.action)
                    buf.writeFloat(payload.inputX)
                    buf.writeFloat(payload.inputZ)
                },
                { buf -> GuardianJumpPayload(buf.readInt(), buf.readFloat(), buf.readFloat()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<GuardianJumpPayload> = ID
    }

    data class ConsumeGrenadeForHeatRisesPayload(val action: Int) : CustomPacketPayload {
        companion object {
            const val START = 0
            const val COMPLETE = 1
            const val RELEASE = 2
            val ID = CustomPacketPayload.Type<ConsumeGrenadeForHeatRisesPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "consume_grenade_for_heat_rises")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ConsumeGrenadeForHeatRisesPayload> = CustomPacketPayload.codec(
                { payload, buf -> buf.writeInt(payload.action) },
                { buf -> ConsumeGrenadeForHeatRisesPayload(buf.readInt()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<ConsumeGrenadeForHeatRisesPayload> = ID
    }

    data class IcarusDashPayload(val direction: Int) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<IcarusDashPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "icarus_dash")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, IcarusDashPayload> = CustomPacketPayload.codec(
                { payload, buf -> buf.writeInt(payload.direction) },
                { buf -> IcarusDashPayload(buf.readInt()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<IcarusDashPayload> = ID
    }

    data class DaybreakFirePayload(val x: Double, val y: Double, val z: Double) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<DaybreakFirePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "daybreak_fire")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, DaybreakFirePayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeDouble(payload.x)
                    buf.writeDouble(payload.y)
                    buf.writeDouble(payload.z)
                },
                { buf -> DaybreakFirePayload(buf.readDouble(), buf.readDouble(), buf.readDouble()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<DaybreakFirePayload> = ID
    }

    class PlayHunterGrenadeThrowPayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<PlayHunterGrenadeThrowPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "play_hunter_grenade_throw")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, PlayHunterGrenadeThrowPayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { PlayHunterGrenadeThrowPayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<PlayHunterGrenadeThrowPayload> = ID
    }

    class PlayHunterChargedMeleePayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<PlayHunterChargedMeleePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "play_hunter_charged_melee")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, PlayHunterChargedMeleePayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { PlayHunterChargedMeleePayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<PlayHunterChargedMeleePayload> = ID
    }

    data class ReleaseHunterGrenadePayload(
        val x: Double,
        val y: Double,
        val z: Double
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ReleaseHunterGrenadePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "release_hunter_grenade")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ReleaseHunterGrenadePayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeDouble(payload.x)
                    buf.writeDouble(payload.y)
                    buf.writeDouble(payload.z)
                },
                { buf -> ReleaseHunterGrenadePayload(buf.readDouble(), buf.readDouble(), buf.readDouble()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<ReleaseHunterGrenadePayload> = ID
    }

    data class ReleaseHunterChargedMeleePayload(
        val x: Double,
        val y: Double,
        val z: Double
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ReleaseHunterChargedMeleePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "release_hunter_charged_melee")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ReleaseHunterChargedMeleePayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeDouble(payload.x)
                    buf.writeDouble(payload.y)
                    buf.writeDouble(payload.z)
                },
                { buf -> ReleaseHunterChargedMeleePayload(buf.readDouble(), buf.readDouble(), buf.readDouble()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<ReleaseHunterChargedMeleePayload> = ID
    }

    class TrappersAmbushPayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<TrappersAmbushPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "trappers_ambush")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, TrappersAmbushPayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { TrappersAmbushPayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<TrappersAmbushPayload> = ID
    }

    class EagerEdgeActivatePayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<EagerEdgeActivatePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "eager_edge_activate")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, EagerEdgeActivatePayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { EagerEdgeActivatePayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<EagerEdgeActivatePayload> = ID
    }

    data class EagerEdgeDashPayload(val directionX: Double, val directionZ: Double) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<EagerEdgeDashPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "eager_edge_dash")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, EagerEdgeDashPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeDouble(payload.directionX)
                    buf.writeDouble(payload.directionZ)
                },
                { buf -> EagerEdgeDashPayload(buf.readDouble(), buf.readDouble()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<EagerEdgeDashPayload> = ID
    }

    data class SyncCooldownPayload(
        val abilityType: Int,
        val remainingTicks: Int,
        val totalDurationTicks: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SyncCooldownPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "sync_cooldown")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SyncCooldownPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeInt(payload.abilityType)
                    buf.writeInt(payload.remainingTicks)
                    buf.writeInt(payload.totalDurationTicks)
                },
                { buf ->
                    SyncCooldownPayload(buf.readInt(), buf.readInt(), buf.readInt())
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<SyncCooldownPayload> = ID
    }

    data class QuickMeleeAssistPayload(
        val targetEntityId: Int,
        val hunterMelee: Boolean
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<QuickMeleeAssistPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "quick_melee_assist")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, QuickMeleeAssistPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeVarInt(payload.targetEntityId)
                    buf.writeBoolean(payload.hunterMelee)
                },
                { buf -> QuickMeleeAssistPayload(buf.readVarInt(), buf.readBoolean()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<QuickMeleeAssistPayload> = ID
    }

    /** 服务器确认 Perk 已触发后，发送给持有者的右侧 HUD 状态条。 */
    data class SyncPerkBuffPayload(
        val id: String,
        val name: String,
        val durationTicks: Int,
        val stacks: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SyncPerkBuffPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "sync_perk_buff")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SyncPerkBuffPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.id)
                    buf.writeUtf(payload.name)
                    buf.writeInt(payload.durationTicks)
                    buf.writeInt(payload.stacks)
                },
                { buf -> SyncPerkBuffPayload(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readInt()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<SyncPerkBuffPayload> = ID
    }

    data class PlayDestinyActionPayload(
        val playerId: UUID,
        val actionId: String
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<PlayDestinyActionPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "play_destiny_action")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, PlayDestinyActionPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUUID(payload.playerId)
                    buf.writeUtf(payload.actionId)
                },
                { buf ->
                    PlayDestinyActionPayload(buf.readUUID(), buf.readUtf())
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<PlayDestinyActionPayload> = ID
    }

    data class VoidHunterSuperAuraPayload(
        val playerId: UUID,
        val durationTicks: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<VoidHunterSuperAuraPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_hunter_super_aura")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, VoidHunterSuperAuraPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUUID(payload.playerId)
                    buf.writeVarInt(payload.durationTicks)
                },
                { buf -> VoidHunterSuperAuraPayload(buf.readUUID(), buf.readVarInt()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<VoidHunterSuperAuraPayload> = ID
    }

    /**
     * Server-confirmed request for one client-only Bedrock particle layer.
     *
     * The server owns when and where the cast occurred. The client only resolves the
     * named visual resource and applies the supplied orientation and presentation delay.
     */
    data class PlayWorldVfxPayload(
        val effectId: String,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float,
        val delayTicks: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<PlayWorldVfxPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "play_world_vfx")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, PlayWorldVfxPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.effectId)
                    buf.writeDouble(payload.x)
                    buf.writeDouble(payload.y)
                    buf.writeDouble(payload.z)
                    buf.writeFloat(payload.yaw)
                    buf.writeFloat(payload.pitch)
                    buf.writeVarInt(payload.delayTicks)
                },
                { buf ->
                    PlayWorldVfxPayload(
                        effectId = buf.readUtf(),
                        x = buf.readDouble(),
                        y = buf.readDouble(),
                        z = buf.readDouble(),
                        yaw = buf.readFloat(),
                        pitch = buf.readFloat(),
                        delayTicks = buf.readVarInt()
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<PlayWorldVfxPayload> = ID
    }

    /** Starts one client-owned world-space cinematic around a server-provided anchor. */
    data class StartCinematicPayload(
        val sessionId: UUID,
        val cinematicId: String,
        val anchorX: Double,
        val anchorY: Double,
        val anchorZ: Double,
        val anchorYaw: Float,
        val actorEntityId: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<StartCinematicPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "start_cinematic")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, StartCinematicPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUUID(payload.sessionId)
                    buf.writeUtf(payload.cinematicId)
                    buf.writeDouble(payload.anchorX)
                    buf.writeDouble(payload.anchorY)
                    buf.writeDouble(payload.anchorZ)
                    buf.writeFloat(payload.anchorYaw)
                    buf.writeVarInt(payload.actorEntityId)
                },
                { buf ->
                    StartCinematicPayload(
                        buf.readUUID(),
                        buf.readUtf(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readFloat(),
                        buf.readVarInt()
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<StartCinematicPayload> = ID
    }

    /** Requests the server-synced Ghost actor when the preceding film has ended. */
    data class BeginCinematicAnimationPayload(val sessionId: UUID) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<BeginCinematicAnimationPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "begin_cinematic_animation")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, BeginCinematicAnimationPayload> =
                CustomPacketPayload.codec(
                    { payload, buf -> buf.writeUUID(payload.sessionId) },
                    { buf -> BeginCinematicAnimationPayload(buf.readUUID()) }
                )
        }

        override fun type(): CustomPacketPayload.Type<BeginCinematicAnimationPayload> = ID
    }

    /** Idempotent client acknowledgement for a naturally completed cinematic. */
    data class FinishCinematicPayload(val sessionId: UUID) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<FinishCinematicPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "finish_cinematic")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, FinishCinematicPayload> = CustomPacketPayload.codec(
                { payload, buf -> buf.writeUUID(payload.sessionId) },
                { buf -> FinishCinematicPayload(buf.readUUID()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<FinishCinematicPayload> = ID
    }

    data class SyncPlayerDataPayload(
        val classId: String,
        val className: String,
        val subclassName: String,
        val grenadeName: String,
        val meleeName: String,
        val classAbilityName: String,
        val superName: String,
        val grenadeId: String,
        val meleeId: String,
        val classAbilityId: String,
        val superId: String,
        val movementId: String,
        val aspectIds: String,
        val fragmentIds: String
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SyncPlayerDataPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "sync_player_data")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SyncPlayerDataPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.classId)
                    buf.writeUtf(payload.className)
                    buf.writeUtf(payload.subclassName)
                    buf.writeUtf(payload.grenadeName)
                    buf.writeUtf(payload.meleeName)
                    buf.writeUtf(payload.classAbilityName)
                    buf.writeUtf(payload.superName)
                    buf.writeUtf(payload.grenadeId)
                    buf.writeUtf(payload.meleeId)
                    buf.writeUtf(payload.classAbilityId)
                    buf.writeUtf(payload.superId)
                    buf.writeUtf(payload.movementId)
                    buf.writeUtf(payload.aspectIds)
                    buf.writeUtf(payload.fragmentIds)
                },
                { buf ->
                    SyncPlayerDataPayload(
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf(),
                        buf.readUtf()
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<SyncPlayerDataPayload> = ID
    }

    data class SyncNavigationStatePayload(
        val journeyStage: String,
        val currentPower: Int,
        val highestAvailablePower: Int,
        val recommendedPower: Int,
        val powerDeficit: Int,
        val suppressionPercent: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SyncNavigationStatePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "sync_navigation_state")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SyncNavigationStatePayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.journeyStage)
                    buf.writeVarInt(payload.currentPower)
                    buf.writeVarInt(payload.highestAvailablePower)
                    buf.writeVarInt(payload.recommendedPower)
                    buf.writeVarInt(payload.powerDeficit)
                    buf.writeVarInt(payload.suppressionPercent)
                },
                { buf ->
                    SyncNavigationStatePayload(
                        buf.readUtf(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<SyncNavigationStatePayload> = ID
    }

    data class SyncWeaponLoadoutPayload(
        val kinetic: List<ItemStack>,
        val energy: List<ItemStack>,
        val power: List<ItemStack>
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SyncWeaponLoadoutPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "sync_weapon_loadout")
            )
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncWeaponLoadoutPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    (payload.kinetic + payload.energy + payload.power).forEach { stack ->
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack)
                    }
                },
                { buf ->
                    fun readColumn(): List<ItemStack> = List(DestinyWeaponReserves.CAPACITY_PER_SLOT) {
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)
                    }
                    SyncWeaponLoadoutPayload(readColumn(), readColumn(), readColumn())
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<SyncWeaponLoadoutPayload> = ID
    }

    data class SyncStatStatePayload(
        val weapons: Int,
        val health: Int,
        val classAbility: Int,
        val grenade: Int,
        val superStat: Int,
        val melee: Int,
        val superEnergy: Float,
        val healthShield: Float,
        val healthShieldCapacity: Float,
        val classOvershield: Float,
        val armorCharge: Int,
        val armorChargeMax: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SyncStatStatePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "sync_stat_state")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SyncStatStatePayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeInt(payload.weapons)
                    buf.writeInt(payload.health)
                    buf.writeInt(payload.classAbility)
                    buf.writeInt(payload.grenade)
                    buf.writeInt(payload.superStat)
                    buf.writeInt(payload.melee)
                    buf.writeFloat(payload.superEnergy)
                    buf.writeFloat(payload.healthShield)
                    buf.writeFloat(payload.healthShieldCapacity)
                    buf.writeFloat(payload.classOvershield)
                    buf.writeByte(payload.armorCharge)
                    buf.writeByte(payload.armorChargeMax)
                },
                { buf ->
                    SyncStatStatePayload(
                        buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readByte().toInt(), buf.readByte().toInt()
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<SyncStatStatePayload> = ID
    }

    data class SetLoadoutPayload(val classId: String, val subclassId: String) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SetLoadoutPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "set_loadout")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SetLoadoutPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.classId)
                    buf.writeUtf(payload.subclassId)
                },
                { buf -> SetLoadoutPayload(buf.readUtf(), buf.readUtf()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<SetLoadoutPayload> = ID
    }

    data class ConfigureSubclassPayload(val action: String, val slotKey: String, val optionId: String) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ConfigureSubclassPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "configure_subclass")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ConfigureSubclassPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.action)
                    buf.writeUtf(payload.slotKey)
                    buf.writeUtf(payload.optionId)
                },
                { buf ->
                    ConfigureSubclassPayload(buf.readUtf(), buf.readUtf(), buf.readUtf())
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<ConfigureSubclassPayload> = ID
    }

    data class EquipDirectorItemPayload(
        val sourceInventorySlot: Int,
        val target: String,
        val expectedItemId: String,
        val expectedComponentsHash: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<EquipDirectorItemPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "equip_director_item")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, EquipDirectorItemPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeVarInt(payload.sourceInventorySlot)
                    buf.writeUtf(payload.target)
                    buf.writeUtf(payload.expectedItemId)
                    buf.writeInt(payload.expectedComponentsHash)
                },
                { buf -> EquipDirectorItemPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readInt()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<EquipDirectorItemPayload> = ID
    }

    data class EquipDirectorReserveWeaponPayload(
        val reserveIndex: Int,
        val target: String,
        val expectedItemId: String,
        val expectedComponentsHash: Int
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<EquipDirectorReserveWeaponPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "equip_director_reserve_weapon")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, EquipDirectorReserveWeaponPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeVarInt(payload.reserveIndex)
                    buf.writeUtf(payload.target)
                    buf.writeUtf(payload.expectedItemId)
                    buf.writeInt(payload.expectedComponentsHash)
                },
                { buf -> EquipDirectorReserveWeaponPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readInt()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<EquipDirectorReserveWeaponPayload> = ID
    }

    class ReloadWeaponPayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ReloadWeaponPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "reload_weapon")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ReloadWeaponPayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { ReloadWeaponPayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<ReloadWeaponPayload> = ID
    }

    class InspectWeaponPayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<InspectWeaponPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "inspect_weapon")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, InspectWeaponPayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { InspectWeaponPayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<InspectWeaponPayload> = ID
    }

    class CycleFireModePayload : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<CycleFireModePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "cycle_fire_mode")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, CycleFireModePayload> = CustomPacketPayload.codec(
                { _, _ -> },
                { CycleFireModePayload() }
            )
        }

        override fun type(): CustomPacketPayload.Type<CycleFireModePayload> = ID
    }

    data class SetWeaponAimPayload(val aiming: Boolean) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SetWeaponAimPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "set_weapon_aim")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SetWeaponAimPayload> = CustomPacketPayload.codec(
                { payload, buf -> buf.writeBoolean(payload.aiming) },
                { buf -> SetWeaponAimPayload(buf.readBoolean()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<SetWeaponAimPayload> = ID
    }

    data class WeaponAimStatePayload(val playerId: UUID, val aiming: Boolean) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<WeaponAimStatePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "weapon_aim_state")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, WeaponAimStatePayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUUID(payload.playerId)
                    buf.writeBoolean(payload.aiming)
                },
                { buf -> WeaponAimStatePayload(buf.readUUID(), buf.readBoolean()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<WeaponAimStatePayload> = ID
    }

    data class WeaponThirdPersonActionPayload(
        val playerId: UUID,
        val action: WeaponThirdPersonAction,
        val durationTicks: Int,
        val sequence: Long,
        val weaponId: ResourceLocation? = null,
        val soundKey: String? = null
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<WeaponThirdPersonActionPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "weapon_third_person_action")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, WeaponThirdPersonActionPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUUID(payload.playerId)
                    buf.writeEnum(payload.action)
                    buf.writeVarInt(payload.durationTicks)
                    buf.writeVarLong(payload.sequence)
                    buf.writeNullable(payload.weaponId, FriendlyByteBuf::writeResourceLocation)
                    buf.writeNullable(payload.soundKey, FriendlyByteBuf::writeUtf)
                },
                { buf ->
                    WeaponThirdPersonActionPayload(
                        buf.readUUID(),
                        buf.readEnum(WeaponThirdPersonAction::class.java),
                        buf.readVarInt(),
                        buf.readVarLong(),
                        buf.readNullable(FriendlyByteBuf::readResourceLocation),
                        buf.readNullable(FriendlyByteBuf::readUtf)
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<WeaponThirdPersonActionPayload> = ID
    }

    data class FireWeaponPayload(
        val hand: InteractionHand,
        val directionX: Double,
        val directionY: Double,
        val directionZ: Double
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<FireWeaponPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "fire_weapon")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, FireWeaponPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeEnum(payload.hand)
                    buf.writeDouble(payload.directionX)
                    buf.writeDouble(payload.directionY)
                    buf.writeDouble(payload.directionZ)
                },
                { buf ->
                    FireWeaponPayload(
                        buf.readEnum(InteractionHand::class.java),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble()
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<FireWeaponPayload> = ID
    }

    data class SyncWeaponStatePayload(
        val weaponId: String,
        val ammoType: DestinyAmmoType,
        val magazine: Int,
        val capacity: Int,
        val reserve: Int,
        val reloadRemaining: Int,
        val reloadTotal: Int,
        val precisionMultiplier: Float,
        val reloadPhase: WeaponReloadPhase,
        val fireMode: WeaponFireMode,
        val chamberEmpty: Boolean,
        val boltRemaining: Int,
        val crosshair: WeaponCrosshairProfile
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<SyncWeaponStatePayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "sync_weapon_state")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, SyncWeaponStatePayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUtf(payload.weaponId)
                    buf.writeEnum(payload.ammoType)
                    buf.writeInt(payload.magazine)
                    buf.writeInt(payload.capacity)
                    buf.writeInt(payload.reserve)
                    buf.writeInt(payload.reloadRemaining)
                    buf.writeInt(payload.reloadTotal)
                    buf.writeFloat(payload.precisionMultiplier)
                    buf.writeEnum(payload.reloadPhase)
                    buf.writeEnum(payload.fireMode)
                    buf.writeBoolean(payload.chamberEmpty)
                    buf.writeInt(payload.boltRemaining)
                    buf.writeFloat(payload.crosshair.baseGap)
                    buf.writeFloat(payload.crosshair.movingPenalty)
                    buf.writeFloat(payload.crosshair.airbornePenalty)
                    buf.writeFloat(payload.crosshair.shotPenalty)
                    buf.writeFloat(payload.crosshair.shotDecayPerTick)
                    buf.writeFloat(payload.crosshair.hideAimProgress)
                },
                { buf ->
                    SyncWeaponStatePayload(
                        buf.readUtf(), buf.readEnum(DestinyAmmoType::class.java), buf.readInt(), buf.readInt(),
                        buf.readInt(), buf.readInt(), buf.readInt(), buf.readFloat(),
                        buf.readEnum(WeaponReloadPhase::class.java),
                        buf.readEnum(WeaponFireMode::class.java),
                        buf.readBoolean(),
                        buf.readInt(),
                        WeaponCrosshairProfile(
                            buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat()
                        )
                    )
                }
            )

            fun from(status: WeaponHudStatus) = SyncWeaponStatePayload(
                status.weaponId, status.ammoType, status.magazine, status.capacity, status.reserve,
                status.reloadRemaining, status.reloadTotal, status.precisionMultiplier,
                status.reloadPhase, status.fireMode, status.chamberEmpty
                , status.boltRemaining, status.crosshair
            )
        }

        override fun type(): CustomPacketPayload.Type<SyncWeaponStatePayload> = ID
    }

    data class PrecisionHitPayload(val damage: Float) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<PrecisionHitPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "precision_hit")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, PrecisionHitPayload> = CustomPacketPayload.codec(
                { payload, buf -> buf.writeFloat(payload.damage) },
                { buf -> PrecisionHitPayload(buf.readFloat()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<PrecisionHitPayload> = ID
    }

    data class WeaponShotFeedbackPayload(
        val shooterId: UUID,
        val startX: Double,
        val startY: Double,
        val startZ: Double,
        val endX: Double,
        val endY: Double,
        val endZ: Double,
        val impactKind: Int,
        val hit: Boolean,
        val precision: Boolean,
        val killed: Boolean,
        val applyRecoil: Boolean,
        val recoilPitch: Float,
        val recoilYaw: Float,
        val recoilKickMs: Int,
        val recoilRecoverMs: Int,
        val aimedRecoilMultiplier: Float,
        val tracerStep: Double,
        val showBulletImpact: Boolean
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<WeaponShotFeedbackPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "weapon_shot_feedback")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, WeaponShotFeedbackPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeUUID(payload.shooterId)
                    buf.writeDouble(payload.startX)
                    buf.writeDouble(payload.startY)
                    buf.writeDouble(payload.startZ)
                    buf.writeDouble(payload.endX)
                    buf.writeDouble(payload.endY)
                    buf.writeDouble(payload.endZ)
                    buf.writeInt(payload.impactKind)
                    buf.writeBoolean(payload.hit)
                    buf.writeBoolean(payload.precision)
                    buf.writeBoolean(payload.killed)
                    buf.writeBoolean(payload.applyRecoil)
                    buf.writeFloat(payload.recoilPitch)
                    buf.writeFloat(payload.recoilYaw)
                    buf.writeInt(payload.recoilKickMs)
                    buf.writeInt(payload.recoilRecoverMs)
                    buf.writeFloat(payload.aimedRecoilMultiplier)
                    buf.writeDouble(payload.tracerStep)
                    buf.writeBoolean(payload.showBulletImpact)
                },
                { buf ->
                    WeaponShotFeedbackPayload(
                        buf.readUUID(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                        buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readInt(), buf.readFloat(),
                        buf.readDouble(), buf.readBoolean()
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<WeaponShotFeedbackPayload> = ID
    }

    data class DamageNumberPayload(
        val x: Double,
        val y: Double,
        val z: Double,
        val amount: Float,
        val precision: Boolean
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<DamageNumberPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "damage_number")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, DamageNumberPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeDouble(payload.x)
                    buf.writeDouble(payload.y)
                    buf.writeDouble(payload.z)
                    buf.writeFloat(payload.amount)
                    buf.writeBoolean(payload.precision)
                },
                { buf ->
                    DamageNumberPayload(
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readDouble(),
                        buf.readFloat(),
                        buf.readBoolean()
                    )
                }
            )
        }

        override fun type(): CustomPacketPayload.Type<DamageNumberPayload> = ID
    }

    /** Empty catalystId means uninstall. The server resolves and validates the menu slot. */
    data class ConfigureGearCatalystPayload(
        val containerId: Int,
        val menuSlot: Int,
        val catalystId: String
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ConfigureGearCatalystPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "configure_gear_catalyst")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ConfigureGearCatalystPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeInt(payload.containerId)
                    buf.writeInt(payload.menuSlot)
                    buf.writeUtf(payload.catalystId)
                },
                { buf -> ConfigureGearCatalystPayload(buf.readInt(), buf.readInt(), buf.readUtf()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<ConfigureGearCatalystPayload> = ID
    }

    /** Empty modId uninstalls the Armor 3.0 stat mod. */
    data class ConfigureArmorStatModPayload(
        val containerId: Int,
        val menuSlot: Int,
        val modId: String
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ConfigureArmorStatModPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "configure_armor_stat_mod")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ConfigureArmorStatModPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeInt(payload.containerId)
                    buf.writeInt(payload.menuSlot)
                    buf.writeUtf(payload.modId)
                },
                { buf -> ConfigureArmorStatModPayload(buf.readInt(), buf.readInt(), buf.readUtf()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<ConfigureArmorStatModPayload> = ID
    }

    /** Configures one of the four Armor 3.0 sockets. Empty modId uninstalls that socket. */
    data class ConfigureArmorModPayload(
        val containerId: Int,
        val menuSlot: Int,
        val socketIndex: Int,
        val modId: String
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ConfigureArmorModPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "configure_armor_mod")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ConfigureArmorModPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeInt(payload.containerId)
                    buf.writeInt(payload.menuSlot)
                    buf.writeByte(payload.socketIndex)
                    buf.writeUtf(payload.modId)
                },
                { buf -> ConfigureArmorModPayload(buf.readInt(), buf.readInt(), buf.readByte().toInt(), buf.readUtf()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<ConfigureArmorModPayload> = ID
    }

    /** Replaces one non-exotic random-roll perk. The server validates the definition and column. */
    data class ConfigureGearPerkPayload(
        val containerId: Int,
        val menuSlot: Int,
        val columnIndex: Int,
        val perkId: String
    ) : CustomPacketPayload {
        companion object {
            val ID = CustomPacketPayload.Type<ConfigureGearPerkPayload>(
                ResourceLocation.fromNamespaceAndPath("destiny2-mod", "configure_gear_perk")
            )
            val CODEC: StreamCodec<FriendlyByteBuf, ConfigureGearPerkPayload> = CustomPacketPayload.codec(
                { payload, buf ->
                    buf.writeInt(payload.containerId)
                    buf.writeInt(payload.menuSlot)
                    buf.writeByte(payload.columnIndex)
                    buf.writeUtf(payload.perkId)
                },
                { buf -> ConfigureGearPerkPayload(buf.readInt(), buf.readInt(), buf.readByte().toInt(), buf.readUtf()) }
            )
        }

        override fun type(): CustomPacketPayload.Type<ConfigureGearPerkPayload> = ID
    }

    fun register() {
        WeaponAimRuntime.register()
        QuickMeleeRuntime.register()
        PayloadTypeRegistry.playC2S().register(CastAbilityPayload.ID, CastAbilityPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(
            ReleaseArcTitanThunderclapPayload.ID,
            ReleaseArcTitanThunderclapPayload.CODEC
        )
        PayloadTypeRegistry.playC2S().register(EagerEdgeJumpPayload.ID, EagerEdgeJumpPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(HeatRisesMovementPayload.ID, HeatRisesMovementPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(GuardianJumpPayload.ID, GuardianJumpPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ConsumeGrenadeForHeatRisesPayload.ID, ConsumeGrenadeForHeatRisesPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(IcarusDashPayload.ID, IcarusDashPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(DaybreakFirePayload.ID, DaybreakFirePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ReleaseHunterGrenadePayload.ID, ReleaseHunterGrenadePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ReleaseHunterChargedMeleePayload.ID, ReleaseHunterChargedMeleePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(TrappersAmbushPayload.ID, TrappersAmbushPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(EagerEdgeActivatePayload.ID, EagerEdgeActivatePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(SetLoadoutPayload.ID, SetLoadoutPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ConfigureSubclassPayload.ID, ConfigureSubclassPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ReloadWeaponPayload.ID, ReloadWeaponPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(InspectWeaponPayload.ID, InspectWeaponPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(CycleFireModePayload.ID, CycleFireModePayload.CODEC)
        PayloadTypeRegistry.playC2S().register(SetWeaponAimPayload.ID, SetWeaponAimPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(FireWeaponPayload.ID, FireWeaponPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ConfigureGearCatalystPayload.ID, ConfigureGearCatalystPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ConfigureArmorStatModPayload.ID, ConfigureArmorStatModPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ConfigureArmorModPayload.ID, ConfigureArmorModPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(ConfigureGearPerkPayload.ID, ConfigureGearPerkPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(EquipDirectorItemPayload.ID, EquipDirectorItemPayload.CODEC)
        PayloadTypeRegistry.playC2S().register(
            EquipDirectorReserveWeaponPayload.ID,
            EquipDirectorReserveWeaponPayload.CODEC
        )
        PayloadTypeRegistry.playC2S().register(
            BeginCinematicAnimationPayload.ID,
            BeginCinematicAnimationPayload.CODEC
        )
        PayloadTypeRegistry.playC2S().register(FinishCinematicPayload.ID, FinishCinematicPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncCooldownPayload.ID, SyncCooldownPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(QuickMeleeAssistPayload.ID, QuickMeleeAssistPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncPerkBuffPayload.ID, SyncPerkBuffPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(EagerEdgeDashPayload.ID, EagerEdgeDashPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(PlayDestinyActionPayload.ID, PlayDestinyActionPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(VoidHunterSuperAuraPayload.ID, VoidHunterSuperAuraPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(PlayHunterGrenadeThrowPayload.ID, PlayHunterGrenadeThrowPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(PlayHunterChargedMeleePayload.ID, PlayHunterChargedMeleePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(PlayWorldVfxPayload.ID, PlayWorldVfxPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(StartCinematicPayload.ID, StartCinematicPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncPlayerDataPayload.ID, SyncPlayerDataPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncNavigationStatePayload.ID, SyncNavigationStatePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncWeaponLoadoutPayload.ID, SyncWeaponLoadoutPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncStatStatePayload.ID, SyncStatStatePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(SyncWeaponStatePayload.ID, SyncWeaponStatePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(PrecisionHitPayload.ID, PrecisionHitPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(DamageNumberPayload.ID, DamageNumberPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(WeaponShotFeedbackPayload.ID, WeaponShotFeedbackPayload.CODEC)
        PayloadTypeRegistry.playS2C().register(WeaponAimStatePayload.ID, WeaponAimStatePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(
            WeaponThirdPersonActionPayload.ID,
            WeaponThirdPersonActionPayload.CODEC
        )

        ServerPlayNetworking.registerGlobalReceiver(CastAbilityPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                if (payload.abilityType == ABILITY_MELEE) {
                    handleMeleeInput(player, payload.extraData)
                } else {
                    handleCastAbility(player, payload.abilityType, payload.extraData)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ReleaseArcTitanThunderclapPayload.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                ArcTitanAbilities.requestThunderclapRelease(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(FinishCinematicPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                GuardianAwakeningRuntime.finishCinematic(player, payload.sessionId)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(BeginCinematicAnimationPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                GuardianAwakeningRuntime.beginAnimation(player, payload.sessionId)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(EagerEdgeJumpPayload.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                GearPerkRuntime.tryEagerEdgeJump(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(HeatRisesMovementPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                DestinyAspectRuntime.handleHeatRisesMovement(player, payload.action)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(GuardianJumpPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                GuardianJumpRuntime.handleMovement(player, payload.action, payload.inputX, payload.inputZ)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ConsumeGrenadeForHeatRisesPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                DestinyAspectRuntime.handleHeatRisesGrenadeHold(player, payload.action)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(IcarusDashPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                DestinyAspectRuntime.tryIcarusDash(player, payload.direction)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(DaybreakFirePayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                DaybreakRuntime.tryFire(player, Vec3(payload.x, payload.y, payload.z))
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ReleaseHunterGrenadePayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                VoidHunterAbilities.releaseGrenadeFromAnimatedHand(
                    player,
                    Vec3(payload.x, payload.y, payload.z)
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ReleaseHunterChargedMeleePayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                VoidHunterAbilities.releaseChargedMeleeFromAnimatedHand(
                    player,
                    Vec3(payload.x, payload.y, payload.z)
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(TrappersAmbushPayload.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                VoidHunterAspectRuntime.tryStartTrappersAmbush(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(EagerEdgeActivatePayload.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                GearPerkRuntime.tryActivateEagerEdge(player)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(SetLoadoutPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                handleSetLoadout(player, payload.classId, payload.subclassId)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ConfigureSubclassPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                handleConfigureSubclass(player, payload.action, payload.slotKey, payload.optionId)
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ReloadWeaponPayload.ID) { _, context ->
            val player = context.player()
            context.server().execute {
                val mainHandStack = player.mainHandItem
                val mainHandItem = mainHandStack.item
                if (
                    mainHandItem is DestinyRangedWeapon &&
                    WeaponLoadoutRuntime.isEquipped(player, mainHandStack) &&
                    mainHandItem.requestReload(player.level(), player, mainHandStack)
                ) {
                    return@execute
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(InspectWeaponPayload.ID) { _, context ->
                val player = context.player()
                context.server().execute {
                    val stack = player.mainHandItem
                    val weapon = stack.item as? DestinyRangedWeapon ?: return@execute
                    if (!WeaponLoadoutRuntime.isEquipped(player, stack)) return@execute
                    weapon.requestInspect(player.level(), player, stack)
                }
        }

        ServerPlayNetworking.registerGlobalReceiver(CycleFireModePayload.ID) { _, context ->
            val player = context.player()
                context.server().execute {
                    val stack = player.mainHandItem
                    val weapon = stack.item as? DestinyRangedWeapon ?: return@execute
                    if (!WeaponLoadoutRuntime.isEquipped(player, stack)) return@execute
                    weapon.cycleFireMode(player, stack)
                }
        }

        ServerPlayNetworking.registerGlobalReceiver(SetWeaponAimPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                val allowed = !payload.aiming || WeaponLoadoutRuntime.isEquipped(player, player.mainHandItem)
                val aiming = WeaponAimRuntime.setAiming(player, payload.aiming && allowed)
                val state = WeaponAimStatePayload(player.uuid, aiming)
                ServerPlayNetworking.send(player, state)
                PlayerLookup.tracking(player).forEach { trackingPlayer ->
                    ServerPlayNetworking.send(trackingPlayer, state)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(FireWeaponPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                val stack = player.getItemInHand(payload.hand)
                if (payload.hand != InteractionHand.MAIN_HAND || !WeaponLoadoutRuntime.isEquipped(player, stack)) {
                    return@execute
                }
                val item = stack.item
                if (item is MicroMissileBurstWeaponItem) {
                    item.requestFire(player.level(), player, payload.hand)
                } else if (item is ForgottenNameItem) {
                    item.requestFire(
                        player.level(),
                        player,
                        payload.hand,
                        net.minecraft.world.phys.Vec3(payload.directionX, payload.directionY, payload.directionZ)
                    )
                } else if (item is IzanagiBurdenItem) {
                    item.requestFire(
                        player.level(),
                        player,
                        payload.hand,
                        net.minecraft.world.phys.Vec3(payload.directionX, payload.directionY, payload.directionZ)
                    )
                } else if (item is GenericGunPackItem) {
                    item.requestFire(
                        player.level(),
                        player,
                        payload.hand,
                        net.minecraft.world.phys.Vec3(payload.directionX, payload.directionY, payload.directionZ)
                    )
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ConfigureGearCatalystPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                val menu = player.containerMenu
                val stack = if (payload.menuSlot < 0) {
                    val inventorySlot = -payload.menuSlot - 1
                    if (inventorySlot !in 0 until player.inventory.containerSize) return@execute
                    player.inventory.getItem(inventorySlot)
                } else {
                    if (menu.containerId != payload.containerId || payload.menuSlot !in menu.slots.indices) {
                        return@execute
                    }
                    menu.getSlot(payload.menuSlot).item
                }
                val definition = GearRegistry.definitionFor(stack) ?: return@execute
                if (!definition.hasCatalystSlot) return@execute
                val catalystId = payload.catalystId.takeIf(String::isNotBlank)?.let {
                    runCatching { ResourceLocation.parse(it) }.getOrNull()
                }
                if (payload.catalystId.isNotBlank() && catalystId == null) return@execute
                if (GearRolls.setCatalyst(stack, catalystId)) {
                    menu.broadcastChanges()
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ConfigureArmorStatModPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                val menu = player.containerMenu
                val stack = if (payload.menuSlot < 0) {
                    val inventorySlot = -payload.menuSlot - 1
                    if (inventorySlot !in 0 until player.inventory.containerSize) return@execute
                    player.inventory.getItem(inventorySlot)
                } else {
                    if (menu.containerId != payload.containerId || payload.menuSlot !in menu.slots.indices) return@execute
                    menu.getSlot(payload.menuSlot).item
                }
                val modId = payload.modId.takeIf(String::isNotBlank)?.let {
                    runCatching { ResourceLocation.parse(it) }.getOrNull()
                }
                if (payload.modId.isNotBlank() && modId == null) return@execute
                if (GearRolls.setArmorStatMod(stack, modId)) {
                    menu.broadcastChanges()
                    ArmorModRuntime.invalidateInstalled(player.uuid)
                    syncStatState(player)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ConfigureArmorModPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                val menu = player.containerMenu
                val stack = if (payload.menuSlot < 0) {
                    val inventorySlot = -payload.menuSlot - 1
                    if (inventorySlot !in 0 until player.inventory.containerSize) return@execute
                    player.inventory.getItem(inventorySlot)
                } else {
                    if (menu.containerId != payload.containerId || payload.menuSlot !in menu.slots.indices) return@execute
                    menu.getSlot(payload.menuSlot).item
                }
                val modId = payload.modId.takeIf(String::isNotBlank)?.let {
                    runCatching { ResourceLocation.parse(it) }.getOrNull()
                }
                if (payload.modId.isNotBlank() && modId == null) return@execute
                if (GearRolls.setArmorMod(stack, payload.socketIndex, modId)) {
                    menu.broadcastChanges()
                    ArmorModRuntime.invalidateInstalled(player.uuid)
                    syncStatState(player)
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(ConfigureGearPerkPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                if (!player.isCreative) return@execute
                val menu = player.containerMenu
                val stack = if (payload.menuSlot < 0) {
                    val inventorySlot = -payload.menuSlot - 1
                    if (inventorySlot !in 0 until player.inventory.containerSize) return@execute
                    player.inventory.getItem(inventorySlot)
                } else {
                    if (menu.containerId != payload.containerId || payload.menuSlot !in menu.slots.indices) return@execute
                    menu.getSlot(payload.menuSlot).item
                }
                val perkId = runCatching { ResourceLocation.parse(payload.perkId) }.getOrNull() ?: return@execute
                if (GearRolls.setPerk(stack, payload.columnIndex, perkId)) {
                    menu.broadcastChanges()
                }
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(EquipDirectorItemPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute { handleEquipDirectorItem(player, payload) }
        }

        ServerPlayNetworking.registerGlobalReceiver(EquipDirectorReserveWeaponPayload.ID) { payload, context ->
            val player = context.player()
            context.server().execute {
                val slot = weaponSlotForTarget(payload.target) ?: return@execute
                val stack = PlayerDestinyDataApi.get(player).weaponReserves.get(slot, payload.reserveIndex)
                if (stack.isEmpty) return@execute
                if (BuiltInRegistries.ITEM.getKey(stack.item).toString() != payload.expectedItemId) return@execute
                if (stack.components.hashCode() != payload.expectedComponentsHash) return@execute
                WeaponLoadoutRuntime.equipFromReserve(player, payload.reserveIndex, slot)
            }
        }

        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            syncPlayerData(handler.player)
        }
    }

    fun syncPlayerData(player: ServerPlayer) {
        WeaponLoadoutRuntime.initializeLegacyLoadout(player)
        WeaponLoadoutRuntime.collectInventoryWeapons(player)
        val data = PlayerDestinyDataApi.get(player)
        val config = DestinySubclassConfigRegistry.normalize(data.subclass, data.subclassConfig)
        data.subclassConfig = config
        data.restrictCurrentSubclassConfig()
        val hasActiveClass = data.isClassUnlocked(data.destinyClass)
        val payload = SyncPlayerDataPayload(
            classId = data.destinyClass.id.takeIf { hasActiveClass }.orEmpty(),
            className = data.destinyClass.displayName.takeIf { hasActiveClass }.orEmpty(),
            subclassName = data.subclass.displayName.takeIf { hasActiveClass }.orEmpty(),
            grenadeName = abilityName(data, AbilitySlot.GRENADE),
            meleeName = abilityName(data, AbilitySlot.MELEE),
            classAbilityName = abilityName(data, AbilitySlot.CLASS_ABILITY),
            superName = abilityName(data, AbilitySlot.SUPER),
            grenadeId = abilityId(data, config.selectedAbilities[AbilitySlot.GRENADE], AbilitySlot.GRENADE),
            meleeId = abilityId(data, config.selectedAbilities[AbilitySlot.MELEE], AbilitySlot.MELEE),
            classAbilityId = abilityId(data, config.selectedAbilities[AbilitySlot.CLASS_ABILITY], AbilitySlot.CLASS_ABILITY),
            superId = abilityId(data, config.selectedAbilities[AbilitySlot.SUPER], AbilitySlot.SUPER),
            movementId = config.selectedMovementId.takeIf(data::isSubclassOptionUnlocked).orEmpty(),
            aspectIds = config.selectedAspects.filter(data::isSubclassOptionUnlocked).joinToString(","),
            fragmentIds = config.selectedFragments.filter(data::isSubclassOptionUnlocked).joinToString(",")
        )
        ServerPlayNetworking.send(player, payload)
        syncWeaponLoadout(player)
        syncNavigationState(player, GuardianPowerRuntime.refresh(player))
        syncStatState(player)
        syncCooldowns(player)
    }

    fun broadcastWorldVfx(
        player: ServerPlayer,
        effectId: ResourceLocation,
        origin: Vec3,
        yaw: Float,
        pitch: Float,
        delayTicks: Int = 0,
        trackingEffectId: ResourceLocation = effectId
    ) {
        fun payload(id: ResourceLocation) = PlayWorldVfxPayload(
            effectId = id.toString(),
            x = origin.x,
            y = origin.y,
            z = origin.z,
            yaw = yaw,
            pitch = pitch,
            delayTicks = delayTicks.coerceIn(0, 20 * 30)
        )
        ServerPlayNetworking.send(player, payload(effectId))
        val observerPayload = payload(trackingEffectId)
        PlayerLookup.tracking(player).forEach { trackingPlayer ->
            ServerPlayNetworking.send(trackingPlayer, observerPayload)
        }
    }

    fun broadcastWeaponThirdPersonAction(
        player: ServerPlayer,
        action: WeaponThirdPersonAction,
        durationTicks: Int,
        weaponId: ResourceLocation? = null,
        soundKey: String? = null
    ) {
        val payload = WeaponThirdPersonActionPayload(
            player.uuid,
            action,
            durationTicks.coerceIn(1, 20 * 30),
            player.level().gameTime,
            weaponId,
            soundKey
        )
        ServerPlayNetworking.send(player, payload)
        PlayerLookup.tracking(player).forEach { trackingPlayer ->
            ServerPlayNetworking.send(trackingPlayer, payload)
        }
    }

    fun broadcastVoidHunterSuperAura(player: ServerPlayer, durationTicks: Int) {
        val payload = VoidHunterSuperAuraPayload(player.uuid, durationTicks.coerceIn(1, 20 * 10))
        ServerPlayNetworking.send(player, payload)
        PlayerLookup.tracking(player).forEach { trackingPlayer ->
            ServerPlayNetworking.send(trackingPlayer, payload)
        }
    }

    fun syncNavigationState(player: ServerPlayer, power: GuardianPowerSnapshot = GuardianPowerRuntime.refresh(player)) {
        val stage = PlayerDestinyDataApi.get(player).journeyStage
        ServerPlayNetworking.send(
            player,
            SyncNavigationStatePayload(
                journeyStage = stage.id,
                currentPower = power.current,
                highestAvailablePower = power.highestAvailable,
                recommendedPower = power.activityRecommended,
                powerDeficit = power.deficit,
                suppressionPercent = power.suppressionPercent
            )
        )
    }

    fun syncWeaponLoadout(player: ServerPlayer) {
        val reserves = PlayerDestinyDataApi.get(player).weaponReserves
        ServerPlayNetworking.send(
            player,
            SyncWeaponLoadoutPayload(
                kinetic = reserves.stacks(DestinyWeaponSlot.KINETIC).map(ItemStack::copy),
                energy = reserves.stacks(DestinyWeaponSlot.ENERGY).map(ItemStack::copy),
                power = reserves.stacks(DestinyWeaponSlot.POWER).map(ItemStack::copy)
            )
        )
    }

    fun syncStatState(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        val stats = DestinyStatsResolver.resolve(player)
        val state = data.combatState
        val capacity = DestinyStatFormulas.healthShieldCapacity(stats)
        state.healthShield = state.healthShield.coerceIn(0.0f, capacity)
        ServerPlayNetworking.send(
            player,
            SyncStatStatePayload(
                weapons = stats.weapons,
                health = stats.health,
                classAbility = stats.classAbility,
                grenade = stats.grenade,
                superStat = stats.superStat,
                melee = stats.melee,
                superEnergy = DestinyStatFormulas.clampEnergy(state.superEnergy),
                healthShield = state.healthShield,
                healthShieldCapacity = capacity,
                classOvershield = state.classOvershield.coerceAtLeast(0.0f),
                armorCharge = state.armorCharge.coerceIn(0, ArmorModRuntime.maxArmorCharge(player)),
                armorChargeMax = ArmorModRuntime.maxArmorCharge(player)
            )
        )
    }

    fun syncCooldowns(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        val currentTime = player.serverLevel().gameTime
        AbilitySlot.entries.filterNot { it == AbilitySlot.SUPER }.forEach { slot ->
            val remainingTicks = (data.cooldowns.nextAvailableTick(slot) - currentTime).coerceAtLeast(0L)
            val totalTicks = data.cooldowns.totalDurationTicks(slot).coerceAtLeast(remainingTicks.toInt())
            ServerPlayNetworking.send(player, SyncCooldownPayload(slot.legacyNetworkId, remainingTicks.toInt(), totalTicks))
        }
    }

    private fun handleEquipDirectorItem(player: ServerPlayer, payload: EquipDirectorItemPayload) {
        val source = payload.sourceInventorySlot
        if (source !in 0..35) return
        val stack = player.inventory.getItem(source)
        if (stack.isEmpty) return
        if (BuiltInRegistries.ITEM.getKey(stack.item).toString() != payload.expectedItemId) return
        if (stack.components.hashCode() != payload.expectedComponentsHash) return

        val changed = when (payload.target) {
            "weapon_kinetic", "weapon_energy", "weapon_power" ->
                weaponSlotForTarget(payload.target)?.let { slot ->
                    WeaponLoadoutRuntime.equipFromInventory(player, source, slot)
                } == true
            "armor_head", "armor_chest", "armor_legs", "armor_feet" -> {
                val targetSlot = when (payload.target) {
                    "armor_head" -> EquipmentSlot.HEAD
                    "armor_chest" -> EquipmentSlot.CHEST
                    "armor_legs" -> EquipmentSlot.LEGS
                    else -> EquipmentSlot.FEET
                }
                if (player.getEquipmentSlotForItem(stack) != targetSlot) {
                    false
                } else {
                    val previous = player.getItemBySlot(targetSlot)
                    player.setItemSlot(targetSlot, stack)
                    player.inventory.setItem(source, previous)
                    true
                }
            }
            "class_item" -> {
                val item = stack.item as? DestinyClassItem
                val data = PlayerDestinyDataApi.get(player)
                if (
                    item == null ||
                    item.requiredClass != data.destinyClass ||
                    !data.isClassUnlocked(item.requiredClass)
                ) {
                    false
                } else {
                    val previous = data.classItem
                    data.classItem = stack
                    player.inventory.setItem(source, previous)
                    true
                }
            }
            else -> false
        }
        if (!changed) return
        player.inventory.setChanged()
        player.inventoryMenu.broadcastChanges()
        syncNavigationState(player)
        syncStatState(player)
    }

    private fun weaponSlotForTarget(target: String): DestinyWeaponSlot? =
        DestinyWeaponSlot.fromSerializedName(target.removePrefix("weapon_"))

    private fun handleSetLoadout(player: ServerPlayer, classId: String, subclassId: String) {
        val data = PlayerDestinyDataApi.get(player)
        val requestedClass = DestinyClassType.findById(classId)
        val requestedSubclass = DestinySubclassType.findById(subclassId)

        if (requestedSubclass != null) {
            if (player.isCreative) {
                ClassResonanceRuntime.unlockAllOptions(data, requestedSubclass.requiredClass)
            }
            if (!ClassResonanceRuntime.switchClass(player, requestedSubclass.requiredClass)) {
                player.displayClientMessage(Component.translatable("message.destiny2-mod.class_switch.denied"), true)
                return
            }
            data.setSubclass(requestedSubclass)
            syncPlayerData(player)
            return
        }

        if (requestedClass != null) {
            if (player.isCreative) {
                ClassResonanceRuntime.unlockAllOptions(data, requestedClass)
            }
            if (!ClassResonanceRuntime.switchClass(player, requestedClass)) {
                player.displayClientMessage(Component.translatable("message.destiny2-mod.class_switch.denied"), true)
                return
            }
            syncPlayerData(player)
        }
    }

    private fun handleConfigureSubclass(player: ServerPlayer, action: String, slotKey: String, optionId: String) {
        val data = PlayerDestinyDataApi.get(player)
        val config = data.subclassConfig
        val definition = DestinySubclassConfigRegistry.definitionFor(data.subclass)
        val optionExists = when (action) {
            "ability" -> AbilitySlot.fromKey(slotKey)?.let { slot ->
                definition.abilityOptions[slot].orEmpty().any { it.id == optionId }
            } == true
            "movement" -> definition.movementOptions.any { it.id == optionId }
            "aspect" -> definition.aspectOptions.any { it.id == optionId }
            "fragment" -> definition.fragmentOptions.any { it.id == optionId }
            else -> false
        }
        if (!optionExists) return
        if (!data.isSubclassOptionUnlocked(optionId)) {
            if (player.isCreative) {
                data.unlockedSubclassOptions += optionId
            } else {
                player.displayClientMessage(Component.translatable("message.destiny2-mod.subclass_option.locked"), true)
                return
            }
        }
        val changed = when (action) {
            "ability" -> {
                val slot = AbilitySlot.fromKey(slotKey) ?: return
                DestinySubclassConfigRegistry.setAbility(data.subclass, config, slot, optionId)
            }
            "movement" -> DestinySubclassConfigRegistry.setMovement(data.subclass, config, optionId)
            "aspect" -> DestinySubclassConfigRegistry.toggleAspect(data.subclass, config, optionId)
            "fragment" -> DestinySubclassConfigRegistry.toggleFragment(data.subclass, config, optionId)
            else -> false
        }

        if (changed) {
            data.subclassConfig = DestinySubclassConfigRegistry.normalize(data.subclass, config)
            data.restrictCurrentSubclassConfig()
            syncPlayerData(player)
        }
    }

    private fun handleCastAbility(player: ServerPlayer, abilityType: Int, extraData: Int) {
        val slot = AbilitySlot.fromLegacyNetworkId(abilityType) ?: return
        val level = player.serverLevel()
        val currentTime = level.gameTime
        val playerData = PlayerDestinyDataApi.get(player)

        if (player.hasEffect(DestinyEffects.SUPPRESSION)) {
            return
        }

        if (slot == AbilitySlot.SUPER && playerData.combatState.superEnergy < 100.0f) {
            syncStatState(player)
            return
        }

        if (slot != AbilitySlot.SUPER && !playerData.cooldowns.isReady(slot, currentTime)) {
            val remainingTicks = (playerData.cooldowns.nextAvailableTick(slot) - currentTime).coerceAtLeast(0L)
            if (remainingTicks > 0) {
                val totalTicks = playerData.cooldowns.totalDurationTicks(slot).coerceAtLeast(remainingTicks.toInt())
                ServerPlayNetworking.send(player, SyncCooldownPayload(slot.legacyNetworkId, remainingTicks.toInt(), totalTicks))
            }
            return
        }

        val ability = DestinyAbilityRegistry.abilityFor(playerData, slot) ?: return
        DestinyStatusRules.removeVoidInvisibility(player)
        val cast = ability.cast(DestinyAbilityContext(player, slot, extraData))
        if (!cast) {
            return
        }

        broadcastAbilityAction(player, ability.id)

        if (slot == AbilitySlot.CLASS_ABILITY) {
            SolarWarlockAspectRuntime.onClassAbilityCast(player)
        }

        if (slot == AbilitySlot.SUPER) {
            DestinyCombatRuntime.consumeSuper(player)
            playerData.cooldowns.clear(AbilitySlot.SUPER)
            return
        }

        if (ability.baseCooldownTicks <= 0) return

        val cooldownTicks = DestinyStatFormulas.cooldownTicks(ability.baseCooldownTicks, slot, DestinyStatsResolver.resolve(player))
        playerData.cooldowns.setCooldown(slot, currentTime, cooldownTicks)
        ArmorModRuntime.onAbilityCast(player, slot)
        val restoredArcBonusCharge = ArcTitanAspectRuntime.onAbilityCast(player, slot)
        if (!restoredArcBonusCharge) {
            ServerPlayNetworking.send(player, SyncCooldownPayload(slot.legacyNetworkId, cooldownTicks, cooldownTicks))
        }
        if (slot == AbilitySlot.CLASS_ABILITY) {
            val duration = if (playerData.destinyClass == DestinyClassType.HUNTER) 5 * 20 else 10 * 20
            DestinyCombatRuntime.grantClassOvershield(player, duration)
        }
    }

    /**
     * C is one contextual melee input: a ready melee charge always wins;
     * otherwise the request falls back to the ordinary close-range attack.
     */
    private fun handleMeleeInput(player: ServerPlayer, extraData: Int) {
        val data = PlayerDestinyDataApi.get(player)
        val chargedMeleeReady =
            !player.hasEffect(DestinyEffects.SUPPRESSION) &&
                data.cooldowns.isReady(AbilitySlot.MELEE, player.serverLevel().gameTime) &&
                DestinyAbilityRegistry.abilityFor(data, AbilitySlot.MELEE) != null

        if (chargedMeleeReady) {
            handleCastAbility(player, ABILITY_MELEE, extraData)
        } else {
            QuickMeleeRuntime.tryAttack(player)?.let { result ->
                ServerPlayNetworking.send(
                    player,
                    QuickMeleeAssistPayload(result.targetEntityId ?: -1, result.hunterMelee)
                )
            }
        }
    }

    private fun broadcastAbilityAction(player: ServerPlayer, abilityId: ResourceLocation) {
        val action = DestinyActionRegistry.definitionForAbility(abilityId) ?: return
        broadcastDestinyAction(player, action.id)
    }

    fun broadcastDestinyAction(player: ServerPlayer, actionId: ResourceLocation) {
        if (DestinyActionRegistry.definition(actionId) == null) return
        val payload = PlayDestinyActionPayload(player.uuid, actionId.toString())
        ServerPlayNetworking.send(player, payload)
        PlayerLookup.tracking(player).forEach { trackingPlayer ->
            ServerPlayNetworking.send(trackingPlayer, payload)
        }
    }

    private fun abilityName(data: atopos.destiny2.common.player.PlayerDestinyData, slot: AbilitySlot): String {
        return DestinyAbilityRegistry.abilityFor(data, slot)?.displayName.orEmpty()
    }

    private fun abilityId(
        data: atopos.destiny2.common.player.PlayerDestinyData,
        selectedId: String?,
        slot: AbilitySlot
    ): String {
        if (!selectedId.isNullOrBlank() && data.isSubclassOptionUnlocked(selectedId)) {
            return selectedId
        }
        return DestinyAbilityRegistry.abilityFor(data, slot)?.id?.toString().orEmpty()
    }

}
