package atopos.destiny2.common.aspect

import atopos.destiny2.common.ability.DestinyAbilityRegistry
import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.entity.ArcAbilityDamageEntity
import atopos.destiny2.common.entity.ArcTitanBarricadeEntity
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.DestinySubclassType
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.weapon.DestinyWeaponDamageCarrier
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Server-owned runtime for Touch of Thunder, Juggernaut, Knockout and Storm's Keep. */
object ArcTitanAspectRuntime {
    private const val BARRICADE_LIFETIME_TICKS = 20 * 20L
    private const val BARRICADE_HALF_WIDTH = 2.0
    private const val BARRICADE_HEIGHT = 2.35
    private const val BARRICADE_BEHIND_RANGE = 6.0
    private const val BOLT_DISCHARGE_RADIUS = 6.0
    private const val BOLT_DISCHARGE_DAMAGE = 7.0f
    private const val BOLT_DISCHARGE_MAX_TARGETS = 5
    private val KNOCKOUT_REACH_ID = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "knockout_melee_reach")

    private data class PlayerState(
        var juggernautSprintTicks: Int = 0,
        var juggernautShield: ArcTitanAspectRules.JuggernautShieldState? = null,
        var knockoutExpiresAt: Long = Long.MIN_VALUE,
        var lightningBonusAvailable: Boolean = true,
        var refundLightningCast: Boolean = false,
        var lightningBaseRechargeObserved: Boolean = false
    )

    data class Barricade(
        val ownerId: UUID,
        val dimension: ResourceLocation,
        val center: Vec3,
        val normal: Vec3,
        val expiresAt: Long,
        val visualEntityId: UUID
    )

    private val playerStates = ConcurrentHashMap<UUID, PlayerState>()
    private val barricades = ConcurrentHashMap<UUID, Barricade>()
    private val nextBarricadeChargeTicks = ConcurrentHashMap<UUID, Long>()
    private val resolvingBoltDischarge = ThreadLocal.withInitial { false }
    private var lastBarricadeTick = Long.MIN_VALUE

    fun tickPlayer(player: ServerPlayer) {
        val now = player.serverLevel().gameTime
        if (!isArcTitan(player) || !player.isAlive || player.isSpectator) {
            clearPlayer(player)
            return
        }
        val state = playerStates.computeIfAbsent(player.uuid) { PlayerState() }
        tickJuggernaut(player, state, now)
        tickKnockoutReach(player, state, now)
        tickLightningCharges(player, state, now)
        tickBarricades(player.server, now)
    }

    fun clearPlayer(player: ServerPlayer) {
        playerStates.remove(player.uuid)
        barricades.remove(player.uuid)?.let { removeBarricadeEntity(player.server, it) }
        nextBarricadeChargeTicks.remove(player.uuid)
        removeKnockoutReach(player)
    }

    fun clearPlayer(playerId: UUID) {
        playerStates.remove(playerId)
        barricades.remove(playerId)
        nextBarricadeChargeTicks.remove(playerId)
    }

    fun deployBarricade(player: ServerPlayer): Boolean {
        if (!isArcTitan(player) || !player.onGround()) return false
        val forward = horizontalFacing(player)
        val center = player.position().add(forward.scale(1.65)).add(0.0, 0.05, 0.0)
        val visual = ArcTitanBarricadeEntity(
            player.serverLevel(),
            center,
            player.yRot,
            player.uuid
        )
        player.serverLevel().addFreshEntity(visual)
        val barricade = Barricade(
            player.uuid,
            player.serverLevel().dimension().location(),
            center,
            forward,
            player.serverLevel().gameTime + BARRICADE_LIFETIME_TICKS,
            visual.uuid
        )
        barricades.put(player.uuid, barricade)?.let { removeBarricadeEntity(player.server, it) }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.75f, 0.72f)
        player.serverLevel().sendParticles(
            ParticleTypes.ELECTRIC_SPARK,
            center.x,
            center.y + 0.12,
            center.z,
            34,
            BARRICADE_HALF_WIDTH * 0.72,
            0.12,
            BARRICADE_HALF_WIDTH * 0.18,
            0.06
        )
        return true
    }

    /** Called by the lightning-grenade ability before shared cooldown handling. */
    fun noteLightningGrenadeCast(player: ServerPlayer) {
        if (!hasAspect(player, ArcTitanAspectRules.TOUCH_OF_THUNDER)) return
        val state = playerStates.computeIfAbsent(player.uuid) { PlayerState() }
        if (state.lightningBonusAvailable) {
            state.lightningBonusAvailable = false
            state.refundLightningCast = true
        }
    }

    /** Called after shared cooldown assignment; returns true when it restored a bonus charge. */
    fun onAbilityCast(player: ServerPlayer, slot: AbilitySlot): Boolean {
        if (!isArcTitan(player)) return false
        if (slot == AbilitySlot.CLASS_ABILITY) grantStormsKeepCastCharge(player)
        if (slot != AbilitySlot.GRENADE) return false
        val state = playerStates.computeIfAbsent(player.uuid) { PlayerState() }
        if (!state.refundLightningCast) return false
        state.refundLightningCast = false
        PlayerDestinyDataApi.get(player).cooldowns.clear(AbilitySlot.GRENADE)
        DestinyNetworking.syncCooldowns(player)
        return true
    }

    fun modifyIncomingDamage(player: ServerPlayer, source: DamageSource, amount: Float): Float {
        if (amount <= 0.0f || !isArcTitan(player)) return amount
        if (isBlockedByBarricade(player, source)) {
            player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.x, player.eyeY, player.z, 8, 0.18, 0.28, 0.18, 0.06)
            return 0.0f
        }
        val state = playerStates.computeIfAbsent(player.uuid) { PlayerState() }
        val shield = state.juggernautShield ?: return amount
        val attacker = source.directEntity ?: source.entity ?: return amount
        val toAttacker = attacker.position().subtract(player.position())
        val horizontal = Vec3(toAttacker.x, 0.0, toAttacker.z)
        val dot = if (horizontal.lengthSqr() > 1.0e-6) horizontal.normalize().dot(horizontalFacing(player)) else -1.0
        val result = ArcTitanAspectRules.absorbJuggernautDamage(shield, amount, dot)
        state.juggernautShield = result.state.takeIf { it.remainingCapacity > 0.0f }
        if (result.absorbedDamage > 0.0f) {
            ArcBoltChargeRuntime.addBoltCharge(player, result.boltChargeGained, "无畏护甲")
            player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.x + horizontalFacing(player).x, player.eyeY, player.z + horizontalFacing(player).z, 16, 0.35, 0.55, 0.35, 0.10)
        }
        if (result.shouldConsumeClassAbility) consumeClassAbility(player)
        return result.passedThroughDamage
    }

    fun modifyOutgoingDamage(attacker: ServerPlayer, source: DamageSource, amount: Float): Float {
        if (amount <= 0.0f || !isArcTitan(attacker)) return amount
        val active = isKnockoutActive(attacker)
        return amount * ArcTitanAspectRules.knockoutMeleeDamageMultiplier(
            hasAspect(attacker, ArcTitanAspectRules.KNOCKOUT),
            active,
            isMeleeDamage(attacker, source),
            isPoweredMelee(attacker, source)
        )
    }

    fun afterSuccessfulDamage(
        target: LivingEntity,
        source: DamageSource,
        actualDamage: Float,
        targetHealthBefore: Float,
        targetShieldWasPresent: Boolean
    ) {
        if (actualDamage <= 0.0f || resolvingBoltDischarge.get()) return
        val attacker = source.entity as? ServerPlayer ?: return
        if (attacker === target) return
        val now = attacker.serverLevel().gameTime
        val state = playerStates.computeIfAbsent(attacker.uuid) { PlayerState() }
        if (isArcTitan(attacker)) {
            val knockout = ArcTitanAspectRules.resolveKnockoutDamageTrigger(
                hasAspect(attacker, ArcTitanAspectRules.KNOCKOUT),
                state.knockoutExpiresAt > now,
                true,
                isMeleeDamage(attacker, source),
                targetHealthBefore > target.maxHealth * 0.5f && target.health <= target.maxHealth * 0.5f,
                targetShieldWasPresent && target.absorptionAmount <= 0.0f &&
                    (target as? ServerPlayer)?.let { PlayerDestinyDataApi.get(it).combatState.healthShield <= 0.0f } != false,
                now
            )
            knockout.refreshedExpiresAt?.let { expiresAt ->
                state.knockoutExpiresAt = expiresAt
                DestinyStatusRules.syncTemporaryBuff(attacker, "destiny2-mod:aspect/knockout", "重击", (expiresAt - now).toInt())
            }
        }

        val qualifyingBarricades = qualifyingStormsKeepBarricades(attacker).size
        val result = ArcTitanAspectRules.resolveStormsKeepWeaponDamage(
            qualifyingBarricades > 0,
            true,
            qualifyingBarricades,
            ArcBoltChargeRuntime.stacks(attacker),
            source.directEntity is DestinyWeaponDamageCarrier,
            true,
            isHostile(attacker, target),
            target.isInvulnerableTo(source)
        )
        if (result.shouldDischargeBoltCharge) {
            ArcBoltChargeRuntime.consumeAll(attacker)
            releaseBoltCharge(attacker, target)
        }
    }

    fun onFinalBlow(attacker: ServerPlayer, defeated: LivingEntity, source: DamageSource) {
        if (!isArcTitan(attacker)) return
        val result = ArcTitanAspectRules.resolveKnockoutMeleeFinalBlow(
            hasAspect(attacker, ArcTitanAspectRules.KNOCKOUT),
            isMeleeDamage(attacker, source) && isKnockoutActive(attacker),
            targetRank(defeated)
        )
        if (result.healAmount > 0.0f) attacker.heal(result.healAmount)
        if (result.shouldGrantAmplified) DestinyStatusRules.applyAmplified(attacker, result.amplifiedDurationTicks)
    }

    fun isKnockoutArcMelee(attacker: ServerPlayer, source: DamageSource): Boolean =
        ArcTitanAspectRules.knockoutMeleeIsArc(
            hasAspect(attacker, ArcTitanAspectRules.KNOCKOUT),
            isKnockoutActive(attacker),
            isMeleeDamage(attacker, source)
        )

    private fun tickJuggernaut(player: ServerPlayer, state: PlayerState, now: Long) {
        if (!hasAspect(player, ArcTitanAspectRules.JUGGERNAUT) || !player.isSprinting) {
            state.juggernautSprintTicks = 0
            state.juggernautShield = null
            return
        }
        state.juggernautSprintTicks++
        val classReady = PlayerDestinyDataApi.get(player).cooldowns.isReady(AbilitySlot.CLASS_ABILITY, now)
        val canActivate = ArcTitanAspectRules.canActivateJuggernaut(
            true,
            state.juggernautSprintTicks,
            if (classReady) 1.0f else 0.0f,
            1.0f
        )
        if (!canActivate) {
            state.juggernautShield = null
            return
        }
        if (state.juggernautShield == null) {
            state.juggernautShield = ArcTitanAspectRules.newJuggernautShield(player.hasEffect(DestinyEffects.AMPLIFIED))
        }
        if (now % 3L == 0L) {
            val forward = horizontalFacing(player)
            player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.x + forward.x * 1.1, player.eyeY, player.z + forward.z * 1.1, 4, 0.42, 0.58, 0.42, 0.03)
        }
    }

    private fun tickKnockoutReach(player: ServerPlayer, state: PlayerState, now: Long) {
        val active = hasAspect(player, ArcTitanAspectRules.KNOCKOUT) && state.knockoutExpiresAt > now
        val attribute = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE) ?: return
        if (active && attribute.getModifier(KNOCKOUT_REACH_ID) == null) {
            val desired = ArcTitanAspectRules.knockoutMeleeReach(attribute.baseValue, true, true)
            attribute.addTransientModifier(AttributeModifier(KNOCKOUT_REACH_ID, desired - attribute.baseValue, AttributeModifier.Operation.ADD_VALUE))
        } else if (!active && attribute.getModifier(KNOCKOUT_REACH_ID) != null) {
            attribute.removeModifier(KNOCKOUT_REACH_ID)
        }
    }

    private fun tickLightningCharges(player: ServerPlayer, state: PlayerState, now: Long) {
        if (!hasAspect(player, ArcTitanAspectRules.TOUCH_OF_THUNDER)) {
            state.lightningBonusAvailable = true
            state.refundLightningCast = false
            state.lightningBaseRechargeObserved = false
            return
        }
        val ready = PlayerDestinyDataApi.get(player).cooldowns.isReady(AbilitySlot.GRENADE, now)
        if (!ready) state.lightningBaseRechargeObserved = true
        if (ready && state.lightningBaseRechargeObserved) {
            state.lightningBonusAvailable = true
            state.lightningBaseRechargeObserved = false
        }
    }

    private fun tickBarricades(server: MinecraftServer, now: Long) {
        if (now % 5L != 0L || lastBarricadeTick == now) return
        lastBarricadeTick = now
        barricades.entries.removeIf { (_, barrier) ->
            val level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, barrier.dimension))
            if (level == null || barrier.expiresAt <= now) {
                if (level != null) level.getEntity(barrier.visualEntityId)?.discard()
                true
            } else false
        }
        if (now % ArcTitanAspectRules.MINECRAFT_CALIBRATION_STORMS_KEEP_BARRICADE_STACK_INTERVAL_TICKS == 0L) {
            server.playerList.players.forEach { target ->
                val qualifying = qualifyingStormsKeepBarricades(target).size
                val gain = ArcTitanAspectRules.stormsKeepBarricadeBoltChargeGain(
                    qualifying > 0,
                    true,
                    qualifying,
                    ArcBoltChargeRuntime.stacks(target),
                    now,
                    nextBarricadeChargeTicks[target.uuid] ?: Long.MIN_VALUE
                )
                if (gain > 0) {
                    ArcBoltChargeRuntime.addBoltCharge(target, gain, "风暴要塞")
                    nextBarricadeChargeTicks[target.uuid] =
                        ArcTitanAspectRules.nextStormsKeepBarricadeStackTick(now, gain) ?: now
                }
            }
        }
    }

    private fun grantStormsKeepCastCharge(caster: ServerPlayer) {
        if (!hasAspect(caster, ArcTitanAspectRules.STORMS_KEEP)) return
        val radius = ArcTitanAspectRules.MINECRAFT_CALIBRATION_STORMS_KEEP_TEAM_RADIUS
        caster.serverLevel().getEntitiesOfClass(ServerPlayer::class.java, caster.boundingBox.inflate(radius)) { target ->
            target.isAlive && !target.isSpectator && (target === caster || caster.isAlliedTo(target))
        }.forEach { target ->
            val gain = ArcTitanAspectRules.stormsKeepCastBoltChargeGain(true, true, true, target.distanceToSqr(caster))
            ArcBoltChargeRuntime.addBoltCharge(target, gain, "风暴要塞")
        }
    }

    private fun qualifyingStormsKeepBarricades(player: ServerPlayer): List<Barricade> = barricades.values.filter { barrier ->
        barrier.dimension == player.serverLevel().dimension().location() && barrier.expiresAt > player.serverLevel().gameTime &&
            player.server.playerList.getPlayer(barrier.ownerId)?.let { owner ->
                hasAspect(owner, ArcTitanAspectRules.STORMS_KEEP) && (owner === player || owner.isAlliedTo(player))
            } == true && isBehindBarrier(player.position(), barrier)
    }

    private fun isBlockedByBarricade(player: ServerPlayer, source: DamageSource): Boolean {
        if (!source.`is`(DamageTypeTags.IS_PROJECTILE)) return false
        val projectile = source.directEntity ?: return false
        val origin = source.entity?.takeIf { it !== projectile }?.position() ?: projectile.position()
        return barricades.values.any { barrier ->
            barrier.dimension == player.serverLevel().dimension().location() &&
                barrier.expiresAt > player.serverLevel().gameTime &&
                player.server.playerList.getPlayer(barrier.ownerId)?.let { owner -> owner === player || owner.isAlliedTo(player) } == true &&
                segmentCrossesBarrier(origin, player.eyePosition, barrier)
        }
    }

    private fun segmentCrossesBarrier(from: Vec3, to: Vec3, barrier: Barricade): Boolean {
        val fromSide = from.subtract(barrier.center).dot(barrier.normal)
        val toSide = to.subtract(barrier.center).dot(barrier.normal)
        if (fromSide * toSide >= 0.0) return false
        val t = fromSide / (fromSide - toSide)
        val hit = from.add(to.subtract(from).scale(t))
        val right = Vec3(-barrier.normal.z, 0.0, barrier.normal.x)
        val local = hit.subtract(barrier.center)
        return kotlin.math.abs(local.dot(right)) <= BARRICADE_HALF_WIDTH && local.y in 0.0..BARRICADE_HEIGHT
    }

    private fun isBehindBarrier(position: Vec3, barrier: Barricade): Boolean {
        val delta = position.subtract(barrier.center)
        val right = Vec3(-barrier.normal.z, 0.0, barrier.normal.x)
        return delta.dot(barrier.normal) in -BARRICADE_BEHIND_RANGE..-0.05 &&
            kotlin.math.abs(delta.dot(right)) <= BARRICADE_HALF_WIDTH + 1.5 &&
            kotlin.math.abs(delta.y) <= 3.0
    }

    private fun removeBarricadeEntity(server: MinecraftServer, barrier: Barricade) {
        val key = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            barrier.dimension
        )
        server.getLevel(key)?.getEntity(barrier.visualEntityId)?.discard()
    }

    private fun releaseBoltCharge(owner: ServerPlayer, primary: LivingEntity) {
        val victims = owner.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(primary.position(), BOLT_DISCHARGE_RADIUS * 2.0, BOLT_DISCHARGE_RADIUS * 2.0, BOLT_DISCHARGE_RADIUS * 2.0)
        ) { candidate -> candidate !== primary && isHostile(owner, candidate) }
            .sortedBy { it.distanceToSqr(primary) }
            .take(BOLT_DISCHARGE_MAX_TARGETS)
        val proxy = ArcAbilityDamageEntity(owner.serverLevel(), primary.position(), owner, AbilitySlot.CLASS_ABILITY)
        owner.serverLevel().addFreshEntity(proxy)
        val damageSource = owner.damageSources().indirectMagic(proxy, owner)
        resolvingBoltDischarge.set(true)
        try {
            victims.forEach { target -> target.hurt(damageSource, BOLT_DISCHARGE_DAMAGE) }
        } finally {
            resolvingBoltDischarge.set(false)
        }
        owner.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, primary.x, primary.y + primary.bbHeight * 0.5, primary.z, 72, 2.0, 0.9, 2.0, 0.24)
        owner.serverLevel().playSound(null, primary.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.9f, 1.4f)
    }

    private fun consumeClassAbility(player: ServerPlayer) {
        val data = PlayerDestinyDataApi.get(player)
        val ability = DestinyAbilityRegistry.abilityFor(data, AbilitySlot.CLASS_ABILITY) ?: return
        val now = player.serverLevel().gameTime
        val cooldown = DestinyStatFormulas.cooldownTicks(ability.baseCooldownTicks, AbilitySlot.CLASS_ABILITY, DestinyStatsResolver.resolve(player))
        data.cooldowns.setCooldown(AbilitySlot.CLASS_ABILITY, now, cooldown)
        ServerPlayNetworking.send(player, DestinyNetworking.SyncCooldownPayload(AbilitySlot.CLASS_ABILITY.legacyNetworkId, cooldown, cooldown))
    }

    private fun removeKnockoutReach(player: ServerPlayer) {
        player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE)?.removeModifier(KNOCKOUT_REACH_ID)
    }

    private fun isKnockoutActive(player: ServerPlayer): Boolean =
        (playerStates[player.uuid]?.knockoutExpiresAt ?: Long.MIN_VALUE) > player.serverLevel().gameTime

    private fun isMeleeDamage(attacker: ServerPlayer, source: DamageSource): Boolean =
        (source.directEntity === attacker && !source.`is`(DamageTypeTags.IS_PROJECTILE)) ||
            (source.directEntity as? DestinyAbilityDamageCarrier)?.destinyAbilitySlot == AbilitySlot.MELEE

    private fun isPoweredMelee(attacker: ServerPlayer, source: DamageSource): Boolean =
        source.directEntity !== attacker &&
            (source.directEntity as? DestinyAbilityDamageCarrier)?.destinyAbilitySlot == AbilitySlot.MELEE

    private fun targetRank(target: LivingEntity): ArcTitanAspectRules.KnockoutTargetRank = when {
        target is Player -> ArcTitanAspectRules.KnockoutTargetRank.PLAYER
        target is EnderDragon || target is WitherBoss || target is Warden -> ArcTitanAspectRules.KnockoutTargetRank.BOSS_OR_CHAMPION
        target.maxHealth >= 40.0f -> ArcTitanAspectRules.KnockoutTargetRank.MAJOR
        else -> ArcTitanAspectRules.KnockoutTargetRank.MINOR
    }

    private fun isHostile(owner: ServerPlayer, target: LivingEntity): Boolean = when (target) {
        is Player -> !target.isCreative && !target.isSpectator && !owner.isAlliedTo(target)
        is Monster -> true
        else -> target !== owner && !owner.isAlliedTo(target)
    }

    private fun horizontalFacing(player: ServerPlayer): Vec3 {
        val look = player.lookAngle
        val horizontal = Vec3(look.x, 0.0, look.z)
        return if (horizontal.lengthSqr() > 1.0e-6) horizontal.normalize() else Vec3(0.0, 0.0, 1.0)
    }

    private fun hasAspect(player: ServerPlayer, id: String): Boolean =
        isArcTitan(player) && DestinyAspectRuntime.hasAspect(player, id)

    private fun isArcTitan(player: ServerPlayer): Boolean =
        PlayerDestinyDataApi.get(player).subclass == DestinySubclassType.ARC_TITAN
}
