package atopos.destiny2.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenClassItemMixin extends AbstractContainerScreen<InventoryMenu> {
    protected InventoryScreenClassItemMixin() { super(null, null, null); }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void destiny2$drawClassItemSlot(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        int x = leftPos + 76;
        int y = topPos + 43;
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF151A20);
        // Small class-item chevron behind an empty slot.
        graphics.fill(x + 7, y + 5, x + 11, y + 6, 0xFF8FA9C2);
        graphics.fill(x + 6, y + 6, x + 12, y + 8, 0xFF536A80);
        graphics.fill(x + 8, y + 8, x + 10, y + 13, 0xFF8FA9C2);
    }
}
