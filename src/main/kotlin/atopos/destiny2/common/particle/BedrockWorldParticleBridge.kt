package atopos.destiny2.common.particle

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3

/** Keeps client-native world particle playback out of common entity classes. */
object BedrockWorldParticleBridge {
    @Volatile
    var listener: ((
        effect: ResourceLocation,
        position: Vec3,
        yaw: Float,
        pitch: Float
    ) -> Unit)? = null

    fun emit(
        effect: ResourceLocation,
        position: Vec3,
        yaw: Float,
        pitch: Float
    ) {
        listener?.invoke(effect, position, yaw, pitch)
    }
}
