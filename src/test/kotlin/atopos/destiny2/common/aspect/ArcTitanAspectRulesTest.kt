package atopos.destiny2.common.aspect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArcTitanAspectRulesTest {
    @Test
    fun `catalog locks all four current official aspect ids`() {
        assertEquals(
            listOf(
                "destiny2-mod:aspect_touch_of_thunder",
                "destiny2-mod:aspect_juggernaut",
                "destiny2-mod:aspect_knockout",
                "destiny2-mod:aspect_storms_keep"
            ),
            ArcTitanAspectRules.ALL_ASPECT_IDS
        )
        assertEquals(4, ArcTitanAspectRules.ALL_ASPECT_IDS.toSet().size)
    }

    @Test
    fun `touch of thunder exposes exactly four grenade strategies`() {
        val rules = ArcTitanAspectRules
        assertEquals(
            ArcTitanAspectRules.TouchOfThunderStrategy.FLASHBANG_FIRST_BOUNCE_BLIND,
            rules.touchOfThunderStrategy(true, ArcTitanAspectRules.ArcGrenadeKind.FLASHBANG)
        )
        assertEquals(
            ArcTitanAspectRules.TouchOfThunderStrategy.PULSE_RAMPING_DAMAGE_AND_IONIC_TRACES,
            rules.touchOfThunderStrategy(true, ArcTitanAspectRules.ArcGrenadeKind.PULSE)
        )
        assertEquals(
            ArcTitanAspectRules.TouchOfThunderStrategy.LIGHTNING_EXTRA_CHARGE_AND_FIRST_DAMAGE_JOLT,
            rules.touchOfThunderStrategy(true, ArcTitanAspectRules.ArcGrenadeKind.LIGHTNING)
        )
        assertEquals(
            ArcTitanAspectRules.TouchOfThunderStrategy.STORM_ROAMING_TRACKING_THUNDERCLOUD,
            rules.touchOfThunderStrategy(true, ArcTitanAspectRules.ArcGrenadeKind.STORM)
        )
        assertNull(rules.touchOfThunderStrategy(true, ArcTitanAspectRules.ArcGrenadeKind.OTHER))
        assertNull(rules.touchOfThunderStrategy(false, ArcTitanAspectRules.ArcGrenadeKind.PULSE))
    }

    @Test
    fun `touch flashbang emits only on its first bounce`() {
        assertFalse(
            ArcTitanAspectRules.shouldEmitTouchFlashbangBlind(
                true,
                ArcTitanAspectRules.ArcGrenadeKind.FLASHBANG,
                0
            )
        )
        assertTrue(
            ArcTitanAspectRules.shouldEmitTouchFlashbangBlind(
                true,
                ArcTitanAspectRules.ArcGrenadeKind.FLASHBANG,
                1
            )
        )
        assertFalse(
            ArcTitanAspectRules.shouldEmitTouchFlashbangBlind(
                true,
                ArcTitanAspectRules.ArcGrenadeKind.PULSE,
                1
            )
        )
    }

    @Test
    fun `touch pulse traces periodically and damage ramps to its cap`() {
        val pulse = ArcTitanAspectRules.ArcGrenadeKind.PULSE
        assertTrue(ArcTitanAspectRules.shouldCreateTouchPulseIonicTrace(true, pulse, true, 1))
        assertFalse(ArcTitanAspectRules.shouldCreateTouchPulseIonicTrace(true, pulse, true, 2))
        assertTrue(ArcTitanAspectRules.shouldCreateTouchPulseIonicTrace(true, pulse, true, 4))
        assertFalse(ArcTitanAspectRules.shouldCreateTouchPulseIonicTrace(true, pulse, false, 4))
        assertEquals(1.0f, ArcTitanAspectRules.touchPulseDamageMultiplier(true, pulse, 1))
        assertEquals(1.2f, ArcTitanAspectRules.touchPulseDamageMultiplier(true, pulse, 3))
        assertEquals(1.6f, ArcTitanAspectRules.touchPulseDamageMultiplier(true, pulse, 100))
        assertEquals(
            1.0f,
            ArcTitanAspectRules.touchPulseDamageMultiplier(false, pulse, 100)
        )
    }

    @Test
    fun `touch lightning grants one charge and jolts after first accepted damage`() {
        val lightning = ArcTitanAspectRules.ArcGrenadeKind.LIGHTNING
        assertEquals(2, ArcTitanAspectRules.touchLightningMaximumCharges(1, true, lightning))
        assertEquals(1, ArcTitanAspectRules.touchLightningMaximumCharges(1, false, lightning))
        assertEquals(
            1,
            ArcTitanAspectRules.touchLightningMaximumCharges(
                1,
                true,
                ArcTitanAspectRules.ArcGrenadeKind.FLASHBANG
            )
        )
        assertFalse(ArcTitanAspectRules.shouldApplyTouchLightningJolt(true, lightning, true, 0))
        assertTrue(ArcTitanAspectRules.shouldApplyTouchLightningJolt(true, lightning, true, 1))
        assertFalse(ArcTitanAspectRules.shouldApplyTouchLightningJolt(true, lightning, true, 2))
        assertFalse(ArcTitanAspectRules.shouldApplyTouchLightningJolt(true, lightning, false, 1))
    }

    @Test
    fun `touch storm creates its tracking cloud only after detonation`() {
        val storm = ArcTitanAspectRules.ArcGrenadeKind.STORM
        assertFalse(ArcTitanAspectRules.shouldCreateTouchStormCloud(true, storm, false))
        assertTrue(ArcTitanAspectRules.shouldCreateTouchStormCloud(true, storm, true))
        assertFalse(
            ArcTitanAspectRules.shouldCreateTouchStormCloud(
                true,
                ArcTitanAspectRules.ArcGrenadeKind.PULSE,
                true
            )
        )
        assertEquals(120, ArcTitanAspectRules.MINECRAFT_CALIBRATION_TOUCH_STORM_CLOUD_DURATION_TICKS)
        assertEquals(8.0, ArcTitanAspectRules.MINECRAFT_CALIBRATION_TOUCH_STORM_TRACKING_RADIUS)
    }

    @Test
    fun `juggernaut activation requires aspect sprint threshold and full class energy`() {
        assertFalse(ArcTitanAspectRules.canActivateJuggernaut(true, 19, 1.0f, 1.0f))
        assertTrue(ArcTitanAspectRules.canActivateJuggernaut(true, 20, 1.0f, 1.0f))
        assertFalse(ArcTitanAspectRules.canActivateJuggernaut(true, 20, 0.99f, 1.0f))
        assertFalse(ArcTitanAspectRules.canActivateJuggernaut(false, 20, 1.0f, 1.0f))
        assertFalse(ArcTitanAspectRules.canActivateJuggernaut(true, 20, 0.0f, 0.0f))
    }

    @Test
    fun `juggernaut angle boundary and amplified capacity are explicit`() {
        assertFalse(ArcTitanAspectRules.isJuggernautFrontalHit(0.4999))
        assertTrue(ArcTitanAspectRules.isJuggernautFrontalHit(0.50))
        assertEquals(20.0f, ArcTitanAspectRules.juggernautShieldCapacity(false))
        assertEquals(30.0f, ArcTitanAspectRules.juggernautShieldCapacity(true))
    }

    @Test
    fun `juggernaut absorbs frontal damage but lets rear damage through`() {
        val shield = ArcTitanAspectRules.newJuggernautShield(isAmplified = false)
        val rear = ArcTitanAspectRules.absorbJuggernautDamage(shield, 5.0f, 0.49)
        assertEquals(0.0f, rear.absorbedDamage)
        assertEquals(5.0f, rear.passedThroughDamage)
        assertEquals(shield, rear.state)

        val front = ArcTitanAspectRules.absorbJuggernautDamage(shield, 5.0f, 0.50)
        assertEquals(5.0f, front.absorbedDamage)
        assertEquals(0.0f, front.passedThroughDamage)
        assertEquals(15.0f, front.state.remainingCapacity)
        assertEquals(1, front.boltChargeGained)
    }

    @Test
    fun `juggernaut break consumes class energy exactly once`() {
        val shield = ArcTitanAspectRules.newJuggernautShield(isAmplified = false)
        val breaking = ArcTitanAspectRules.absorbJuggernautDamage(shield, 25.0f, 1.0)
        assertTrue(breaking.shieldBroken)
        assertTrue(breaking.shouldConsumeClassAbility)
        assertEquals(20.0f, breaking.absorbedDamage)
        assertEquals(5.0f, breaking.passedThroughDamage)

        val repeated = ArcTitanAspectRules.absorbJuggernautDamage(breaking.state, 4.0f, 1.0)
        assertFalse(repeated.shieldBroken)
        assertFalse(repeated.shouldConsumeClassAbility)
        assertEquals(4.0f, repeated.passedThroughDamage)
    }

    @Test
    fun `knockout critical wound or shield break primes only subsequent hits`() {
        val critical = ArcTitanAspectRules.resolveKnockoutDamageTrigger(
            hasAspect = true,
            wasActiveBeforeDamage = false,
            damageAccepted = true,
            currentDamageIsMelee = true,
            targetBecameCriticallyWounded = true,
            targetShieldBroken = false,
            currentTick = 100
        )
        assertFalse(critical.enhanceCurrentHit)
        assertTrue(critical.triggeredForSubsequentHits)
        assertEquals(220L, critical.refreshedExpiresAt)

        val broken = ArcTitanAspectRules.resolveKnockoutDamageTrigger(
            true,
            false,
            true,
            true,
            false,
            true,
            100
        )
        assertFalse(broken.enhanceCurrentHit)
        assertTrue(broken.triggeredForSubsequentHits)

        val rejected = ArcTitanAspectRules.resolveKnockoutDamageTrigger(
            true,
            false,
            false,
            true,
            true,
            true,
            100
        )
        assertFalse(rejected.triggeredForSubsequentHits)
        assertNull(rejected.refreshedExpiresAt)
    }

    @Test
    fun `knockout current melee is enhanced only when buff existed before hit`() {
        assertEquals(
            1.0f,
            ArcTitanAspectRules.knockoutMeleeDamageMultiplier(true, false, true, false)
        )
        assertEquals(
            2.0f,
            ArcTitanAspectRules.knockoutMeleeDamageMultiplier(true, true, true, false)
        )
        assertEquals(
            1.5f,
            ArcTitanAspectRules.knockoutMeleeDamageMultiplier(true, true, true, true)
        )
        assertEquals(
            1.0f,
            ArcTitanAspectRules.knockoutMeleeDamageMultiplier(true, true, false, false)
        )
        assertTrue(ArcTitanAspectRules.knockoutMeleeIsArc(true, true, true))
        assertFalse(ArcTitanAspectRules.knockoutMeleeIsArc(true, false, true))
        assertEquals(5.5, ArcTitanAspectRules.knockoutMeleeReach(3.0, true, true))
        assertEquals(3.0, ArcTitanAspectRules.knockoutMeleeReach(3.0, true, false))
    }

    @Test
    fun `knockout melee final blows heal by rank and grant amplified`() {
        val minor = ArcTitanAspectRules.resolveKnockoutMeleeFinalBlow(
            true,
            true,
            ArcTitanAspectRules.KnockoutTargetRank.MINOR
        )
        assertEquals(4.0f, minor.healAmount)
        assertTrue(minor.shouldGrantAmplified)
        assertEquals(300, minor.amplifiedDurationTicks)

        val boss = ArcTitanAspectRules.resolveKnockoutMeleeFinalBlow(
            true,
            true,
            ArcTitanAspectRules.KnockoutTargetRank.BOSS_OR_CHAMPION
        )
        assertEquals(8.0f, boss.healAmount)

        val notMelee = ArcTitanAspectRules.resolveKnockoutMeleeFinalBlow(
            true,
            false,
            ArcTitanAspectRules.KnockoutTargetRank.BOSS_OR_CHAMPION
        )
        assertEquals(0.0f, notMelee.healAmount)
        assertFalse(notMelee.shouldGrantAmplified)
    }

    @Test
    fun `storms keep cast grants calibrated charge only to nearby team members`() {
        assertEquals(3, ArcTitanAspectRules.stormsKeepCastBoltChargeGain(true, true, true, 64.0))
        assertEquals(0, ArcTitanAspectRules.stormsKeepCastBoltChargeGain(true, true, true, 64.01))
        assertEquals(0, ArcTitanAspectRules.stormsKeepCastBoltChargeGain(true, true, false, 0.0))
        assertEquals(0, ArcTitanAspectRules.stormsKeepCastBoltChargeGain(true, false, true, 0.0))
    }

    @Test
    fun `storms keep overlapping barricades never multiply stack gain`() {
        assertEquals(0, ArcTitanAspectRules.normalizedStormsKeepBarricadeSources(0))
        assertEquals(1, ArcTitanAspectRules.normalizedStormsKeepBarricadeSources(1))
        assertEquals(1, ArcTitanAspectRules.normalizedStormsKeepBarricadeSources(4))

        val one = ArcTitanAspectRules.stormsKeepBarricadeBoltChargeGain(true, true, 1, 5, 100, 100)
        val four = ArcTitanAspectRules.stormsKeepBarricadeBoltChargeGain(true, true, 4, 5, 100, 100)
        assertEquals(1, one)
        assertEquals(one, four)
        assertEquals(120L, ArcTitanAspectRules.nextStormsKeepBarricadeStackTick(100, one))
        assertEquals(0, ArcTitanAspectRules.stormsKeepBarricadeBoltChargeGain(true, true, 1, 5, 99, 100))
        assertEquals(0, ArcTitanAspectRules.stormsKeepBarricadeBoltChargeGain(true, true, 1, 10, 100, 100))
        assertEquals(10, ArcTitanAspectRules.addBoltChargeStacks(9, 5))
    }

    @Test
    fun `storms keep full stacks release on hostile accepted weapon damage`() {
        val released = ArcTitanAspectRules.resolveStormsKeepWeaponDamage(
            hasAspect = true,
            isOwnerOrAlly = true,
            qualifyingBarricadeCount = 1,
            currentBoltChargeStacks = 10,
            isWeaponDamage = true,
            damageAccepted = true,
            targetIsHostile = true,
            targetIsImmune = false
        )
        assertTrue(released.shouldDischargeBoltCharge)
        assertEquals(0, released.remainingBoltChargeStacks)

        val notFull = ArcTitanAspectRules.resolveStormsKeepWeaponDamage(
            true,
            true,
            1,
            9,
            true,
            true,
            true,
            false
        )
        assertFalse(notFull.shouldDischargeBoltCharge)
        assertEquals(9, notFull.remainingBoltChargeStacks)
    }

    @Test
    fun `storms keep weapon release rejects allies immune targets and missing barricades`() {
        fun resolve(hostile: Boolean, immune: Boolean, barricades: Int) =
            ArcTitanAspectRules.resolveStormsKeepWeaponDamage(
                true,
                true,
                barricades,
                10,
                true,
                true,
                hostile,
                immune
            )

        assertFalse(resolve(hostile = false, immune = false, barricades = 1).shouldDischargeBoltCharge)
        assertFalse(resolve(hostile = true, immune = true, barricades = 1).shouldDischargeBoltCharge)
        assertFalse(resolve(hostile = true, immune = false, barricades = 0).shouldDischargeBoltCharge)
        assertTrue(resolve(hostile = true, immune = false, barricades = 3).shouldDischargeBoltCharge)
    }
}
