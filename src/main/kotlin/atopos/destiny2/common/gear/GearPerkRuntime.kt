package atopos.destiny2.common.gear

import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.entity.MicroMissileProjectile
import atopos.destiny2.common.entity.HuntingMarkEntity
import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.Arrow
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object GearPerkRuntime {
    private const val RICOCHETED_TAG = "destiny2mod_ricocheted"
    private const val PART_STATS_APPLIED_TAG = "destiny2mod_part_stats_applied"
    private const val SPLIT_ARROW_TAG = "destiny2mod_split_arrow"
    private const val EAGER_EDGE_STOW_TICKS = 60
    private val MELEE_PART_ATTACK_SPEED_ID = ResourceLocation.fromNamespaceAndPath("destiny2-mod", "melee_part_attack_speed")

    private data class ActiveState(var ticks: Int, var stacks: Int = 1)

    private val cooldowns = mutableMapOf<UUID, MutableMap<GearPerkEffect, Int>>()
    private val activeStates = mutableMapOf<UUID, MutableMap<GearPerkEffect, ActiveState>>()
    private val lastHurtTicks = mutableMapOf<UUID, Int>()
    private val emberHits = mutableMapOf<UUID, MutableMap<Int, Int>>()
    private val emberHitTimers = mutableMapOf<UUID, MutableMap<Int, Int>>()
    private val markedTargets = mutableMapOf<UUID, MutableMap<Int, Int>>()
    private val boneRhythmTargets = mutableMapOf<UUID, MutableSet<Int>>()
    private val boneRhythmTimers = mutableMapOf<UUID, MutableMap<Int, Int>>()
    private val slaughterHits = mutableMapOf<UUID, Pair<Int, Int>>()
    private val huntingMarkArmed = mutableMapOf<UUID, Int>()
    private val huntingMarks = mutableMapOf<UUID, MutableMap<Int, Int>>()
    private val eagerEdgeStowedTicks = mutableMapOf<UUID, MutableMap<String, Int>>()
    private val eagerEdgeReady = mutableMapOf<UUID, Int>()
    private val eagerEdgeExtraJumps = mutableSetOf<UUID>()

    fun clear(playerId: UUID) {
        cooldowns.remove(playerId)
        activeStates.remove(playerId)
        lastHurtTicks.remove(playerId)
        emberHits.remove(playerId)
        emberHitTimers.remove(playerId)
        markedTargets.remove(playerId)
        boneRhythmTargets.remove(playerId)
        boneRhythmTimers.remove(playerId)
        slaughterHits.remove(playerId)
        huntingMarkArmed.remove(playerId)
        huntingMarks.remove(playerId)
        eagerEdgeStowedTicks.remove(playerId)
        eagerEdgeReady.remove(playerId)
        eagerEdgeExtraJumps.remove(playerId)
    }

    fun tick(player: ServerPlayer) {
        cooldowns[player.uuid]?.let(::tickMap)
        activeStates[player.uuid]?.let(::tickStateMap)
        markedTargets[player.uuid]?.let(::tickMap)
        emberHitTimers[player.uuid]?.let(::tickMap)
        emberHits[player.uuid]?.keys?.retainAll(emberHitTimers[player.uuid].orEmpty().keys)
        boneRhythmTimers[player.uuid]?.let(::tickMap)
        boneRhythmTargets[player.uuid]?.retainAll(boneRhythmTimers[player.uuid].orEmpty().keys)
        huntingMarks[player.uuid]?.let(::tickMap)
        tickMap(huntingMarkArmed)
        tickMap(eagerEdgeReady)
        updateEagerEdge(player)
        val markedNearby = huntingMarks[player.uuid].orEmpty().keys.any { id ->
            player.level().getEntity(id)?.distanceToSqr(player)?.let { it <= 16.0 } == true
        }
        if (markedNearby) player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6, 0, true, false, true))
        applyHeldWeaponPartAttributes(player)
        if (player.tickCount % MOBILITY_REFRESH_INTERVAL_TICKS == 0) {
            applyFirstSlotMobility(player)
        }
        if (player.tickCount % ARMOR_PERK_REFRESH_INTERVAL_TICKS == 0) {
            applyArmorPerks(player)
        }
    }

    /** 客户端仅报告按下跳跃键，服务端验证急切刀锋状态后才允许额外跳跃。 */
    fun tryEagerEdgeJump(player: ServerPlayer) {
        if (player.uuid !in eagerEdgeExtraJumps ||
            !hasState(player, GearPerkEffect.EAGER_EDGE) ||
            player.isInWater ||
            player.isFallFlying
        ) {
            return
        }
        eagerEdgeExtraJumps.remove(player.uuid)
        val motion = player.deltaMovement
        player.deltaMovement = Vec3(motion.x, 0.56, motion.z)
        player.fallDistance = 0.0f
        player.hasImpulse = true
    }

    /** 急切刀锋已就绪时，挥动武器才会消耗就绪状态并开始冲刺。 */
    fun tryActivateEagerEdge(player: ServerPlayer) {
        if ((eagerEdgeReady[player.uuid] ?: 0) <= 0) {
            return
        }
        val perk = GearRolls.rollPerks(player.mainHandItem).firstOrNull { it.effect == GearPerkEffect.EAGER_EDGE } ?: return
        eagerEdgeReady.remove(player.uuid)
        triggerState(player, GearPerkEffect.EAGER_EDGE, perk.durationTicks)
        eagerEdgeExtraJumps.add(player.uuid)
        beginEagerEdgeDash(player)
        showPerkBuff(player, perk, "二段跳")
    }

    private fun updateEagerEdge(player: ServerPlayer) {
        val stowed = eagerEdgeStowedTicks.getOrPut(player.uuid) { mutableMapOf() }
        val stacks = player.inventory.items + player.inventory.offhand
        val eagerStacks = stacks.mapNotNull { stack ->
            eagerEdgeSignature(stack)?.let { signature -> signature to stack }
        }
        val heldSignature = eagerEdgeSignature(player.mainHandItem)
        val heldStowedTicks = heldSignature?.let { stowed[it] ?: 0 } ?: 0
        eagerStacks.map { it.first }.forEach { signature -> stowed.putIfAbsent(signature, 0) }
        stowed.keys.toList().forEach { signature ->
            if (signature == heldSignature) {
                stowed[signature] = 0
            } else {
                stowed[signature] = (stowed[signature] ?: 0).coerceAtMost(EAGER_EDGE_STOW_TICKS) + 1
            }
        }

        if (heldSignature == null || heldStowedTicks < EAGER_EDGE_STOW_TICKS) {
            return
        }
        val perk = GearRolls.rollPerks(player.mainHandItem).firstOrNull { it.effect == GearPerkEffect.EAGER_EDGE } ?: return
        stowed[heldSignature] = 0
        eagerEdgeReady[player.uuid] = perk.durationTicks
        showPerkBuff(player, perk, "急切刀锋：就绪")
    }

    private fun eagerEdgeSignature(stack: ItemStack): String? {
        if (stack.isEmpty || GearRegistry.definitionFor(stack)?.frame?.id?.path != "vanilla_melee") {
            return null
        }
        GearRolls.ensureRoll(stack)
        val perks = GearRolls.rollPerks(stack)
        if (perks.none { it.effect == GearPerkEffect.EAGER_EDGE }) {
            return null
        }
        return BuiltInRegistries.ITEM.getKey(stack.item).toString() + ":" + perks.joinToString(",") { it.id.toString() }
    }

    private fun beginEagerEdgeDash(player: ServerPlayer) {
        val look = player.lookAngle
        val horizontal = Vec3(look.x, 0.0, look.z)
        if (horizontal.lengthSqr() >= 0.01) {
            val direction = horizontal.normalize()
            player.push(direction.x * 2.5, 0.12, direction.z * 2.5)
            player.hasImpulse = true
            ServerPlayNetworking.send(player, DestinyNetworking.EagerEdgeDashPayload(direction.x, direction.z))
        }
    }

    /** 供自定义投射物等服务端战斗物件汇报“实际已触发”的 Perk。 */
    fun announcePerk(player: ServerPlayer, effect: GearPerkEffect, title: String) {
        val perk = GearRolls.rollPerks(player.mainHandItem).firstOrNull { it.effect == effect } ?: return
        showPerkBuff(player, perk, title)
    }

    fun onPlayerHurt(player: ServerPlayer) {
        lastHurtTicks[player.uuid] = player.tickCount
        val lowHealth = player.health <= player.maxHealth * 0.35f
        if (lowHealth) {
            val panicGuard = GearRolls.rollPerks(player.mainHandItem).firstOrNull { it.effect == GearPerkEffect.PANIC_GUARD }
            if (panicGuard != null) {
                triggerState(player, GearPerkEffect.PANIC_GUARD, panicGuard.durationTicks)
                showPerkBuff(player, panicGuard, "危急护势：就绪")
            }
        }
    }

    /**
     * 将二号位近战部件的攻击节奏真实写入 ATTACK_SPEED 属性。
     * 每 tick 先移除旧修饰符，切换武器或失去 Roll 时会立即还原原版攻速。
     */
    private fun applyHeldWeaponPartAttributes(player: ServerPlayer) {
        val attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED) ?: return
        val stack = player.mainHandItem
        val definition = GearRegistry.definitionFor(stack)
        val desiredAmount = if (definition?.frame?.id?.path == "vanilla_melee") {
            GearRolls.ensureRoll(stack)
            val cooldownMultiplier = GearRolls.rollPerks(stack)
                .filter { GearPerkScope.MELEE in it.scopes }
                .fold(1.0f) { value, perk -> value * perk.cooldownMultiplier }
            if (cooldownMultiplier == 1.0f) null else 1.0 / cooldownMultiplier.toDouble() - 1.0
        } else {
            null
        }
        val current = attackSpeed.getModifier(MELEE_PART_ATTACK_SPEED_ID)
        if (desiredAmount == null) {
            if (current != null) attackSpeed.removeModifier(MELEE_PART_ATTACK_SPEED_ID)
            return
        }
        if (current != null && kotlin.math.abs(current.amount - desiredAmount) < 1.0e-7) return
        if (current != null) attackSpeed.removeModifier(MELEE_PART_ATTACK_SPEED_ID)
        attackSpeed.addTransientModifier(
            AttributeModifier(
                MELEE_PART_ATTACK_SPEED_ID,
                desiredAmount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            )
        )
    }

    private fun applyFirstSlotMobility(player: ServerPlayer) {
        val stack = player.mainHandItem
        val definition = GearRegistry.definitionFor(stack) ?: return
        val effects = GearRolls.rollPerks(stack).map { it.effect }.toSet()
        when (definition.frame.id.path) {
            "vanilla_bow" -> if (player.isUsingItem && GearPerkEffect.LIGHTWEIGHT_LIMBS in effects) {
                player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6, 1, true, false, true))
            }
            "vanilla_crossbow" -> if (GearPerkEffect.LIGHTWEIGHT_ARMS in effects) {
                player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6, 0, true, false, true))
            }
        }
    }

    fun modifyIncomingDamage(player: ServerPlayer, amount: Float): Float {
        var result = amount
        if (hasState(player, GearPerkEffect.DUELIST_PULSE)) {
            result *= 0.90f
        }
        if (hasState(player, GearPerkEffect.HEAVY_REVERSAL)) {
            result *= 0.85f
        }
        return result
    }

    fun modifyOutgoingDamage(attacker: ServerPlayer, target: LivingEntity, source: DamageSource, amount: Float): Float {
        val context = attackContext(attacker, source) ?: return amount
        var result = amount
        var meleeBonus = 0.0f

        context.perks.forEach { perk ->
            if (!matchesScope(perk, context.scope) || !rollChance(perk)) {
                return@forEach
            }

            // 一号、二号位部件的基础伤害属性同样作用于原版近战、弓和弩。
            if (context.isMelee) {
                meleeBonus += (perk.damageMultiplier - 1.0f).coerceAtLeast(0.0f)
            } else {
                result *= perk.damageMultiplier
            }

            when (perk.effect) {
                GearPerkEffect.HONED_EDGE -> {
                    if (context.isMelee && attacker.getAttackStrengthScale(0.5f) >= 0.95f) {
                        meleeBonus += 0.25f
                        spawnParticles(attacker.serverLevel(), ParticleTypes.CRIT, target, 16)
                        showPerkBuff(attacker, perk, "锋锐蓄力")
                    }
                }
                GearPerkEffect.EXECUTIONERS_EDGE -> {
                    if (context.isMelee && target.health <= target.maxHealth * 0.35f) {
                        meleeBonus += 0.15f
                        showPerkBuff(attacker, perk, "处刑者之刃")
                    }
                }
                GearPerkEffect.GUARD_COUNTER -> {
                    val lastHurt = lastHurtTicks[attacker.uuid] ?: Int.MIN_VALUE
                    if (context.isMelee && attacker.tickCount - lastHurt <= 40 && cooldownReady(attacker, perk)) {
                        meleeBonus += 0.15f
                        target.knockback(0.7, target.x - attacker.x, target.z - attacker.z)
                        showPerkBuff(attacker, perk, "守势反击")
                        setCooldown(attacker, perk)
                    }
                }
                GearPerkEffect.HEAVY_REVERSAL -> {
                    if (context.isMelee && attacker.health <= attacker.maxHealth * 0.35f && cooldownReady(attacker, perk)) {
                        meleeBonus += 0.20f
                        target.knockback(1.1, target.x - attacker.x, target.z - attacker.z)
                        triggerState(attacker, GearPerkEffect.HEAVY_REVERSAL, perk.durationTicks)
                        showPerkBuff(attacker, perk, "重击逆转")
                        setCooldown(attacker, perk)
                    }
                }
                GearPerkEffect.PANIC_GUARD -> {
                    if (context.isMelee && hasState(attacker, GearPerkEffect.PANIC_GUARD) && cooldownReady(attacker, perk)) {
                        target.knockback(1.0, target.x - attacker.x, target.z - attacker.z)
                        clearState(attacker, GearPerkEffect.PANIC_GUARD)
                        showPerkBuff(attacker, perk, "危急护势")
                        setCooldown(attacker, perk)
                    }
                }
                GearPerkEffect.PINNING_SHOT -> {
                    if (context.isRanged && target.health < target.maxHealth && cooldownReady(attacker, perk)) {
                        target.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, perk.durationTicks.coerceAtLeast(40), 0))
                        showPerkBuff(attacker, perk, "钉刺射击")
                        setCooldown(attacker, perk)
                    }
                }
                GearPerkEffect.GRAVITY_ARC -> {
                    if (context.isRanged) {
                        val flightTicks = context.projectile?.tickCount ?: 0
                        if (flightTicks > 12) {
                            result *= 1.0f + min(0.25f, flightTicks / 150.0f)
                        }
                    }
                }
                GearPerkEffect.MARKED_QUIVER -> {
                    if (context.isRanged) {
                        val marks = markedTargets.getOrPut(attacker.uuid) { mutableMapOf() }
                        if ((marks[target.id] ?: 0) > 0) {
                            result *= 1.15f
                            marks.remove(target.id)
                            showPerkBuff(attacker, perk, "猎物已标记")
                        } else {
                            marks[target.id] = perk.durationTicks.coerceAtLeast(80)
                            showPerkBuff(attacker, perk, "猎物已标记")
                        }
                    }
                }
                GearPerkEffect.PERFECT_DRAW -> {
                    if (context.isRanged && context.projectile != null && context.projectile.tickCount <= 8 && cooldownReady(attacker, perk)) {
                        result *= 1.10f
                        spawnParticles(attacker.serverLevel(), ParticleTypes.CRIT, target, 10)
                        showPerkBuff(attacker, perk, "完美拉弓")
                        setCooldown(attacker, perk)
                    }
                }
                GearPerkEffect.CALM_RELEASE -> {
                    if (context.isRanged && attacker.deltaMovement.horizontalDistanceSqr() < 0.0025 && cooldownReady(attacker, perk)) {
                        spawnParticles(attacker.serverLevel(), ParticleTypes.END_ROD, attacker, 8)
                        showPerkBuff(attacker, perk, "静心释放")
                        setCooldown(attacker, perk)
                    }
                }
                GearPerkEffect.REINFORCED_LIMBS -> {
                    if (context.scope == GearPerkScope.BOW && (context.projectile?.tickCount ?: 0) >= 15) {
                        result *= 1.25f
                        spawnParticles(attacker.serverLevel(), ParticleTypes.CRIT, target, 16)
                        showPerkBuff(attacker, perk, "远距强化")
                    }
                }
                GearPerkEffect.HUNTING_MARK -> {
                    if (context.isMelee && (huntingMarks[attacker.uuid]?.get(target.id) ?: 0) > 0) {
                        meleeBonus += 0.15f
                        showPerkBuff(attacker, perk, "猎杀印记")
                    }
                }
                else -> Unit
            }
        }

        if (context.isMelee) {
            result *= 1.0f + meleeBonus
        }

        return result
    }

    /** 在原版箭矢/弩箭生成后的首个 tick 应用部件提供的弹速修正。 */
    fun applyRangedProjectilePartStats(projectile: Projectile) {
        if (projectile.tags.contains(PART_STATS_APPLIED_TAG)) {
            return
        }
        val attacker = projectile.owner as? ServerPlayer ?: return
        val context = rangedContext(attacker, projectile) ?: return
        val speedMultiplier = context.perks
            .filter { matchesScope(it, context.scope) }
            .fold(1.0f) { value, perk -> value * perk.projectileSpeedMultiplier }
        if (speedMultiplier != 1.0f) {
            projectile.deltaMovement = projectile.deltaMovement.scale(speedMultiplier.toDouble())
            projectile.hasImpulse = true
        }
        projectile.addTag(PART_STATS_APPLIED_TAG)
    }

    fun onProjectileBlockHit(projectile: Projectile, hit: BlockHitResult, state: BlockState): Boolean {
        val attacker = projectile.owner as? ServerPlayer ?: return false
        val context = rangedContext(attacker, projectile) ?: return false
        val perk = context.perks.firstOrNull {
            it.effect == GearPerkEffect.RICOCHET_FLETCHING &&
                matchesScope(it, context.scope) &&
                cooldownReady(attacker, it) &&
                rollChance(it)
        } ?: return false

        if (projectile.tags.contains(RICOCHETED_TAG) || state.isAir) {
            return false
        }

        val normal = Vec3(hit.direction.stepX.toDouble(), hit.direction.stepY.toDouble(), hit.direction.stepZ.toDouble())
        val velocity = projectile.deltaMovement
        val reflected = velocity.subtract(normal.scale(2.0 * velocity.dot(normal))).scale(0.75)
        if (reflected.lengthSqr() < 0.015) {
            return false
        }

        projectile.addTag(RICOCHETED_TAG)
        projectile.setPos(projectile.x + normal.x * 0.18, projectile.y + normal.y * 0.18, projectile.z + normal.z * 0.18)
        projectile.deltaMovement = reflected
        projectile.hasImpulse = true
        spawnParticles(attacker.serverLevel(), ParticleTypes.ELECTRIC_SPARK, projectile.x, projectile.y, projectile.z, 8, 0.12, 0.12, 0.12, 0.04)
        setCooldown(attacker, perk)
        showPerkBuff(attacker, perk, "弹跳弹道")
        return true
    }

    fun afterSuccessfulHit(attacker: ServerPlayer, target: LivingEntity, source: DamageSource) {
        val context = attackContext(attacker, source) ?: return
        context.perks.forEach { perk ->
            if (!matchesScope(perk, context.scope) || !rollChance(perk)) {
                return@forEach
            }

            when (perk.effect) {
                GearPerkEffect.SERRATED_EDGE -> {
                    if (context.isMelee && cooldownReady(attacker, perk)) {
                        target.addEffect(MobEffectInstance(MobEffects.POISON, 60, 0))
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "锯齿创伤")
                    }
                }
                GearPerkEffect.EMBER_BLADE -> {
                    if (context.isMelee && cooldownReady(attacker, perk)) {
                        DestinyStatusRules.applyScorch(
                            target,
                            20,
                            perk.durationTicks,
                            attacker,
                            SolarDamageKind.WEAPON
                        )
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "炽热锋刃")
                    }
                }
                GearPerkEffect.ARROW_SPLIT -> {
                    if (context.scope == GearPerkScope.BOW && context.projectile is AbstractArrow && !context.projectile.tags.contains(SPLIT_ARROW_TAG) && cooldownReady(attacker, perk)) {
                        spawnSplitArrows(attacker, target, context.projectile)
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "箭裂")
                    }
                }
                GearPerkEffect.ARC_FLASH -> {
                    if (context.scope == GearPerkScope.BOW && isHeadshot(target, context.projectile) && cooldownReady(attacker, perk)) {
                        DestinyStatusRules.applyAmplified(attacker, perk.durationTicks)
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "闪击")
                    }
                }
                GearPerkEffect.PRECISION_BOMBARDMENT -> {
                    if (context.scope == GearPerkScope.BOW && isHeadshot(target, context.projectile) && cooldownReady(attacker, perk)) {
                        launchPrecisionMissile(attacker, target, context.projectile)
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "精准投弹")
                    }
                }
                GearPerkEffect.SLAUGHTER_OVERTURE -> {
                    if (context.isMelee && cooldownReady(attacker, perk)) {
                        val (previousCount, previousTick) = slaughterHits[attacker.uuid] ?: (0 to Int.MIN_VALUE)
                        val count = if (attacker.tickCount - previousTick <= 40) previousCount + 1 else 1
                        if (count >= perk.maxStacks.coerceAtLeast(3)) {
                            attacker.heal(attacker.maxHealth * 0.05f)
                            slaughterHits.remove(attacker.uuid)
                            setCooldown(attacker, perk)
                            showPerkBuff(attacker, perk, "屠戮序曲", perk.maxStacks.coerceAtLeast(3))
                        } else {
                            slaughterHits[attacker.uuid] = count to attacker.tickCount
                            showPerkBuff(attacker, perk, "屠戮序曲", count)
                        }
                    }
                }
                GearPerkEffect.HUNTING_MARK -> {
                    // 击杀那一击也会经过命中回调；仅允许仍活着的下一名目标消耗待命印记。
                    if (context.isMelee && target.isAlive && target.health > 0.0f && (huntingMarkArmed[attacker.uuid] ?: 0) > 0) {
                        huntingMarkArmed.remove(attacker.uuid)
                        huntingMarks.getOrPut(attacker.uuid) { mutableMapOf() }[target.id] = perk.durationTicks
                        attacker.serverLevel().addFreshEntity(HuntingMarkEntity(attacker.serverLevel(), target, perk.durationTicks))
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "猎杀印记")
                    }
                }
                GearPerkEffect.HEAVY_GUARD -> {
                    if (context.isMelee) {
                        attacker.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, perk.durationTicks, 0, true, false, true))
                        showPerkBuff(attacker, perk, "重型护手")
                    }
                }
                GearPerkEffect.DUELIST_GUARD -> {
                    if (context.isMelee) {
                        attacker.addEffect(MobEffectInstance(MobEffects.ABSORPTION, perk.durationTicks, 0, true, false, true))
                        showPerkBuff(attacker, perk, "决斗护手")
                    }
                }
                GearPerkEffect.EMBER_CARVE -> {
                    if (context.isMelee && cooldownReady(attacker, perk)) {
                        val hits = emberHits.getOrPut(attacker.uuid) { mutableMapOf() }
                        val timers = emberHitTimers.getOrPut(attacker.uuid) { mutableMapOf() }
                        val count = (hits[target.id] ?: 0) + 1
                        if (count >= perk.maxStacks.coerceAtLeast(3)) {
                            hits.remove(target.id)
                            timers.remove(target.id)
                            areaDamage(attacker, target, baseAttackDamage(attacker, context) * 0.15f, 2.0, fireParticles = true)
                            setCooldown(attacker, perk)
                            showPerkBuff(attacker, perk, "余烬刻痕", count)
                        } else {
                            hits[target.id] = count
                            timers[target.id] = 40
                            showPerkBuff(attacker, perk, "余烬刻痕", count)
                        }
                    }
                }
                GearPerkEffect.MOMENTUM_CLEAVE -> {
                    if (context.isMelee && attacker.isSprinting && cooldownReady(attacker, perk)) {
                        areaDamage(attacker, target, baseAttackDamage(attacker, context) * 0.15f, 2.5)
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "动量顺劈")
                    }
                }
                GearPerkEffect.DUELIST_PULSE -> {
                    if (context.isMelee && cooldownReady(attacker, perk) && nearbyEnemies(attacker, 5.0).size <= 1) {
                        triggerState(attacker, GearPerkEffect.DUELIST_PULSE, perk.durationTicks)
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "决斗者的韵律")
                    }
                }
                GearPerkEffect.BONE_RHYTHM -> {
                    if (context.isMelee && cooldownReady(attacker, perk)) {
                        val hits = boneRhythmTargets.getOrPut(attacker.uuid) { linkedSetOf() }
                        val timers = boneRhythmTimers.getOrPut(attacker.uuid) { mutableMapOf() }
                        hits.add(target.id)
                        timers[target.id] = 60
                        if (hits.size >= perk.maxStacks.coerceAtLeast(3)) {
                            hits.clear()
                            timers.clear()
                            areaDamage(attacker, target, baseAttackDamage(attacker, context) * 0.20f, 2.5, spawnVisuals = false)
                            spawnBoneRhythmCombo(attacker.serverLevel(), target)
                            setCooldown(attacker, perk)
                            showPerkBuff(attacker, perk, "裂骨节奏", perk.maxStacks.coerceAtLeast(3))
                        }
                    }
                }
                GearPerkEffect.ECHO_ARROW -> {
                    if (context.isRanged && cooldownReady(attacker, perk)) {
                        target.hurt(attacker.damageSources().magic(), baseAttackDamage(attacker, context) * 0.10f)
                        spawnParticles(attacker.serverLevel(), ParticleTypes.ENCHANTED_HIT, target, 14)
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "回声箭")
                    }
                }
                GearPerkEffect.STEADY_LIMBS -> {
                    if (context.scope == GearPerkScope.BOW) {
                        target.addEffect(MobEffectInstance(MobEffects.GLOWING, 100, 0))
                        showPerkBuff(attacker, perk, "标记箭矢")
                    }
                }
                GearPerkEffect.REINFORCED_ARMS -> {
                    if (context.scope == GearPerkScope.CROSSBOW) {
                        target.knockback(1.15, target.x - attacker.x, target.z - attacker.z)
                        showPerkBuff(attacker, perk, "震荡弩臂")
                    }
                }
                GearPerkEffect.MULTISHOT_ARMS -> {
                    if (context.scope == GearPerkScope.CROSSBOW) {
                        target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 80, 0))
                        showPerkBuff(attacker, perk, "削弱弩臂")
                    }
                }
                GearPerkEffect.LUCKY_REBOUND -> {
                    if (context.isRanged) {
                        attacker.inventory.add(ItemStack(Items.ARROW))
                        showPerkBuff(attacker, perk, "幸运回弹")
                    }
                }
                else -> Unit
            }
        }
    }

    fun onKill(attacker: ServerPlayer, killed: LivingEntity, source: DamageSource) {
        val context = attackContext(attacker, source) ?: return
        context.perks.forEach { perk ->
            if (!matchesScope(perk, context.scope) || !rollChance(perk) || !cooldownReady(attacker, perk)) {
                return@forEach
            }

            when (perk.effect) {
                GearPerkEffect.EXECUTIONERS_EDGE -> {
                    if (context.isMelee) {
                        attacker.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, perk.durationTicks.coerceAtLeast(80), 0))
                        attacker.addEffect(MobEffectInstance(MobEffects.DIG_SPEED, perk.durationTicks.coerceAtLeast(80), 0))
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "处刑者之刃")
                    }
                }
                GearPerkEffect.BLOODLESS_FINISH -> {
                    if (context.isMelee) {
                        attacker.heal(2.0f)
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "无血终结")
                    }
                }
                GearPerkEffect.CHAIN_STEP -> {
                    if (context.isMelee) {
                        attacker.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, perk.durationTicks.coerceAtLeast(60), 1))
                        attacker.addEffect(MobEffectInstance(MobEffects.JUMP, perk.durationTicks.coerceAtLeast(60), 0))
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "连步")
                    }
                }
                GearPerkEffect.EXTINCTION_PROTOCOL -> {
                    if (context.isMelee) {
                        DestinyStatusRules.applyAmplified(attacker, perk.durationTicks)
                        DestinyStatusRules.applyRadiant(attacker, perk.durationTicks)
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "灭绝协议")
                    }
                }
                GearPerkEffect.HUNTING_MARK -> {
                    if (context.isMelee) {
                        huntingMarkArmed[attacker.uuid] = perk.durationTicks
                        showPerkBuff(attacker, perk, "猎杀印记：就绪")
                    }
                }
                GearPerkEffect.SPLIT_STRING -> {
                    if (context.isRanged) {
                        nearbyEnemies(killed, 5.0).take(3).forEach { target ->
                            if (target !== killed) {
                                target.hurt(attacker.damageSources().magic(), baseAttackDamage(attacker, context) * 0.10f)
                                spawnParticles(attacker.serverLevel(), ParticleTypes.CRIT, target, 8)
                            }
                        }
                        setCooldown(attacker, perk)
                        showPerkBuff(attacker, perk, "分裂弓弦")
                    }
                }
                GearPerkEffect.FIREWORK_FINISHER -> {
                    spawnParticles(attacker.serverLevel(), ParticleTypes.FIREWORK, killed, 24)
                    areaDamage(attacker, killed, baseAttackDamage(attacker, context) * 0.10f, 2.0)
                    setCooldown(attacker, perk)
                    showPerkBuff(attacker, perk, "烟花终结")
                }
                GearPerkEffect.SOLAR_POPCORN -> {
                    spawnParticles(attacker.serverLevel(), ParticleTypes.FLAME, killed, 24)
                    areaDamage(attacker, killed, baseAttackDamage(attacker, context) * 0.15f, 2.5, fireParticles = true)
                    setCooldown(attacker, perk)
                    showPerkBuff(attacker, perk, "日炎爆米花")
                }
                GearPerkEffect.FEATHER_STEP -> {
                    attacker.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, perk.durationTicks.coerceAtLeast(80), 0))
                    attacker.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, perk.durationTicks.coerceAtLeast(80), 0))
                    setCooldown(attacker, perk)
                    showPerkBuff(attacker, perk, "羽步")
                }
                else -> Unit
            }
        }
    }

    private data class AttackContext(
        val stack: ItemStack,
        val perks: List<GearPerk>,
        val scope: GearPerkScope,
        val projectile: Projectile?
    ) {
        val isMelee: Boolean = scope == GearPerkScope.MELEE
        val isRanged: Boolean = scope == GearPerkScope.BOW || scope == GearPerkScope.CROSSBOW
    }

    private fun attackContext(attacker: ServerPlayer, source: DamageSource): AttackContext? {
        val projectile = source.directEntity as? Projectile
        val stack = if (projectile != null) findRangedStack(attacker) else attacker.mainHandItem
        if (stack.isEmpty) {
            return null
        }
        GearRolls.ensureRoll(stack)
        val definition = GearRegistry.definitionFor(stack) ?: return null
        val scope = when (definition.frame.id.path) {
            "vanilla_melee" -> GearPerkScope.MELEE
            "vanilla_bow" -> GearPerkScope.BOW
            "vanilla_crossbow" -> GearPerkScope.CROSSBOW
            else -> return null
        }
        return AttackContext(stack, GearRolls.rollPerks(stack), scope, projectile)
    }

    private fun baseAttackDamage(attacker: ServerPlayer, context: AttackContext): Float {
        val projectileDamage = (context.projectile as? AbstractArrow)?.baseDamage?.toFloat()
        if (projectileDamage != null && projectileDamage > 0.0f) return projectileDamage
        return attacker.getAttributeValue(Attributes.ATTACK_DAMAGE).toFloat().coerceAtLeast(1.0f)
    }

    private fun rangedContext(attacker: ServerPlayer, projectile: Projectile? = null): AttackContext? {
        val stack = findRangedStack(attacker)
        if (stack.isEmpty) {
            return null
        }
        GearRolls.ensureRoll(stack)
        val definition = GearRegistry.definitionFor(stack) ?: return null
        val scope = when (definition.frame.id.path) {
            "vanilla_bow" -> GearPerkScope.BOW
            "vanilla_crossbow" -> GearPerkScope.CROSSBOW
            else -> return null
        }
        return AttackContext(stack, GearRolls.rollPerks(stack), scope, projectile)
    }

    private fun findRangedStack(player: ServerPlayer): ItemStack {
        val main = player.mainHandItem
        val off = player.offhandItem
        val mainDefinition = GearRegistry.definitionFor(main)
        if (mainDefinition?.frame?.id?.path == "vanilla_bow" || mainDefinition?.frame?.id?.path == "vanilla_crossbow") {
            return main
        }
        val offDefinition = GearRegistry.definitionFor(off)
        if (offDefinition?.frame?.id?.path == "vanilla_bow" || offDefinition?.frame?.id?.path == "vanilla_crossbow") {
            return off
        }
        return main
    }

    private fun matchesScope(perk: GearPerk, scope: GearPerkScope): Boolean {
        return perk.scopes.isEmpty() || scope in perk.scopes
    }

    private fun rollChance(perk: GearPerk): Boolean {
        return perk.triggerChance >= 1.0f || Random.nextFloat() <= perk.triggerChance
    }

    private fun cooldownReady(player: ServerPlayer, perk: GearPerk): Boolean {
        if (perk.internalCooldownTicks <= 0) {
            return true
        }
        return (cooldowns.getOrPut(player.uuid) { mutableMapOf() }[perk.effect] ?: 0) <= 0
    }

    private fun setCooldown(player: ServerPlayer, perk: GearPerk) {
        if (perk.internalCooldownTicks > 0) {
            cooldowns.getOrPut(player.uuid) { mutableMapOf() }[perk.effect] = perk.internalCooldownTicks
        }
    }

    private fun triggerState(player: ServerPlayer, effect: GearPerkEffect, duration: Int) {
        if (duration <= 0) {
            return
        }
        activeStates.getOrPut(player.uuid) { mutableMapOf() }[effect] = ActiveState(duration)
    }

    private fun showPerkBuff(player: ServerPlayer, perk: GearPerk, title: String = perk.displayName, stacks: Int = 1) {
        ServerPlayNetworking.send(
            player,
            DestinyNetworking.SyncPerkBuffPayload(
                perk.id.toString(),
                title,
                perk.durationTicks.coerceAtLeast(60),
                stacks
            )
        )
    }

    private fun clearState(player: ServerPlayer, effect: GearPerkEffect) {
        activeStates.getOrPut(player.uuid) { mutableMapOf() }.remove(effect)
    }

    private fun hasState(player: ServerPlayer, effect: GearPerkEffect): Boolean {
        return (activeStates.getOrPut(player.uuid) { mutableMapOf() }[effect]?.ticks ?: 0) > 0
    }

    private fun applyArmorPerks(player: ServerPlayer) {
        val effects = player.inventory.armor.flatMap { stack ->
            if (stack.isEmpty) {
                emptyList()
            } else {
                GearRolls.ensureRoll(stack)
                GearRolls.rollPerks(stack).filter { GearPerkScope.ARMOR in it.scopes }
            }
        }.map { it.effect }.toSet()

        if (GearPerkEffect.DISCIPLINE_CORE in effects) {
            player.addEffect(MobEffectInstance(MobEffects.DIG_SPEED, 45, 0, true, false, true))
        }
        if (GearPerkEffect.RECOVERY_CORE in effects && player.tickCount % 80 == 0 && player.hurtTime <= 0 && player.health < player.maxHealth) {
            player.heal(0.5f)
            spawnParticles(player.serverLevel(), ParticleTypes.HEART, player, 2)
        }
        if (GearPerkEffect.RESILIENCE_CORE in effects) {
            player.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 45, 0, true, false, true))
        }
        if (GearPerkEffect.MOBILITY_CORE in effects) {
            player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45, 0, true, false, true))
        }
    }

    private fun areaDamage(
        attacker: ServerPlayer,
        center: LivingEntity,
        damage: Float,
        radius: Double,
        fireParticles: Boolean = false,
        spawnVisuals: Boolean = true
    ) {
        nearbyEnemies(center, radius).forEach { target ->
            if (target !== attacker && target !== center) {
                target.hurt(attacker.damageSources().magic(), damage)
            }
        }
        if (spawnVisuals && fireParticles) {
            spawnParticles(attacker.serverLevel(), ParticleTypes.FLAME, center, 16)
        }
    }

    private fun nearbyEnemies(center: LivingEntity, radius: Double): List<LivingEntity> {
        val box = center.boundingBox.inflate(radius)
        return center.level().getEntitiesOfClass(LivingEntity::class.java, box) { entity ->
            entity.isAlive && entity !== center && entity !is ServerPlayer
        }
    }

    private fun spawnParticles(level: ServerLevel, particle: net.minecraft.core.particles.ParticleOptions, target: LivingEntity, count: Int) {
        level.sendParticles(particle, target.x, target.y + target.bbHeight * 0.55, target.z, count, 0.35, 0.35, 0.35, 0.02)
    }

    private fun spawnParticles(
        level: ServerLevel,
        particle: net.minecraft.core.particles.ParticleOptions,
        x: Double,
        y: Double,
        z: Double,
        count: Int,
        xSpread: Double,
        ySpread: Double,
        zSpread: Double,
        speed: Double
    ) {
        level.sendParticles(particle, x, y, z, count, xSpread, ySpread, zSpread, speed)
    }

    private fun spawnBoneRhythmCombo(level: ServerLevel, target: LivingEntity) {
        val centerX = target.x
        val centerY = target.y + target.bbHeight * 0.55
        val centerZ = target.z
    }

    private const val MOBILITY_REFRESH_INTERVAL_TICKS = 4
    private const val ARMOR_PERK_REFRESH_INTERVAL_TICKS = 20

    private fun isHeadshot(target: LivingEntity, projectile: Projectile?): Boolean {
        val hitY = projectile?.y ?: return false
        return abs(hitY - target.eyeY) <= target.bbHeight * 0.35
    }

    private fun spawnSplitArrows(attacker: ServerPlayer, target: LivingEntity, sourceArrow: AbstractArrow) {
        repeat(4) { index ->
            val angle = Math.PI * 2.0 * index / 4.0
            // 1.21.1 会校验箭矢的发射武器；不能传空 ItemStack，否则服务端会崩溃。
            val arrow = Arrow(attacker.serverLevel(), attacker, ItemStack(Items.ARROW), attacker.mainHandItem)
            arrow.setPos(target.x, target.y + target.bbHeight * 0.55, target.z)
            arrow.baseDamage = (sourceArrow.baseDamage * 0.25).coerceAtLeast(0.5)
            arrow.deltaMovement = Vec3(cos(angle) * 0.9, 0.14, sin(angle) * 0.9)
            arrow.addTag(SPLIT_ARROW_TAG)
            attacker.serverLevel().addFreshEntity(arrow)
        }
    }

    private fun launchPrecisionMissile(attacker: ServerPlayer, target: LivingEntity, source: Projectile?) {
        val baseDamage = (source as? AbstractArrow)?.baseDamage?.toFloat()
            ?: attacker.mainHandItem.let(GearRegistry::definitionFor)?.baseDamage?.takeIf { it > 0.0f }
            ?: 14.0f
        val missile = MicroMissileProjectile(attacker.serverLevel(), attacker, baseDamage * 0.15f, 1.0f)
        missile.setPos(target.x, target.y + target.bbHeight * 0.55, target.z)
        val forward = source?.deltaMovement?.normalize() ?: attacker.lookAngle
        missile.deltaMovement = forward.add(0.0, 0.65, 0.0).normalize().scale(1.35)
        attacker.serverLevel().addFreshEntity(missile)
    }

    private fun <K> tickMap(values: MutableMap<K, Int>) {
        val iterator = values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val next = entry.value - 1
            if (next <= 0) {
                iterator.remove()
            } else {
                entry.setValue(next)
            }
        }
    }

    private fun tickStateMap(values: MutableMap<GearPerkEffect, ActiveState>) {
        val iterator = values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.ticks -= 1
            if (entry.value.ticks <= 0) {
                iterator.remove()
            }
        }
    }
}
