package atopos.destiny2.common.ability

import atopos.destiny2.common.aspect.SolarWarlockAspectRules
import atopos.destiny2.common.player.AbilitySlot
import atopos.destiny2.common.player.DestinySubclassConfigRegistry
import atopos.destiny2.common.player.DestinySubclassType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolarWarlockGrenadeAbilityTest {
    @Test
    fun `all four official solar grenade families are executable and selectable`() {
        val expected = listOf(
            "destiny2-mod:solar_warlock_solar_grenade",
            "destiny2-mod:solar_warlock_healing_grenade",
            "destiny2-mod:solar_warlock_firebolt_grenade",
            "destiny2-mod:solar_warlock_fusion_grenade"
        )
        val executable = SolarWarlockAbilities.ALL
            .filter { it.slot == AbilitySlot.GRENADE }
            .map { it.id.toString() }
            .toSet()
        val selectable = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.SOLAR_WARLOCK)
            .abilityOptions.getValue(AbilitySlot.GRENADE)
            .map { it.id }

        assertEquals(expected.toSet(), executable)
        assertEquals(expected, selectable)
        assertEquals(
            expected.first(),
            DestinySubclassConfigRegistry.defaultFor(DestinySubclassType.SOLAR_WARLOCK)
                .selectedAbilities[AbilitySlot.GRENADE]
        )
    }

    @Test
    fun `touch of flame strengthens healing without changing ally pickup geometry`() {
        val base = SolarWarlockAspectRules.healingGrenadeProfile(hasTouchOfFlame = false)
        val enhanced = SolarWarlockAspectRules.healingGrenadeProfile(hasTouchOfFlame = true)

        assertTrue(enhanced.cureHealth > base.cureHealth)
        assertTrue(enhanced.restorationDurationTicks > base.restorationDurationTicks)
        assertTrue(enhanced.restorationLevel > base.restorationLevel)
        assertEquals(base.impactRadius, enhanced.impactRadius)
        assertEquals(base.orbPickupRadius, enhanced.orbPickupRadius)
        assertEquals(base.orbLifetimeTicks, enhanced.orbLifetimeTicks)
    }

    @Test
    fun `touch of flame increases firebolt search and target cap only`() {
        val base = SolarWarlockAspectRules.fireboltGrenadeProfile(hasTouchOfFlame = false)
        val enhanced = SolarWarlockAspectRules.fireboltGrenadeProfile(hasTouchOfFlame = true)

        assertTrue(enhanced.targetSearchRadius > base.targetSearchRadius)
        assertTrue(enhanced.maximumTargets > base.maximumTargets)
        assertEquals(base.damage, enhanced.damage)
        assertEquals(base.scorchStacks, enhanced.scorchStacks)
        assertEquals(base.scorchDurationTicks, enhanced.scorchDurationTicks)
    }

    @Test
    fun `touch of flame makes fusion detonate exactly twice`() {
        val base = SolarWarlockAspectRules.fusionGrenadeProfile(hasTouchOfFlame = false)
        val enhanced = SolarWarlockAspectRules.fusionGrenadeProfile(hasTouchOfFlame = true)

        assertEquals(1, base.explosionCount)
        assertEquals(2, enhanced.explosionCount)
        assertEquals(base.explosionDamage, enhanced.explosionDamage)
        assertEquals(base.explosionRadius, enhanced.explosionRadius)
        assertEquals(base.scorchStacks, enhanced.scorchStacks)
        assertTrue(enhanced.repeatExplosionDelayTicks > 0)
    }

    @Test
    fun `all added grenade cooldowns and projectile bounds are positive calibrations`() {
        assertTrue(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_HEALING_GRENADE_COOLDOWN_TICKS > 0)
        assertTrue(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_FIREBOLT_GRENADE_COOLDOWN_TICKS > 0)
        assertTrue(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_FUSION_GRENADE_COOLDOWN_TICKS > 0)
        assertTrue(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_PROJECTILE_SPEED > 0.0f)
        assertTrue(SolarWarlockAspectRules.MINECRAFT_CALIBRATION_GRENADE_MAX_FLIGHT_TICKS > 0)
    }
}
