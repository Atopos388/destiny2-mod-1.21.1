package atopos.destiny2.client.gui

import atopos.destiny2.common.player.GuardianJourneyStage
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items

data class DestinyNavigationData(
    val location: String,
    val coordinates: String,
    val objectiveTitle: String,
    val objectiveDescription: String,
    val objectiveItems: List<ObjectiveItem>,
    val guardianName: String,
    val className: String,
    val subclassName: String,
    val power: Int,
    val highestAvailablePower: Int,
    val recommendedPower: Int,
    val powerDeficit: Int,
    val suppressionPercent: Int,
    val superEnergy: Int,
    val weaponName: String,
    val fireteam: List<FireteamMember>
) {
    data class ObjectiveItem(val name: String, val count: Int, val complete: Boolean)
    data class FireteamMember(val name: String, val latency: Int, val local: Boolean)
}

object DestinyNavigationState {
    var journeyStageId: String = "mortal"
        private set
    var currentPower: Int = 0
        private set
    var highestAvailablePower: Int = 0
        private set
    var recommendedPower: Int = 0
        private set
    var powerDeficit: Int = 0
        private set
    var suppressionPercent: Int = 0
        private set

    fun update(id: String, current: Int, highestAvailable: Int, recommended: Int, deficit: Int, suppression: Int) {
        journeyStageId = id.ifBlank { "mortal" }
        currentPower = current.coerceAtLeast(0)
        highestAvailablePower = highestAvailable.coerceAtLeast(0)
        recommendedPower = recommended.coerceAtLeast(0)
        powerDeficit = deficit.coerceAtLeast(0)
        suppressionPercent = suppression.coerceIn(0, 100)
    }

    fun reset() {
        journeyStageId = "mortal"
        currentPower = 0
        highestAvailablePower = 0
        recommendedPower = 0
        powerDeficit = 0
        suppressionPercent = 0
    }
}

object DestinyNavigationDataAdapter {
    fun snapshot(client: Minecraft = Minecraft.getInstance()): DestinyNavigationData? {
        val player = client.player ?: return null
        val level = client.level ?: return null
        val dimension = level.dimension().location()
        val dimensionName = when (dimension.toString()) {
            "minecraft:overworld" -> Component.translatable("navigation.destiny2-mod.dimension.overworld").string
            "minecraft:the_nether" -> Component.translatable("navigation.destiny2-mod.dimension.nether").string
            "minecraft:the_end" -> Component.translatable("navigation.destiny2-mod.dimension.end").string
            else -> dimension.path.replace('_', ' ').replaceFirstChar(Char::uppercase)
        }
        val biomeName = level.getBiome(player.blockPosition()).unwrapKey().map { key ->
            val id = key.location()
            Component.translatable("biome.${id.namespace}.${id.path}").string
        }.orElse(Component.translatable("navigation.destiny2-mod.location.unknown").string)

        val awakened = GuardianJourneyStage.fromId(DestinyNavigationState.journeyStageId) != GuardianJourneyStage.MORTAL
        val objectiveItems = if (awakened) {
            listOf(Items.COPPER_INGOT, Items.REDSTONE, Items.IRON_INGOT, Items.AMETHYST_SHARD).map { item ->
                val count = player.inventory.countItem(item)
                DestinyNavigationData.ObjectiveItem(item.description.string, count, count > 0)
            }
        } else {
            emptyList()
        }
        val objectiveTitle = Component.translatable(
            if (awakened) "navigation.destiny2-mod.objective.awakened.title"
            else "navigation.destiny2-mod.objective.mortal.title"
        ).string
        val objectiveDescription = Component.translatable(
            if (awakened) "navigation.destiny2-mod.objective.awakened.description"
            else "navigation.destiny2-mod.objective.mortal.description"
        ).string

        val fireteam = client.connection?.onlinePlayers.orEmpty()
            .sortedWith(compareByDescending<net.minecraft.client.multiplayer.PlayerInfo> { it.profile.id == player.uuid }
                .thenBy { it.profile.name.lowercase() })
            .map { info ->
                DestinyNavigationData.FireteamMember(
                    name = info.profile.name,
                    latency = info.latency.coerceAtLeast(0),
                    local = info.profile.id == player.uuid
                )
            }

        return DestinyNavigationData(
            location = "$dimensionName  //  $biomeName",
            coordinates = "X ${player.blockX}   Y ${player.blockY}   Z ${player.blockZ}",
            objectiveTitle = objectiveTitle,
            objectiveDescription = objectiveDescription,
            objectiveItems = objectiveItems,
            guardianName = player.gameProfile.name,
            className = DestinyHUDState.className,
            subclassName = DestinyHUDState.subclassName,
            power = DestinyNavigationState.currentPower,
            highestAvailablePower = DestinyNavigationState.highestAvailablePower,
            recommendedPower = DestinyNavigationState.recommendedPower,
            powerDeficit = DestinyNavigationState.powerDeficit,
            suppressionPercent = DestinyNavigationState.suppressionPercent,
            superEnergy = DestinyHUDState.superEnergy.toInt().coerceIn(0, 100),
            weaponName = player.mainHandItem.takeUnless { it.isEmpty }?.hoverName?.string
                ?: Component.translatable("navigation.destiny2-mod.weapon.empty").string,
            fireteam = fireteam
        )
    }
}
