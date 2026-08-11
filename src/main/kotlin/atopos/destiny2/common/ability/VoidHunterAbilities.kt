package atopos.destiny2.common.ability

import atopos.destiny2.common.ability.GamblerDodgeAbility
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.entity.ShadowshotAnchorEntity
import atopos.destiny2.common.entity.SnareBombEntity
import atopos.destiny2.common.entity.VoidGrenadeEntity
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.sound.DestinySounds
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

object VoidHunterAbilities {
    private const val MOD_ID = "destiny2-mod"
    private const val GRENADE_RELEASE_DELAY_TICKS = 6L
    private const val GRENADE_RELEASE_FALLBACK_TICKS = 5L
    private const val CHARGED_MELEE_RELEASE_DELAY_TICKS = 6L
    private const val CHARGED_MELEE_RELEASE_FALLBACK_TICKS = 5L
    private const val SHADOWSHOT_AURA_DURATION_TICKS = 28
    private const val GRENADE_LOCATOR_RIGHT = -0.29004
    private const val CHARGED_MELEE_LOCATOR_RIGHT = 0.29004
    private const val GRENADE_LOCATOR_UP = -0.07891
    private const val GRENADE_LOCATOR_FORWARD = 1.02164
    private val pendingGrenades = ConcurrentLinkedQueue<PendingGrenade>()
    private val pendingChargedMelees = ConcurrentLinkedQueue<PendingChargedMelee>()

    private data class PendingGrenade(
        val playerId: UUID,
        val releaseGameTime: Long,
        var animatedHandOrigin: Vec3? = null
    )

    private data class PendingChargedMelee(
        val playerId: UUID,
        val releaseGameTime: Long,
        var animatedHandOrigin: Vec3? = null
    )

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            val continuing = mutableListOf<PendingGrenade>()
            while (true) {
                val pending = pendingGrenades.poll() ?: break
                val player = server.playerList.getPlayer(pending.playerId) ?: continue
                val now = player.serverLevel().gameTime
                val animatedOrigin = pending.animatedHandOrigin
                if (now >= pending.releaseGameTime && animatedOrigin != null) {
                    releaseVoidGrenade(player, animatedOrigin)
                } else if (now >= pending.releaseGameTime + GRENADE_RELEASE_FALLBACK_TICKS) {
                    releaseVoidGrenade(player, null)
                } else {
                    continuing.add(pending)
                }
            }
            continuing.forEach(pendingGrenades::add)

            val continuingMelees = mutableListOf<PendingChargedMelee>()
            while (true) {
                val pending = pendingChargedMelees.poll() ?: break
                val player = server.playerList.getPlayer(pending.playerId) ?: continue
                val now = player.serverLevel().gameTime
                val animatedOrigin = pending.animatedHandOrigin
                if (now >= pending.releaseGameTime && animatedOrigin != null) {
                    releaseSnareBomb(player, animatedOrigin)
                } else if (now >= pending.releaseGameTime + CHARGED_MELEE_RELEASE_FALLBACK_TICKS) {
                    releaseSnareBomb(player, null)
                } else {
                    continuingMelees.add(pending)
                }
            }
            continuingMelees.forEach(pendingChargedMelees::add)
        }
    }

    val VOID_GRENADE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_hunter_void_grenade"),
        slot = AbilitySlot.GRENADE,
        displayName = "虚空手雷",
        baseCooldownTicks = 121 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val level = context.player.serverLevel()
            pendingGrenades.add(
                PendingGrenade(
                    playerId = context.player.uuid,
                    releaseGameTime = level.gameTime + GRENADE_RELEASE_DELAY_TICKS
                )
            )
            ServerPlayNetworking.send(context.player, DestinyNetworking.PlayHunterGrenadeThrowPayload())
            return true
        }
    }

    val SNARE_BOMB = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_hunter_snare_bomb"),
        slot = AbilitySlot.MELEE,
        displayName = "陷阱炸弹",
        baseCooldownTicks = 90 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val level = context.player.serverLevel()
            pendingChargedMelees.add(
                PendingChargedMelee(
                    playerId = context.player.uuid,
                    releaseGameTime = level.gameTime + CHARGED_MELEE_RELEASE_DELAY_TICKS
                )
            )
            ServerPlayNetworking.send(context.player, DestinyNetworking.PlayHunterChargedMeleePayload())
            return true
        }
    }

    val GAMBLER_DODGE = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_hunter_gambler_dodge"),
        slot = AbilitySlot.CLASS_ABILITY,
        displayName = "赌徒闪身",
        baseCooldownTicks = 38 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            GamblerDodgeAbility.perform(context.player, context.extraData)
            return true
        }
    }

    val SHADOWSHOT = object : AbstractExecutableAbility(
        id = ResourceLocation.fromNamespaceAndPath(MOD_ID, "void_hunter_shadowshot"),
        slot = AbilitySlot.SUPER,
        displayName = "暗影陷阱",
        baseCooldownTicks = 455 * 20
    ) {
        override fun cast(context: DestinyAbilityContext): Boolean {
            val level = context.player.serverLevel()
            DestinyNetworking.broadcastVoidHunterSuperAura(context.player, SHADOWSHOT_AURA_DURATION_TICKS)
            val anchor = ShadowshotAnchorEntity(level, context.player)
            anchor.shootFromRotation(context.player, context.player.xRot, context.player.yRot, 0.0f, 3.0f, 1.0f)
            level.addFreshEntity(anchor)
            DestinyStatusRules.applyVoidInvisibility(context.player, 100)
            level.sendParticles(ParticleTypes.DRAGON_BREATH, context.player.x, context.player.eyeY, context.player.z, 28, 0.35, 0.25, 0.35, 0.05)
            level.playSound(null, context.player.blockPosition(), DestinySounds.SHADOWSHOT_CAST, SoundSource.PLAYERS, 0.9f, 1.0f)
            return true
        }
    }

    val ALL = listOf(VOID_GRENADE, SNARE_BOMB, GAMBLER_DODGE, SHADOWSHOT)

    fun releaseGrenadeFromAnimatedHand(player: ServerPlayer, origin: Vec3) {
        if (!origin.x.isFinite() || !origin.y.isFinite() || !origin.z.isFinite()) return
        val eye = Vec3(player.x, player.eyeY, player.z)
        if (origin.distanceToSqr(eye) > 9.0) return
        val constrainedOrigin = constrainAnimatedHandSide(player, origin, GRENADE_LOCATOR_RIGHT)
        val pending = pendingGrenades.firstOrNull { it.playerId == player.uuid } ?: return
        pending.animatedHandOrigin = constrainedOrigin
        if (player.serverLevel().gameTime >= pending.releaseGameTime && pendingGrenades.remove(pending)) {
            releaseVoidGrenade(player, constrainedOrigin)
        }
    }

    fun releaseChargedMeleeFromAnimatedHand(player: ServerPlayer, origin: Vec3) {
        if (!origin.x.isFinite() || !origin.y.isFinite() || !origin.z.isFinite()) return
        val eye = Vec3(player.x, player.eyeY, player.z)
        if (origin.distanceToSqr(eye) > 9.0) return
        val constrainedOrigin = constrainAnimatedHandSide(player, origin, CHARGED_MELEE_LOCATOR_RIGHT)
        val pending = pendingChargedMelees.firstOrNull { it.playerId == player.uuid } ?: return
        pending.animatedHandOrigin = constrainedOrigin
        if (player.serverLevel().gameTime >= pending.releaseGameTime && pendingChargedMelees.remove(pending)) {
            releaseSnareBomb(player, constrainedOrigin)
        }
    }

    private fun releaseSnareBomb(player: ServerPlayer, animatedHandOrigin: Vec3?) {
        if (!player.isAlive) return
        val level = player.serverLevel()
        val handOrigin = animatedHandOrigin ?: fallbackHandOrigin(player, CHARGED_MELEE_LOCATOR_RIGHT)
        val snare = SnareBombEntity(level, player)
        snare.setPos(handOrigin.x, handOrigin.y, handOrigin.z)
        snare.shootFromRotation(player, player.xRot, player.yRot, 0.0f, 1.5f, 1.0f)
        level.addFreshEntity(snare)
        level.sendParticles(ParticleTypes.SMOKE, handOrigin.x, handOrigin.y, handOrigin.z, 12, 0.3, 0.15, 0.3, 0.02)
        level.playSound(null, player.blockPosition(), DestinySounds.SNARE_BOMB_CAST, SoundSource.PLAYERS, 0.7f, 1.0f)
    }

    private fun releaseVoidGrenade(player: ServerPlayer, animatedHandOrigin: Vec3?) {
        if (!player.isAlive) return
        val level = player.serverLevel()
        /*
         * The visual-left-hand grenade uses the authored righthand/righthand_pos
         * chain and its own release side at t=0.30. The guide cube is not rendered.
         */
        val handOrigin = animatedHandOrigin ?: fallbackHandOrigin(player, GRENADE_LOCATOR_RIGHT)
        val grenade = VoidGrenadeEntity(level, player)
        grenade.setPos(handOrigin.x, handOrigin.y, handOrigin.z)
        DestinyGrenadeThrow.launch(grenade, player, DestinyGrenadeThrow.Profile.AREA)
        level.addFreshEntity(grenade)
        level.playSound(null, handOrigin.x, handOrigin.y, handOrigin.z, DestinySounds.VOID_GRENADE_CAST, SoundSource.PLAYERS, 1.0f, 1.0f)
    }

    private fun fallbackHandOrigin(player: ServerPlayer, rightOffset: Double): Vec3 {
        val look = player.lookAngle.normalize()
        val right = look.cross(Vec3(0.0, 1.0, 0.0)).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(-1.0, 0.0, 0.0)
        }
        val viewUp = right.cross(look).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(0.0, 1.0, 0.0)
        }
        return Vec3(player.x, player.eyeY, player.z)
            .add(look.scale(GRENADE_LOCATOR_FORWARD))
            .add(right.scale(rightOffset))
            .add(viewUp.scale(GRENADE_LOCATOR_UP))
    }

    /**
     * Keep the authored forward/up motion, but never allow the two throw
     * actions to cross the player's centre line because of a mirrored model
     * matrix or a missed render sample.
     */
    private fun constrainAnimatedHandSide(player: ServerPlayer, origin: Vec3, sideOffset: Double): Vec3 {
        val eye = Vec3(player.x, player.eyeY, player.z)
        val look = player.lookAngle.normalize()
        val right = look.cross(Vec3(0.0, 1.0, 0.0)).let {
            if (it.lengthSqr() > 1.0e-8) it.normalize() else Vec3(-1.0, 0.0, 0.0)
        }
        val lateral = origin.subtract(eye).dot(right)
        val lateralMagnitude = kotlin.math.max(kotlin.math.abs(lateral), kotlin.math.abs(sideOffset))
        val constrainedLateral = if (sideOffset < 0.0) -lateralMagnitude else lateralMagnitude
        return origin.add(right.scale(constrainedLateral - lateral))
    }
}
