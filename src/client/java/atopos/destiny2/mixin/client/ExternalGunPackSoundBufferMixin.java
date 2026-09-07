package atopos.destiny2.mixin.client;

import atopos.destiny2.client.weapon.ExternalGunPackSoundResources;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Lets the vanilla sound engine decode OGG bytes supplied by an external gun-pack ZIP. */
@Mixin(SoundBufferLibrary.class)
public abstract class ExternalGunPackSoundBufferMixin {
    private static final Logger DESTINY2_LOGGER = LoggerFactory.getLogger("DestinyExternalGunSound");

    @Shadow @Final
    private Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache;

    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void destiny2$loadExternalGunSound(
            ResourceLocation location,
            CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir
    ) {
        byte[] bytes = ExternalGunPackSoundResources.bytesForPlaybackPath(location);
        if (bytes == null) {
            return;
        }
        cir.setReturnValue(cache.computeIfAbsent(location, ignored ->
                CompletableFuture.supplyAsync(() -> decode(location, bytes), Util.backgroundExecutor())
        ));
    }

    private static SoundBuffer decode(ResourceLocation location, byte[] bytes) {
        try (JOrbisAudioStream stream = new JOrbisAudioStream(new ByteArrayInputStream(bytes))) {
            SoundBuffer buffer = new SoundBuffer(stream.readAll(), stream.getFormat());
            DESTINY2_LOGGER.info("Decoded external gun sound {}", location);
            return buffer;
        } catch (IOException exception) {
            throw new CompletionException("Failed to decode external gun sound", exception);
        }
    }
}
