package atopos.destiny2.mixin.client

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.font.FontSet
import net.minecraft.resources.ResourceLocation
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.gen.Invoker

@Mixin(Font::class)
interface FontAccessor {
    @Invoker("getFontSet")
    fun `destiny2$getFontSet`(id: ResourceLocation): FontSet
}
