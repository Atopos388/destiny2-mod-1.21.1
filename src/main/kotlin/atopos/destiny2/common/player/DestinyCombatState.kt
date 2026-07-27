package atopos.destiny2.common.player

import net.minecraft.nbt.CompoundTag

/** Server-authoritative Armor 3.0 resources that must survive reconnects. */
data class DestinyCombatState(
    var superEnergy: Float = 0.0f,
    var healthShield: Float = 0.0f,
    var classOvershield: Float = 0.0f,
    var classOvershieldExpiresAt: Long = 0L,
    var lastDamageGameTime: Long = Long.MIN_VALUE,
    var armorCharge: Int = 0,
    var armorChargeDecayAt: Long = 0L
) {
    fun copyState(): DestinyCombatState = copy()

    fun resetForLoadout() {
        superEnergy = 0.0f
        classOvershield = 0.0f
        classOvershieldExpiresAt = 0L
        armorCharge = 0
        armorChargeDecayAt = 0L
    }

    fun resetAfterDeath() {
        healthShield = 0.0f
        classOvershield = 0.0f
        classOvershieldExpiresAt = 0L
        lastDamageGameTime = Long.MIN_VALUE
        armorCharge = 0
        armorChargeDecayAt = 0L
    }

    fun toTag(): CompoundTag = CompoundTag().also { tag ->
        tag.putFloat("super_energy", DestinyStatFormulas.clampEnergy(superEnergy))
        tag.putFloat("health_shield", healthShield.coerceAtLeast(0.0f))
        tag.putFloat("class_overshield", classOvershield.coerceAtLeast(0.0f))
        tag.putLong("class_overshield_expires_at", classOvershieldExpiresAt)
        tag.putLong("last_damage_game_time", lastDamageGameTime)
        tag.putInt("armor_charge", armorCharge.coerceIn(0, 6))
        tag.putLong("armor_charge_decay_at", armorChargeDecayAt)
    }

    companion object {
        fun fromTag(tag: CompoundTag): DestinyCombatState = DestinyCombatState(
            superEnergy = if (tag.contains("super_energy")) tag.getFloat("super_energy").coerceIn(0.0f, 100.0f) else 0.0f,
            healthShield = if (tag.contains("health_shield")) tag.getFloat("health_shield").coerceAtLeast(0.0f) else 0.0f,
            classOvershield = if (tag.contains("class_overshield")) tag.getFloat("class_overshield").coerceAtLeast(0.0f) else 0.0f,
            classOvershieldExpiresAt = if (tag.contains("class_overshield_expires_at")) tag.getLong("class_overshield_expires_at") else 0L,
            lastDamageGameTime = if (tag.contains("last_damage_game_time")) tag.getLong("last_damage_game_time") else Long.MIN_VALUE,
            armorCharge = if (tag.contains("armor_charge")) tag.getInt("armor_charge").coerceIn(0, 6) else 0,
            armorChargeDecayAt = if (tag.contains("armor_charge_decay_at")) tag.getLong("armor_charge_decay_at") else 0L
        )
    }
}
