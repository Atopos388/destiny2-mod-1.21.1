package atopos.destiny2.common.mixin

import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.combat.DestinyExplosionRuntime
import atopos.destiny2.common.gear.GearPerkRuntime
import atopos.destiny2.common.gear.ArmorModRuntime
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.aspect.VoidAbilityDamageCarrier
import atopos.destiny2.common.aspect.VoidHunterAspectRuntime
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyCombatRuntime
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.GuardianPowerRuntime
import atopos.destiny2.common.player.GuardianPowerSystem
import atopos.destiny2.common.player.GuardianActivityPower
import atopos.destiny2.common.weapon.DamageNumberRuntime
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.weapon.DestinyAmmoType
import net.minecraft.tags.DamageTypeTags
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.core.Holder
import net.minecraft.world.phys.AABB
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyVariable
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

private const val VOLATILE_DAMAGE_THRESHOLD = 20.0f

@Mixin(LivingEntity::class)
abstract class MixinLivingEntity {
    @Shadow abstract fun hasEffect(effect: Holder<MobEffect>): Boolean
    @Shadow abstract fun removeEffect(effect: Holder<MobEffect>): Boolean
    @Shadow abstract fun addEffect(effectInstance: MobEffectInstance): Boolean
    @Shadow abstract fun heal(amount: Float)

    private val self: LivingEntity
        get() = this as Any as LivingEntity

    private var destiny2modHealthBeforeDamage = 0.0f
    private var destiny2modAbsorptionBeforeDamage = 0.0f
    private var destiny2modShieldAbsorbed = 0.0f
    private var destiny2modVolatileDamage = 0.0f

    @Inject(method = ["hurt"], at = [At("HEAD")])
    fun destiny2modRemoveVoidInvisibilityOnHurt(
        source: DamageSource,
        amount: Float,
        ci: CallbackInfoReturnable<Boolean>
    ) {
        destiny2modHealthBeforeDamage = self.health
        destiny2modAbsorptionBeforeDamage = self.absorptionAmount
        destiny2modShieldAbsorbed = 0.0f
        (self as? ServerPlayer)?.takeIf { amount > 0 }?.let(GearPerkRuntime::onPlayerHurt)
    }

    @ModifyVariable(method = ["hurt"], at = At("HEAD"), argsOnly = true)
    fun destiny2modModifyDamage(amount: Float, source: DamageSource): Float {
        var newAmount = amount

        self.getEffect(DestinyEffects.WEAKEN)?.let { weaken ->
            newAmount *= if (weaken.amplifier >= 1) 1.35f else 1.15f
        }

        if (hasEffect(DestinyEffects.VOID_OVERSHIELD) && self.absorptionAmount > 0.0f) {
            val shieldCapacity = self.absorptionAmount
            val rawDamageCoveredByShield = minOf(newAmount, shieldCapacity * 2.0f)
            newAmount = rawDamageCoveredByShield * 0.5f + (newAmount - rawDamageCoveredByShield)
        }

        val player = self as? ServerPlayer
        if (player != null) {
            if (!self.level().isClientSide && player.hasEffect(DestinyEffects.AMPLIFIED) && source.entity is Monster) {
                // 增幅降低 PvE 所受伤害；敌方投射物还有额外的落空概率。
                if (source.directEntity is Projectile && player.random.nextFloat() < 0.20f) {
                    return 0.0f
                }
                newAmount *= 0.85f
            }
            newAmount = GearPerkRuntime.modifyIncomingDamage(player, newAmount)
            newAmount = ArmorModRuntime.modifyIncomingDamage(player, source, newAmount)
            if (!self.level().isClientSide && source.entity is LivingEntity && source.entity !is Player) {
                val recommended = GuardianActivityPower.recommended(self.level(), source.entity as LivingEntity)
                newAmount *= GuardianPowerSystem.forActivity(GuardianPowerRuntime.current(player), recommended).incomingMultiplier
            }
        }

        val attacker = source.entity as? ServerPlayer
        if (attacker != null && attacker !== self) {
            attacker.removeEffect(DestinyEffects.VOID_INVISIBILITY)
            newAmount = GearPerkRuntime.modifyOutgoingDamage(attacker, self, source, newAmount)
            if (source.directEntity !is DestinyAbilityDamageCarrier) {
                newAmount = ArmorModRuntime.modifyOutgoingWeaponDamage(attacker, newAmount)
                if (attacker.hasEffect(DestinyEffects.RADIANT) && self !is Player) {
                    newAmount *= 1.25f
                }
            }
            val stats = DestinyStatsResolver.resolve(attacker)
            val abilitySource = source.directEntity as? DestinyAbilityDamageCarrier
            newAmount *= if (abilitySource != null) {
                DestinyStatFormulas.damageMultiplier(abilitySource.destinyAbilitySlot, stats)
            } else {
                val ammoType = GearRegistry.definitionFor(attacker.mainHandItem)?.ammoType ?: DestinyAmmoType.PRIMARY
                val isBoss = self is EnderDragon || self is WitherBoss || self is Warden
                DestinyStatFormulas.weaponDamageMultiplier(stats, isBoss, ammoType)
            }
            if (!self.level().isClientSide && self !is Player) {
                val recommended = GuardianActivityPower.recommended(self.level(), self)
                newAmount *= GuardianPowerSystem.forActivity(GuardianPowerRuntime.current(attacker), recommended).outgoingMultiplier
            }
        }

        if (player != null && !self.level().isClientSide) {
            val beforeShield = newAmount
            newAmount = DestinyCombatRuntime.absorbIncomingDamage(player, newAmount)
            destiny2modShieldAbsorbed = (beforeShield - newAmount).coerceAtLeast(0.0f)
        }

        return newAmount
    }

    @Inject(method = ["tick"], at = [At("TAIL")])
    fun destiny2modPassiveRecovery(ci: CallbackInfo) {
        val player = self as? ServerPlayer ?: return
        if (player.level().isClientSide || player.isDeadOrDying) {
            return
        }
        DestinyStatusRules.tickArcMovement(player)
        DestinyAspectRuntime.tickPlayer(player)
        DestinyCombatRuntime.tick(player)
    }

    @Inject(method = ["hurt"], at = [At("RETURN")])
    fun destiny2modTriggerVolatileExplosion(
        source: DamageSource,
        amount: Float,
        ci: CallbackInfoReturnable<Boolean>
    ) {
        val successfulHit = ci.returnValue || destiny2modShieldAbsorbed > 0.0f
        if (successfulHit) {
            (source.entity as? ServerPlayer)?.takeIf { it !== self }?.let { attacker ->
                if (!self.level().isClientSide) {
                    val context = DamageNumberRuntime.contextFor(self)
                    val actualLoss = (destiny2modHealthBeforeDamage - self.health).coerceAtLeast(0.0f) +
                        (destiny2modAbsorptionBeforeDamage - self.absorptionAmount).coerceAtLeast(0.0f) +
                        destiny2modShieldAbsorbed
                    val displayAmount = context?.amount ?: actualLoss
                    DamageNumberRuntime.deliver(attacker, self, displayAmount, context?.precision == true)
                    DestinyCombatRuntime.grantSuperEnergyFromDamage(attacker, actualLoss)
                }
                GearPerkRuntime.afterSuccessfulHit(attacker, self, source)
                ArmorModRuntime.onAbilityHit(attacker, source)
                DestinyAspectRuntime.onMeleeHit(attacker, self, source)
                (source.directEntity as? VoidAbilityDamageCarrier)?.let { carrier ->
                    VoidHunterAspectRuntime.onVoidAbilityDamage(attacker, carrier.voidAbilitySource)
                }
            }
        }

        if (!successfulHit || !hasEffect(DestinyEffects.VOLATILE)) {
            return
        }
        if (source.`is`(DamageTypeTags.IS_EXPLOSION)) {
            return
        }

        destiny2modVolatileDamage += amount.coerceAtLeast(0.0f)
        if (destiny2modVolatileDamage >= VOLATILE_DAMAGE_THRESHOLD) {
            destiny2modDetonateVolatile()
        }
    }

    @Inject(method = ["die"], at = [At("HEAD")])
    fun destiny2modRefreshDevourOnKill(source: DamageSource, ci: CallbackInfo) {
        val attacker = source.entity as? LivingEntity ?: return
        if (attacker.hasEffect(DestinyEffects.DEVOUR)) {
            attacker.heal(10.0f)
            attacker.addEffect(MobEffectInstance(DestinyEffects.DEVOUR, 200, 0))
        }
        if (hasEffect(DestinyEffects.VOLATILE)) {
            destiny2modDetonateVolatile()
        }
        (attacker as? ServerPlayer)?.let {
            GearPerkRuntime.onKill(it, self, source)
            ArmorModRuntime.onKill(it, self, source)
        }
    }

    private fun destiny2modDetonateVolatile() {
        val level = self.level()
        if (level.isClientSide) return
        removeEffect(DestinyEffects.VOLATILE)
        destiny2modVolatileDamage = 0.0f
        val radius = 4.0
        val source = self.damageSources().explosion(null, null)
        level.getEntitiesOfClass(
            LivingEntity::class.java,
            AABB(
                self.x - radius,
                self.y - radius,
                self.z - radius,
                self.x + radius,
                self.y + radius,
                self.z + radius
            )
        ) { it !== self && it.isAlive }.forEach { target ->
            DestinyExplosionRuntime.hurtWithoutKnockback(target, source, 8.0f)
        }
    }
}
