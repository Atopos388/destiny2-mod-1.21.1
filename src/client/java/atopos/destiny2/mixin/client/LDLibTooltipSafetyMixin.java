package atopos.destiny2.mixin.client;

import atopos.destiny2.client.gear.DestinyPerkTooltipComponent;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/** Prevents the Fabric LDLib2 port from crashing on unsupported 1.21.1 tooltip components. */
@Mixin(DrawerHelper.class)
public abstract class LDLibTooltipSafetyMixin {
    @Inject(method = "drawTooltip", at = @At("HEAD"), cancellable = true)
    private static void destiny2$renderSafeTooltip(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            List<Component> lines,
            ItemStack stack,
            TooltipComponent component,
            Font font,
            CallbackInfo callback
    ) {
        if (component instanceof DestinyPerkTooltipComponent) {
            graphics.renderTooltip(font, lines, Optional.of(component), mouseX, mouseY);
        } else {
            graphics.renderTooltip(font, lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
        }
        callback.cancel();
    }
}
