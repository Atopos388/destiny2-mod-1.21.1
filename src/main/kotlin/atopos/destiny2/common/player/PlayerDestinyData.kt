package atopos.destiny2.common.player

import net.minecraft.nbt.CompoundTag
import net.minecraft.core.HolderLookup
import net.minecraft.world.item.ItemStack

data class PlayerDestinyData(
    var destinyClass: DestinyClassType = DestinyClassType.DEFAULT,
    var subclass: DestinySubclassType = DestinySubclassType.DEFAULT,
    var stats: DestinyStats = DestinyStats.defaultFor(DestinyClassType.DEFAULT),
    var cooldowns: AbilityCooldowns = AbilityCooldowns(),
    var combatState: DestinyCombatState = DestinyCombatState(),
    var subclassConfig: PlayerSubclassConfiguration = DestinySubclassConfigRegistry.defaultFor(DestinySubclassType.DEFAULT),
    var subclassDefaultsVersion: Int = CURRENT_SUBCLASS_DEFAULTS_VERSION,
    var classItem: ItemStack = ItemStack.EMPTY,
    var journeyStage: GuardianJourneyStage = GuardianJourneyStage.MORTAL,
    var awakeningPresentationPending: Boolean = false
) {
    fun setClass(destinyClass: DestinyClassType) {
        this.destinyClass = destinyClass
        this.subclass = DestinySubclassType.defaultFor(destinyClass)
        this.stats = DestinyStats.defaultFor(destinyClass)
        this.cooldowns = AbilityCooldowns()
        this.combatState.resetForLoadout()
        this.subclassConfig = DestinySubclassConfigRegistry.defaultFor(this.subclass)
    }

    fun setSubclass(subclass: DestinySubclassType): Boolean {
        if (subclass.requiredClass != destinyClass) {
            return false
        }
        this.subclass = subclass
        this.cooldowns = AbilityCooldowns()
        this.combatState.resetForLoadout()
        this.subclassConfig = DestinySubclassConfigRegistry.defaultFor(subclass)
        return true
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
            journeyStage = journeyStage,
            awakeningPresentationPending = awakeningPresentationPending
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
        tag.putString("journey_stage", journeyStage.id)
        tag.putBoolean("awakening_presentation_pending", awakeningPresentationPending)
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
            val journeyStage = if (tag.contains("journey_stage")) {
                GuardianJourneyStage.fromId(tag.getString("journey_stage"))
            } else if (destinyClass != DestinyClassType.DEFAULT) {
                // Existing class-configured development worlds are already guardians.
                GuardianJourneyStage.AWAKENED
            } else {
                GuardianJourneyStage.MORTAL
            }
            val awakeningPresentationPending = tag.getBoolean("awakening_presentation_pending")

            return PlayerDestinyData(
                destinyClass,
                subclass,
                stats,
                cooldowns,
                combatState,
                subclassConfig,
                CURRENT_SUBCLASS_DEFAULTS_VERSION,
                classItem,
                journeyStage,
                awakeningPresentationPending
            )
        }
    }
}
