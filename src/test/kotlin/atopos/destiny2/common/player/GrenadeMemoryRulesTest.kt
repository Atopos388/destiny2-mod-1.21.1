package atopos.destiny2.common.player

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class GrenadeMemoryRulesTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraftRegistries() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `universal memory resolves every active subclass to its default grenade`() {
        assertEquals(
            GrenadeMemoryRules.Target(DestinySubclassType.VOID_HUNTER, GrenadeMemoryRules.VOID_HUNTER_GRENADE_ID),
            GrenadeMemoryRules.targetFor(DestinySubclassType.VOID_HUNTER)
        )
        assertEquals(
            GrenadeMemoryRules.Target(DestinySubclassType.ARC_TITAN, GrenadeMemoryRules.ARC_TITAN_GRENADE_ID),
            GrenadeMemoryRules.targetFor(DestinySubclassType.ARC_TITAN)
        )
        assertEquals(
            GrenadeMemoryRules.Target(DestinySubclassType.SOLAR_WARLOCK, GrenadeMemoryRules.SOLAR_WARLOCK_GRENADE_ID),
            GrenadeMemoryRules.targetFor(DestinySubclassType.SOLAR_WARLOCK)
        )
    }

    @Test
    fun `memory requires awakening and its corresponding class`() {
        assertEquals(
            GrenadeMemoryRules.UseResult.NOT_AWAKENED,
            GrenadeMemoryRules.useResult(
                GuardianJourneyStage.MORTAL,
                setOf(DestinyClassType.HUNTER),
                emptySet(),
                DestinySubclassType.VOID_HUNTER,
                GrenadeMemoryRules.VOID_HUNTER_GRENADE_ID
            )
        )
        assertEquals(
            GrenadeMemoryRules.UseResult.CLASS_LOCKED,
            GrenadeMemoryRules.useResult(
                GuardianJourneyStage.AWAKENED,
                setOf(DestinyClassType.WARLOCK),
                emptySet(),
                DestinySubclassType.VOID_HUNTER,
                GrenadeMemoryRules.VOID_HUNTER_GRENADE_ID
            )
        )
    }

    @Test
    fun `already recovered grenade memory cannot be consumed again`() {
        assertEquals(
            GrenadeMemoryRules.UseResult.ALREADY_UNLOCKED,
            GrenadeMemoryRules.useResult(
                GuardianJourneyStage.AWAKENED,
                setOf(DestinyClassType.TITAN),
                setOf(GrenadeMemoryRules.ARC_TITAN_GRENADE_ID),
                DestinySubclassType.ARC_TITAN,
                GrenadeMemoryRules.ARC_TITAN_GRENADE_ID
            )
        )
    }

    @Test
    fun `successful memory permanently unlocks and equips the active grenade`() {
        val data = PlayerDestinyData.createDefault().also {
            it.journeyStage = GuardianJourneyStage.AWAKENED
            it.unlockedClasses += DestinyClassType.TITAN
            it.setClass(DestinyClassType.TITAN)
        }

        assertTrue(
            GrenadeMemoryRules.unlock(
                data,
                DestinySubclassType.ARC_TITAN,
                GrenadeMemoryRules.ARC_TITAN_GRENADE_ID
            )
        )
        assertTrue(data.isSubclassOptionUnlocked(GrenadeMemoryRules.ARC_TITAN_GRENADE_ID))
        assertEquals(
            GrenadeMemoryRules.ARC_TITAN_GRENADE_ID,
            data.subclassConfig.selectedAbilities[AbilitySlot.GRENADE]
        )
        assertFalse(
            GrenadeMemoryRules.unlock(
                data,
                DestinySubclassType.ARC_TITAN,
                GrenadeMemoryRules.ARC_TITAN_GRENADE_ID
            )
        )
    }

    @Test
    fun `memory for an inactive unlocked class does not change the active loadout`() {
        val data = PlayerDestinyData.createDefault().also {
            it.journeyStage = GuardianJourneyStage.AWAKENED
            it.unlockedClasses += DestinyClassType.WARLOCK
            it.unlockedClasses += DestinyClassType.HUNTER
            it.setClass(DestinyClassType.WARLOCK)
        }

        assertTrue(
            GrenadeMemoryRules.unlock(
                data,
                DestinySubclassType.VOID_HUNTER,
                GrenadeMemoryRules.VOID_HUNTER_GRENADE_ID
            )
        )
        assertTrue(data.isSubclassOptionUnlocked(GrenadeMemoryRules.VOID_HUNTER_GRENADE_ID))
        assertFalse(data.subclassConfig.selectedAbilities.containsKey(AbilitySlot.GRENADE))
    }

    @Test
    fun `memory ids point at registered grenades`() {
        assertTrue(
            GrenadeMemoryRules.isRegisteredGrenade(
                DestinySubclassType.VOID_HUNTER,
                GrenadeMemoryRules.VOID_HUNTER_GRENADE_ID
            )
        )
        assertTrue(
            GrenadeMemoryRules.isRegisteredGrenade(
                DestinySubclassType.ARC_TITAN,
                GrenadeMemoryRules.ARC_TITAN_GRENADE_ID
            )
        )
        assertTrue(
            GrenadeMemoryRules.isRegisteredGrenade(
                DestinySubclassType.SOLAR_WARLOCK,
                GrenadeMemoryRules.SOLAR_WARLOCK_GRENADE_ID
            )
        )
        assertFalse(
            GrenadeMemoryRules.isRegisteredGrenade(
                DestinySubclassType.SOLAR_WARLOCK,
                "destiny2-mod:not_a_grenade"
            )
        )
    }
}
