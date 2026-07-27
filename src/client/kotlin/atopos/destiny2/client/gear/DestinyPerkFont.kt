package atopos.destiny2.client.gear

import atopos.destiny2.client.gui.DestinyNavigationTemplate
import atopos.destiny2.mixin.client.FontAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font

/** Uses the bundled Director typeface without replacing Minecraft's global font. */
object DestinyPerkFont {
    private val instance: Font by lazy(LazyThreadSafetyMode.NONE) {
        Font(
            {
                (Minecraft.getInstance().font as FontAccessor)
                    .`destiny2$getFontSet`(DestinyNavigationTemplate.DIRECTOR_FONT)
            },
            false
        )
    }

    fun get(): Font = instance
}
