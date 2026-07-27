package atopos.destiny2.common.particle

/** Keeps GeckoLib's common-side animation controller independent of client classes. */
object BedrockParticleKeyframeBridge {
    @Volatile
    var listener: ((effect: String, locator: String) -> Unit)? = null

    fun emit(effect: String, locator: String) {
        listener?.invoke(effect, locator)
    }
}
