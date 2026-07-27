package atopos.destiny2.common.player

import net.minecraft.nbt.CompoundTag

class AbilityCooldowns(
    private val nextAvailableTicks: MutableMap<AbilitySlot, Long> = mutableMapOf(),
    private val totalDurationTicks: MutableMap<AbilitySlot, Int> = mutableMapOf()
) {
    fun nextAvailableTick(slot: AbilitySlot): Long = nextAvailableTicks[slot] ?: 0L
    fun totalDurationTicks(slot: AbilitySlot): Int = totalDurationTicks[slot] ?: 0

    fun isReady(slot: AbilitySlot, currentGameTime: Long): Boolean {
        return currentGameTime >= nextAvailableTick(slot)
    }

    fun setCooldown(slot: AbilitySlot, currentGameTime: Long, durationTicks: Int) {
        nextAvailableTicks[slot] = currentGameTime + durationTicks
        totalDurationTicks[slot] = durationTicks.coerceAtLeast(0)
    }

    fun clear(slot: AbilitySlot) {
        nextAvailableTicks.remove(slot)
        totalDurationTicks.remove(slot)
    }

    /** Reduces an active cooldown without ever making its stored end time precede now. */
    fun reduce(slot: AbilitySlot, currentGameTime: Long, ticks: Int): Int {
        val previous = nextAvailableTick(slot)
        if (previous <= currentGameTime || ticks <= 0) {
            return 0
        }
        val next = (previous - ticks).coerceAtLeast(currentGameTime)
        nextAvailableTicks[slot] = next
        return (previous - next).toInt()
    }

    fun copy(): AbilityCooldowns {
        return AbilityCooldowns(nextAvailableTicks.toMutableMap(), totalDurationTicks.toMutableMap())
    }

    fun toTag(): CompoundTag {
        val tag = CompoundTag()
        nextAvailableTicks.forEach { (slot, tick) ->
            tag.putLong(slot.key, tick)
            tag.putInt("${slot.key}_duration", totalDurationTicks(slot))
        }
        return tag
    }

    companion object {
        fun fromTag(tag: CompoundTag): AbilityCooldowns {
            val cooldowns = AbilityCooldowns()
            AbilitySlot.entries.forEach { slot ->
                if (tag.contains(slot.key)) {
                    cooldowns.nextAvailableTicks[slot] = tag.getLong(slot.key)
                    if (tag.contains("${slot.key}_duration")) {
                        cooldowns.totalDurationTicks[slot] = tag.getInt("${slot.key}_duration")
                    }
                }
            }
            return cooldowns
        }
    }
}
