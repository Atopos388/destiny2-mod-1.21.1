package atopos.destiny2.common.mixin

import atopos.destiny2.common.effect.DestinyEffects
import atopos.destiny2.common.effect.DestinyStatusRules
import atopos.destiny2.common.effect.SolarDamageKind
import atopos.destiny2.common.effect.SolarScorchCarrier
import atopos.destiny2.common.effect.SolarScorchContext
import atopos.destiny2.common.effect.SolarIgnitionRuntime
import atopos.destiny2.common.combat.DestinyExplosionRuntime
import atopos.destiny2.common.gear.GearPerkRuntime
import atopos.destiny2.common.gear.ArmorModRuntime
import atopos.destiny2.common.aspect.DestinyAspectRuntime
import atopos.destiny2.common.aspect.VoidAbilityDamageCarrier
import atopos.destiny2.common.aspect.VoidHunterAspectRuntime
import atopos.destiny2.common.aspect.SolarWarlockFragmentRuntime
import atopos.destiny2.common.aspect.ArcTitanFragmentRuntime
import atopos.destiny2.common.aspect.ArcTitanAspectRuntime
import atopos.destiny2.common.aspect.SolarReviveRuntime
import atopos.destiny2.common.player.DestinyStatFormulas
import atopos.destiny2.common.player.DestinyCombatRuntime
import atopos.destiny2.common.player.GuardianJumpRuntime
import atopos.destiny2.common.player.DestinyAbilityDamageCarrier
import atopos.destiny2.common.player.DestinyStatsResolver
import atopos.destiny2.common.player.GuardianPowerRuntime
import atopos.destiny2.common.player.GuardianPowerSystem
import atopos.destiny2.common.player.GuardianActivityPower
import atopos.destiny2.common.weapon.MonteCarloExoticRuntime
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
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.AABB
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.ModifyVariable
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

private const val VOLATILE_DAMAGE_THRESHOLD = 20.0f

@Mixin(LivingEntity::class)
abstract class MixinLivingEntity : SolarScorchCarrier {
    @Shadow abstract fun hasEffect(effect: Holder<MobEffect>): Boolean
    @Shadow abstract fun removeEffect(effect: Holder<MobEffect>): Boolean
    @Shadow abstract fun addEffect(effectInstance: MobEffectInstance): Boolean
    @Shadow abstract fun heal(amount: Float)

    private val self: LivingEntity
        get() = this as Any as LivingEntity

    private var destiny2modHealthBeforeDamage = 0.0f
    private var destiny2modAbsorptionBeforeDamage = 0.0f
    private var destiny2modShieldAbsorbed = 0.0f
    private var destiny2modHealthShieldBeforeDamage = 0.0f
    private var destiny2modVolatileDamage = 0.0f

    @Unique
    private var destiny2modScorchSourcePlayerId: java.util.UUID? = null

    @Unique
    private var destiny2modScorchCastId: java.util.UUID? = null

    @Unique
    private var destiny2modScorchSourceKind: SolarDamageKind = SolarDamageKind.GENERIC

    override fun destiny2modScorchContext(): SolarScorchContext? {
        val sourcePlayerId = destiny2modScorchSourcePlayerId ?: return null
        return SolarScorchContext(
            sourcePlayerId,
            destiny2modScorchSourceKind,
            destiny2modScorchCastId ?: java.util.UUID.randomUUID()
        )
    }

    override fun destiny2modSetScorchContext(context: SolarScorchContext?) {
        destiny2modScorchSourcePlayerId = context?.sourcePlayerId
        destiny2modScorchCastId = context?.castId
        destiny2modScorchSourceKind = context?.sourceKind ?: SolarDamageKind.GENERIC
    }

    @Inject(method = ["addAdditionalSaveData"], at = [At("TAIL")])
    fun destiny2modSaveScorchContext(tag: CompoundTag, ci: CallbackInfo) {
        val sourcePlayerId = destiny2modScorchSourcePlayerId ?: return
        val contextTag = CompoundTag()
        contextTag.putUUID("source_player", sourcePlayerId)
        destiny2modScorchCastId?.let { contextTag.putUUID("cast_id", it) }
        contextTag.putString("source_kind", destiny2modScorchSourceKind.name)
        tag.put("destiny2mod_scorch_context", contextTag)
    }

    @Inject(method = ["readAdditionalSaveData"], at = [At("TAIL")])
    fun destiny2modLoadScorchContext(tag: CompoundTag, ci: CallbackInfo) {
        if (!tag.contains("destiny2mod_scorch_context")) {
            destiny2modSetScorchContext(null)
            return
        }
        val contextTag = tag.getCompound("destiny2mod_scorch_context")
        if (!contextTag.hasUUID("source_player")) {
            destiny2modSetScorchContext(null)
            return
        }
        destiny2modScorchSourcePlayerId = contextTag.getUUID("source_player")
        destiny2modScorchCastId = if (contextTag.hasUUID("cast_id")) contextTag.getUUID("cast_id") else java.util.UUID.randomUUID()
        destiny2modScorchSourceKind = SolarDamageKind.fromSerializedName(contextTag.getString("source_kind"))
    }

    @Inject(method = ["hurt"], at = [At("HEAD")])
    fun destiny2modRemoveVoidInvisibilityOnHurt(
        source: DamageSource,
        amount: Float,
        ci: CallbackInfoReturnable<Boolean>
    ) {
        destiny2modHealthBeforeDamage = self.health
        destiny2modAbsorptionBeforeDamage = self.absorptionAmount
        destiny2modShieldAbsorbed = 0.0f
        destiny2modHealthShieldBeforeDamage = (self as? ServerPlayer)?.let {
            atopos.destiny2.common.player.PlayerDestinyDataApi.get(it).combatState.healthShield
        } ?: 0.0f
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
            newAmount = ArcTitanAspectRuntime.modifyIncomingDamage(player, source, newAmount)
            newAmount = ArcTitanFragmentRuntime.modifyIncomingDamage(player, newAmount)
            if (!self.level().isClientSide && source.entity is LivingEntity && source.entity !is Player) {
                val recommended = GuardianActivityPower.recommended(self.level(), source.entity as LivingEntity)
                newAmount *= GuardianPowerSystem.forActivity(GuardianPowerRuntime.current(player), recommended).incomingMultiplier
            }
        }

        val attacker = source.entity as? ServerPlayer
        if (attacker != null && attacker !== self) {
            newAmount = ArcTitanAspectRuntime.modifyOutgoingDamage(attacker, source, newAmount)
            newAmount = ArcTitanFragmentRuntime.modifyOutgoingDamage(attacker, source, newAmount)
            val attributedSolarContext = SolarIgnitionRuntime.activeDamageContext(self)
            DestinyStatusRules.removeVoidInvisibility(attacker)
            if (attributedSolarContext == null) {
                newAmount = GearPerkRuntime.modifyOutgoingDamage(attacker, self, source, newAmount)
                if (source.directEntity !is DestinyAbilityDamageCarrier) {
                    newAmount = ArmorModRuntime.modifyOutgoingWeaponDamage(attacker, newAmount)
                    if (attacker.hasEffect(DestinyEffects.RADIANT) && self !is Player) {
                        newAmount *= 1.25f
                    }
                }
            }
            val stats = DestinyStatsResolver.resolve(attacker)
            val abilitySource = source.directEntity as? DestinyAbilityDamageCarrier
            newAmount *= if (attributedSolarContext != null) {
                1.0f
            } else if (VoidHunterAspectRuntime.isQuickfallDamage(attacker, self)) {
                1.0f
            } else if (abilitySource != null) {
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
        if (!self.hasEffect(DestinyEffects.SCORCH) && destiny2modScorchSourcePlayerId != null) {
            destiny2modSetScorchContext(null)
        }
        val player = self as? ServerPlayer ?: return
        if (player.level().isClientSide || player.isDeadOrDying) {
            return
        }
        DestinyStatusRules.tickArcMovement(player)
        DestinyAspectRuntime.tickPlayer(player)
        GuardianJumpRuntime.tickPlayer(player)
        DestinyCombatRuntime.tick(player)
        ArcTitanFragmentRuntime.tickPlayer(player)
    }

    @Inject(method = ["hurt"], at = [At("RETURN")])
    fun destiny2modTriggerVolatileExplosion(
        source: DamageSource,
        amount: Float,
        ci: CallbackInfoReturnable<Boolean>
    ) {
        val successfulHit = ci.returnValue || destiny2modShieldAbsorbed > 0.0f
        if (successfulHit) {
            val actualLoss = (destiny2modHealthBeforeDamage - self.health).coerceAtLeast(0.0f) +
                (destiny2modAbsorptionBeforeDamage - self.absorptionAmount).coerceAtLeast(0.0f) +
                destiny2modShieldAbsorbed
            ArcTitanFragmentRuntime.afterSuccessfulDamage(self, source, actualLoss)
            ArcTitanAspectRuntime.afterSuccessfulDamage(
                self,
                source,
                actualLoss,
                destiny2modHealthBeforeDamage,
                destiny2modAbsorptionBeforeDamage > 0.0f || destiny2modHealthShieldBeforeDamage > 0.0f
            )
            (source.entity as? ServerPlayer)?.takeIf { it !== self }?.let { attacker ->
                val attributedSolarContext = SolarIgnitionRuntime.activeDamageContext(self)
                if (!self.level().isClientSide) {
                    DestinyCombatRuntime.grantSuperEnergyFromDamage(attacker, actualLoss)
                    DamageNumberRuntime.afterDamage(self, source, actualLoss)
                }
                if (attributedSolarContext == null) {
                    GearPerkRuntime.afterSuccessfulHit(attacker, self, source)
                    ArmorModRuntime.onAbilityHit(attacker, source)
                    DestinyAspectRuntime.onMeleeHit(attacker, self, source)
                    VoidHunterAspectRuntime.onWeaponHit(attacker, self, source)
                    (source.directEntity as? VoidAbilityDamageCarrier)?.let { carrier ->
                        VoidHunterAspectRuntime.onVoidAbilityDamage(attacker, carrier.voidAbilitySource)
                    }
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
        (self as? ServerPlayer)?.let(SolarReviveRuntime::onPlayerDeath)
        val attacker = source.entity as? LivingEntity ?: return
        (attacker as? ServerPlayer)?.let { player ->
            ArcTitanAspectRuntime.onFinalBlow(player, self, source)
            ArcTitanFragmentRuntime.onFinalBlow(
                player,
                self,
                source,
                precisionFinalBlow = DamageNumberRuntime.contextFor(self)?.precision == true,
                targetWasBlinded = hasEffect(DestinyEffects.ARC_BLIND),
                targetWasJolted = ArcTitanFragmentRuntime.isJolted(self)
            )
        }
        val attributedSolarContext = SolarIgnitionRuntime.activeDamageContext(self)
        if (attributedSolarContext == null) {
            if (attacker.hasEffect(DestinyEffects.DEVOUR)) {
                DestinyStatusRules.applyDevour(attacker, 200)
            }
            (attacker as? ServerPlayer)?.let { player ->
                DestinyAspectRuntime.onFinalBlow(player, source)
                SolarWarlockFragmentRuntime.onFinalBlow(
                    player,
                    self,
                    source,
                    wasScorched = hasEffect(DestinyEffects.SCORCH)
                )
                VoidHunterAspectRuntime.onKill(
                    player,
                    self,
                    source,
                    wasWeakened = hasEffect(DestinyEffects.WEAKEN),
                    wasSuppressed = hasEffect(DestinyEffects.SUPPRESSION),
                    wasVolatile = hasEffect(DestinyEffects.VOLATILE)
                )
            }
        } else {
            (attacker as? ServerPlayer)?.let { player ->
                DestinyAspectRuntime.onFinalBlow(player, source, attributedSolarContext)
            }
        }
        if (hasEffect(DestinyEffects.VOLATILE)) {
            destiny2modDetonateVolatile()
        }
        if (attributedSolarContext == null) {
            (attacker as? ServerPlayer)?.let {
                GearPerkRuntime.onKill(it, self, source)
                ArmorModRuntime.onKill(it, self, source)
                if (source.directEntity === attacker) {
                    MonteCarloExoticRuntime.onMeleeKill(it)
                }
            }
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
