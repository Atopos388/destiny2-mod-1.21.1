package atopos.destiny2.common.effect

import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.aspect.SolarWarlockFragmentRules
import atopos.destiny2.common.aspect.SolarWarlockFragmentRuntime
import atopos.destiny2.common.combat.DestinyExplosionRuntime
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.phys.AABB
import java.util.ArrayDeque
import java.util.UUID

/** One queued, server-authoritative ignition path shared by Solar abilities and fragments. */
object SolarIgnitionRuntime {
    const val BASE_RADIUS = 4.0
    const val BASE_DAMAGE = 20.0f

    private data class IgnitionRequest(
        val target: LivingEntity,
        val context: SolarScorchContext?
    )

    private data class IgnitionChain(
        val pending: ArrayDeque<IgnitionRequest> = ArrayDeque(),
        val visitedTargets: MutableSet<UUID> = linkedSetOf(),
        var processing: Boolean = false
    )

    private data class ActiveSolarDamage(
        val targetId: UUID,
        val context: SolarScorchContext,
        val ignition: Boolean
    )

    private val chains = mutableMapOf<UUID, IgnitionChain>()
    private val activeDamage = ThreadLocal<ActiveSolarDamage?>()

    /** Enqueues instead of recursing, so Char chains cannot overflow or revisit an entity. */
    fun ignite(primaryTarget: LivingEntity, context: SolarScorchContext?) {
        if (primaryTarget.level() !is ServerLevel) return
        val chainId = context?.castId ?: UUID.randomUUID()
        val chain = chains.getOrPut(chainId) { IgnitionChain() }
        chain.pending.addLast(IgnitionRequest(primaryTarget, context))
        if (chain.processing) return

        chain.processing = true
        try {
            while (chain.pending.isNotEmpty()) {
                val request = chain.pending.removeFirst()
                if (!chain.visitedTargets.add(request.target.uuid)) continue
                processIgnition(request.target, request.context)
            }
        } finally {
            chains.remove(chainId)
        }
    }

    fun activeDamageContext(target: LivingEntity): SolarScorchContext? =
        activeDamage.get()?.takeIf { it.targetId == target.uuid }?.context

    fun isApplyingIgnitionDamage(target: LivingEntity): Boolean =
        activeDamage.get()?.let { it.targetId == target.uuid && it.ignition } == true

    fun <T> withAttributedSolarDamage(
        target: LivingEntity,
        context: SolarScorchContext,
        ignition: Boolean,
        action: () -> T
    ): T {
        val previous = activeDamage.get()
        activeDamage.set(ActiveSolarDamage(target.uuid, context, ignition))
        return try {
            action()
        } finally {
            activeDamage.set(previous)
        }
    }

    private fun processIgnition(primaryTarget: LivingEntity, context: SolarScorchContext?) {
        val level = primaryTarget.level() as? ServerLevel ?: return
        val sourcePlayer = context?.let { level.server.playerList.getPlayer(it.sourcePlayerId) }
        val radius = SolarWarlockFragmentRules.ignitionRadius(
            BASE_RADIUS,
            sourcePlayer.hasFragment(SolarWarlockFragmentRules.EMBER_OF_ERUPTION)
        )
        val source = level.damageSources().explosion(null, sourcePlayer)
        val center = primaryTarget.position()
        val bounds = AABB(
            center.x - radius,
            center.y - radius,
            center.z - radius,
            center.x + radius,
            center.y + radius,
            center.z + radius
        )
        val radiusSquared = radius * radius
        val victims = level.getEntitiesOfClass(LivingEntity::class.java, bounds) { candidate ->
            candidate.isAlive && candidate.position().distanceToSqr(center) <= radiusSquared &&
                isEnemy(sourcePlayer, candidate)
        }.toMutableList()
        if (primaryTarget.isAlive && primaryTarget !in victims && isEnemy(sourcePlayer, primaryTarget)) {
            victims += primaryTarget
        }

        victims.forEach { victim ->
            val wasAlive = victim.isAlive
            val wasScorched = victim === primaryTarget || victim.hasEffect(DestinyEffects.SCORCH)
            if (context != null) {
                withAttributedSolarDamage(victim, context, ignition = true) {
                    DestinyExplosionRuntime.hurtWithoutKnockback(victim, source, BASE_DAMAGE)
                }
            } else {
                DestinyExplosionRuntime.hurtWithoutKnockback(victim, source, BASE_DAMAGE)
            }
            if (wasAlive && !victim.isAlive && sourcePlayer != null) {
                SolarWarlockFragmentRuntime.onIgnitionFinalBlow(sourcePlayer, victim, context, wasScorched)
            }
        }

        val charStacks = sourcePlayer?.let { player ->
            SolarWarlockFragmentRules.charSpreadScorchStacks(
                player.hasFragment(SolarWarlockFragmentRules.EMBER_OF_CHAR),
                player.hasFragment(SolarWarlockFragmentRules.EMBER_OF_ASHES)
            )
        }
        if (charStacks != null && context != null) {
            val charContext = context.copy(sourceKind = SolarDamageKind.IGNITION)
            victims.asSequence()
                .filter { it !== primaryTarget && it.isAlive }
                .forEach { victim ->
                    DestinyStatusRules.applyScorchExact(
                        victim,
                        charStacks,
                        DestinyStatusRules.MEDIUM_DURATION,
                        sourcePlayer,
                        SolarDamageKind.IGNITION,
                        charContext.castId
                    )
                }
        }

        level.sendParticles(
            ParticleTypes.FLAME,
            center.x,
            primaryTarget.y + primaryTarget.bbHeight * 0.5,
            center.z,
            if (radius > BASE_RADIUS) 72 else 48,
            radius * 0.30,
            radius * 0.25,
            radius * 0.30,
            0.12
        )
        level.playSound(
            null,
            primaryTarget.blockPosition(),
            SoundEvents.GENERIC_EXPLODE.value(),
            SoundSource.PLAYERS,
            0.9f,
            1.15f
        )
    }

    private fun ServerPlayer?.hasFragment(id: String): Boolean =
        this != null && DestinyAspectRuntime.hasFragment(this, id)

    /** If the owner is offline, never guess that an arbitrary player is hostile. */
    private fun isEnemy(sourcePlayer: ServerPlayer?, candidate: LivingEntity): Boolean {
        if (candidate === sourcePlayer) return false
        if (sourcePlayer == null) {
            return candidate !is Player && candidate !is TamableAnimal && candidate.team == null
        }
        return !candidate.isAlliedTo(sourcePlayer)
    }
}
