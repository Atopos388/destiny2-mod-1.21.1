package atopos.destiny2.common.player

import atopos.destiny2.common.gear.DestinyArmorSlot
import atopos.destiny2.common.gear.GearCategory
import atopos.destiny2.common.gear.GearRegistry
import atopos.destiny2.common.gear.GearRolls
import atopos.destiny2.common.item.DestinyClassItem
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlin.random.Random

data class GuardianPowerSnapshot(
    val current: Int,
    val highestAvailable: Int,
    val activityRecommended: Int,
    val deficit: Int,
    val outgoingMultiplier: Float,
    val incomingMultiplier: Float
) {
    val suppressed: Boolean get() = deficit > 0
    val suppressionPercent: Int get() = ((1.0f - outgoingMultiplier) * 100.0f).roundToInt().coerceAtLeast(0)
}

enum class GuardianRewardTier { NORMAL, POWERFUL, PINNACLE }

/** Resolves the recommended power of the current combat environment. */
object GuardianActivityPower {
    const val OVERWORLD = 100
    const val NETHER = 160
    const val END = 180

    fun recommended(level: Level, target: LivingEntity? = null): Int = when (target) {
        is WitherBoss -> 180
        is EnderDragon, is Warden -> 190
        else -> when (level.dimension()) {
            Level.NETHER -> NETHER
            Level.END -> END
            else -> OVERWORLD
        }
    }
}

/** Server-authoritative Destiny-style gear power, drop progression and activity suppression. */
object GuardianPowerSystem {
    const val BASE_POWER = 100
    const val SOFT_CAP = 150
    const val POWERFUL_CAP = 180
    const val PINNACLE_CAP = 200
    private const val EQUIPMENT_SLOT_COUNT = 6

    fun calculate(
        awakened: Boolean,
        equippedPower: List<Int>,
        highestSlotPower: List<Int>,
        activityRecommended: Int
    ): GuardianPowerSnapshot {
        if (!awakened) return GuardianPowerSnapshot(0, 0, 0, 0, 1.0f, 1.0f)
        val current = averageSlots(equippedPower)
        val highest = maxOf(current, averageSlots(highestSlotPower))
        return forActivity(current, highest, activityRecommended)
    }

    fun calculate(player: ServerPlayer): GuardianPowerSnapshot {
        val data = PlayerDestinyDataApi.get(player)
        val awakened = data.journeyStage != GuardianJourneyStage.MORTAL
        return calculate(
            awakened = awakened,
            equippedPower = equippedSlotPower(player, data),
            highestSlotPower = highestAvailableSlotPower(player, data),
            activityRecommended = GuardianActivityPower.recommended(player.serverLevel())
        )
    }

    fun forActivity(current: GuardianPowerSnapshot, recommended: Int): GuardianPowerSnapshot {
        // MORTAL players deliberately use the zero-power sentinel and do not
        // participate in power suppression. Recomputing that sentinel against
        // an activity recommendation would otherwise turn all PvE output into
        // 0 and inflate all incoming damage to the maximum penalty.
        if (current.current <= 0) return current
        return forActivity(current.current, current.highestAvailable, recommended)
    }

    fun normalDropPower(player: ServerPlayer, random: Random = Random.Default): Int {
        val data = PlayerDestinyDataApi.get(player)
        if (data.journeyStage == GuardianJourneyStage.MORTAL) return BASE_POWER
        val highest = calculate(player).highestAvailable.coerceAtLeast(BASE_POWER)
        return rewardPower(data.journeyStage, highest, GuardianRewardTier.NORMAL, random.nextInt(-2, 3))
    }

    fun normalDropPower(stage: GuardianJourneyStage, highestAvailable: Int, offset: Int): Int {
        return rewardPower(stage, highestAvailable, GuardianRewardTier.NORMAL, offset)
    }

    fun rewardPower(
        stage: GuardianJourneyStage,
        highestAvailable: Int,
        tier: GuardianRewardTier,
        offset: Int
    ): Int {
        if (stage == GuardianJourneyStage.MORTAL) return BASE_POWER
        val current = highestAvailable.coerceAtLeast(BASE_POWER)
        val target = when (tier) {
            GuardianRewardTier.NORMAL -> if (current < SOFT_CAP) current + offset else current + offset.coerceAtMost(0)
            GuardianRewardTier.POWERFUL -> if (current < POWERFUL_CAP) current + offset else current
            GuardianRewardTier.PINNACLE -> current + if (current < POWERFUL_CAP) maxOf(offset, 3) else offset
        }
        val systemCap = when (tier) {
            GuardianRewardTier.NORMAL -> if (current < SOFT_CAP) SOFT_CAP else current
            GuardianRewardTier.POWERFUL -> maxOf(current, POWERFUL_CAP)
            GuardianRewardTier.PINNACLE -> PINNACLE_CAP
        }
        val upper = maxOf(stage.dropFloor, minOf(stage.rewardCap, systemCap))
        return target.coerceIn(stage.dropFloor, upper)
    }

    private fun forActivity(current: Int, highest: Int, recommended: Int): GuardianPowerSnapshot {
        val deficit = (recommended - current).coerceAtLeast(0)
        return GuardianPowerSnapshot(
            current = current,
            highestAvailable = highest,
            activityRecommended = recommended,
            deficit = deficit,
            outgoingMultiplier = powerCurve(
                deficit,
                1.0f,
                0.93f,
                0.87f,
                0.60f,
                0.0f
            ),
            incomingMultiplier = powerCurve(
                deficit,
                1.0f,
                1.07f,
                1.16f,
                1.50f,
                1.50f
            )
        )
    }

    private fun powerCurve(
        deficit: Int,
        atZero: Float,
        atTen: Float,
        atTwenty: Float,
        atFifty: Float,
        atHundred: Float
    ): Float = when {
        deficit <= 0 -> atZero
        deficit <= 10 -> lerp(atZero, atTen, deficit / 10.0f)
        deficit <= 20 -> lerp(atTen, atTwenty, (deficit - 10) / 10.0f)
        deficit <= 50 -> lerp(atTwenty, atFifty, (deficit - 20) / 30.0f)
        deficit < 100 -> lerp(atFifty, atHundred, (deficit - 50) / 50.0f)
        else -> atHundred
    }

    private fun lerp(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress.coerceIn(0.0f, 1.0f)

    private fun equippedSlotPower(player: ServerPlayer, data: PlayerDestinyData): List<Int> = buildList {
        add(GearRolls.power(player.mainHandItem))
        addAll(player.inventory.armor.map(GearRolls::power))
        val classItem = data.classItem.takeIf {
            (it.item as? DestinyClassItem)?.requiredClass == data.destinyClass
        } ?: ItemStack.EMPTY
        add(GearRolls.power(classItem))
    }

    private fun highestAvailableSlotPower(player: ServerPlayer, data: PlayerDestinyData): List<Int> {
        var weapon = BASE_POWER
        val armor = DestinyArmorSlot.entries.associateWith { BASE_POWER }.toMutableMap()
        val candidates = buildList {
            addAll(player.inventory.items)
            addAll(player.inventory.armor)
            addAll(player.inventory.offhand)
            add(data.classItem)
        }
        candidates.forEach { stack ->
            if (stack.isEmpty) return@forEach
            val definition = GearRegistry.definitionFor(stack) ?: return@forEach
            val power = GearRolls.power(stack).takeIf { it > 0 } ?: return@forEach
            when (definition.category) {
                GearCategory.WEAPON -> weapon = maxOf(weapon, power)
                GearCategory.ARMOR -> DestinyArmorSlot.from(stack)?.let { slot ->
                    if (slot != DestinyArmorSlot.CLASS_ITEM ||
                        (stack.item as? DestinyClassItem)?.requiredClass == data.destinyClass
                    ) armor[slot] = maxOf(armor.getValue(slot), power)
                }
            }
        }
        return listOf(
            weapon,
            armor.getValue(DestinyArmorSlot.HELMET),
            armor.getValue(DestinyArmorSlot.CHEST),
            armor.getValue(DestinyArmorSlot.LEGS),
            armor.getValue(DestinyArmorSlot.BOOTS),
            armor.getValue(DestinyArmorSlot.CLASS_ITEM)
        )
    }

    private fun averageSlots(values: List<Int>): Int {
        val normalized = List(EQUIPMENT_SLOT_COUNT) { index ->
            values.getOrNull(index)?.takeIf { it > 0 } ?: BASE_POWER
        }
        return normalized.average().roundToInt()
    }
}

/** Small cache avoids copying CustomData for every damage calculation. */
object GuardianPowerRuntime {
    private val snapshots = ConcurrentHashMap<UUID, GuardianPowerSnapshot>()

    fun refresh(player: ServerPlayer): GuardianPowerSnapshot =
        GuardianPowerSystem.calculate(player).also { snapshots[player.uuid] = it }

    fun refreshIfChanged(player: ServerPlayer): GuardianPowerSnapshot? {
        val next = GuardianPowerSystem.calculate(player)
        val previous = snapshots.put(player.uuid, next)
        return next.takeIf { it != previous }
    }

    fun current(player: ServerPlayer): GuardianPowerSnapshot = snapshots[player.uuid] ?: refresh(player)

    fun clear(playerId: UUID) {
        snapshots.remove(playerId)
    }
}
