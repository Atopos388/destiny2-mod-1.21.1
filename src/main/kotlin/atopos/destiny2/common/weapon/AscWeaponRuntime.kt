package atopos.destiny2.common.weapon

import atopos.destiny2.common.combat.DestinyExplosionRuntime
import atopos.destiny2.common.item.GenericGunPackItem
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.tags.DamageTypeTags
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Server-authoritative signature traits for the six ASC imports.
 * Bungie's public weapon definitions provide the behavior, RPM and magazine values;
 * constants below are explicit Minecraft balance conversions rather than hidden D2 scalars.
 */
object AscWeaponRules {
    const val MEMENTO_ROUNDS = 6
    const val MEMENTO_DAMAGE_MULTIPLIER = 1.30f
    const val SIVA_MAX_STACKS = 6
    const val SIVA_DAMAGE_PER_STACK = 0.08f
    const val SIVA_DURATION_TICKS = 200
    const val ARC_CONDUCTOR_TICKS = 100
    const val ARC_DAMAGE_MULTIPLIER = 1.25f
    const val ARC_RESISTANCE_MULTIPLIER = 0.50f
    const val CHAIN_RADIUS = 5.0
    const val CHAIN_DAMAGE = 4.0f
    const val WHISPER_PRECISION_HITS = 3
    const val ACE_FIREFLY_POWER = 1.35f
    const val XENOPHAGE_EXPLOSION_POWER = 1.65f
    const val MOUNTAINTOP_EXPLOSION_POWER = 1.45f
    const val MOUNTAINTOP_SELF_DAMAGE_MULTIPLIER = 0.25f
    const val FIREFLY_RELOAD_MULTIPLIER = 0.75f
    const val MULLIGAN_CHANCE = 0.20f

    fun sivaDamageMultiplier(stacks: Int): Float =
        1.0f + stacks.coerceIn(0, SIVA_MAX_STACKS) * SIVA_DAMAGE_PER_STACK
}

object AscWeaponRuntime {
    val ACE = id("ace")
    val OUTBREAK = id("outbreakprefected")
    val RISKRUNNER = id("riskrunner")
    val MOUNTAINTOP = id("summit")
    val WHISPER = id("whisper")
    val XENOPHAGE = id("xeno")

    private const val ACE_KILL_PENDING = "AscAceKillPending"
    private const val ACE_MEMENTO = "AscAceMementoRounds"
    private const val ACE_FIREFLY_RELOAD = "AscAceFireflyReload"
    private const val WHISPER_HITS = "AscWhisperPrecisionHits"
    private val arcConductorUntil = ConcurrentHashMap<UUID, Long>()
    private val sivaStacks = ConcurrentHashMap<UUID, SivaState>()
    private val rapidHits = ConcurrentHashMap<Pair<UUID, UUID>, RapidHitState>()

    private data class RapidHitState(val hits: Int, val lastTick: Long)
    private data class SivaState(val stacks: Int, val expiresAtTick: Long)

    fun outgoingDamageMultiplier(player: ServerPlayer, stack: ItemStack, target: LivingEntity? = null): Float =
        when (GenericGunPackItem.id(stack)) {
            ACE -> if (int(stack, ACE_MEMENTO) > 0) AscWeaponRules.MEMENTO_DAMAGE_MULTIPLIER else 1.0f
            OUTBREAK -> AscWeaponRules.sivaDamageMultiplier(target?.let { currentSivaStacks(player, it) } ?: 0)
            RISKRUNNER -> if (isArcConductorActive(player)) AscWeaponRules.ARC_DAMAGE_MULTIPLIER else 1.0f
            else -> 1.0f
        }

    fun onShot(stack: ItemStack) {
        if (GenericGunPackItem.id(stack) == ACE) {
            putInt(stack, ACE_MEMENTO, (int(stack, ACE_MEMENTO) - 1).coerceAtLeast(0))
        }
    }

    fun onReloadCompleted(stack: ItemStack) {
        if (GenericGunPackItem.id(stack) != ACE) return
        if (bool(stack, ACE_KILL_PENDING)) {
            putBool(stack, ACE_KILL_PENDING, false)
            putInt(stack, ACE_MEMENTO, AscWeaponRules.MEMENTO_ROUNDS)
        }
        putBool(stack, ACE_FIREFLY_RELOAD, false)
    }

    fun reloadMultiplier(stack: ItemStack): Float =
        if (GenericGunPackItem.id(stack) == ACE && bool(stack, ACE_FIREFLY_RELOAD)) {
            AscWeaponRules.FIREFLY_RELOAD_MULTIPLIER
        } else 1.0f

    fun onHit(
        level: ServerLevel,
        shooter: ServerPlayer,
        stack: ItemStack,
        target: LivingEntity,
        impact: Vec3,
        precision: Boolean,
        killed: Boolean
    ) {
        when (GenericGunPackItem.id(stack)) {
            ACE -> onAceHit(level, stack, target, impact, precision, killed)
            OUTBREAK -> onOutbreakHit(level, shooter, target, precision, killed)
            RISKRUNNER -> onRiskrunnerHit(level, shooter, stack, target, impact, killed)
            WHISPER -> onWhisperHit(shooter, stack, precision)
            XENOPHAGE -> DestinyExplosionRuntime.explodeWithoutKnockback(
                level, shooter, impact.x, impact.y, impact.z, AscWeaponRules.XENOPHAGE_EXPLOSION_POWER
            )
            MOUNTAINTOP -> DestinyExplosionRuntime.explodeWithoutKnockback(
                level, shooter, impact.x, impact.y, impact.z, AscWeaponRules.MOUNTAINTOP_EXPLOSION_POWER
            )
        }
    }

    fun onBlockImpact(level: ServerLevel, shooter: ServerPlayer, weaponId: ResourceLocation?, impact: Vec3) {
        if (weaponId == WHISPER) onMiss(shooter, weaponId)
        val power = when (weaponId) {
            XENOPHAGE -> AscWeaponRules.XENOPHAGE_EXPLOSION_POWER
            MOUNTAINTOP -> AscWeaponRules.MOUNTAINTOP_EXPLOSION_POWER
            else -> return
        }
        DestinyExplosionRuntime.explodeWithoutKnockback(level, shooter, impact.x, impact.y, impact.z, power)
    }

    fun onMiss(player: ServerPlayer, weaponId: ResourceLocation?) {
        if (weaponId != WHISPER || player.random.nextFloat() >= AscWeaponRules.MULLIGAN_CHANCE) return
        val stack = heldStack(player, WHISPER) ?: return
        val profile = (stack.item as? DestinyRangedWeapon)?.combatProfile(stack) ?: return
        WeaponAmmoState.setMagazine(stack, profile, WeaponAmmoState.read(stack, profile).magazine + 1)
    }

    fun onArcDamageTaken(player: ServerPlayer, sourceElement: DestinyDamageElement, accepted: Boolean) {
        if (!accepted || sourceElement != DestinyDamageElement.ARC || heldStack(player, RISKRUNNER) == null) return
        arcConductorUntil[player.uuid] = player.level().gameTime + AscWeaponRules.ARC_CONDUCTOR_TICKS
    }

    fun modifyIncomingDamage(
        player: ServerPlayer,
        source: DamageSource,
        sourceElement: DestinyDamageElement?,
        amount: Float
    ): Float {
        var result = amount
        if (sourceElement == DestinyDamageElement.ARC && isArcConductorActive(player)) {
            result *= AscWeaponRules.ARC_RESISTANCE_MULTIPLIER
        }
        if (source.entity === player && source.`is`(DamageTypeTags.IS_EXPLOSION) && heldStack(player, MOUNTAINTOP) != null) {
            result *= AscWeaponRules.MOUNTAINTOP_SELF_DAMAGE_MULTIPLIER
        }
        return result
    }

    fun tickSelected(player: ServerPlayer, stack: ItemStack) {
        if (GenericGunPackItem.id(stack) == MOUNTAINTOP) {
            player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5, 0, false, false, false))
        }
        if (GenericGunPackItem.id(stack) == ACE && WeaponAimRuntime.progress(player) > 0.0f) {
            player.serverLevel().getEntitiesOfClass(
                LivingEntity::class.java,
                player.boundingBox.inflate(16.0)
            ) { it !== player && it is Enemy && !it.isAlliedTo(player) }
                .forEach { it.addEffect(MobEffectInstance(MobEffects.GLOWING, 5, 0, false, false, false)) }
        }
    }

    private fun onAceHit(
        level: ServerLevel,
        stack: ItemStack,
        target: LivingEntity,
        impact: Vec3,
        precision: Boolean,
        killed: Boolean
    ) {
        if (killed) putBool(stack, ACE_KILL_PENDING, true)
        if (precision && killed) {
            putBool(stack, ACE_FIREFLY_RELOAD, true)
            DestinyExplosionRuntime.explodeWithoutKnockback(
                level, target, impact.x, impact.y, impact.z, AscWeaponRules.ACE_FIREFLY_POWER
            )
        }
    }

    private fun onOutbreakHit(
        level: ServerLevel,
        shooter: ServerPlayer,
        target: LivingEntity,
        precision: Boolean,
        killed: Boolean
    ) {
        val key = shooter.uuid to target.uuid
        val previous = rapidHits[key]
        val hits = if (previous != null && level.gameTime - previous.lastTick <= 16) previous.hits + 1 else 1
        rapidHits[key] = RapidHitState(hits, level.gameTime)
        if (hits >= 3) {
            rapidHits[key] = RapidHitState(0, level.gameTime)
            attachNanites(target, 1)
        }
        if (precision && killed) {
            level.getEntitiesOfClass(LivingEntity::class.java, target.boundingBox.inflate(6.0)) {
                it !== target && it !== shooter && it.isAlive && !it.isAlliedTo(shooter)
            }.take(3).forEach { attachNanites(it, 2) }
        }
        if (killed) sivaStacks.remove(target.uuid)
    }

    private fun onRiskrunnerHit(
        level: ServerLevel,
        shooter: ServerPlayer,
        stack: ItemStack,
        target: LivingEntity,
        impact: Vec3,
        killed: Boolean
    ) {
        if (!isArcConductorActive(shooter)) return
        val chained = level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB.ofSize(impact, AscWeaponRules.CHAIN_RADIUS * 2, AscWeaponRules.CHAIN_RADIUS * 2, AscWeaponRules.CHAIN_RADIUS * 2)
        ) { it !== target && it !== shooter && it.isAlive && !it.isAlliedTo(shooter) }
            .minByOrNull { it.distanceToSqr(impact) }
        chained?.hurt(level.damageSources().magic(), AscWeaponRules.CHAIN_DAMAGE)
        if (level.random.nextFloat() < 0.5f) {
            val profile = (stack.item as? DestinyRangedWeapon)?.combatProfile(stack) ?: return
            WeaponAmmoState.setMagazine(stack, profile, WeaponAmmoState.read(stack, profile).magazine + 1)
        }
        if (killed) arcConductorUntil[shooter.uuid] = level.gameTime + AscWeaponRules.ARC_CONDUCTOR_TICKS
    }

    private fun onWhisperHit(player: ServerPlayer, stack: ItemStack, precision: Boolean) {
        val hits = if (precision) int(stack, WHISPER_HITS) + 1 else 0
        if (hits < AscWeaponRules.WHISPER_PRECISION_HITS) {
            putInt(stack, WHISPER_HITS, hits)
            return
        }
        putInt(stack, WHISPER_HITS, 0)
        val profile = (stack.item as? DestinyRangedWeapon)?.combatProfile(stack) ?: return
        WeaponAmmoState.reloadFromReservesInstantly(stack, player, profile)
    }

    private fun attachNanites(target: LivingEntity, amount: Int) {
        val now = target.level().gameTime
        sivaStacks.compute(target.uuid) { _, current ->
            val activeStacks = current?.takeIf { it.expiresAtTick >= now }?.stacks ?: 0
            SivaState(
                (activeStacks + amount).coerceAtMost(AscWeaponRules.SIVA_MAX_STACKS),
                now + AscWeaponRules.SIVA_DURATION_TICKS
            )
        }
    }

    private fun currentSivaStacks(player: ServerPlayer, target: LivingEntity): Int {
        val state = sivaStacks[target.uuid] ?: return 0
        if (state.expiresAtTick >= player.level().gameTime) return state.stacks
        sivaStacks.remove(target.uuid, state)
        return 0
    }

    private fun isArcConductorActive(player: ServerPlayer): Boolean =
        (arcConductorUntil[player.uuid] ?: 0L) >= player.level().gameTime && heldStack(player, RISKRUNNER) != null

    private fun heldStack(player: ServerPlayer, id: ResourceLocation): ItemStack? =
        listOf(player.mainHandItem, player.offhandItem).firstOrNull {
            !it.isEmpty && it.item is GenericGunPackItem && GenericGunPackItem.id(it) == id
        }

    private fun int(stack: ItemStack, key: String): Int =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getInt(key) ?: 0

    private fun bool(stack: ItemStack, key: String): Boolean =
        stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getBoolean(key) ?: false

    private fun putInt(stack: ItemStack, key: String, value: Int) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data -> data.update { it.putInt(key, value) } }
    }

    private fun putBool(stack: ItemStack, key: String, value: Boolean) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY) { data -> data.update { it.putBoolean(key, value) } }
    }

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("ascgun", path)
}
