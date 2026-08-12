package atopos.destiny2.common.aspect

import atopos.destiny2.common.ability.DestinyAbilityRegistry
import atopos.destiny2.common.combat.DestinyExplosionRuntime
import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.gear.ArmorModRuntime
import atopos.destiny2.common.entity.OrbOfPowerEntity
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponAmmoState
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import java.util.UUID

enum class VoidAbilitySource {
    GRENADE,
    MELEE,
    CLASS_ABILITY,
    SUPER
}

interface VoidAbilityDamageCarrier : DestinyAbilityDamageCarrier {
    val voidAbilitySource: VoidAbilitySource

    override val destinyAbilitySlot: AbilitySlot
        get() = when (voidAbilitySource) {
            VoidAbilitySource.GRENADE -> AbilitySlot.GRENADE
            VoidAbilitySource.MELEE -> AbilitySlot.MELEE
            VoidAbilitySource.CLASS_ABILITY -> AbilitySlot.CLASS_ABILITY
            VoidAbilitySource.SUPER -> AbilitySlot.SUPER
        }
}

object VoidHunterAspectRules {
    const val BASE_VORTEX_DURATION_TICKS = 4 * 20
    const val REMNANTS_VORTEX_DURATION_TICKS = 6 * 20
    const val PROVISION_COOLDOWN_REDUCTION_TICKS = 20
    const val PROVISION_TRIGGER_INTERVAL_TICKS = 20L
    const val PERSISTENCE_DURATION_MULTIPLIER = 1.5f
    const val SUCCESSFUL_HUNT_MAX_STACKS = 3
    const val SUCCESSFUL_HUNT_DURATION_TICKS = 7 * 20
    const val VOLATILE_ROUNDS_DURATION_TICKS = 10 * 20
    const val VOID_BREACH_CLASS_ENERGY_TICKS = 80

    fun vortexDuration(hasRemnants: Boolean): Int =
        if (hasRemnants) REMNANTS_VORTEX_DURATION_TICKS else BASE_VORTEX_DURATION_TICKS

    fun shouldApplyGrenadeWeaken(hasUndermining: Boolean): Boolean = hasUndermining

    fun canTriggerProvision(
        source: VoidAbilitySource,
        hasProvision: Boolean,
        currentTick: Long,
        nextEligibleTick: Long
    ): Boolean = source == VoidAbilitySource.GRENADE && hasProvision && currentTick >= nextEligibleTick

    fun successfulHuntReloadMultiplier(stacks: Int): Float = when (stacks.coerceIn(0, SUCCESSFUL_HUNT_MAX_STACKS)) {
        1 -> 0.90f
        2 -> 0.82f
        3 -> 0.75f
        else -> 1.0f
    }

    fun successfulHuntStabilityMultiplier(stacks: Int): Float = when (stacks.coerceIn(0, SUCCESSFUL_HUNT_MAX_STACKS)) {
        1 -> 0.94f
        2 -> 0.88f
        3 -> 0.82f
        else -> 1.0f
    }

    fun trappersAmbushDamage(classStat: Int, hasVoidBuff: Boolean): Float {
        val classScaling = 1.0f + DestinyStatFormulas.specialization(classStat) * 0.50f
        return (if (hasVoidBuff) 18.0f else 11.0f) * classScaling
    }

    fun trappersAmbushRadius(hasVoidBuff: Boolean): Double = if (hasVoidBuff) 6.0 else 4.25
}

/**
 * Server-authoritative Nightstalker Aspect and Fragment runtime.
 *
 * Names, fragment slots, descriptions and stat modifiers are taken from Bungie's
 * current 9.7 Simplified Chinese Destiny manifest. Destiny concepts without a
 * vanilla Minecraft equivalent use explicit adaptations:
 * - Truesight/enhanced radar: short, server-controlled glowing on nearby hostiles.
 * - Finisher: the project's existing direct close-range player final blow.
 * - Void Breach: a world pickup consumed before it can enter normal inventory.
 */
object VoidHunterAspectRuntime {
    const val ON_THE_PROWL = "destiny2-mod:aspect_on_the_prowl"
    const val TRAPPERS_AMBUSH = "destiny2-mod:aspect_trappers_ambush"
    const val STYLISH_EXECUTIONER = "destiny2-mod:aspect_stylish_executioner"
    const val VANISHING_STEP = "destiny2-mod:aspect_vanishing_step"

    const val ECHO_OF_EXCHANGE = "destiny2-mod:fragment_echo_of_exchange"
    const val ECHO_OF_CESSATION = "destiny2-mod:fragment_echo_of_cessation"
    const val ECHO_OF_LEECHING = "destiny2-mod:fragment_echo_of_leeching"
    const val ECHO_OF_PERSISTENCE = "destiny2-mod:fragment_echo_of_persistence"
    const val ECHO_OF_INSTABILITY = "destiny2-mod:fragment_echo_of_instability"
    const val ECHO_OF_UNDERMINING = "destiny2-mod:fragment_echo_of_undermining"
    const val ECHO_OF_DILATION = "destiny2-mod:fragment_echo_of_dilation"
    const val ECHO_OF_REPRISAL = "destiny2-mod:fragment_echo_of_reprisal"
    const val ECHO_OF_HARVEST = "destiny2-mod:fragment_echo_of_harvest"
    const val ECHO_OF_OBSCURITY = "destiny2-mod:fragment_echo_of_obscurity"
    const val ECHO_OF_REMNANTS = "destiny2-mod:fragment_echo_of_remnants"
    const val ECHO_OF_PROVISION = "destiny2-mod:fragment_echo_of_provision"
    const val ECHO_OF_VIGILANCE = "destiny2-mod:fragment_echo_of_vigilance"
    const val ECHO_OF_DOMINEERING = "destiny2-mod:fragment_echo_of_domineering"
    const val ECHO_OF_STARVATION = "destiny2-mod:fragment_echo_of_starvation"
    const val ECHO_OF_EXPULSION = "destiny2-mod:fragment_echo_of_expulsion"

    private const val MARK_RADIUS = 32.0
    private const val PASSIVE_MARK_INTERVAL_TICKS = 5 * 20L
    private const val TRUESIGHT_DURATION_TICKS = 5 * 20
    private const val STYLISH_LOCKOUT_TICKS = 2 * 20L
    private const val SMOKE_DURATION_TICKS = 5 * 20
    private const val SMOKE_RADIUS = 4.0
    private const val SUCCESSFUL_HUNT_RADIUS = 16.0
    private const val SUCCESSFUL_HUNT_ABILITY_ENERGY_TICKS = 60
    private const val DOMINEERING_DURATION_TICKS = 10 * 20
    private const val HARVEST_COOLDOWN_TICKS = 10 * 20L
    private const val REPRISAL_COOLDOWN_TICKS = 20L
    private const val REPRISAL_SURROUNDED_COUNT = 3
    private const val REPRISAL_RADIUS = 8.0
    private const val REPRISAL_SUPER_ENERGY = 2.5f
    private const val VOLATILE_EXPLOSION_RADIUS = 4.0
    private const val VOLATILE_EXPLOSION_DAMAGE = 8.0f
    private const val FINISHER_RADIUS_SQUARED = 9.0
    private const val QUICKFALL_HIT_MARKER_TICKS = 2L
    private const val VOID_OVERSHIELD_DURATION_TICKS = 10 * 20
    private const val DEVOUR_DURATION_TICKS = 10 * 20

    private val voidWeaponTag = TagKey.create(
        Registries.ITEM,
        ResourceLocation.fromNamespaceAndPath("destiny2-mod", "void_weapons")
    )

    private data class TimedStacks(var stacks: Int, var expiresAt: Long)
    private data class SmokeZone(
        val level: ServerLevel,
        val ownerId: UUID,
        val position: Vec3,
        val expiresAt: Long
    )
    private data class Quickfall(var startedAt: Long)

    private val provisionNextEligibleTick = mutableMapOf<UUID, Long>()
    private val markedTargets = mutableMapOf<UUID, UUID>()
    private val nextPassiveMarkTick = mutableMapOf<UUID, Long>()
    private val successfulHunt = mutableMapOf<UUID, TimedStacks>()
    private val truesightUntil = mutableMapOf<UUID, Long>()
    private val stylishMeleeReady = mutableSetOf<UUID>()
    private val stylishLockoutUntil = mutableMapOf<UUID, Long>()
    private val volatileRoundsUntil = mutableMapOf<UUID, Long>()
    private val domineeringUntil = mutableMapOf<UUID, Long>()
    private val suppressionTriggers = mutableMapOf<Pair<UUID, UUID>, Long>()
    private val harvestNextEligibleTick = mutableMapOf<UUID, Long>()
    private val reprisalNextEligibleTick = mutableMapOf<UUID, Long>()
    private val smokeZones = mutableListOf<SmokeZone>()
    private val quickfalls = mutableMapOf<UUID, Quickfall>()
    private val quickfallDamage = mutableMapOf<Pair<UUID, UUID>, Long>()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register(::tickServer)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            clearPlayer(handler.player.uuid)
        }
    }

    fun vortexDuration(owner: ServerPlayer?): Int = VoidHunterAspectRules.vortexDuration(
        owner != null && DestinyAspectRuntime.hasFragment(owner, ECHO_OF_REMNANTS)
    )

    fun shouldApplyGrenadeWeaken(owner: ServerPlayer?): Boolean =
        owner != null && VoidHunterAspectRules.shouldApplyGrenadeWeaken(
            DestinyAspectRuntime.hasFragment(owner, ECHO_OF_UNDERMINING)
        )

    fun modifyVoidBuffDuration(target: LivingEntity, durationTicks: Int): Int {
        val player = target as? ServerPlayer ?: return durationTicks
        return if (DestinyAspectRuntime.hasFragment(player, ECHO_OF_PERSISTENCE)) {
            (durationTicks * VoidHunterAspectRules.PERSISTENCE_DURATION_MULTIPLIER).toInt()
        } else {
            durationTicks
        }
    }

    fun fragmentStatBonuses(player: ServerPlayer): DestinyStats {
        fun has(id: String) = DestinyAspectRuntime.hasFragment(player, id)
        return DestinyStats(
            weapons = (if (has(ECHO_OF_DILATION)) 10 else 0) + (if (has(ECHO_OF_PERSISTENCE)) -10 else 0),
            health = (if (has(ECHO_OF_LEECHING)) 10 else 0) + (if (has(ECHO_OF_PERSISTENCE)) -10 else 0),
            classAbility = (if (has(ECHO_OF_OBSCURITY)) 10 else 0) +
                (if (has(ECHO_OF_PERSISTENCE)) -10 else 0) +
                (if (has(ECHO_OF_STARVATION)) -10 else 0),
            grenade = (if (has(ECHO_OF_DOMINEERING)) 10 else 0) +
                (if (has(ECHO_OF_PROVISION)) 10 else 0) +
                (if (has(ECHO_OF_UNDERMINING)) -10 else 0),
            superStat = (if (has(ECHO_OF_DILATION)) 10 else 0) + (if (has(ECHO_OF_EXPULSION)) 10 else 0),
            melee = (if (has(ECHO_OF_EXCHANGE)) 10 else 0) + (if (has(ECHO_OF_INSTABILITY)) 10 else 0)
        )
    }

    fun reloadMultiplier(player: ServerPlayer): Float {
        val now = player.serverLevel().gameTime
        val hunt = successfulHunt[player.uuid]?.takeIf { it.expiresAt > now }?.stacks ?: 0
        val domineering = if ((domineeringUntil[player.uuid] ?: Long.MIN_VALUE) > now) 0.85f else 1.0f
        return VoidHunterAspectRules.successfulHuntReloadMultiplier(hunt) * domineering
    }

    fun stabilityMultiplier(player: ServerPlayer): Float {
        val now = player.serverLevel().gameTime
        val hunt = successfulHunt[player.uuid]?.takeIf { it.expiresAt > now }?.stacks ?: 0
        val domineering = if ((domineeringUntil[player.uuid] ?: Long.MIN_VALUE) > now) 0.88f else 1.0f
        return VoidHunterAspectRules.successfulHuntStabilityMultiplier(hunt) * domineering
    }

    fun onInvisibilityApplied(player: ServerPlayer) {
        if (DestinyAspectRuntime.hasAspect(player, ON_THE_PROWL)) {
            val current = markedTargets[player.uuid]
                ?.let(player.serverLevel()::getEntity) as? LivingEntity
            if (current != null && current.isAlive) return
            markPriorityTarget(player, immediate = true)
        }
    }

    fun onVoidAbilityDamage(owner: ServerPlayer, source: VoidAbilitySource) {
        val now = owner.serverLevel().gameTime
        val hasProvision = DestinyAspectRuntime.hasFragment(owner, ECHO_OF_PROVISION)
        val nextEligible = provisionNextEligibleTick[owner.uuid] ?: Long.MIN_VALUE
        if (!VoidHunterAspectRules.canTriggerProvision(source, hasProvision, now, nextEligible)) return

        val reduced = reduceCooldown(owner, AbilitySlot.MELEE, VoidHunterAspectRules.PROVISION_COOLDOWN_REDUCTION_TICKS)
        if (reduced <= 0) return
        provisionNextEligibleTick[owner.uuid] = now + VoidHunterAspectRules.PROVISION_TRIGGER_INTERVAL_TICKS
        DestinyStatusRules.syncTemporaryBuff(
            owner,
            "destiny2-mod:fragment/echo_of_provision",
            "补能回声",
            VoidHunterAspectRules.PROVISION_TRIGGER_INTERVAL_TICKS.toInt(),
            reduced
        )
    }

    fun onSuppressed(source: ServerPlayer?, target: LivingEntity) {
        source ?: return
        if (!DestinyAspectRuntime.hasFragment(source, ECHO_OF_DOMINEERING)) return
        val now = source.serverLevel().gameTime
        val key = source.uuid to target.uuid
        if ((suppressionTriggers[key] ?: Long.MIN_VALUE) > now) return
        suppressionTriggers[key] = now + 20L
        domineeringUntil[source.uuid] = now + DOMINEERING_DURATION_TICKS
        reloadEquippedWeaponFromReserves(source)
        DestinyStatusRules.syncTemporaryBuff(
            source,
            "destiny2-mod:fragment/echo_of_domineering",
            "霸道回声",
            DOMINEERING_DURATION_TICKS
        )
    }

    fun onWeaponHit(attacker: ServerPlayer, target: LivingEntity, source: DamageSource) {
        if (source.directEntity is DestinyAbilityDamageCarrier) return
        val now = attacker.serverLevel().gameTime
        if ((volatileRoundsUntil[attacker.uuid] ?: Long.MIN_VALUE) <= now) return
        if (!attacker.mainHandItem.`is`(voidWeaponTag)) return
        DestinyStatusRules.applyVolatile(target, 10 * 20)
    }

    fun onMeleeHit(attacker: ServerPlayer, target: LivingEntity, source: DamageSource) {
        if (source.directEntity !== attacker || isQuickfallDamage(attacker, target)) return
        if (!stylishMeleeReady.contains(attacker.uuid)) return
        if (!attacker.hasEffect(DestinyEffects.VOID_INVISIBILITY)) return
        stylishMeleeReady.remove(attacker.uuid)
        DestinyStatusRules.applyWeaken(target, 8 * 20)
    }

    fun onKill(
        attacker: ServerPlayer,
        killed: LivingEntity,
        source: DamageSource,
        wasWeakened: Boolean,
        wasSuppressed: Boolean,
        wasVolatile: Boolean
    ) {
        val now = attacker.serverLevel().gameTime
        val quickfallKill = isQuickfallDamage(attacker, killed)
        val carrier = source.directEntity as? VoidAbilityDamageCarrier
        val voidAbilityKill = carrier != null || quickfallKill
        val meleeFinalBlow = source.directEntity === attacker && !quickfallKill
        val finisherFinalBlow = meleeFinalBlow && attacker.distanceToSqr(killed) <= FINISHER_RADIUS_SQUARED

        resolvePriorityTargetKill(attacker, killed)

        if (
            DestinyAspectRuntime.hasAspect(attacker, STYLISH_EXECUTIONER) &&
            (wasWeakened || wasSuppressed || wasVolatile) &&
            (stylishLockoutUntil[attacker.uuid] ?: Long.MIN_VALUE) <= now
        ) {
            DestinyStatusRules.applyVoidInvisibility(attacker, TRUESIGHT_DURATION_TICKS)
            truesightUntil[attacker.uuid] = now + TRUESIGHT_DURATION_TICKS
            stylishMeleeReady += attacker.uuid
            stylishLockoutUntil[attacker.uuid] = now + STYLISH_LOCKOUT_TICKS
            DestinyStatusRules.syncTemporaryBuff(
                attacker,
                "destiny2-mod:aspect/stylish_executioner",
                "潇洒行刑者",
                TRUESIGHT_DURATION_TICKS
            )
        }

        if (meleeFinalBlow && DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_EXCHANGE)) {
            reduceCooldown(attacker, AbilitySlot.GRENADE, 40)
        }
        if (meleeFinalBlow && DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_LEECHING)) {
            applyLeechingRegeneration(attacker)
        }
        if (finisherFinalBlow && DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_CESSATION)) {
            createCessationBurst(attacker, killed.position())
        }
        if (finisherFinalBlow && DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_OBSCURITY)) {
            DestinyStatusRules.applyVoidInvisibility(attacker, 5 * 20)
        }
        if (
            carrier?.voidAbilitySource == VoidAbilitySource.GRENADE &&
            DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_INSTABILITY)
        ) {
            volatileRoundsUntil[attacker.uuid] = now + VoidHunterAspectRules.VOLATILE_ROUNDS_DURATION_TICKS
            DestinyStatusRules.syncTemporaryBuff(
                attacker,
                "destiny2-mod:fragment/echo_of_instability",
                "不稳定弹药",
                VoidHunterAspectRules.VOLATILE_ROUNDS_DURATION_TICKS
            )
        }
        if (voidAbilityKill && DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_EXPULSION)) {
            createVoidExplosion(attacker, killed.position())
        }
        if (
            wasWeakened &&
            DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_HARVEST) &&
            (harvestNextEligibleTick[attacker.uuid] ?: Long.MIN_VALUE) <= now
        ) {
            harvestNextEligibleTick[attacker.uuid] = now + HARVEST_COOLDOWN_TICKS
            spawnOrb(attacker.serverLevel(), killed.position())
            spawnVoidBreach(attacker.serverLevel(), killed.position())
        }
        if (
            DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_REPRISAL) &&
            (reprisalNextEligibleTick[attacker.uuid] ?: Long.MIN_VALUE) <= now &&
            isSurrounded(attacker)
        ) {
            reprisalNextEligibleTick[attacker.uuid] = now + REPRISAL_COOLDOWN_TICKS
            val state = PlayerDestinyDataApi.get(attacker).combatState
            state.superEnergy = (state.superEnergy + REPRISAL_SUPER_ENERGY).coerceAtMost(100.0f)
            DestinyNetworking.syncStatState(attacker)
        }
        if (
            DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_VIGILANCE) &&
            shieldsAreDepleted(attacker)
        ) {
            DestinyStatusRules.applyVoidOvershield(attacker, VOID_OVERSHIELD_DURATION_TICKS)
        }
        if (
            (wasVolatile && DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_CESSATION)) ||
            (wasSuppressed && DestinyAspectRuntime.hasFragment(attacker, ECHO_OF_DOMINEERING))
        ) {
            spawnVoidBreach(attacker.serverLevel(), killed.position())
        }
    }

    fun onOrbPickup(player: ServerPlayer) {
        if (DestinyAspectRuntime.hasFragment(player, ECHO_OF_STARVATION)) {
            DestinyStatusRules.applyDevour(player, DEVOUR_DURATION_TICKS)
        }
    }

    fun onVoidBreachPickup(player: ServerPlayer) {
        reduceCooldown(player, AbilitySlot.CLASS_ABILITY, VoidHunterAspectRules.VOID_BREACH_CLASS_ENERGY_TICKS)
        if (DestinyAspectRuntime.hasFragment(player, ECHO_OF_STARVATION)) {
            DestinyStatusRules.applyDevour(player, DEVOUR_DURATION_TICKS)
        }
        DestinyStatusRules.syncTemporaryBuff(player, "destiny2-mod:pickup/void_breach", "虚空裂口", 20)
    }

    fun isQuickfallDamage(attacker: ServerPlayer, target: LivingEntity): Boolean {
        val expiresAt = quickfallDamage[attacker.uuid to target.uuid] ?: return false
        return expiresAt >= attacker.serverLevel().gameTime
    }

    fun tryStartTrappersAmbush(player: ServerPlayer): Boolean {
        if (!isVoidHunter(player) || !DestinyAspectRuntime.hasAspect(player, TRAPPERS_AMBUSH)) return false
        if (player.onGround() || player.isSpectator || player.abilities.flying || player.isFallFlying || player.isPassenger) return false
        if (player.hasEffect(DestinyEffects.SUPPRESSION) || quickfalls.containsKey(player.uuid)) return false

        val data = PlayerDestinyDataApi.get(player)
        val now = player.serverLevel().gameTime
        if (!data.cooldowns.isReady(AbilitySlot.CLASS_ABILITY, now)) return false
        val classAbility = DestinyAbilityRegistry.abilityFor(data, AbilitySlot.CLASS_ABILITY) ?: return false
        val cooldown = DestinyStatFormulas.cooldownTicks(
            classAbility.baseCooldownTicks,
            AbilitySlot.CLASS_ABILITY,
            DestinyStatsResolver.resolve(player)
        )
        data.cooldowns.setCooldown(AbilitySlot.CLASS_ABILITY, now, cooldown)
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.SyncCooldownPayload(AbilitySlot.CLASS_ABILITY.legacyNetworkId, cooldown, cooldown)
        )
        ArmorModRuntime.onAbilityCast(player, AbilitySlot.CLASS_ABILITY)
        quickfalls[player.uuid] = Quickfall(now)
        player.fallDistance = 0.0f
        player.deltaMovement = Vec3(player.deltaMovement.x * 0.25, -1.35, player.deltaMovement.z * 0.25)
        player.hasImpulse = true
        player.hurtMarked = true
        player.serverLevel().sendParticles(
            ParticleTypes.DRAGON_BREATH,
            player.x,
            player.y + 0.25,
            player.z,
            18,
            0.45,
            0.15,
            0.45,
            0.04
        )
        return true
    }

    private fun tickServer(server: MinecraftServer) {
        server.playerList.players.forEach(::tickPlayer)
        tickQuickfalls(server)
        tickSmokeZones()
        val nowByServer = server.overworld().gameTime
        quickfallDamage.entries.removeIf { it.value < nowByServer }
        suppressionTriggers.entries.removeIf { it.value < nowByServer }
    }

    private fun tickPlayer(player: ServerPlayer) {
        val now = player.serverLevel().gameTime
        if (!isVoidHunter(player)) {
            markedTargets.remove(player.uuid)
            return
        }
        if (DestinyAspectRuntime.hasAspect(player, ON_THE_PROWL)) {
            val current = markedTargets[player.uuid]?.let(player.serverLevel()::getEntity) as? LivingEntity
            if (current == null || !current.isAlive) {
                markedTargets.remove(player.uuid)
                if ((nextPassiveMarkTick[player.uuid] ?: Long.MIN_VALUE) <= now) {
                    markPriorityTarget(player, immediate = false)
                }
            } else {
                current.addEffect(MobEffectInstance(MobEffects.GLOWING, 10, 0, true, false, false))
            }
        } else {
            markedTargets.remove(player.uuid)
        }

        successfulHunt[player.uuid]?.takeIf { it.expiresAt <= now }?.let { successfulHunt.remove(player.uuid) }
        if ((truesightUntil[player.uuid] ?: Long.MIN_VALUE) > now && player.tickCount % 5 == 0) {
            revealNearbyHostiles(player, 24.0)
        }
        if (DestinyAspectRuntime.hasFragment(player, ECHO_OF_DILATION) && player.isCrouching) {
            player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 8, 0, true, false, false))
            if (player.tickCount % 5 == 0) revealNearbyHostiles(player, 12.0)
        }
    }

    private fun tickQuickfalls(server: MinecraftServer) {
        val iterator = quickfalls.iterator()
        while (iterator.hasNext()) {
            val (playerId, state) = iterator.next()
            val player = server.playerList.getPlayer(playerId)
            if (player == null || !player.isAlive || player.isSpectator) {
                iterator.remove()
                continue
            }
            if (!player.onGround()) {
                player.fallDistance = 0.0f
                player.deltaMovement = Vec3(player.deltaMovement.x * 0.70, minOf(player.deltaMovement.y, -1.35), player.deltaMovement.z * 0.70)
                player.hasImpulse = true
                player.hurtMarked = true
                continue
            }
            iterator.remove()
            if (player.serverLevel().gameTime > state.startedAt + 60L) continue
            resolveTrappersAmbushImpact(player)
        }
    }

    private fun resolveTrappersAmbushImpact(player: ServerPlayer) {
        val level = player.serverLevel()
        val hadVoidBuff = hasPositiveVoidBuff(player)
        val stats = DestinyStatsResolver.resolve(player)
        val radius = VoidHunterAspectRules.trappersAmbushRadius(hadVoidBuff)
        val damage = VoidHunterAspectRules.trappersAmbushDamage(stats.classAbility, hadVoidBuff)
        val targets = level.getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(radius, 2.0, radius)
        ) { target ->
            target !== player && target.isAlive && !target.isAlliedTo(player) &&
                target.position().distanceToSqr(player.position()) <= radius * radius
        }

        var hits = 0
        var kills = 0
        for (target in targets) {
            quickfallDamage[player.uuid to target.uuid] = level.gameTime + QUICKFALL_HIT_MARKER_TICKS
            val wasAlive = target.isAlive
            if (target.hurt(player.damageSources().playerAttack(player), damage)) {
                hits++
                if (wasAlive && !target.isAlive) kills++
            }
        }
        if (hits > 0) player.heal((hits * 2.0f).coerceAtMost(8.0f))
        if (kills > 0) DestinyStatusRules.applyDevour(player, DEVOUR_DURATION_TICKS)
        if (player.hasEffect(DestinyEffects.VOID_INVISIBILITY)) {
            DestinyStatusRules.applyVoidInvisibility(player, 5 * 20)
        }
        if (DestinyAspectRuntime.hasAspect(player, VANISHING_STEP)) {
            createSmokeZone(level, player, player.position())
        }
        level.sendParticles(ParticleTypes.DRAGON_BREATH, player.x, player.y + 0.2, player.z, 80, radius * 0.65, 0.35, radius * 0.65, 0.08)
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.75f, 0.62f)
    }

    private fun markPriorityTarget(player: ServerPlayer, immediate: Boolean) {
        if (!DestinyAspectRuntime.hasAspect(player, ON_THE_PROWL)) return
        val now = player.serverLevel().gameTime
        val target = player.serverLevel().getEntitiesOfClass(
            Monster::class.java,
            player.boundingBox.inflate(MARK_RADIUS)
        ) { candidate ->
            candidate.isAlive && !candidate.isAlliedTo(player) &&
                candidate !is WitherBoss && candidate !is Warden &&
                candidate.position().distanceToSqr(player.position()) <= MARK_RADIUS * MARK_RADIUS
        }.minByOrNull(player::distanceToSqr)
        nextPassiveMarkTick[player.uuid] = now + PASSIVE_MARK_INTERVAL_TICKS
        if (target == null) return
        markedTargets[player.uuid] = target.uuid
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, if (immediate) 30 else 15, 0, true, false, false))
        DestinyStatusRules.syncTemporaryBuff(
            player,
            "destiny2-mod:aspect/on_the_prowl",
            "首要目标",
            PASSIVE_MARK_INTERVAL_TICKS.toInt()
        )
    }

    private fun resolvePriorityTargetKill(killer: ServerPlayer, killed: LivingEntity) {
        val owners = markedTargets.entries
            .filter { it.value == killed.uuid }
            .mapNotNull { (ownerId, _) -> killer.server.playerList.getPlayer(ownerId) }
            .filter { owner -> owner === killer || owner.isAlliedTo(killer) }
        owners.forEach { owner ->
            markedTargets.remove(owner.uuid)
            nextPassiveMarkTick[owner.uuid] = owner.serverLevel().gameTime + PASSIVE_MARK_INTERVAL_TICKS
            createSmokeZone(owner.serverLevel(), owner, killed.position())
            grantSuccessfulHunt(owner)
        }
    }

    private fun grantSuccessfulHunt(owner: ServerPlayer) {
        val now = owner.serverLevel().gameTime
        owner.serverLevel().players()
            .filter { ally ->
                ally.isAlive && !ally.isSpectator &&
                    (ally === owner || ally.isAlliedTo(owner)) &&
                    ally.distanceToSqr(owner) <= SUCCESSFUL_HUNT_RADIUS * SUCCESSFUL_HUNT_RADIUS
            }
            .forEach { ally ->
                val state = successfulHunt.getOrPut(ally.uuid) { TimedStacks(0, now) }
                state.stacks = (state.stacks + 1).coerceAtMost(VoidHunterAspectRules.SUCCESSFUL_HUNT_MAX_STACKS)
                state.expiresAt = now + VoidHunterAspectRules.SUCCESSFUL_HUNT_DURATION_TICKS
                reduceCooldown(ally, AbilitySlot.GRENADE, SUCCESSFUL_HUNT_ABILITY_ENERGY_TICKS)
                reduceCooldown(ally, AbilitySlot.MELEE, SUCCESSFUL_HUNT_ABILITY_ENERGY_TICKS)
                reduceCooldown(ally, AbilitySlot.CLASS_ABILITY, SUCCESSFUL_HUNT_ABILITY_ENERGY_TICKS)
                DestinyStatusRules.syncTemporaryBuff(
                    ally,
                    "destiny2-mod:aspect/successful_hunt",
                    "成功狩猎",
                    VoidHunterAspectRules.SUCCESSFUL_HUNT_DURATION_TICKS,
                    state.stacks
                )
            }
    }

    private fun createSmokeZone(level: ServerLevel, owner: ServerPlayer, position: Vec3) {
        smokeZones += SmokeZone(level, owner.uuid, position, level.gameTime + SMOKE_DURATION_TICKS)
    }

    private fun tickSmokeZones() {
        val iterator = smokeZones.iterator()
        while (iterator.hasNext()) {
            val zone = iterator.next()
            if (zone.level.gameTime >= zone.expiresAt) {
                iterator.remove()
                continue
            }
            val owner = zone.level.server.playerList.getPlayer(zone.ownerId) ?: continue
            val area = AABB.ofSize(zone.position, SMOKE_RADIUS * 2.0, 5.0, SMOKE_RADIUS * 2.0)
            zone.level.getEntitiesOfClass(LivingEntity::class.java, area) { it.isAlive }.forEach { entity ->
                if (entity.position().distanceToSqr(zone.position) > SMOKE_RADIUS * SMOKE_RADIUS) return@forEach
                if (entity === owner || entity.isAlliedTo(owner)) {
                    val remaining = entity.getEffect(DestinyEffects.VOID_INVISIBILITY)?.duration ?: 0
                    if (remaining < 10) {
                        DestinyStatusRules.applyVoidInvisibility(entity, 30)
                    }
                } else {
                    DestinyStatusRules.applyWeaken(entity, 40)
                }
            }
            if (zone.level.gameTime % 4L == 0L) {
                zone.level.sendParticles(
                    ParticleTypes.DRAGON_BREATH,
                    zone.position.x,
                    zone.position.y + 0.35,
                    zone.position.z,
                    12,
                    SMOKE_RADIUS * 0.55,
                    0.55,
                    SMOKE_RADIUS * 0.55,
                    0.02
                )
            }
        }
    }

    private fun revealNearbyHostiles(player: ServerPlayer, radius: Double) {
        player.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(radius)
        ) { target ->
            target !== player && target.isAlive && !target.isAlliedTo(player)
        }.forEach { target ->
            target.addEffect(MobEffectInstance(MobEffects.GLOWING, 10, 0, true, false, false))
        }
    }

    private fun applyLeechingRegeneration(player: ServerPlayer) {
        player.serverLevel().players()
            .filter { ally ->
                ally.isAlive && (ally === player || ally.isAlliedTo(player)) && ally.distanceToSqr(player) <= 8.0 * 8.0
            }
            .forEach { ally ->
                ally.addEffect(MobEffectInstance(MobEffects.REGENERATION, 5 * 20, 0, false, false, true))
            }
    }

    private fun createCessationBurst(player: ServerPlayer, center: Vec3) {
        val radius = 4.0
        player.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(center, radius * 2.0, radius * 2.0, radius * 2.0)
        ) { target ->
            target !== player && target.isAlive && !target.isAlliedTo(player)
        }.forEach { target ->
            if (target.position().distanceToSqr(center) <= radius * radius) {
                // Keep kill ownership without classifying the burst as another direct
                // close-range melee final blow (which would recursively retrigger Cessation).
                DestinyExplosionRuntime.hurtWithoutKnockback(
                    target,
                    player.damageSources().explosion(null, player),
                    4.0f
                )
                DestinyStatusRules.applyVolatile(target, 10 * 20)
            }
        }
        player.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 0.6, center.z, 40, 1.2, 0.8, 1.2, 0.08)
    }

    private fun createVoidExplosion(player: ServerPlayer, center: Vec3) {
        player.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(
                center,
                VOLATILE_EXPLOSION_RADIUS * 2.0,
                VOLATILE_EXPLOSION_RADIUS * 2.0,
                VOLATILE_EXPLOSION_RADIUS * 2.0
            )
        ) { target ->
            target !== player && target.isAlive && !target.isAlliedTo(player)
        }.forEach { target ->
            if (target.position().distanceToSqr(center) <= VOLATILE_EXPLOSION_RADIUS * VOLATILE_EXPLOSION_RADIUS) {
                DestinyExplosionRuntime.hurtWithoutKnockback(
                    target,
                    player.damageSources().explosion(null, player),
                    VOLATILE_EXPLOSION_DAMAGE
                )
            }
        }
        player.serverLevel().sendParticles(ParticleTypes.DRAGON_BREATH, center.x, center.y + 0.6, center.z, 55, 1.4, 1.0, 1.4, 0.12)
    }

    private fun spawnOrb(level: ServerLevel, position: Vec3) {
        OrbOfPowerEntity.spawn(level, position.add(0.0, 0.35, 0.0))
    }

    private fun spawnVoidBreach(level: ServerLevel, position: Vec3) {
        level.addFreshEntity(ItemEntity(level, position.x, position.y + 0.25, position.z, net.minecraft.world.item.ItemStack(DestinyItems.VOID_BREACH)))
    }

    private fun reloadEquippedWeaponFromReserves(player: ServerPlayer) {
        val stack = player.mainHandItem
        val weapon = stack.item as? DestinyRangedWeapon ?: return
        WeaponAmmoState.reloadFromReservesInstantly(stack, player, weapon.combatProfile(stack))
    }

    private fun isSurrounded(player: ServerPlayer): Boolean {
        return player.serverLevel().getEntitiesOfClass(
            Monster::class.java,
            player.boundingBox.inflate(REPRISAL_RADIUS)
        ) { it.isAlive && !it.isAlliedTo(player) }.size >= REPRISAL_SURROUNDED_COUNT
    }

    private fun shieldsAreDepleted(player: ServerPlayer): Boolean {
        val state = PlayerDestinyDataApi.get(player).combatState
        return state.healthShield <= 0.0f && state.classOvershield <= 0.0f && player.absorptionAmount <= 0.0f
    }

    private fun hasPositiveVoidBuff(player: ServerPlayer): Boolean {
        return player.hasEffect(DestinyEffects.VOID_INVISIBILITY) ||
            player.hasEffect(DestinyEffects.DEVOUR) ||
            player.hasEffect(DestinyEffects.VOID_OVERSHIELD)
    }

    private fun reduceCooldown(player: ServerPlayer, slot: AbilitySlot, ticks: Int): Int {
        if (ticks <= 0) return 0
        val now = player.serverLevel().gameTime
        val data = PlayerDestinyDataApi.get(player)
        val reduced = data.cooldowns.reduce(slot, now, ticks)
        if (reduced > 0) {
            val remaining = (data.cooldowns.nextAvailableTick(slot) - now).toInt().coerceAtLeast(0)
            ServerPlayNetworking.send(
                player,
                DestinyNetworking.SyncCooldownPayload(
                    slot.legacyNetworkId,
                    remaining,
                    data.cooldowns.totalDurationTicks(slot).coerceAtLeast(remaining)
                )
            )
        }
        return reduced
    }

    private fun isVoidHunter(player: ServerPlayer): Boolean =
        PlayerDestinyDataApi.get(player).subclass == DestinySubclassType.VOID_HUNTER

    private fun clearPlayer(playerId: UUID) {
        provisionNextEligibleTick.remove(playerId)
        markedTargets.remove(playerId)
        nextPassiveMarkTick.remove(playerId)
        successfulHunt.remove(playerId)
        truesightUntil.remove(playerId)
        stylishMeleeReady.remove(playerId)
        stylishLockoutUntil.remove(playerId)
        volatileRoundsUntil.remove(playerId)
        domineeringUntil.remove(playerId)
        harvestNextEligibleTick.remove(playerId)
        reprisalNextEligibleTick.remove(playerId)
        quickfalls.remove(playerId)
        suppressionTriggers.keys.removeIf { it.first == playerId }
        quickfallDamage.keys.removeIf { it.first == playerId }
        smokeZones.removeIf { it.ownerId == playerId }
    }
}
