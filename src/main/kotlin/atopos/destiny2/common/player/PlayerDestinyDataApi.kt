package atopos.destiny2.common.player

import net.minecraft.server.level.ServerPlayer

object PlayerDestinyDataApi {
    fun get(player: ServerPlayer): PlayerDestinyData {
        return (player as PlayerDestinyDataHolder).`destiny2-mod$getDestinyData`()
    }

    fun set(player: ServerPlayer, data: PlayerDestinyData) {
        (player as PlayerDestinyDataHolder).`destiny2-mod$setDestinyData`(data)
    }
}
