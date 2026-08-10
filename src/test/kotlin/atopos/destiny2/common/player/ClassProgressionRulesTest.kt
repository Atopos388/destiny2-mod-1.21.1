package atopos.destiny2.common.player

import net.minecraft.core.HolderLookup
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import java.util.stream.Stream

class ClassProgressionRulesTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapMinecraftRegistries() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `resonance requires awakening ghost core and a locked class`() {
        assertEquals(
            ClassProgressionRules.ResonanceResult.NOT_AWAKENED,
            ClassProgressionRules.resonanceResult(
                GuardianJourneyStage.MORTAL,
                hasGhostCore = true,
                emptySet(),
                DestinyClassType.HUNTER
            )
        )
        assertEquals(
            ClassProgressionRules.ResonanceResult.MISSING_GHOST_CORE,
            ClassProgressionRules.resonanceResult(
                GuardianJourneyStage.AWAKENED,
                hasGhostCore = false,
                emptySet(),
                DestinyClassType.HUNTER
            )
        )
        assertEquals(
            ClassProgressionRules.ResonanceResult.ALREADY_UNLOCKED,
            ClassProgressionRules.resonanceResult(
                GuardianJourneyStage.AWAKENED,
                hasGhostCore = true,
                setOf(DestinyClassType.HUNTER),
                DestinyClassType.HUNTER
            )
        )
        assertEquals(
            ClassProgressionRules.ResonanceResult.ALLOWED,
            ClassProgressionRules.resonanceResult(
                GuardianJourneyStage.AWAKENED,
                hasGhostCore = true,
                emptySet(),
                DestinyClassType.HUNTER
            )
        )
    }

    @Test
    fun `class switching is unlocked and combat locked`() {
        val unlocked = setOf(DestinyClassType.TITAN)
        assertFalse(ClassProgressionRules.canSwitchClass(DestinyClassType.HUNTER, unlocked, Long.MIN_VALUE, 1_000L))
        assertFalse(ClassProgressionRules.canSwitchClass(DestinyClassType.TITAN, unlocked, 900L, 1_000L))
        assertTrue(ClassProgressionRules.canSwitchClass(DestinyClassType.TITAN, unlocked, 800L, 1_000L))
        assertTrue(ClassProgressionRules.canSwitchClass(DestinyClassType.TITAN, unlocked, Long.MIN_VALUE, 1_000L))
    }

    @Test
    fun `unlocked classes and basic options survive nbt round trip`() {
        val data = PlayerDestinyData.createDefault().also {
            it.journeyStage = GuardianJourneyStage.AWAKENED
            it.unlockedClasses += DestinyClassType.TITAN
            it.unlockedSubclassOptions += GuardianJumpRules.TITAN_LIFT_ID
            val classAbility = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.ARC_TITAN)
                .abilityOptions.getValue(AbilitySlot.CLASS_ABILITY)
                .first().id
            it.unlockedSubclassOptions += classAbility
            it.setClass(DestinyClassType.TITAN)
        }
        val registries = HolderLookup.Provider.create(Stream.empty())
        val restored = PlayerDestinyData.fromTag(data.toTag(registries), registries)

        assertEquals(setOf(DestinyClassType.TITAN), restored.unlockedClasses)
        assertTrue(restored.isSubclassOptionUnlocked(GuardianJumpRules.TITAN_LIFT_ID))
        assertTrue(restored.subclassConfig.selectedAbilities.containsKey(AbilitySlot.CLASS_ABILITY))
        assertFalse(restored.subclassConfig.selectedAbilities.containsKey(AbilitySlot.GRENADE))
        assertFalse(restored.subclassConfig.selectedAbilities.containsKey(AbilitySlot.MELEE))
        assertFalse(restored.subclassConfig.selectedAbilities.containsKey(AbilitySlot.SUPER))
        assertTrue(restored.subclassConfig.selectedAspects.isEmpty())
        assertTrue(restored.subclassConfig.selectedFragments.isEmpty())
    }

    @Test
    fun `copy owns independent progression sets`() {
        val original = PlayerDestinyData.createDefault().also {
            it.unlockedClasses += DestinyClassType.WARLOCK
            it.unlockedSubclassOptions += GuardianJumpRules.WARLOCK_VECTOR_GLIDE_ID
        }
        val copy = original.copy()
        copy.unlockedClasses += DestinyClassType.HUNTER
        copy.unlockedSubclassOptions += GuardianJumpRules.HUNTER_TRIPLE_JUMP_ID

        assertEquals(setOf(DestinyClassType.WARLOCK), original.unlockedClasses)
        assertEquals(setOf(GuardianJumpRules.WARLOCK_VECTOR_GLIDE_ID), original.unlockedSubclassOptions)
    }

    @Test
    fun `full option unlock grants every option for the requested class`() {
        val data = PlayerDestinyData.createDefault()
        val destinyClass = DestinyClassType.HUNTER
        val definition = DestinySubclassConfigRegistry.definitionFor(DestinySubclassType.defaultFor(destinyClass))
        val expectedOptions = buildSet {
            definition.abilityOptions.values.flatten().mapTo(this) { it.id }
            definition.movementOptions.mapTo(this) { it.id }
            definition.aspectOptions.mapTo(this) { it.id }
            definition.fragmentOptions.mapTo(this) { it.id }
        }

        ClassResonanceRuntime.unlockAllOptions(data, destinyClass)

        assertTrue(data.isClassUnlocked(destinyClass))
        assertTrue(data.unlockedSubclassOptions.containsAll(expectedOptions))
    }
}
