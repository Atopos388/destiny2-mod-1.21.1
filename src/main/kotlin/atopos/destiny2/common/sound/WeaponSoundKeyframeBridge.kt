package atopos.destiny2.common.sound

/** Keeps GeckoLib sound keyframes usable by GeoItem animations without client classes on the server. */
object WeaponSoundKeyframeBridge {
    @Volatile
    var listener: ((sound: String) -> Unit)? = null

    fun emit(sound: String) {
        listener?.invoke(sound)
    }
}
