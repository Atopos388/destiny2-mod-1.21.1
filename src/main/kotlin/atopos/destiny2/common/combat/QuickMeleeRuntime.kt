package atopos.destiny2.common.combat

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.player.DestinyClassType
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.sound.DestinySounds
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Server-authoritative uncharged melee with a small cone-based target assist. */
object QuickMeleeRuntime {
    private val nextAllowedTick = mutableMapOf<UUID, Long>()

    fun register() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            nextAllowedTick.remove(handler.player.uuid)
        }
    }

    fun tryAttack(player: ServerPlayer): AttackResult? {
        if (!player.isAlive || player.isSpectator || player.isPassenger) return null

        val now = player.serverLevel().gameTime
        if (now < (nextAllowedTick[player.uuid] ?: 0L)) return null
        nextAllowedTick[player.uuid] = now + QuickMeleeRules.COOLDOWN_TICKS

        DestinyStatusRules.removeVoidInvisibility(player)
        player.swing(InteractionHand.MAIN_HAND, true)
        val hunterMelee = PlayerDestinyDataApi.get(player).destinyClass == DestinyClassType.HUNTER
        playSwingSound(player, hunterMelee)

        val target = selectTarget(player) ?: return AttackResult(null, hunterMelee)
        applyLightLunge(player, target)

        val damage = QuickMeleeRules.BASE_DAMAGE *
            DestinyStatFormulas.meleeDamageMultiplier(DestinyStatsResolver.resolve(player))
        if (target.hurt(player.damageSources().playerAttack(player), damage)) {
            target.knockback(0.22, player.x - target.x, player.z - target.z)
            if (hunterMelee) {
                playHunterHitSound(player, target)
            }
        }
        return AttackResult(target.id, hunterMelee)
    }

    private fun playSwingSound(player: ServerPlayer, hunterMelee: Boolean) {
        val level = player.serverLevel()
        level.playSound(
            null,
            player.x,
            player.y,
            player.z,
            if (hunterMelee) DestinySounds.HUNTER_MELEE_SWING else SoundEvents.PLAYER_ATTACK_STRONG,
            SoundSource.PLAYERS,
            if (hunterMelee) QuickMeleeRules.HUNTER_SOUND_VOLUME else 0.65f,
            if (hunterMelee) 1.0f else 1.05f
        )
    }

    private fun playHunterHitSound(player: ServerPlayer, target: LivingEntity) {
        val level = player.serverLevel()
        level.playSound(
            null,
            target.x,
            target.y + target.bbHeight * 0.5,
            target.z,
            DestinySounds.HUNTER_MELEE_HIT_COMMON,
            SoundSource.PLAYERS,
            QuickMeleeRules.HUNTER_SOUND_VOLUME,
            1.0f
        )
        level.playSound(
            null,
            target.x,
            target.y + target.bbHeight * 0.5,
            target.z,
            DestinySounds.HUNTER_MELEE_HIT_FLESH,
            SoundSource.PLAYERS,
            QuickMeleeRules.HUNTER_SOUND_VOLUME,
            1.0f
        )
    }

    private fun selectTarget(player: ServerPlayer): LivingEntity? {
        val eye = player.eyePosition
        val look = player.lookAngle.normalize()
        return player.serverLevel().getEntitiesOfClass(
            LivingEntity::class.java,
            player.boundingBox.inflate(QuickMeleeRules.ASSIST_RANGE)
        ) { candidate ->
            isValidTarget(player, candidate) && player.hasLineOfSight(candidate)
        }.mapNotNull { candidate ->
            val bounds = candidate.boundingBox
            val aimPoint = Vec3(
                candidate.x,
                bounds.minY + bounds.ysize * 0.62,
                candidate.z
            )
            val aimOffset = aimPoint.subtract(eye)
            if (aimOffset.lengthSqr() <= 0.0001) return@mapNotNull null

            // Range uses the closest point of the physical hitbox, while
            // direction aims at center mass. This avoids eye-point snapping
            // and keeps large or laterally moving targets stable.
            val closestPoint = Vec3(
                eye.x.coerceIn(bounds.minX, bounds.maxX),
                eye.y.coerceIn(bounds.minY, bounds.maxY),
                eye.z.coerceIn(bounds.minZ, bounds.maxZ)
            )
            val distance = closestPoint.distanceTo(eye)
            val score = QuickMeleeRules.candidateScore(
                distance,
                look.dot(aimOffset.normalize())
            )
                ?: return@mapNotNull null
            TargetCandidate(candidate, distance, score)
        }.maxByOrNull(TargetCandidate::score)?.entity
    }

    private fun isValidTarget(player: ServerPlayer, candidate: LivingEntity): Boolean {
        if (candidate === player || !candidate.isAlive || player.isAlliedTo(candidate)) return false
        if (candidate is Player && (candidate.isCreative || candidate.isSpectator)) return false
        return true
    }

    private fun applyLightLunge(player: ServerPlayer, target: LivingEntity) {
        val horizontal = Vec3(target.x - player.x, 0.0, target.z - player.z)
        val distance = horizontal.length()
        val speed = QuickMeleeRules.lungeSpeed(distance)
        if (speed <= 0.0 || distance <= 0.01) return

        val direction = horizontal.scale(1.0 / distance)
        val motion = player.deltaMovement
        player.deltaMovement = Vec3(
            motion.x * 0.25 + direction.x * speed,
            if (player.onGround()) 0.02 else motion.y,
            motion.z * 0.25 + direction.z * speed
        )
        player.hasImpulse = true
        player.hurtMarked = true
    }

    private data class TargetCandidate(
        val entity: LivingEntity,
        val distance: Double,
        val score: Double
    )

    data class AttackResult(
        val targetEntityId: Int?,
        val hunterMelee: Boolean
    )
}
