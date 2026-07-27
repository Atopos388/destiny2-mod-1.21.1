package atopos.destiny2.common.weapon

enum class DestinyAmmoType(val displayName: String, val hudColor: Int) {
    PRIMARY("白弹", 0xFFF4F5F7.toInt()),
    SPECIAL("特殊弹药", 0xFF72E49A.toInt()),
    HEAVY("重型弹药", 0xFFB785F5.toInt())
}
