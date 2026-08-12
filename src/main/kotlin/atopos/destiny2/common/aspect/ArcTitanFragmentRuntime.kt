package atopos.destiny2.common.aspect

import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.entity.ArcAbilityDamageEntity
import atopos.destiny2.common.entity.ArcPulseGrenadeEntity
import atopos.destiny2.common.entity.ArcGrenadeDamageEntity
import atopos.destiny2.common.entity.OrbOfPowerEntity
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.weapon.DestinyDamageElement
import atopos.destiny2.common.weapon.DestinyElementalDamageCarrier
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.DestinyWeaponDamageCarrier
import atopos.destiny2.common.weapon.WeaponAmmoState
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-authoritative runtime for all sixteen Arc Fragments.
 *
 * The runtime owns transient timers and Jolt attribution. UI selection remains
 * data only; every trigger is revalidated against the active Arc Titan loadout.
 * Blind, Ionic Trace and Bolt Charge use their shared Arc keyword runtimes.
 */
object ArcTitanFragmentRuntime {
    const val MINECRAFT_CALIBRATION_RESISTANCE_SCAN_RADIUS = 6.0
    const val MINECRAFT_CALIBRATION_JOLT_CHAIN_RADIUS = 5.0
    const val MINECRAFT_CALIBRATION_JOLT_CHAIN_DAMAGE = 4.0f
    const val MINECRAFT_CALIBRATION_JOLT_CHAIN_COOLDOWN_TICKS = 20L
    const val MINECRAFT_CALIBRATION_JOLT_MAX_CHAIN_TARGETS = 3

    private data class PlayerState(
        var focusSprintTicks: Int = 0,
        var focusActiveUntil: Long = Long.MIN_VALUE,
        var rechargeLatched: Boolean = false,
        var feedbackActiveUntil: Long = Long.MIN_VALUE,
        var frequencyActiveUntil: Long = Long.MIN_VALUE,
        var amplitudeState: ArcTitanFragmentRules.AmplitudeState = ArcTitanFragmentRules.AmplitudeState(),
        var nextIonicTraceEligibleTick: Long = Long.MIN_VALUE,
        var nextInstinctEligibleTick: Long = Long.MIN_VALUE
    )

    data class JoltState(
        val sourcePlayerId: UUID,
        val expiresAt: Long,
        val nextChainEligibleTick: Long
    )

    private val playerStates = ConcurrentHashMap<UUID, PlayerState>()
    private val joltedTargets = ConcurrentHashMap<UUID, JoltState>()
    private val handledMomentumPickups = ConcurrentHashMap.newKeySet<Pair<UUID, UUID>>()
    private val resolvingJoltChain = ThreadLocal.withInitial { false }

    fun register() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            clearPlayer(handler.player.uuid)
        }
    }

    fun clearPlayer(playerId: UUID) {
        playerStates.remove(playerId)
        ArcBoltChargeRuntime.clearPlayer(playerId)
        ArcTitanAspectRuntime.clearPlayer(playerId)
        joltedTargets.remove(playerId)
        joltedTargets.entries.removeIf { it.value.sourcePlayerId == playerId }
        handledMomentumPickups.removeIf { it.first == playerId }
    }

    fun tickPlayer(player: ServerPlayer) {
        val now = player.serverLevel().gameTime
        if (!player.isAlive || player.isSpectator || !isArcTitan(player)) {
            playerStates.remove(player.uuid)
            pruneExpiredJoltStates(now)
            return
        }

        val state = playerStates.computeIfAbsent(player.uuid) { PlayerState() }
        tickFocus(player, state, now)
        tickRecharge(player, state, now)
        if (state.feedbackActiveUntil <= now || !hasFragment(player, ArcTitanFragmentRules.SPARK_OF_FEEDBACK)) {
            state.feedbackActiveUntil = Long.MIN_VALUE
        }
        if (state.frequencyActiveUntil <= now || !hasFragment(player, ArcTitanFragmentRules.SPARK_OF_FREQUENCY)) {
            state.frequencyActiveUntil = Long.MIN_VALUE
        }
        if (now % 20L == 0L) pruneExpiredJoltStates(now)
    }

    fun dynamicStatBonuses(player: ServerPlayer): DestinyStats = DestinyStats(
        weapons = 0,
        health = ArcTitanFragmentRules.hasteHealthStatBonus(
            hasFragment(player, ArcTitanFragmentRules.SPARK_OF_HASTE),
            player.isSprinting
        ),
        classAbility = 0,
        grenade = 0,
        superStat = 0,
        melee = 0
    )

    fun modifyIncomingDamage(player: ServerPlayer, amount: Float): Float {
        if (amount <= 0.0f || !hasFragment(player, ArcTitanFragmentRules.SPARK_OF_RESISTANCE)) return amount
        val radius = MINECRAFT_CALIBRATION_RESISTANCE_SCAN_RADIUS
        val hostileCount = player.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(player.position(), radius * 2.0, radius * 2.0, radius * 2.0)
        ) { candidate ->
            candidate !== player && candidate.isAlive && isHostileCombatant(player, candidate) &&
                candidate.position().distanceToSqr(player.position()) <= radius * radius
        }.size
        return amount * ArcTitanFragmentRules.resistanceIncomingDamageMultiplier(hostileCount)
    }

    fun modifyOutgoingDamage(attacker: ServerPlayer, source: DamageSource, amount: Float): Float {
        if (amount <= 0.0f || !isMeleeDamage(attacker, source)) return amount
        val active = playerStates[attacker.uuid]?.feedbackActiveUntil?.let {
            it > attacker.serverLevel().gameTime
        } == true && hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_FEEDBACK)
        return amount * ArcTitanFragmentRules.feedbackMeleeDamageMultiplier(active)
    }

    /** Called once after the shared damage pipeline confirms real damage or shield loss. */
    fun afterSuccessfulDamage(target: LivingEntity, source: DamageSource, actualDamage: Float) {
        if (actualDamage <= 0.0f || target.level().isClientSide) return
        triggerJoltChain(target, actualDamage)
        (target as? ServerPlayer)?.let { onPlayerDamaged(it, source, actualDamage) }

        val attacker = source.entity as? ServerPlayer ?: return
        if (attacker === target) return
        onMeleeHit(attacker, source, actualDamage)
        onArcGrenadeHit(attacker, target, source, actualDamage)
    }

    fun reloadMultiplier(player: ServerPlayer): Float {
        val active = isFrequencyActive(player)
        return ArcTitanFragmentRules.frequencyReloadMultiplier(
            active,
            player.hasEffect(DestinyEffects.AMPLIFIED)
        )
    }

    fun stabilityMultiplier(player: ServerPlayer): Float {
        val active = isFrequencyActive(player)
        return ArcTitanFragmentRules.frequencyStabilityMultiplier(
            active,
            player.hasEffect(DestinyEffects.AMPLIFIED)
        )
    }

    fun pulseGrenadePulses(owner: LivingEntity?): Int {
        val player = owner as? ServerPlayer
        return ArcTitanFragmentRules.pulseGrenadePulses(
            player != null && hasFragment(player, ArcTitanFragmentRules.SPARK_OF_MAGNITUDE)
        )
    }

    fun isJolted(target: LivingEntity): Boolean {
        val state = joltedTargets[target.uuid] ?: return false
        val now = (target.level() as? ServerLevel)?.gameTime ?: return false
        if (now >= state.expiresAt) {
            joltedTargets.remove(target.uuid, state)
            return false
        }
        return true
    }

    fun joltState(target: LivingEntity): JoltState? =
        joltedTargets[target.uuid]?.takeIf { it.expiresAt > target.level().gameTime }

    fun pendingFoundationDependencies(fragmentId: String): Set<ArcFoundationDependency> =
        ArcTitanFragmentRuntimeRules.pendingFoundationDependencies(fragmentId)

    /** Called before vanilla consumes a Special/Heavy brick. */
    fun onAmmoPickup(player: ServerPlayer, entity: ItemEntity) {
        if (!isArcTitan(player)) return
        val specialOrHeavy = entity.item.`is`(DestinyItems.SPECIAL_AMMO) || entity.item.`is`(DestinyItems.HEAVY_AMMO)
        val horizontalSpeed = player.deltaMovement.horizontalDistanceSqr()
        val sliding = player.isShiftKeyDown && (player.isSprinting || horizontalSpeed >= 0.015)
        val triggered = ArcTitanFragmentRules.shouldTriggerMomentum(
            hasFragment(player, ArcTitanFragmentRules.SPARK_OF_MOMENTUM),
            sliding,
            specialOrHeavy
        )
        if (!triggered || !handledMomentumPickups.add(player.uuid to entity.uuid)) return

        val stack = player.mainHandItem
        val weapon = stack.item as? DestinyRangedWeapon
        if (weapon != null) {
            val profile = weapon.combatProfile(stack)
            WeaponAmmoState.setMagazine(stack, profile.magazineSize, profile.magazineSize)
        }
        ArcBoltChargeRuntime.addBoltCharge(
            player,
            ArcTitanFragmentRules.momentumBoltChargeGain(true),
            "动量火花"
        )
        player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.x, player.y + 0.3, player.z, 18, 0.35, 0.18, 0.35, 0.08)
    }

    /** Captures all target keywords before death removes effects and transient state. */
    fun onFinalBlow(
        attacker: ServerPlayer,
        defeated: LivingEntity,
        source: DamageSource,
        precisionFinalBlow: Boolean,
        targetWasBlinded: Boolean,
        targetWasJolted: Boolean
    ) {
        if (!isArcTitan(attacker)) return
        val now = attacker.serverLevel().gameTime
        val state = playerStates.computeIfAbsent(attacker.uuid) { PlayerState() }
        val direct = source.directEntity
        val weaponCarrier = direct as? DestinyWeaponDamageCarrier
        val element = (direct as? DestinyElementalDamageCarrier)?.destinyDamageElement
            ?: GearRegistry.definitionFor(attacker.mainHandItem)?.damageElement
            ?: DestinyDamageElement.KINETIC
        val ammoType = weaponCarrier?.destinyAmmoType
            ?: GearRegistry.definitionFor(attacker.mainHandItem)?.ammoType
            ?: DestinyAmmoType.PRIMARY
        val isWeaponFinalBlow = weaponCarrier != null
        val isArcWeaponFinalBlow = isWeaponFinalBlow && element == DestinyDamageElement.ARC

        val beacons = ArcTitanFragmentRules.shouldTriggerBeacons(
            hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_BEACONS),
            attacker.hasEffect(DestinyEffects.AMPLIFIED),
            isArcWeaponFinalBlow,
            ammoType
        )
        val brilliance = ArcTitanFragmentRules.shouldTriggerBrilliance(
            hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_BRILLIANCE),
            precisionFinalBlow,
            targetWasBlinded
        )
        if (beacons || brilliance) applyBlindExplosion(attacker, defeated)

        val amplitude = ArcTitanFragmentRules.recordAmplitudeFinalBlow(
            hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_AMPLITUDE),
            attacker.hasEffect(DestinyEffects.AMPLIFIED),
            now,
            state.amplitudeState
        )
        state.amplitudeState = amplitude.state
        if (amplitude.shouldSpawnOrb) {
            OrbOfPowerEntity.spawn(attacker.serverLevel(), defeated.position().add(0.0, 0.35, 0.0))
        }

        val finisherFinalBlow = direct === attacker && attacker.distanceToSqr(defeated) <= 9.0
        if (ArcTitanFragmentRules.shouldTriggerVolts(
                hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_VOLTS),
                finisherFinalBlow
            )
        ) {
            DestinyStatusRules.applyAmplified(attacker, ArcTitanFragmentRules.VOLTS_AMPLIFIED_DURATION_TICKS)
            ArcBoltChargeRuntime.addBoltCharge(
                attacker,
                ArcTitanFragmentRules.voltsBoltChargeGain(true),
                "伏特火花"
            )
        }

        val targetHadBoltCharge = (defeated as? ServerPlayer)?.let(ArcBoltChargeRuntime::hasBoltCharge) == true
        if (ArcTitanFragmentRules.shouldSpawnIonicTraceFromIons(
                hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_IONS),
                targetWasJolted,
                targetHadBoltCharge,
                now,
                state.nextIonicTraceEligibleTick
            )
        ) {
            ArcBoltChargeRuntime.spawnIonicTrace(attacker, defeated.x, defeated.y + 0.35, defeated.z)
            state.nextIonicTraceEligibleTick = now + ArcTitanFragmentRules.IONIC_TRACE_COOLDOWN_TICKS
        } else if (ArcTitanFragmentRules.shouldSpawnIonicTraceFromDischarge(
                hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_DISCHARGE),
                isArcWeaponFinalBlow,
                attacker.random.nextFloat()
            )
        ) {
            ArcBoltChargeRuntime.spawnIonicTrace(attacker, defeated.x, defeated.y + 0.35, defeated.z)
        }
    }

    private fun tickFocus(player: ServerPlayer, state: PlayerState, now: Long) {
        val update = ArcTitanFragmentRuntimeRules.tickFocus(
            hasFragment = hasFragment(player, ArcTitanFragmentRules.SPARK_OF_FOCUS),
            isSprinting = player.isSprinting,
            currentTick = now,
            previousSprintTicks = state.focusSprintTicks,
            previousActiveUntil = state.focusActiveUntil
        )
        val becameActive = state.focusActiveUntil <= now && update.activeUntil > now
        state.focusSprintTicks = update.sprintTicks
        state.focusActiveUntil = update.activeUntil
        val reduced = reduceCooldown(player, AbilitySlot.CLASS_ABILITY, update.classCooldownReductionTicks)
        if (becameActive) {
            DestinyStatusRules.syncTemporaryBuff(
                player,
                "destiny2-mod:fragment/spark_of_focus",
                "专注火花",
                ArcTitanFragmentRules.FOCUS_DURATION_TICKS.toInt()
            )
        }
        syncCooldownsIfNeeded(player, reduced, now)
    }

    private fun tickRecharge(player: ServerPlayer, state: PlayerState, now: Long) {
        val data = PlayerDestinyDataApi.get(player)
        val fullShield = DestinyStatFormulas.healthShieldCapacity(DestinyStatsResolver.resolve(player))
        val update = ArcTitanFragmentRuntimeRules.tickRecharge(
            hasFragment = hasFragment(player, ArcTitanFragmentRules.SPARK_OF_RECHARGE),
            previouslyLatched = state.rechargeLatched,
            currentShield = data.combatState.healthShield,
            fullShield = fullShield
        )
        val becameActive = !state.rechargeLatched && update.latched
        state.rechargeLatched = update.latched
        val grenadeReduced = reduceCooldown(player, AbilitySlot.GRENADE, update.grenadeCooldownReductionTicks)
        val meleeReduced = reduceCooldown(player, AbilitySlot.MELEE, update.meleeCooldownReductionTicks)
        if (becameActive) {
            DestinyStatusRules.syncTemporaryBuff(
                player,
                "destiny2-mod:fragment/spark_of_recharge",
                "充能火花",
                40
            )
        }
        syncCooldownsIfNeeded(player, grenadeReduced + meleeReduced, now)
    }

    private fun onPlayerDamaged(player: ServerPlayer, source: DamageSource, actualDamage: Float) {
        val attacker = source.entity as? LivingEntity
        val hostileDirectMelee = attacker != null && attacker !== player && !player.isAlliedTo(attacker) &&
            source.directEntity === attacker && !source.`is`(DamageTypeTags.IS_PROJECTILE)
        if (ArcTitanFragmentRules.shouldTriggerFeedback(
                hasFragment(player, ArcTitanFragmentRules.SPARK_OF_FEEDBACK),
                hostileDirectMelee,
                actualDamage
            )
        ) {
            val state = playerStates.computeIfAbsent(player.uuid) { PlayerState() }
            state.feedbackActiveUntil = player.serverLevel().gameTime + ArcTitanFragmentRules.FEEDBACK_DURATION_TICKS
            DestinyStatusRules.syncTemporaryBuff(
                player,
                "destiny2-mod:fragment/spark_of_feedback",
                "反馈火花",
                ArcTitanFragmentRules.FEEDBACK_DURATION_TICKS.toInt()
            )
        }

        val now = player.serverLevel().gameTime
        val state = playerStates.computeIfAbsent(player.uuid) { PlayerState() }
        val hostileDamage = attacker != null && attacker !== player && !player.isAlliedTo(attacker)
        if (ArcTitanFragmentRules.shouldTriggerInstinct(
                hasFragment(player, ArcTitanFragmentRules.SPARK_OF_INSTINCT),
                player.health <= player.maxHealth * 0.5f,
                hostileDamage,
                actualDamage,
                now,
                state.nextInstinctEligibleTick
            )
        ) {
            state.nextInstinctEligibleTick = now + ArcTitanFragmentRules.INSTINCT_COOLDOWN_TICKS
            triggerInstinctBurst(player)
        }
    }

    private fun onMeleeHit(attacker: ServerPlayer, source: DamageSource, actualDamage: Float) {
        if (!ArcTitanFragmentRules.shouldTriggerFrequency(
                hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_FREQUENCY),
                isMeleeDamage(attacker, source),
                actualDamage > 0.0f
            )
        ) return

        val state = playerStates.computeIfAbsent(attacker.uuid) { PlayerState() }
        state.frequencyActiveUntil = attacker.serverLevel().gameTime + ArcTitanFragmentRules.FREQUENCY_DURATION_TICKS
        DestinyStatusRules.syncTemporaryBuff(
            attacker,
            "destiny2-mod:fragment/spark_of_frequency",
            "频率火花",
            ArcTitanFragmentRules.FREQUENCY_DURATION_TICKS.toInt()
        )
    }

    private fun onArcGrenadeHit(
        attacker: ServerPlayer,
        target: LivingEntity,
        source: DamageSource,
        actualDamage: Float
    ) {
        val isCurrentArcGrenade = source.directEntity is ArcGrenadeDamageEntity
        if (!ArcTitanFragmentRules.shouldApplyShock(
                hasFragment(attacker, ArcTitanFragmentRules.SPARK_OF_SHOCK),
                isCurrentArcGrenade,
                actualDamage > 0.0f
            )
        ) return

        applyJolt(attacker, target)
    }

    fun applyJolt(owner: ServerPlayer, target: LivingEntity) {
        val now = owner.serverLevel().gameTime
        val previous = joltedTargets[target.uuid]
        joltedTargets[target.uuid] = JoltState(
            sourcePlayerId = owner.uuid,
            expiresAt = now + ArcTitanFragmentRules.JOLT_DURATION_TICKS,
            nextChainEligibleTick = previous?.nextChainEligibleTick?.coerceAtLeast(now) ?: now
        )
        owner.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + target.bbHeight * 0.55, target.z, 12, 0.28, 0.42, 0.28, 0.08)
    }

    private fun applyBlindExplosion(owner: ServerPlayer, origin: LivingEntity) {
        val radius = ArcTitanFragmentRules.BLIND_EXPLOSION_RADIUS
        owner.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(origin.position(), radius * 2.0, radius * 2.0, radius * 2.0)
        ) { candidate ->
            candidate !== owner && candidate.isAlive && isHostileCombatant(owner, candidate) &&
                candidate.position().distanceToSqr(origin.position()) <= radius * radius
        }.forEach { DestinyStatusRules.applyArcBlind(it, ArcTitanFragmentRules.BLIND_DURATION_TICKS) }
        owner.serverLevel().sendParticles(ParticleTypes.FLASH, origin.x, origin.y + origin.bbHeight * 0.5, origin.z, 1, 0.0, 0.0, 0.0, 0.0)
        owner.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y + origin.bbHeight * 0.5, origin.z, 36, radius * 0.25, 0.65, radius * 0.25, 0.18)
    }

    private fun triggerInstinctBurst(owner: ServerPlayer) {
        val radius = ArcTitanFragmentRules.INSTINCT_RADIUS
        val victims = owner.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(owner.position(), radius * 2.0, radius * 2.0, radius * 2.0)
        ) { candidate -> candidate !== owner && candidate.isAlive && isHostileCombatant(owner, candidate) }
        val proxy = ArcAbilityDamageEntity(owner.serverLevel(), owner.position(), owner, AbilitySlot.MELEE)
        owner.serverLevel().addFreshEntity(proxy)
        val damageSource = owner.damageSources().indirectMagic(proxy, owner)
        victims.forEach { target ->
            applyJolt(owner, target)
            target.hurt(damageSource, ArcTitanFragmentRules.INSTINCT_DAMAGE)
        }
        owner.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, owner.x, owner.y + 0.9, owner.z, 48, radius * 0.32, 0.8, radius * 0.32, 0.22)
    }

    private fun triggerJoltChain(target: LivingEntity, actualDamage: Float) {
        val level = target.level() as? ServerLevel ?: return
        val now = level.gameTime
        val state = joltedTargets[target.uuid] ?: return
        if (!ArcTitanFragmentRuntimeRules.shouldTriggerJoltChain(
                currentTick = now,
                expiresAt = state.expiresAt,
                nextEligibleTick = state.nextChainEligibleTick,
                actualDamage = actualDamage,
                alreadyResolvingChain = resolvingJoltChain.get()
            )
        ) {
            if (now >= state.expiresAt) joltedTargets.remove(target.uuid, state)
            return
        }

        val owner = level.server.playerList.getPlayer(state.sourcePlayerId) ?: return
        val radius = MINECRAFT_CALIBRATION_JOLT_CHAIN_RADIUS
        val victims = level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(target.position(), radius * 2.0, radius * 2.0, radius * 2.0)
        ) { candidate ->
            candidate !== target && candidate !== owner && candidate.isAlive &&
                isHostileCombatant(owner, candidate) &&
                candidate.position().distanceToSqr(target.position()) <= radius * radius
        }.sortedBy { it.distanceToSqr(target) }
            .take(MINECRAFT_CALIBRATION_JOLT_MAX_CHAIN_TARGETS)
        if (victims.isEmpty()) return
        joltedTargets[target.uuid] = state.copy(
            nextChainEligibleTick = now + MINECRAFT_CALIBRATION_JOLT_CHAIN_COOLDOWN_TICKS
        )

        val proxy = ArcAbilityDamageEntity(level, target.position(), owner, AbilitySlot.GRENADE)
        level.addFreshEntity(proxy)
        val chainSource = owner.damageSources().indirectMagic(proxy, owner)
        resolvingJoltChain.set(true)
        try {
            victims.forEach { victim -> victim.hurt(chainSource, MINECRAFT_CALIBRATION_JOLT_CHAIN_DAMAGE) }
        } finally {
            resolvingJoltChain.set(false)
        }
        level.sendParticles(
            ParticleTypes.ELECTRIC_SPARK,
            target.x,
            target.y + target.bbHeight * 0.5,
            target.z,
            28,
            radius * 0.28,
            0.55,
            radius * 0.28,
            0.16
        )
    }

    private fun isFrequencyActive(player: ServerPlayer): Boolean =
        hasFragment(player, ArcTitanFragmentRules.SPARK_OF_FREQUENCY) &&
            (playerStates[player.uuid]?.frequencyActiveUntil ?: Long.MIN_VALUE) > player.serverLevel().gameTime

    private fun isMeleeDamage(attacker: ServerPlayer, source: DamageSource): Boolean {
        if (source.directEntity === attacker) return !source.`is`(DamageTypeTags.IS_PROJECTILE)
        return (source.directEntity as? atopos.destiny2.common.player.DestinyAbilityDamageCarrier)
            ?.destinyAbilitySlot == AbilitySlot.MELEE
    }

    private fun reduceCooldown(player: ServerPlayer, slot: AbilitySlot, ticks: Int): Int {
        if (ticks <= 0) return 0
        return PlayerDestinyDataApi.get(player).cooldowns.reduce(slot, player.serverLevel().gameTime, ticks)
    }

    private fun syncCooldownsIfNeeded(player: ServerPlayer, reducedTicks: Int, now: Long) {
        if (reducedTicks > 0 && now % 10L == 0L) DestinyNetworking.syncCooldowns(player)
    }

    private fun hasFragment(player: ServerPlayer, fragmentId: String): Boolean {
        val data = PlayerDestinyDataApi.get(player)
        return data.subclass == DestinySubclassType.ARC_TITAN &&
            data.subclassConfig.selectedFragments.contains(fragmentId)
    }

    private fun isArcTitan(player: ServerPlayer): Boolean =
        PlayerDestinyDataApi.get(player).subclass == DestinySubclassType.ARC_TITAN

    private fun isHostileCombatant(player: ServerPlayer, candidate: LivingEntity): Boolean = when (candidate) {
        is Player -> !candidate.isCreative && !candidate.isSpectator && !player.isAlliedTo(candidate)
        is Monster -> true
        is Mob -> candidate.target === player
        else -> false
    }

    private fun pruneExpiredJoltStates(now: Long) {
        joltedTargets.entries.removeIf { it.value.expiresAt <= now }
    }
}

enum class ArcFoundationDependency {
    BLIND,
    IONIC_TRACE,
    BOLT_CHARGE
}

/** Pure state transitions used by the runtime and boundary tests. */
object ArcTitanFragmentRuntimeRules {
    data class FocusTick(
        val sprintTicks: Int,
        val activeUntil: Long,
        val classCooldownReductionTicks: Int
    )

    data class RechargeTick(
        val latched: Boolean,
        val grenadeCooldownReductionTicks: Int,
        val meleeCooldownReductionTicks: Int
    )

    fun tickFocus(
        hasFragment: Boolean,
        isSprinting: Boolean,
        currentTick: Long,
        previousSprintTicks: Int,
        previousActiveUntil: Long
    ): FocusTick {
        if (!hasFragment) return FocusTick(0, Long.MIN_VALUE, 0)
        val sprintTicks = if (isSprinting) previousSprintTicks.coerceAtLeast(0) + 1 else 0
        val refreshedUntil = ArcTitanFragmentRules.focusWindowExpiresAt(currentTick, sprintTicks)
            ?: previousActiveUntil
        val activeUntil = if (refreshedUntil > currentTick) refreshedUntil else Long.MIN_VALUE
        return FocusTick(
            sprintTicks = sprintTicks,
            activeUntil = activeUntil,
            classCooldownReductionTicks = ArcTitanFragmentRules.focusExtraClassCooldownTicks(
                activeUntil > currentTick
            )
        )
    }

    fun tickRecharge(
        hasFragment: Boolean,
        previouslyLatched: Boolean,
        currentShield: Float,
        fullShield: Float
    ): RechargeTick {
        val latched = hasFragment && ArcTitanFragmentRules.nextRechargeLatched(
            previouslyLatched,
            currentShield,
            fullShield
        )
        val reduction = ArcTitanFragmentRules.rechargeExtraCooldownTicks(latched)
        return RechargeTick(latched, reduction, reduction)
    }

    fun shouldTriggerJoltChain(
        currentTick: Long,
        expiresAt: Long,
        nextEligibleTick: Long,
        actualDamage: Float,
        alreadyResolvingChain: Boolean
    ): Boolean = actualDamage > 0.0f && !alreadyResolvingChain &&
        currentTick < expiresAt && currentTick >= nextEligibleTick

    fun pendingFoundationDependencies(@Suppress("UNUSED_PARAMETER") fragmentId: String): Set<ArcFoundationDependency> = emptySet()
}
