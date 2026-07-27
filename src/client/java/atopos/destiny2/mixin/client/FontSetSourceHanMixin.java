package atopos.destiny2.mixin.client;

import atopos.destiny2.client.font.SourceHanGlyphProvider;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Adds Source Han only after vanilla's eager provider scan has completed. */
@Mixin(FontSet.class)
public abstract class FontSetSourceHanMixin {
    private static final ResourceLocation DESTINY_DIRECTOR_FONT =
            ResourceLocation.fromNamespaceAndPath("destiny2-mod", "director");

    @Shadow @Final private ResourceLocation name;
    @Shadow private List<GlyphProvider> activeProviders;

    @Inject(method = "reload(Ljava/util/Set;)V", at = @At("TAIL"))
    private void destiny2$installLazySourceHan(Set<FontOption> options, CallbackInfo callbackInfo) {
        if (!DESTINY_DIRECTOR_FONT.equals(name)) {
            return;
        }
        SourceHanGlyphProvider sourceHan = SourceHanGlyphProvider.create();
        if (sourceHan == null) {
            return;
        }
        ArrayList<GlyphProvider> providers = new ArrayList<>(activeProviders.size() + 1);
        providers.add(sourceHan);
        providers.addAll(activeProviders);
        activeProviders = List.copyOf(providers);
    }
}
