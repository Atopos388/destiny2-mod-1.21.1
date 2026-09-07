package atopos.destiny2.client.weapon

import net.minecraft.client.resources.sounds.AbstractSoundInstance
import net.minecraft.client.resources.sounds.Sound
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.client.sounds.SoundManager
import net.minecraft.client.sounds.WeighedSoundEvents
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.util.valueproviders.ConstantFloat

/** A positional sound backed by an OGG that remains inside an external gun-pack ZIP. */
class ExternalGunPackSoundInstance(
    private val resource: ResourceLocation,
    source: SoundSource,
    volume: Float,
    x: Double,
    y: Double,
    z: Double
) : AbstractSoundInstance(resource, source, RandomSource.create()) {
    private val directSound = Sound(
        ResourceLocation.fromNamespaceAndPath(resource.namespace, resource.path.removeSuffix(OGG_SUFFIX)),
        ConstantFloat.of(1.0f),
        ConstantFloat.of(1.0f),
        1,
        Sound.Type.FILE,
        false,
        false,
        ATTENUATION_DISTANCE
    )

    init {
        this.volume = volume.coerceAtLeast(0.0f)
        this.pitch = 1.0f
        this.x = x
        this.y = y
        this.z = z
        this.looping = false
        this.delay = 0
        this.attenuation = SoundInstance.Attenuation.LINEAR
        this.relative = false
    }

    override fun resolve(manager: SoundManager): WeighedSoundEvents {
        sound = directSound
        return WeighedSoundEvents(location, null).also { it.addSound(directSound) }
    }

    companion object {
        private const val OGG_SUFFIX = ".ogg"
        private const val ATTENUATION_DISTANCE = 32
    }
}
