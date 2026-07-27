package atopos.destiny2.mixin.client;

import atopos.destiny2.client.gear.DestinyPerkTooltipController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only registered Destiny gear tooltips in ordinary container screens. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerPerkTooltipMixin<T extends AbstractContainerMenu> {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void destiny2$renderPerkTooltip(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo callback) {
        if (DestinyPerkTooltipController.isPinned()) {
            // The pinned card is rendered by the screen after-render hook.
            // Suppress competing slot tooltips while that inspection surface is open.
            callback.cancel();
            return;
        }
        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
        if (screen instanceof CreativeModeInventoryScreen &&
                hoveredSlot.container == CreativeModeInventoryScreenAccessor.destiny2$getCreativeContainer()) {
            if (DestinyPerkTooltipController.renderTransient(
                    graphics, hoveredSlot.getItem(), mouseX, mouseY
            )) {
                callback.cancel();
            }
            return;
        }
        int targetSlot = hoveredSlot.index;
        if (Minecraft.getInstance().player != null &&
                hoveredSlot.container == Minecraft.getInstance().player.getInventory()) {
            targetSlot = -hoveredSlot.getContainerSlot() - 1;
        }
        if (DestinyPerkTooltipController.render(
                graphics,
                hoveredSlot.getItem(),
                mouseX,
                mouseY,
                screen.getMenu().containerId,
                targetSlot
        )) {
            callback.cancel();
        }
    }
}
