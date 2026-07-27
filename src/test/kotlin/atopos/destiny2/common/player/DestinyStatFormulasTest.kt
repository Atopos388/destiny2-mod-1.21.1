package atopos.destiny2.common.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import net.minecraft.nbt.CompoundTag

class DestinyStatFormulasTest {
    @Test
    fun `recharge curve keeps seventy as baseline and caps at one hundred`() {
        assertEquals(0.70f, DestinyStatFormulas.rechargeRate(0), 0.0001f)
        assertEquals(1.0f, DestinyStatFormulas.rechargeRate(70), 0.0001f)
        assertEquals(1.15f, DestinyStatFormulas.rechargeRate(100), 0.0001f)
        assertEquals(1.15f, DestinyStatFormulas.rechargeRate(200), 0.0001f)
    }

    @Test
    fun `specialization starts after one hundred and ends at two hundred`() {
        assertEquals(0.0f, DestinyStatFormulas.specialization(100), 0.0001f)
        assertEquals(0.01f, DestinyStatFormulas.specialization(101), 0.0001f)
        assertEquals(0.5f, DestinyStatFormulas.specialization(150), 0.0001f)
        assertEquals(1.0f, DestinyStatFormulas.specialization(200), 0.0001f)
    }

    @Test
    fun `minecraft tuned damage bonuses stay within their caps`() {
        val max = DestinyStats(200, 200, 200, 200, 200, 200)
        assertEquals(1.15f, DestinyStatFormulas.weaponDamageMultiplier(max), 0.0001f)
        assertEquals(1.35f, DestinyStatFormulas.grenadeDamageMultiplier(max), 0.0001f)
        assertEquals(1.30f, DestinyStatFormulas.meleeDamageMultiplier(max), 0.0001f)
        assertEquals(1.30f, DestinyStatFormulas.superDamageMultiplier(max), 0.0001f)
        assertEquals(0.90f, DestinyStatFormulas.weaponReloadTimeMultiplier(max), 0.0001f)
    }

    @Test
    fun `health shield grows only in the specialization band`() {
        val base = DestinyStats.UNIFIED_BASE
        val atHundred = base.copy(health = 100)
        val atTwoHundred = base.copy(health = 200)
        assertEquals(4.0f, DestinyStatFormulas.healthShieldCapacity(base), 0.0001f)
        assertEquals(4.0f, DestinyStatFormulas.healthShieldCapacity(atHundred), 0.0001f)
        assertEquals(8.0f, DestinyStatFormulas.healthShieldCapacity(atTwoHundred), 0.0001f)
        assertTrue(DestinyStatFormulas.healthShieldRechargePerTick(atTwoHundred) > DestinyStatFormulas.healthShieldRechargePerTick(atHundred))
    }

    @Test
    fun `legacy armor two stats migrate to the unified baseline`() {
        val old = CompoundTag().also {
            it.putInt("mobility", 8)
            it.putInt("resilience", 4)
            it.putInt("recovery", 6)
            it.putInt("discipline", 6)
            it.putInt("intellect", 5)
            it.putInt("strength", 7)
        }
        assertEquals(DestinyStats.UNIFIED_BASE, DestinyStats.fromTag(old, DestinyClassType.HUNTER))
    }
}
