package atopos.destiny2.mixin.client;

import atopos.destiny2.common.equipment.DestinyClassItemSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Keeps the extra class-item slot inside the compact creative inventory layout. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeClassItemSlotMixin extends
        AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {
    protected CreativeClassItemSlotMixin() { super(null, null, null); }

    @ModifyArgs(
            method = "selectTab",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$SlotWrapper;<init>(Lnet/minecraft/world/inventory/Slot;III)V"
            )
    )
    private void destiny2$positionClassItemSlot(Args args) {
        if (args.get(0) instanceof DestinyClassItemSlot) {
            // Empty socket immediately left of vanilla's off-hand slot (35, 20).
            args.set(2, 9);
            args.set(3, 20);
        }
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void destiny2$drawClassItemSocket(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY,
            CallbackInfo callback
    ) {
        if (!((CreativeModeInventoryScreen)(Object)this).isInventoryOpen()) return;
        int x = leftPos + 8;
        int y = topPos + 19;
        graphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF151A20);
        graphics.fill(x + 7, y + 5, x + 11, y + 6, 0xFF8FA9C2);
        graphics.fill(x + 6, y + 6, x + 12, y + 8, 0xFF536A80);
        graphics.fill(x + 8, y + 8, x + 10, y + 13, 0xFF8FA9C2);
    }
}
