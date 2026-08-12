package atopos.destiny2.common.gear

import atopos.destiny2.common.aspect.VoidHunterAspectRuntime
import atopos.destiny2.common.aspect.ArcTitanFragmentRuntime
import atopos.destiny2.common.aspect.ArcBoltChargeRuntime
import atopos.destiny2.common.aspect.SolarReviveRuntime
import atopos.destiny2.common.item.DestinyClassItem
import atopos.destiny2.common.item.DestinyItems
import atopos.destiny2.common.entity.OrbOfPowerEntity
import atopos.destiny2.common.entity.OrbOfPowerRules
import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.player.DestinyStats
import atopos.destiny2.common.player.PlayerDestinyDataApi
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponAmmoState
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** Server-authoritative Armor Charge, Orb of Power and permanent armor-mod effects. */
object ArmorModRuntime {
    private data class InstalledCache(val gameTime: Long, val mods: List<ArmorModDefinition>)

    private val lastOrbTick = mutableMapOf<Pair<UUID, ArmorModEffect>, Long>()
    private val reaperArmed = mutableSetOf<UUID>()
    private val finderProgress = mutableMapOf<Pair<UUID, Boolean>, Int>()
    private val scavengedEntities = mutableSetOf<Pair<UUID, UUID>>()
    private val installedCache = mutableMapOf<UUID, InstalledCache>()

    fun equippedStacks(player: ServerPlayer): List<ItemStack> = buildList {
        addAll(player.inventory.armor.filterNot(ItemStack::isEmpty))
        val data = PlayerDestinyDataApi.get(player)
        val classItem = data.classItem
        val item = classItem.item as? DestinyClassItem
        if (!classItem.isEmpty && item?.requiredClass == data.destinyClass) add(classItem)
    }

    fun installed(player: ServerPlayer): List<ArmorModDefinition> {
        val now = player.serverLevel().gameTime
        installedCache[player.uuid]?.takeIf { it.gameTime == now }?.let { return it.mods }
        val mods = equippedStacks(player).flatMap(GearRolls::equippedArmorMods).filterNotNull()
        installedCache[player.uuid] = InstalledCache(now, mods)
        return mods
    }

    fun clear(playerId: UUID) {
        installedCache.remove(playerId)
        lastOrbTick.keys.removeIf { it.first == playerId }
        reaperArmed.remove(playerId)
        finderProgress.keys.removeIf { it.first == playerId }
        scavengedEntities.removeIf { it.first == playerId }
    }

    /** Invalidates only the equipped-mod snapshot after a loadout mutation. */
    fun invalidateInstalled(playerId: UUID) {
        installedCache.remove(playerId)
    }

    fun count(player: ServerPlayer, effect: ArmorModEffect): Int = installed(player).count { it.effect == effect }

    fun maxArmorCharge(player: ServerPlayer): Int = (3 + count(player, ArmorModEffect.CHARGED_UP)).coerceAtMost(6)

    fun addArmorCharge(player: ServerPlayer, amount: Int) {
        val state = PlayerDestinyDataApi.get(player).combatState
        state.armorCharge = (state.armorCharge + amount).coerceIn(0, maxArmorCharge(player))
        if (usesTimedCharge(player) && state.armorCharge > 0) state.armorChargeDecayAt = player.serverLevel().gameTime + decayInterval(player)
        DestinyNetworking.syncStatState(player)
    }

    fun consumeArmorCharge(player: ServerPlayer, amount: Int): Int {
        val state = PlayerDestinyDataApi.get(player).combatState
        val consumed = minOf(amount, state.armorCharge)
        state.armorCharge -= consumed
        if (state.armorCharge <= 0) state.armorChargeDecayAt = 0L
        DestinyNetworking.syncStatState(player)
        return consumed
    }

    fun spawnOrb(owner: ServerPlayer, target: LivingEntity, effect: ArmorModEffect) {
        val now = owner.serverLevel().gameTime
        val key = owner.uuid to effect
        if (now - (lastOrbTick[key] ?: Long.MIN_VALUE / 2) < 200L) return
        lastOrbTick[key] = now
        OrbOfPowerEntity.spawn(owner.serverLevel(), target.position().add(0.0, 0.35, 0.0))
    }

    /** Returns true when the item was consumed without entering inventory. */
    fun onItemPickup(player: ServerPlayer, entity: ItemEntity): Boolean {
        if (entity.item.`is`(DestinyItems.IONIC_TRACE)) {
            return ArcBoltChargeRuntime.handleIonicTracePickup(player, entity)
        }
        if (entity.item.`is`(DestinyItems.REVIVE_GHOST)) {
            return SolarReviveRuntime.handlePickup(player, entity)
        }
        if (entity.item.`is`(DestinyItems.VOID_BREACH)) {
            entity.discard()
            VoidHunterAspectRuntime.onVoidBreachPickup(player)
            return true
        }
        val isAmmo = entity.item.`is`(DestinyItems.SPECIAL_AMMO) || entity.item.`is`(DestinyItems.HEAVY_AMMO)
        if (isAmmo) ArcTitanFragmentRuntime.onAmmoPickup(player, entity)
        val key = player.uuid to entity.uuid
        if (isAmmo && scavengedEntities.add(key)) {
            val copies = count(player, ArmorModEffect.SCAVENGER)
            val reserves = count(player, ArmorModEffect.AMMO_RESERVES)
            entity.item.grow((diminishing(copies) + diminishing(reserves)).toInt())
        }
        return false
    }

    /** Applies the base super-energy reward and every installed Orb of Power interaction. */
    fun onOrbPickup(player: ServerPlayer): Boolean {
        if (!player.isAlive || player.isSpectator) return false
        VoidHunterAspectRuntime.onOrbPickup(player)
        val data = PlayerDestinyDataApi.get(player)
        data.combatState.superEnergy = OrbOfPowerRules.addSuperEnergy(data.combatState.superEnergy)
        addArmorCharge(player, if (count(player, ArmorModEffect.STACKS_ON_STACKS) > 0) 2 else 1)
        if (count(player, ArmorModEffect.ORB_HEAL) > 0) {
            player.heal((3 * diminishing(count(player, ArmorModEffect.ORB_HEAL))).toFloat())
            data.combatState.healthShield += (3 * diminishing(count(player, ArmorModEffect.ORB_HEAL))).toFloat()
        }
        if (count(player, ArmorModEffect.ORB_REGEN) > 0) data.combatState.lastDamageGameTime = Long.MIN_VALUE
        reduceCooldown(player, AbilitySlot.GRENADE, 40 * diminishing(count(player, ArmorModEffect.ORB_GRENADE)))
        reduceCooldown(player, AbilitySlot.MELEE, 40 * diminishing(count(player, ArmorModEffect.ORB_MELEE)))
        reduceCooldown(player, AbilitySlot.CLASS_ABILITY, 40 * diminishing(count(player, ArmorModEffect.ORB_CLASS)))
        val absolution = count(player, ArmorModEffect.ORB_ALL_ABILITIES)
        if (absolution > 0) AbilitySlot.entries.filterNot { it == AbilitySlot.SUPER }.forEach { reduceCooldown(player, it, 25 * diminishing(absolution)) }
        DestinyNetworking.syncCooldowns(player)
        DestinyNetworking.syncStatState(player)
        player.serverLevel().playSound(
            null,
            player.blockPosition(),
            SoundEvents.EXPERIENCE_ORB_PICKUP,
            SoundSource.PLAYERS,
            0.55f,
            1.35f
        )
        return true
    }

    fun onKill(attacker: ServerPlayer, killed: LivingEntity, source: DamageSource) {
        val ability = source.directEntity as? DestinyAbilityDamageCarrier
        when (ability?.destinyAbilitySlot) {
            AbilitySlot.GRENADE -> {
                if (count(attacker, ArmorModEffect.ORB_ON_GRENADE_KILL) > 0) spawnOrb(attacker, killed, ArmorModEffect.ORB_ON_GRENADE_KILL)
                grantSuper(attacker, (1.5 * diminishing(count(attacker, ArmorModEffect.SUPER_FROM_GRENADE))).toFloat())
            }
            AbilitySlot.MELEE -> {
                if (count(attacker, ArmorModEffect.ORB_ON_MELEE_KILL) > 0) spawnOrb(attacker, killed, ArmorModEffect.ORB_ON_MELEE_KILL)
                grantSuper(attacker, (1.5 * diminishing(count(attacker, ArmorModEffect.SUPER_FROM_MELEE))).toFloat())
            }
            else -> {
                if (count(attacker, ArmorModEffect.ORB_ON_WEAPON_KILL) > 0 && attacker.random.nextFloat() < 0.34f) {
                    spawnOrb(attacker, killed, ArmorModEffect.ORB_ON_WEAPON_KILL)
                }
                if (reaperArmed.remove(attacker.uuid)) spawnOrb(attacker, killed, ArmorModEffect.REAPER)
                tickAmmoFinder(attacker, killed)
            }
        }

        // Minecraft adaptation of a finisher: a direct close-range player kill.
        if (source.directEntity === attacker && attacker.distanceToSqr(killed) <= 9.0) applyFinisherMods(attacker, killed)
    }

    fun onAbilityHit(attacker: ServerPlayer, source: DamageSource) {
        when ((source.directEntity as? DestinyAbilityDamageCarrier)?.destinyAbilitySlot) {
            AbilitySlot.GRENADE -> {
                reduceCooldown(attacker, AbilitySlot.CLASS_ABILITY, 20 * diminishing(count(attacker, ArmorModEffect.CLASS_FROM_GRENADE)))
                reduceCooldown(attacker, AbilitySlot.MELEE, 20 * diminishing(count(attacker, ArmorModEffect.MELEE_FROM_GRENADE)))
            }
            AbilitySlot.MELEE -> {
                reduceCooldown(attacker, AbilitySlot.GRENADE, 20 * diminishing(count(attacker, ArmorModEffect.GRENADE_FROM_MELEE)))
                reduceCooldown(attacker, AbilitySlot.CLASS_ABILITY, 20 * diminishing(count(attacker, ArmorModEffect.CLASS_FROM_MELEE)))
            }
            else -> return
        }
        DestinyNetworking.syncCooldowns(attacker)
    }

    fun onAbilityCast(player: ServerPlayer, slot: AbilitySlot) {
        val data = PlayerDestinyDataApi.get(player)
        when (slot) {
            AbilitySlot.GRENADE -> kickstart(player, AbilitySlot.GRENADE, ArmorModEffect.GRENADE_KICKSTART)
            AbilitySlot.MELEE -> kickstart(player, AbilitySlot.MELEE, ArmorModEffect.MELEE_KICKSTART)
            AbilitySlot.CLASS_ABILITY -> {
                reduceCooldown(player, AbilitySlot.GRENADE, 40 * diminishing(count(player, ArmorModEffect.CLASS_TO_GRENADE)))
                reduceCooldown(player, AbilitySlot.MELEE, 40 * diminishing(count(player, ArmorModEffect.CLASS_TO_MELEE)))
                val distribution = count(player, ArmorModEffect.CLASS_TO_ALL)
                if (distribution > 0) AbilitySlot.entries.filterNot { it == AbilitySlot.SUPER }.forEach { reduceCooldown(player, it, 25 * diminishing(distribution)) }
                kickstart(player, AbilitySlot.CLASS_ABILITY, ArmorModEffect.UTILITY_KICKSTART)
                if (count(player, ArmorModEffect.REAPER) > 0) reaperArmed += player.uuid
                if (count(player, ArmorModEffect.POWERFUL_ATTRACTION) > 0) {
                    player.serverLevel().getEntitiesOfClass(OrbOfPowerEntity::class.java, player.boundingBox.inflate(6.0))
                        .forEach { orb -> if (onOrbPickup(player)) orb.discard() }
                }
                grantSuper(player, (0.8 * diminishing(count(player, ArmorModEffect.SUPER_FROM_CLASS))).toFloat())
            }
            else -> Unit
        }
        data.combatState.armorCharge = data.combatState.armorCharge.coerceAtMost(maxArmorCharge(player))
        DestinyNetworking.syncCooldowns(player)
    }

    fun tick(player: ServerPlayer) {
        val state = PlayerDestinyDataApi.get(player).combatState
        val now = player.serverLevel().gameTime
        if (now % 1200L == 0L) scavengedEntities.removeIf { it.first == player.uuid }
        if (usesTimedCharge(player) && state.armorCharge > 0 && state.armorChargeDecayAt > 0 && now >= state.armorChargeDecayAt) {
            state.armorCharge--
            state.armorChargeDecayAt = if (state.armorCharge > 0) now + decayInterval(player) else 0L
            DestinyNetworking.syncStatState(player)
        }
        if (player.tickCount % MOVEMENT_REFRESH_INTERVAL_TICKS == 0) {
            val movementCopies = count(player, ArmorModEffect.MOVEMENT)
            if (movementCopies > 0) {
                player.addEffect(MobEffectInstance(MobEffects.MOVEMENT_SPEED, 8, (movementCopies - 1).coerceAtMost(1), true, false, true))
                player.addEffect(MobEffectInstance(MobEffects.JUMP, 8, 0, true, false, true))
            }
        }
        val holsterCopies = count(player, ArmorModEffect.HOLSTER)
        if (holsterCopies > 0 && now % (60L / holsterCopies.coerceAtMost(3)).coerceAtLeast(20L) == 0L) {
            player.inventory.items.forEachIndexed { index, stack ->
                if (index != player.inventory.selected) {
                    (stack.item as? DestinyRangedWeapon)?.let { WeaponAmmoState.holsterRound(stack, player, it.combatProfile(stack)) }
                }
            }
        }
    }

    fun modifyIncomingDamage(player: ServerPlayer, source: DamageSource, amount: Float): Float {
        var result = amount
        val resist = count(player, ArmorModEffect.DAMAGE_RESIST)
        val resistReduction = when (resist.coerceAtMost(3)) {
            1 -> 0.15f
            2 -> 0.25f
            3 -> 0.30f
            else -> 0.0f
        }
        result *= 1.0f - resistReduction
        val emergency = count(player, ArmorModEffect.EMERGENCY_REINFORCEMENT)
        val state = PlayerDestinyDataApi.get(player).combatState
        if (emergency > 0 && state.armorCharge > 0 && player.health <= player.maxHealth * 0.5f) {
            consumeArmorCharge(player, 1)
            result *= 0.85f
        }
        return result
    }

    fun modifyOutgoingWeaponDamage(player: ServerPlayer, amount: Float): Float {
        if (PlayerDestinyDataApi.get(player).combatState.armorCharge <= 0) return amount
        val copies = count(player, ArmorModEffect.WEAPON_SURGE)
        val bonus = when (copies.coerceAtMost(3)) { 1 -> 0.10f; 2 -> 0.17f; 3 -> 0.22f; else -> 0.0f }
        return amount * (1.0f + bonus)
    }

    fun reloadMultiplier(player: ServerPlayer): Float {
        val copies = count(player, ArmorModEffect.RELOAD)
        val ready = count(player, ArmorModEffect.READY_SPEED)
        val loaderMultiplier = if (copies > 0) 0.85f else 1.0f
        val readyMultiplier = (1.0 - 0.04 * diminishing(ready)).toFloat()
        return (
            loaderMultiplier * readyMultiplier *
                VoidHunterAspectRuntime.reloadMultiplier(player) *
                ArcTitanFragmentRuntime.reloadMultiplier(player)
            ).coerceAtLeast(0.45f)
    }

    fun projectileInaccuracyMultiplier(player: ServerPlayer): Float =
        (if (count(player, ArmorModEffect.TARGETING) > 0) 0.90f else 1.0f) *
            VoidHunterAspectRuntime.stabilityMultiplier(player) *
            ArcTitanFragmentRuntime.stabilityMultiplier(player)

    fun statBonuses(player: ServerPlayer): DestinyStats {
        if (PlayerDestinyDataApi.get(player).combatState.armorCharge <= 0) return DestinyStats(0, 0, 0, 0, 0, 0)
        fun font(effect: ArmorModEffect): Int = when (count(player, effect).coerceAtMost(3)) { 1 -> 20; 2 -> 40; 3 -> 50; else -> 0 }
        return DestinyStats(
            weapons = font(ArmorModEffect.FONT_WEAPONS),
            health = font(ArmorModEffect.FONT_HEALTH),
            classAbility = font(ArmorModEffect.FONT_CLASS),
            grenade = font(ArmorModEffect.FONT_GRENADE),
            superStat = 0,
            melee = font(ArmorModEffect.FONT_MELEE)
        )
    }

    private fun usesTimedCharge(player: ServerPlayer): Boolean = installed(player).any {
        it.effect in setOf(ArmorModEffect.WEAPON_SURGE, ArmorModEffect.FONT_WEAPONS, ArmorModEffect.FONT_HEALTH, ArmorModEffect.FONT_CLASS, ArmorModEffect.FONT_GRENADE, ArmorModEffect.FONT_MELEE)
    }
    private fun decayInterval(player: ServerPlayer): Long = 200L + count(player, ArmorModEffect.TIME_DILATION) * 50L
    private fun diminishing(copies: Int): Double = when (copies.coerceAtMost(3)) { 1 -> 1.0; 2 -> 1.5; 3 -> 1.75; else -> 0.0 }
    private fun reduceCooldown(player: ServerPlayer, slot: AbilitySlot, ticks: Double) {
        if (ticks <= 0.0) return
        PlayerDestinyDataApi.get(player).cooldowns.reduce(slot, player.serverLevel().gameTime, ticks.toInt())
    }
    private fun grantSuper(player: ServerPlayer, amount: Float) {
        if (amount <= 0.0f) return
        val state = PlayerDestinyDataApi.get(player).combatState
        state.superEnergy = (state.superEnergy + amount).coerceAtMost(100.0f)
        DestinyNetworking.syncStatState(player)
    }
    private fun kickstart(player: ServerPlayer, slot: AbilitySlot, effect: ArmorModEffect) {
        val copies = count(player, effect)
        if (copies <= 0) return
        val consumed = consumeArmorCharge(player, PlayerDestinyDataApi.get(player).combatState.armorCharge)
        reduceCooldown(player, slot, 30.0 * (copies + consumed).coerceAtMost(6))
    }
    private fun tickAmmoFinder(player: ServerPlayer, killed: LivingEntity) {
        val mods = installed(player)
        listOf(false, true).forEach { heavy ->
            val finderPath = if (heavy) "heavy_ammo_finder" else "special_ammo_finder"
            if (mods.none { it.id.path.contains(finderPath) }) return@forEach
            val key = player.uuid to heavy
            val progress = (finderProgress[key] ?: 0) + 1
            val threshold = if (heavy) 18 else 12
            if (progress >= threshold) {
                finderProgress[key] = 0
                val item = if (heavy) DestinyItems.HEAVY_AMMO else DestinyItems.SPECIAL_AMMO
                val count = 1 + count(player, ArmorModEffect.AMMO_RESERVES).coerceAtMost(2)
                killed.spawnAtLocation(ItemStack(item, count))
                val scoutPath = if (heavy) "heavy_ammo_scout" else "special_ammo_scout"
                if (mods.any { it.id.path.contains(scoutPath) }) {
                    player.serverLevel().players().filter { it !== player && it.distanceToSqr(player) <= 24.0 * 24.0 }
                        .forEach { teammate -> teammate.spawnAtLocation(ItemStack(item, 1)) }
                }
            } else finderProgress[key] = progress
        }
    }
    private fun applyFinisherMods(player: ServerPlayer, killed: LivingEntity) {
        val data = PlayerDestinyDataApi.get(player)
        if (count(player, ArmorModEffect.FINISHER_ARMOR_CHARGE) > 0 && data.combatState.armorCharge == 0) addArmorCharge(player, 1)
        if (count(player, ArmorModEffect.FINISHER_SPECIAL_AMMO) > 0 && data.combatState.armorCharge >= 3) {
            consumeArmorCharge(player, 3)
            killed.spawnAtLocation(ItemStack(DestinyItems.SPECIAL_AMMO, 1))
        }
        if (count(player, ArmorModEffect.FINISHER_GRENADE) > 0 && consumeArmorCharge(player, 1) > 0) reduceCooldown(player, AbilitySlot.GRENADE, 80.0)
        if (count(player, ArmorModEffect.FINISHER_HEAL) > 0 && consumeArmorCharge(player, 1) > 0) player.heal(6.0f)
        if (count(player, ArmorModEffect.FINISHER_OVERSHIELD) > 0 && consumeArmorCharge(player, 1) > 0) data.combatState.classOvershield += 4.0f
    }

    private const val MOVEMENT_REFRESH_INTERVAL_TICKS = 5
}
