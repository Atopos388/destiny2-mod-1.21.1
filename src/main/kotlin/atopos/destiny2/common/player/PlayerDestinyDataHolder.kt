package atopos.destiny2.common.player

interface PlayerDestinyDataHolder {
    fun `destiny2-mod$getDestinyData`(): PlayerDestinyData

    fun `destiny2-mod$setDestinyData`(data: PlayerDestinyData)
}
