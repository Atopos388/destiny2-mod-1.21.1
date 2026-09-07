package atopos.destiny2.common.player

import atopos.destiny2.common.weapon.DestinyWeaponReserves
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.HolderLookup
import net.minecraft.world.item.ItemStack

data class PlayerDestinyData(
    var destinyClass: DestinyClassType = DestinyClassType.DEFAULT,
    var subclass: DestinySubclassType = DestinySubclassType.DEFAULT,
    var stats: DestinyStats = DestinyStats.defaultFor(DestinyClassType.DEFAULT),
    var cooldowns: AbilityCooldowns = AbilityCooldowns(),
    var combatState: DestinyCombatState = DestinyCombatState(),
    var subclassConfig: PlayerSubclassConfiguration = PlayerSubclassConfiguration(),
    var subclassDefaultsVersion: Int = CURRENT_SUBCLASS_DEFAULTS_VERSION,
    var classItem: ItemStack = ItemStack.EMPTY,
    var weaponReserves: DestinyWeaponReserves = DestinyWeaponReserves(),
    var weaponLoadoutInitialized: Boolean = false,
    var journeyStage: GuardianJourneyStage = GuardianJourneyStage.MORTAL,
    var awakeningPresentationPending: Boolean = false,
    var unlockedClasses: MutableSet<DestinyClassType> = mutableSetOf(),
    var unlockedSubclassOptions: MutableSet<String> = mutableSetOf()
) {
    fun setClass(destinyClass: DestinyClassType) {
        this.destinyClass = destinyClass
        this.subclass = DestinySubclassType.defaultFor(destinyClass)
        this.stats = DestinyStats.defaultFor(destinyClass)
        this.cooldowns = AbilityCooldowns()
        this.combatState.resetForLoadout()
        this.subclassConfig = DestinySubclassConfigRegistry.defaultFor(this.subclass)
        restrictCurrentSubclassConfig()
    }

    fun setSubclass(subclass: DestinySubclassType): Boolean {
        if (subclass.requiredClass != destinyClass) {
            return false
        }
        this.subclass = subclass
        this.cooldowns = AbilityCooldowns()
        this.combatState.resetForLoadout()
        this.subclassConfig = DestinySubclassConfigRegistry.defaultFor(subclass)
        restrictCurrentSubclassConfig()
        return true
    }

    fun isClassUnlocked(destinyClass: DestinyClassType): Boolean = destinyClass in unlockedClasses

    fun isSubclassOptionUnlocked(optionId: String?): Boolean =
        !optionId.isNullOrBlank() && optionId in unlockedSubclassOptions

    fun restrictCurrentSubclassConfig() {
        subclassConfig.selectedAbilities.entries.removeAll { (_, id) -> !isSubclassOptionUnlocked(id) }
        if (!isSubclassOptionUnlocked(subclassConfig.selectedMovementId)) {
            subclassConfig.selectedMovementId = ""
        }
        subclassConfig.selectedAspects.removeAll { !isSubclassOptionUnlocked(it) }
        subclassConfig.selectedFragments.removeAll { !isSubclassOptionUnlocked(it) }
    }

    fun copy(): PlayerDestinyData {
        return PlayerDestinyData(
            destinyClass = destinyClass,
            subclass = subclass,
            stats = stats,
            cooldowns = cooldowns.copy(),
            combatState = combatState.copyState(),
            subclassConfig = subclassConfig.copy(),
            subclassDefaultsVersion = subclassDefaultsVersion,
            classItem = classItem.copy(),
            weaponReserves = weaponReserves.copy(),
            weaponLoadoutInitialized = weaponLoadoutInitialized,
            journeyStage = journeyStage,
            awakeningPresentationPending = awakeningPresentationPending,
            unlockedClasses = unlockedClasses.toMutableSet(),
            unlockedSubclassOptions = unlockedSubclassOptions.toMutableSet()
        )
    }

    fun toTag(registries: HolderLookup.Provider): CompoundTag {
        val tag = CompoundTag()
        tag.putString("class", destinyClass.id)
        tag.putString("subclass", subclass.id)
        tag.put("stats", stats.toTag())
        tag.put("cooldowns", cooldowns.toTag())
        tag.put("combat_state", combatState.toTag())
        tag.put("subclass_config", subclassConfig.toTag())
        tag.putInt("subclass_defaults_version", subclassDefaultsVersion)
        if (!classItem.isEmpty) tag.put("class_item", classItem.saveOptional(registries))
        tag.put("weapon_reserves", weaponReserves.toTag(registries))
        tag.putBoolean("weapon_loadout_initialized", weaponLoadoutInitialized)
        tag.putString("journey_stage", journeyStage.id)
        tag.putBoolean("awakening_presentation_pending", awakeningPresentationPending)
        tag.putString("unlocked_classes", unlockedClasses.joinToString(",") { it.id })
        tag.putString("unlocked_subclass_options", unlockedSubclassOptions.joinToString(","))
        return tag
    }

    companion object {
        const val NBT_KEY = "Destiny2PlayerData"
        private const val CURRENT_SUBCLASS_DEFAULTS_VERSION = 1

        fun createDefault(): PlayerDestinyData {
            return PlayerDestinyData()
        }

        fun fromTag(tag: CompoundTag, registries: HolderLookup.Provider): PlayerDestinyData {
            val destinyClass = DestinyClassType.fromId(tag.getString("class"))
            val subclass = DestinySubclassType.fromId(tag.getString("subclass"))
                .takeIf { it.requiredClass == destinyClass }
                ?: DestinySubclassType.defaultFor(destinyClass)
            val stats = if (tag.contains("stats")) {
                DestinyStats.fromTag(tag.getCompound("stats"), destinyClass)
            } else {
                DestinyStats.defaultFor(destinyClass)
            }
            val cooldowns = if (tag.contains("cooldowns")) {
                AbilityCooldowns.fromTag(tag.getCompound("cooldowns"))
            } else {
                AbilityCooldowns()
            }
            val combatState = if (tag.contains("combat_state")) {
                DestinyCombatState.fromTag(tag.getCompound("combat_state"))
            } else {
                DestinyCombatState()
            }
            var subclassConfig = if (tag.contains("subclass_config")) {
                DestinySubclassConfigRegistry.normalize(
                    subclass,
                    PlayerSubclassConfiguration.fromTag(tag.getCompound("subclass_config"))
                )
            } else {
                DestinySubclassConfigRegistry.defaultFor(subclass)
            }
            val defaultsVersion = if (tag.contains("subclass_defaults_version")) {
                tag.getInt("subclass_defaults_version")
            } else {
                0
            }
            if (defaultsVersion < CURRENT_SUBCLASS_DEFAULTS_VERSION) {
                subclassConfig = DestinySubclassConfigRegistry.installMissingDefaults(subclass, subclassConfig)
            }
            val classItem = if (tag.contains("class_item")) {
                ItemStack.parseOptional(registries, tag.getCompound("class_item"))
            } else ItemStack.EMPTY
            val weaponReserves = if (tag.contains("weapon_reserves")) {
                DestinyWeaponReserves.fromTag(tag.getCompound("weapon_reserves"), registries)
            } else DestinyWeaponReserves()
            val weaponLoadoutInitialized = tag.getBoolean("weapon_loadout_initialized")
            val journeyStage = if (tag.contains("journey_stage")) {
                GuardianJourneyStage.fromId(tag.getString("journey_stage"))
            } else if (destinyClass != DestinyClassType.DEFAULT) {
                // Existing class-configured development worlds are already guardians.
                GuardianJourneyStage.AWAKENED
            } else {
                GuardianJourneyStage.MORTAL
            }
            val awakeningPresentationPending = tag.getBoolean("awakening_presentation_pending")
            val hasProgressionData = tag.contains("unlocked_classes")
            val unlockedClasses = readCsv(tag.getString("unlocked_classes"))
                .mapNotNull(DestinyClassType::findById)
                .toMutableSet()
            val unlockedOptions = readCsv(tag.getString("unlocked_subclass_options")).toMutableSet()
            if (!hasProgressionData && journeyStage != GuardianJourneyStage.MORTAL) {
                // Preserve pre-progression development worlds that previously had unrestricted class access.
                unlockedClasses.addAll(DestinyClassType.entries)
                DestinySubclassType.entries.forEach { type ->
                    val definition = DestinySubclassConfigRegistry.definitionFor(type)
                    definition.abilityOptions.values.flatten().mapTo(unlockedOptions) { it.id }
                    definition.movementOptions.mapTo(unlockedOptions) { it.id }
                    definition.aspectOptions.mapTo(unlockedOptions) { it.id }
                    definition.fragmentOptions.mapTo(unlockedOptions) { it.id }
                }
            }

            return PlayerDestinyData(
                destinyClass = destinyClass,
                subclass = subclass,
                stats = stats,
                cooldowns = cooldowns,
                combatState = combatState,
                subclassConfig = subclassConfig,
                subclassDefaultsVersion = CURRENT_SUBCLASS_DEFAULTS_VERSION,
                classItem = classItem,
                weaponReserves = weaponReserves,
                weaponLoadoutInitialized = weaponLoadoutInitialized,
                journeyStage = journeyStage,
                awakeningPresentationPending = awakeningPresentationPending,
                unlockedClasses = unlockedClasses,
                unlockedSubclassOptions = unlockedOptions
            ).also(PlayerDestinyData::restrictCurrentSubclassConfig)
        }

        private fun readCsv(value: String): List<String> =
            value.split(",").map(String::trim).filter(String::isNotBlank)
    }
}
