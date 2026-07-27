package atopos.destiny2.common.mixin;

import atopos.destiny2.common.equipment.DestinyClassItemContainer;
import atopos.destiny2.common.equipment.DestinyClassItemSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import atopos.destiny2.common.item.DestinyClassItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryMenu.class)
public abstract class InventoryMenuClassItemMixin extends AbstractContainerMenu {
    protected InventoryMenuClassItemMixin() { super(null, 0); }

    @Unique private DestinyClassItemContainer destiny2$classItemContainer;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void destiny2$addClassItemSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        destiny2$classItemContainer = new DestinyClassItemContainer(owner);
        // Directly above vanilla's off-hand slot in the 176x166 inventory canvas.
        addSlot(new DestinyClassItemSlot(destiny2$classItemContainer, owner, 77, 44));
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void destiny2$quickMoveClassItem(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        InventoryMenu menu = (InventoryMenu)(Object)this;
        if (index == 46) {
            Slot slot = menu.getSlot(index);
            if (!slot.hasItem()) { cir.setReturnValue(ItemStack.EMPTY); return; }
            ItemStack source = slot.getItem();
            ItemStack copy = source.copy();
            if (!moveItemStackTo(source, 9, 45, false)) { cir.setReturnValue(ItemStack.EMPTY); return; }
            if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
            cir.setReturnValue(copy);
            return;
        }
        if (index >= 9 && index < 45 && menu.getSlot(index).getItem().getItem() instanceof DestinyClassItem) {
            Slot sourceSlot = menu.getSlot(index);
            ItemStack source = sourceSlot.getItem();
            ItemStack copy = source.copy();
            if (!menu.getSlot(46).mayPlace(source) || !moveItemStackTo(source, 46, 47, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            if (source.isEmpty()) sourceSlot.set(ItemStack.EMPTY); else sourceSlot.setChanged();
            cir.setReturnValue(copy);
        }
    }
}
