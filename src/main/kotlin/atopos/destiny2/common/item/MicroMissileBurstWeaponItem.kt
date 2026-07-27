package atopos.destiny2.common.item

import atopos.destiny2.common.entity.MicroMissileProjectile
import atopos.destiny2.common.entity.MicroMissileBarrelEffect
import atopos.destiny2.common.gear.GearDefinition
import atopos.destiny2.common.gear.GearPerkEffect
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.gear.GearRolls
import atopos.destiny2.common.gear.ArmorModRuntime
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.weapon.DestinyRangedWeapon
import atopos.destiny2.common.weapon.WeaponAmmoState
import atopos.destiny2.common.weapon.WeaponCombatProfile
import atopos.destiny2.common.weapon.WeaponHudStatus
import atopos.destiny2.common.weapon.WeaponThirdPersonAction
import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.network.DestinyNetworking
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.UseAnim
import net.minecraft.world.level.Level
import java.util.UUID
import kotlin.math.ceil

open class MicroMissileBurstWeaponItem(properties: Properties) : Item(properties), DestinyRangedWeapon {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (level.isClientSide) {
            return InteractionResultHolder.pass(stack)
        }

        return if (requestFire(level, player, hand)) {
            InteractionResultHolder.consume(stack)
        } else {
            InteractionResultHolder.fail(stack)
        }
    }

    override fun getUseAnimation(stack: ItemStack): UseAnim {
        return UseAnim.NONE
    }

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slotId: Int, isSelected: Boolean) {
        if (level.isClientSide || entity !is LivingEntity) return
        if (entity is ServerPlayer) {
            if (isSelected) {
                if (selectedSlots.put(entity.uuid, slotId) != slotId) {
                    DestinyNetworking.broadcastWeaponThirdPersonAction(
                        entity,
                        WeaponThirdPersonAction.DRAW,
                        DRAW_THIRD_PERSON_TICKS
                    )
                }
            } else if (selectedSlots.remove(entity.uuid, slotId)) {
                DestinyNetworking.broadcastWeaponThirdPersonAction(
                    entity,
                    WeaponThirdPersonAction.PUT_AWAY,
                    PUT_AWAY_THIRD_PERSON_TICKS
                )
            }
        }
        GearRolls.ensureRoll(stack)
        GearRegistry.definitionFor(stack)?.let { definition ->
            tickReload(level, entity, stack, definition, RuntimeKey(entity.uuid, slotId))
        }

        val runtimeKey = RuntimeKey(entity.uuid, slotId)
        var remaining = burstRemaining[runtimeKey] ?: 0
        if (remaining <= 0) return

        var timer = (burstTimer[runtimeKey] ?: 0) - 1
        if (timer <= 0) {
            fireMissile(level, entity, stack)
            remaining--
            timer = GearRegistry.definitionFor(stack)?.frame?.burstIntervalTicks ?: 4
        }
        if (remaining > 0) {
            burstRemaining[runtimeKey] = remaining
            burstTimer[runtimeKey] = timer
        } else {
            burstRemaining.remove(runtimeKey)
            burstTimer.remove(runtimeKey)
        }
    }

    protected open fun triggerShootAnimation(level: Level, shooter: LivingEntity, stack: ItemStack) {
    }

    protected open fun triggerReloadAnimation(level: Level, shooter: LivingEntity, stack: ItemStack) {
    }

    override fun requestReload(level: Level, player: ServerPlayer, stack: ItemStack): Boolean {
        return requestReload(level, player, stack, RuntimeKey(player.uuid, player.inventory.selected))
    }

    override fun combatProfile(stack: ItemStack): WeaponCombatProfile {
        val definition = GearRegistry.definitionFor(stack)
            ?: return WeaponCombatProfile(atopos.destiny2.common.weapon.DestinyAmmoType.HEAVY, 0f, 1f, 1, 1)
        return WeaponCombatProfile(
            definition.ammoType,
            definition.baseDamage,
            definition.precisionMultiplier,
            magazineCapacity(stack, definition),
            (definition.frame.reloadTicks * GearRolls.reloadTimeMultiplier(stack)).toInt().coerceAtLeast(1)
        )
    }

    override fun weaponHudStatus(player: ServerPlayer, stack: ItemStack): WeaponHudStatus {
        GearRolls.ensureRoll(stack)
        val definition = GearRegistry.definitionFor(stack) ?: return WeaponHudStatus.INACTIVE
        val key = RuntimeKey(player.uuid, player.inventory.selected)
        val capacity = magazineCapacity(stack, definition)
        val total = (definition.frame.reloadTicks * GearRolls.reloadTimeMultiplier(stack)).toInt().coerceAtLeast(1)
        val reserve = definition.ammoItem?.let { WeaponAmmoState.countReserve(player, it) } ?: -1
        return WeaponHudStatus(
            "", definition.ammoType, getMagazineAmmo(key, stack, definition), capacity, reserve,
            reloadTimer[key] ?: 0, if (isReloading(key)) total else 0, definition.precisionMultiplier
        )
    }

    fun requestFire(level: Level, player: Player, hand: InteractionHand): Boolean {
        if (level.isClientSide) return false
        val stack = player.getItemInHand(hand)
        val runtimeKey = RuntimeKey(player.uuid, if (hand == InteractionHand.MAIN_HAND) player.inventory.selected else OFFHAND_SLOT)
        if ((burstRemaining[runtimeKey] ?: 0) > 0) return false

        GearRolls.ensureRoll(stack)
        val definition = GearRegistry.definitionFor(stack) ?: return false
        val frame = definition.frame

        if (isReloading(runtimeKey)) {
            return false
        }
        if (getMagazineAmmo(runtimeKey, stack, definition) <= 0) {
            startReload(level, player, stack, definition, runtimeKey)
            return false
        }

        setMagazineAmmo(runtimeKey, stack, definition, getMagazineAmmo(runtimeKey, stack, definition) - 1)
        fireMissile(level, player, stack)
        val remaining = (frame.burstCount - 1).coerceAtLeast(0)
        if (remaining > 0) {
            burstRemaining[runtimeKey] = remaining
            burstTimer[runtimeKey] = frame.burstIntervalTicks
        }
        val cooldown = (frame.cooldownTicks * GearRolls.cooldownMultiplier(stack)).toInt().coerceAtLeast(1)
        player.cooldowns.addCooldown(this, cooldown)
        return true
    }

    private fun requestReload(level: Level, player: Player, stack: ItemStack, runtimeKey: RuntimeKey): Boolean {
        if (level.isClientSide || (burstRemaining[runtimeKey] ?: 0) > 0) return false
        GearRolls.ensureRoll(stack)
        val definition = GearRegistry.definitionFor(stack) ?: return false
        return startReload(level, player, stack, definition, runtimeKey)
    }

    private fun fireMissile(level: Level, shooter: LivingEntity, stack: ItemStack) {
        val definition = GearRegistry.definitionFor(stack) ?: return
        val speed = definition.frame.projectileSpeed * GearRolls.projectileSpeedMultiplier(stack)
        val effects = GearRolls.rollPerks(stack).map { it.effect }.toSet()
        val vorpalMultiplier = if (GearPerkEffect.VORPAL_WEAPON in effects) {
            when (definition.ammoType) {
                DestinyAmmoType.HEAVY -> 1.10f
                DestinyAmmoType.SPECIAL -> 1.15f
                DestinyAmmoType.PRIMARY -> 1.20f
            }
        } else {
            1.0f
        }
        val damage = definition.baseDamage * GearRolls.damageMultiplier(stack) * vorpalMultiplier
        val radius = definition.explosionRadius * GearRolls.explosionRadiusMultiplier(stack)
        val barrelEffect = GearRolls.rollPerks(stack).firstOrNull {
            it.effect == GearPerkEffect.PRECISION_BARREL ||
                it.effect == GearPerkEffect.HIGH_EXPLOSIVE_BARREL ||
                it.effect == GearPerkEffect.STABLE_LAUNCHER
        }?.effect
        val missile = MicroMissileProjectile(
            level,
            shooter,
            damage,
            radius,
            when (barrelEffect) {
                GearPerkEffect.PRECISION_BARREL -> MicroMissileBarrelEffect.PRECISION
                GearPerkEffect.HIGH_EXPLOSIVE_BARREL -> MicroMissileBarrelEffect.HIGH_EXPLOSIVE
                GearPerkEffect.STABLE_LAUNCHER -> MicroMissileBarrelEffect.TRACKING
                else -> MicroMissileBarrelEffect.NONE
            },
            definition.ammoType,
            (if (GearPerkEffect.PRECISION_BARREL in effects) 1.125f else 1.0f) *
                (if (GearPerkEffect.IMPACT_CASING in effects) 1.10f else 1.0f)
        )
        val armorTargeting = (shooter as? ServerPlayer)?.let(ArmorModRuntime::projectileInaccuracyMultiplier) ?: 1.0f
        missile.shootFromRotation(shooter, shooter.xRot, shooter.yRot, 0.0f, speed, GearRolls.projectileInaccuracyMultiplier(stack) * armorTargeting)
        level.addFreshEntity(missile)
        triggerShootAnimation(level, shooter, stack)
    }

    private fun startReload(level: Level, entity: LivingEntity, stack: ItemStack, definition: GearDefinition, runtimeKey: RuntimeKey): Boolean {
        val reloadCapacity = reloadCapacity(runtimeKey, stack, definition)
        if (isReloading(runtimeKey) || getMagazineAmmo(runtimeKey, stack, definition) >= reloadCapacity) {
            return false
        }
        if (entity is Player && !hasReserveAmmo(entity, definition.ammoItem)) {
            entity.displayClientMessage(Component.literal("没有可用弹药"), true)
            return false
        }

        val armorThreeMultiplier = (entity as? ServerPlayer)?.let {
            DestinyStatFormulas.weaponReloadTimeMultiplier(DestinyStatsResolver.resolve(it)) * ArmorModRuntime.reloadMultiplier(it)
        } ?: 1.0f
        val fieldPrepMultiplier = if (
            entity.isCrouching &&
            GearRolls.rollPerks(stack).any { it.effect == GearPerkEffect.FIELD_PREP }
        ) 0.85f else 1.0f
        reloadTimer[runtimeKey] = (
            definition.frame.reloadTicks * GearRolls.reloadTimeMultiplier(stack) * armorThreeMultiplier * fieldPrepMultiplier
        ).toInt().coerceAtLeast(1)
        triggerReloadAnimation(level, entity, stack)
        return true
    }

    private fun tickReload(level: Level, entity: LivingEntity, stack: ItemStack, definition: GearDefinition, runtimeKey: RuntimeKey) {
        val timer = reloadTimer[runtimeKey] ?: 0
        if (timer <= 0) return
        if (timer > 1) {
            reloadTimer[runtimeKey] = timer - 1
            return
        }

        reloadTimer.remove(runtimeKey)
        val targetCapacity = reloadCapacity(runtimeKey, stack, definition)
        val needed = (targetCapacity - getMagazineAmmo(runtimeKey, stack, definition)).coerceAtLeast(0)
        if (needed <= 0) return

        val loaded = if (entity is Player) {
            consumeAmmo(entity, definition.ammoItem, stack, needed)
        } else {
            needed
        }
        setMagazineAmmo(runtimeKey, stack, definition, (getMagazineAmmo(runtimeKey, stack, definition) + loaded).coerceAtMost(targetCapacity))
        ambitiousReloadArmed.remove(runtimeKey.playerId)
    }

    private fun hasReserveAmmo(player: Player, ammoItem: Item?): Boolean {
        if (ammoItem == null || player.abilities.instabuild) {
            return true
        }
        return (player.inventory.items + player.inventory.offhand).any { stack -> stack.`is`(ammoItem) && !stack.isEmpty }
    }

    private fun consumeAmmo(player: Player, ammoItem: Item?, weaponStack: ItemStack, maxCount: Int): Int {
        if (ammoItem == null || player.abilities.instabuild) {
            return maxCount
        }
        if (player.random.nextFloat() < GearRolls.ammoRefundChance(weaponStack)) {
            return maxCount
        }

        var remaining = maxCount
        var consumed = 0
        val inventory = player.inventory
        for (stack in inventory.items + inventory.offhand) {
            if (remaining <= 0) break
            if (stack.`is`(ammoItem) && !stack.isEmpty) {
                val amount = stack.count.coerceAtMost(remaining)
                stack.shrink(amount)
                remaining -= amount
                consumed += amount
            }
        }
        return consumed
    }

    private fun magazineCapacity(stack: ItemStack, definition: GearDefinition): Int {
        return (definition.frame.magazineSize + GearRolls.magazineSizeBonus(stack)).coerceAtLeast(1)
    }

    private fun getMagazineAmmo(runtimeKey: RuntimeKey, stack: ItemStack, definition: GearDefinition): Int {
        val capacity = maxOf(
            magazineCapacity(stack, definition),
            ceil(definition.frame.magazineSize * 1.5).toInt()
        )
        return (magazineAmmo[runtimeKey] ?: magazineCapacity(stack, definition)).coerceIn(0, capacity)
    }

    private fun setMagazineAmmo(runtimeKey: RuntimeKey, stack: ItemStack, definition: GearDefinition, ammo: Int) {
        val maximum = maxOf(
            magazineCapacity(stack, definition),
            ceil(definition.frame.magazineSize * 1.5).toInt()
        )
        magazineAmmo[runtimeKey] = ammo.coerceIn(0, maximum)
    }

    private fun reloadCapacity(runtimeKey: RuntimeKey, stack: ItemStack, definition: GearDefinition): Int {
        if (runtimeKey.playerId !in ambitiousReloadArmed) return magazineCapacity(stack, definition)
        return maxOf(
            magazineCapacity(stack, definition),
            ceil(definition.frame.magazineSize * 1.5).toInt()
        )
    }

    private fun isReloading(runtimeKey: RuntimeKey): Boolean = (reloadTimer[runtimeKey] ?: 0) > 0

    companion object {
        private const val OFFHAND_SLOT = 40
        private const val DRAW_THIRD_PERSON_TICKS = 12
        private const val PUT_AWAY_THIRD_PERSON_TICKS = 8
        private val selectedSlots = mutableMapOf<UUID, Int>()
        private val magazineAmmo = mutableMapOf<RuntimeKey, Int>()
        private val burstRemaining = mutableMapOf<RuntimeKey, Int>()
        private val burstTimer = mutableMapOf<RuntimeKey, Int>()
        private val reloadTimer = mutableMapOf<RuntimeKey, Int>()
        private val ambitiousReloadArmed = mutableSetOf<UUID>()

        fun onMicroMissileKill(player: ServerPlayer) {
            val stack = player.mainHandItem
            if (stack.item !is MicroMissileBurstWeaponItem) return
            if (GearRolls.rollPerks(stack).any { it.effect == GearPerkEffect.AMBITIOUS_ASSASSIN }) {
                ambitiousReloadArmed += player.uuid
            }
        }
    }

    private data class RuntimeKey(val playerId: UUID, val slotId: Int)
}
