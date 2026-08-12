package atopos.destiny2.common.aspect

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.effect.SolarIgnitionRuntime
import atopos.destiny2.common.effect.SolarScorchContext
import atopos.destiny2.common.entity.OrbOfPowerEntity
import atopos.destiny2.common.entity.FirespriteEntity
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.weapon.DestinyDamageElement
import atopos.destiny2.common.weapon.DestinyElementalDamageCarrier
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import java.util.UUID
import kotlin.math.ceil

/** Server-owned bridge from the pure Solar fragment rules into live combat. */
object SolarWarlockFragmentRuntime {
    private const val MINECRAFT_CALIBRATION_TORCHES_RADIUS = 8.0
    private const val MINECRAFT_CALIBRATION_TEMPERING_ALLY_RADIUS = 8.0
    private const val MINECRAFT_CALIBRATION_MERCY_REVIVE_RADIUS = 8.0
    private const val MINECRAFT_CALIBRATION_FIRESPRITE_COOLDOWN_TICKS = 5 * 20L

    private data class TemperingState(val stacks: Int, val expiresAt: Long)

    private val singeingActiveUntil = mutableMapOf<UUID, Long>()
    private val benevolenceActiveUntil = mutableMapOf<UUID, Long>()
    private val temperingStates = mutableMapOf<UUID, TemperingState>()
    private val nextFirespriteEligibleTick = mutableMapOf<UUID, Long>()
    private val torchesProcessedCasts = mutableMapOf<UUID, Long>()
    private val wonderStates = mutableMapOf<UUID, SolarIgnitionMultikillState>()

    fun register() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> clear(handler.player.uuid) }
    }

    fun clear(playerId: UUID) {
        singeingActiveUntil.remove(playerId)
        benevolenceActiveUntil.remove(playerId)
        temperingStates.remove(playerId)
        nextFirespriteEligibleTick.remove(playerId)
        wonderStates.remove(playerId)
        DestinyAspectRuntime.clearPlayer(playerId)
    }

    fun tickPlayer(player: ServerPlayer) {
        val now = player.serverLevel().gameTime
        tickSingeing(player, now)
        tickBenevolence(player, now)
        temperingStates[player.uuid]?.takeIf { now >= it.expiresAt }?.let {
            temperingStates.remove(player.uuid)
            DestinyNetworking.syncStatState(player)
        }
    }

    fun onScorchApplied(source: ServerPlayer?, target: LivingEntity) {
        source ?: return
        if (!SolarWarlockFragmentRules.singeingTriggers(
                DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_SINGEING),
                appliedScorchToCombatant = isCombatant(source, target)
            )
        ) return

        val duration = SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_SINGEING_DURATION_TICKS
        singeingActiveUntil[source.uuid] = source.serverLevel().gameTime + duration
        DestinyStatusRules.syncTemporaryBuff(
            source,
            "destiny2-mod:fragment/ember_of_singeing",
            "焦燃余烬",
            duration
        )
    }

    fun onPoweredMeleeHit(attacker: ServerPlayer, target: LivingEntity, castId: UUID = UUID.randomUUID()) {
        val now = attacker.serverLevel().gameTime
        torchesProcessedCasts.entries.removeIf { it.value <= now }
        if (!SolarWarlockFragmentRules.torchesTriggers(
                DestinyAspectRuntime.hasFragment(attacker, SolarWarlockFragmentRules.EMBER_OF_TORCHES),
                SolarFragmentCombatSource.SOLAR_POWERED_MELEE,
                targetIsCombatant = target !== attacker &&
                    (target is Enemy || (target is Player && !attacker.isAlliedTo(target)))
            )
        ) return
        if (torchesProcessedCasts.putIfAbsent(castId, now + 20L) != null) return

        val level = attacker.serverLevel()
        level.getEntitiesOfClass(
            ServerPlayer::class.java,
            attacker.boundingBox.inflate(MINECRAFT_CALIBRATION_TORCHES_RADIUS)
        ) { candidate ->
            candidate.isAlive && !candidate.isSpectator &&
                (candidate === attacker || candidate.isAlliedTo(attacker))
        }.forEach { ally ->
            DestinyStatusRules.applyRadiant(
                ally,
                SolarWarlockFragmentRules.TORCHES_BASE_DURATION_TICKS,
                source = attacker
            )
            level.sendParticles(ParticleTypes.FLAME, ally.x, ally.eyeY, ally.z, 10, 0.25, 0.35, 0.25, 0.01)
        }
    }

    fun modifyScorchStacks(source: ServerPlayer?, stacks: Int, sourceKind: SolarDamageKind): Int {
        if (source == null || !DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_ASHES)) {
            return stacks
        }
        val safeBase = stacks.coerceAtLeast(0)
        val profile = SolarScorchProfile(safeBase, ashesBonusFor(sourceKind, safeBase))
        return SolarWarlockFragmentRules.scorchStacks(profile, hasAshes = true)
    }

    fun modifySolarBuffDuration(target: LivingEntity, durationTicks: Int): Int {
        val player = target as? ServerPlayer ?: return durationTicks
        return SolarWarlockFragmentRules.solarBuffDuration(
            durationTicks,
            SolarFragmentBuff.RADIANT,
            DestinyAspectRuntime.hasFragment(player, SolarWarlockFragmentRules.EMBER_OF_SOLACE)
        )
    }

    fun onSolarBuffApplied(source: ServerPlayer?, target: LivingEntity, buff: SolarFragmentBuff) {
        source ?: return
        val recipient = target as? ServerPlayer ?: return
        if (!SolarWarlockFragmentRules.benevolenceTriggers(
                DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_BENEVOLENCE),
                buff,
                recipientIsAlly = recipient.isAlliedTo(source),
                recipientIsSelf = recipient === source
            )
        ) return

        val duration = SolarWarlockFragmentRules.benevolenceDuration(hasSolace = false)
        benevolenceActiveUntil[source.uuid] = source.serverLevel().gameTime + duration
        DestinyStatusRules.syncTemporaryBuff(
            source,
            "destiny2-mod:fragment/ember_of_benevolence",
            "仁慈余烬",
            duration
        )
    }

    fun onFirespritePickup(player: ServerPlayer) {
        val now = player.serverLevel().gameTime
        PlayerDestinyDataApi.get(player).cooldowns.reduce(
            AbilitySlot.GRENADE,
            now,
            SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_FIRESPRITE_GRENADE_REFUND_TICKS
        )
        DestinyNetworking.syncCooldowns(player)
        if (SolarWarlockFragmentRules.mercyTriggersOnFirespritePickup(
                DestinyAspectRuntime.hasFragment(player, SolarWarlockFragmentRules.EMBER_OF_MERCY),
                pickedUpFiresprite = true
            )
        ) {
            DestinyStatusRules.applyRestoration(
                player,
                SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_MERCY_RESTORATION_DURATION_TICKS,
                source = player
            )
        }
        player.serverLevel().sendParticles(ParticleTypes.FLAME, player.x, player.eyeY, player.z, 20, 0.35, 0.45, 0.35, 0.03)
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.65f, 1.35f)
    }

    fun onAllyRevived(reviver: ServerPlayer, revived: ServerPlayer) {
        if (!SolarWarlockFragmentRules.mercyTriggersOnRevive(
                DestinyAspectRuntime.hasFragment(reviver, SolarWarlockFragmentRules.EMBER_OF_MERCY),
                revivedAlly = revived !== reviver && revived.isAlliedTo(reviver)
            )
        ) return

        reviver.serverLevel().getEntitiesOfClass(
            ServerPlayer::class.java,
            revived.boundingBox.inflate(MINECRAFT_CALIBRATION_MERCY_REVIVE_RADIUS)
        ) { candidate ->
            candidate.isAlive && (candidate === reviver || candidate === revived || candidate.isAlliedTo(reviver))
        }.forEach { ally ->
            DestinyStatusRules.applyRestoration(
                ally,
                SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_MERCY_RESTORATION_DURATION_TICKS,
                source = reviver
            )
        }
    }

    fun onFinalBlow(
        source: ServerPlayer,
        defeated: LivingEntity,
        damageSource: DamageSource,
        wasScorched: Boolean
    ) {
        if (PlayerDestinyDataApi.get(source).subclass != DestinySubclassType.SOLAR_WARLOCK) return
        handleSolarFinalBlow(
            source,
            defeated,
            classifyFinalBlowSource(source, damageSource),
            wasScorched,
            fromIgnition = false
        )
    }

    fun onScorchFinalBlow(source: ServerPlayer, defeated: LivingEntity, context: SolarScorchContext) {
        onAttributedFinalBlow(source, defeated, context, wasScorched = true)
    }

    /** Handles Solar ability damage whose attribution is carried out-of-band rather than by an entity type. */
    fun onAttributedFinalBlow(
        source: ServerPlayer,
        defeated: LivingEntity,
        context: SolarScorchContext,
        wasScorched: Boolean
    ) {
        val combatSource = when (context.sourceKind) {
            SolarDamageKind.GRENADE -> SolarFragmentCombatSource.SOLAR_GRENADE
            SolarDamageKind.POWERED_MELEE -> SolarFragmentCombatSource.SOLAR_POWERED_MELEE
            SolarDamageKind.SUPER -> SolarFragmentCombatSource.SOLAR_SUPER
            SolarDamageKind.WEAPON -> SolarFragmentCombatSource.SOLAR_WEAPON
            SolarDamageKind.IGNITION, SolarDamageKind.GENERIC -> SolarFragmentCombatSource.SOLAR_ABILITY
        }
        handleSolarFinalBlow(source, defeated, combatSource, wasScorched, fromIgnition = false)
    }

    fun onIgnitionFinalBlow(
        source: ServerPlayer,
        defeated: LivingEntity,
        context: SolarScorchContext? = null,
        wasScorched: Boolean = false
    ) {
        val now = source.serverLevel().gameTime
        if (SolarWarlockFragmentRules.blisteringTriggers(
                DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_BLISTERING),
                ignitionFinalBlow = true
            )
        ) {
            PlayerDestinyDataApi.get(source).cooldowns.reduce(
                AbilitySlot.GRENADE,
                now,
                SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_BLISTERING_GRENADE_REFUND_TICKS
            )
            DestinyNetworking.syncCooldowns(source)
        }

        val update = SolarWarlockFragmentRules.recordWonderIgnitionFinalBlow(
            wonderStates[source.uuid] ?: SolarIgnitionMultikillState(),
            now,
            DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_WONDER)
        )
        wonderStates[source.uuid] = update.state
        if (update.shouldGenerateOrb) {
            OrbOfPowerEntity.spawn(source.serverLevel(), defeated.position().add(0.0, 0.35, 0.0))
        }
        val fragmentSource = when (context?.sourceKind) {
            SolarDamageKind.GRENADE -> SolarFragmentCombatSource.SOLAR_GRENADE
            SolarDamageKind.POWERED_MELEE -> SolarFragmentCombatSource.SOLAR_POWERED_MELEE
            SolarDamageKind.SUPER -> SolarFragmentCombatSource.SOLAR_SUPER
            SolarDamageKind.WEAPON -> SolarFragmentCombatSource.SOLAR_WEAPON
            SolarDamageKind.IGNITION, SolarDamageKind.GENERIC, null -> SolarFragmentCombatSource.SOLAR_ABILITY
        }
        handleSolarFinalBlow(source, defeated, fragmentSource, wasScorched, fromIgnition = true)
    }

    fun dynamicStatBonuses(player: ServerPlayer): DestinyStats {
        val now = player.serverLevel().gameTime
        val state = temperingStates[player.uuid]?.takeIf { now < it.expiresAt } ?: return ZERO_STATS
        return DestinyStats(
            weapons = 0,
            health = SolarWarlockFragmentRules.temperingHealthBonus(state.stacks),
            classAbility = 0,
            grenade = 0,
            superStat = 0,
            melee = 0
        )
    }

    fun airborneWeaponSpreadMultiplier(player: ServerPlayer): Float {
        if (player.onGround()) return 1.0f
        val state = temperingStates[player.uuid] ?: return 1.0f
        return if (player.serverLevel().gameTime < state.expiresAt) {
            SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_TEMPERING_AIRBORNE_SPREAD_MULTIPLIER
        } else 1.0f
    }

    private fun handleSolarFinalBlow(
        source: ServerPlayer,
        defeated: LivingEntity,
        combatSource: SolarFragmentCombatSource,
        wasScorched: Boolean,
        fromIgnition: Boolean
    ) {
        val now = source.serverLevel().gameTime
        if (SolarWarlockFragmentRules.resolveTriggers(
                DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_RESOLVE),
                combatSource,
                finalBlow = true
            )
        ) {
            DestinyStatusRules.applyCure(
                source,
                SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_RESOLVE_CURE_HEALTH,
                source
            )
        }

        if (SolarWarlockFragmentRules.searingTriggers(
                DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_SEARING),
                targetWasScorched = wasScorched,
                finalBlow = true
            )
        ) {
            PlayerDestinyDataApi.get(source).cooldowns.reduce(
                AbilitySlot.MELEE,
                now,
                SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_SEARING_MELEE_REFUND_TICKS
            )
            DestinyNetworking.syncCooldowns(source)
            trySpawnFiresprite(source, defeated)
        }

        val temperingWasActive = isTemperingActive(source, now)
        if (SolarWarlockFragmentRules.temperingTriggers(
                DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_TEMPERING),
                combatSource,
                finalBlow = true
            )
        ) {
            if (temperingWasActive) trySpawnFiresprite(source, defeated)
            applyTemperingToAllies(source, now)
        }

        val hasSolarBuff = source.hasEffect(DestinyEffects.RADIANT) || source.hasEffect(DestinyEffects.RESTORATION)
        if (SolarWarlockFragmentRules.empyreanTriggers(
                DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_EMPYREAN),
                combatSource,
                finalBlow = true,
                hasRadiantOrRestoration = hasSolarBuff
            )
        ) {
            extendEmpyreanBuffs(source, combatantTier(defeated))
        }

        if (!fromIgnition && SolarWarlockFragmentRules.combustionTriggers(
                DestinyAspectRuntime.hasFragment(source, SolarWarlockFragmentRules.EMBER_OF_COMBUSTION),
                combatSource,
                finalBlow = true
            )
        ) {
            trySpawnFiresprite(source, defeated)
            SolarIgnitionRuntime.ignite(
                defeated,
                SolarScorchContext(source.uuid, SolarDamageKind.SUPER)
            )
        }
    }

    private fun tickSingeing(player: ServerPlayer, now: Long) {
        val activeUntil = singeingActiveUntil[player.uuid] ?: return
        if (now >= activeUntil || player.isDeadOrDying ||
            !DestinyAspectRuntime.hasFragment(player, SolarWarlockFragmentRules.EMBER_OF_SINGEING)
        ) {
            singeingActiveUntil.remove(player.uuid)
            return
        }
        val bonusTicks = (SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_SINGEING_RECHARGE_MULTIPLIER - 1.0f)
            .toInt().coerceAtLeast(0)
        val reduced = PlayerDestinyDataApi.get(player).cooldowns.reduce(AbilitySlot.CLASS_ABILITY, now, bonusTicks)
        if (reduced > 0 && now % 10L == 0L) DestinyNetworking.syncCooldowns(player)
    }

    private fun tickBenevolence(player: ServerPlayer, now: Long) {
        val activeUntil = benevolenceActiveUntil[player.uuid] ?: return
        if (now >= activeUntil || player.isDeadOrDying ||
            !DestinyAspectRuntime.hasFragment(player, SolarWarlockFragmentRules.EMBER_OF_BENEVOLENCE)
        ) {
            benevolenceActiveUntil.remove(player.uuid)
            return
        }
        val bonusTicks = (SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_BENEVOLENCE_RECHARGE_MULTIPLIER - 1.0f)
            .toInt().coerceAtLeast(0)
        var changed = false
        val cooldowns = PlayerDestinyDataApi.get(player).cooldowns
        listOf(AbilitySlot.GRENADE, AbilitySlot.MELEE, AbilitySlot.CLASS_ABILITY).forEach { slot ->
            changed = cooldowns.reduce(slot, now, bonusTicks) > 0 || changed
        }
        if (changed && now % 10L == 0L) DestinyNetworking.syncCooldowns(player)
    }

    private fun classifyFinalBlowSource(source: ServerPlayer, damageSource: DamageSource): SolarFragmentCombatSource {
        val direct = damageSource.directEntity
        val ability = direct as? DestinyAbilityDamageCarrier
        if (ability != null) {
            return when (ability.destinyAbilitySlot) {
                AbilitySlot.GRENADE -> SolarFragmentCombatSource.SOLAR_GRENADE
                AbilitySlot.MELEE -> SolarFragmentCombatSource.SOLAR_POWERED_MELEE
                AbilitySlot.SUPER -> SolarFragmentCombatSource.SOLAR_SUPER
                AbilitySlot.CLASS_ABILITY -> SolarFragmentCombatSource.SOLAR_ABILITY
            }
        }
        val element = when (direct) {
            is DestinyElementalDamageCarrier -> direct.destinyDamageElement
            source -> GearRegistry.definitionFor(source.mainHandItem)?.damageElement ?: DestinyDamageElement.KINETIC
            else -> DestinyDamageElement.KINETIC
        }
        if (element == DestinyDamageElement.SOLAR) return SolarFragmentCombatSource.SOLAR_WEAPON
        return if (direct === source) SolarFragmentCombatSource.UNCHARGED_MELEE else SolarFragmentCombatSource.OTHER
    }

    private fun applyTemperingToAllies(source: ServerPlayer, now: Long) {
        source.serverLevel().getEntitiesOfClass(
            ServerPlayer::class.java,
            source.boundingBox.inflate(MINECRAFT_CALIBRATION_TEMPERING_ALLY_RADIUS)
        ) { candidate ->
            candidate.isAlive && !candidate.isSpectator &&
                (candidate === source || candidate.isAlliedTo(source))
        }.forEach { ally ->
            val previous = temperingStates[ally.uuid]?.takeIf { now < it.expiresAt }
            val stacks = SolarWarlockFragmentRules.temperingNextStacks(previous?.stacks ?: 0, triggered = true)
            val duration = SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_TEMPERING_DURATION_TICKS
            temperingStates[ally.uuid] = TemperingState(stacks, now + duration)
            DestinyStatusRules.syncTemporaryBuff(
                ally,
                "destiny2-mod:fragment/ember_of_tempering",
                "回火余烬",
                duration,
                stacks
            )
            DestinyNetworking.syncStatState(ally)
        }
    }

    private fun isTemperingActive(player: ServerPlayer, now: Long): Boolean =
        temperingStates[player.uuid]?.let { now < it.expiresAt && it.stacks > 0 } == true

    private fun extendEmpyreanBuffs(player: ServerPlayer, tier: SolarCombatantTier) {
        listOf(
            Triple(DestinyEffects.RADIANT, "radiant", "焕光"),
            Triple(DestinyEffects.RESTORATION, "restoration", "恢复")
        ).forEach { (effect, id, name) ->
            val current = player.getEffect(effect) ?: return@forEach
            val extended = SolarWarlockFragmentRules.empyreanExtendedDuration(current.duration, tier)
            if (extended <= current.duration) return@forEach
            player.addEffect(MobEffectInstance(effect, extended, current.amplifier, false, false, true))
            DestinyStatusRules.syncTemporaryBuff(
                player,
                "destiny2-mod:status/$id",
                name,
                extended,
                current.amplifier + 1
            )
        }
    }

    private fun trySpawnFiresprite(owner: ServerPlayer, defeated: LivingEntity): Boolean {
        val now = owner.serverLevel().gameTime
        if (now < (nextFirespriteEligibleTick[owner.uuid] ?: Long.MIN_VALUE)) return false
        nextFirespriteEligibleTick[owner.uuid] = now + MINECRAFT_CALIBRATION_FIRESPRITE_COOLDOWN_TICKS
        FirespriteEntity.spawn(
            owner.serverLevel(),
            defeated.position().add(0.0, 0.18, 0.0),
            owner
        )
        return true
    }

    private fun combatantTier(defeated: LivingEntity): SolarCombatantTier = when {
        defeated is EnderDragon || defeated is WitherBoss || defeated is Warden -> SolarCombatantTier.BOSS
        defeated.maxHealth >= 40.0f -> SolarCombatantTier.MAJOR
        else -> SolarCombatantTier.MINOR
    }

    private fun ashesBonusFor(kind: SolarDamageKind, baseStacks: Int): Int {
        val halfRoundedUp = ceil(baseStacks * 0.5).toInt().coerceAtLeast(1)
        return when (kind) {
            // Separate branches are deliberate: Bungie tunes scorch sources
            // independently, so later A/B calibration must not restore a
            // blanket multiplier.
            SolarDamageKind.GRENADE -> halfRoundedUp
            SolarDamageKind.POWERED_MELEE -> halfRoundedUp
            SolarDamageKind.SUPER -> halfRoundedUp
            SolarDamageKind.WEAPON -> halfRoundedUp
            SolarDamageKind.IGNITION -> SolarWarlockFragmentRules.MINECRAFT_CALIBRATION_CHAR_ASHES_BONUS_STACKS
            SolarDamageKind.GENERIC -> halfRoundedUp
        }
    }

    private fun isCombatant(owner: ServerPlayer, target: LivingEntity): Boolean =
        target !== owner && target.isAlive &&
            (target is Enemy || (target is Player && !owner.isAlliedTo(target)))

    private val ZERO_STATS = DestinyStats(0, 0, 0, 0, 0, 0)
}
