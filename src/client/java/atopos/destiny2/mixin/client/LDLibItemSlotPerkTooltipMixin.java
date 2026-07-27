package atopos.destiny2.mixin.client;

import atopos.destiny2.client.gear.DestinyPerkTooltipController;
import atopos.destiny2.client.gear.DestinyPerkTooltipComponent;
import atopos.destiny2.client.gear.DestinyEquipmentTooltipSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the migrated LDLib2 ItemSlot HOVER_TOOLTIPS pipeline when available. */
@Mixin(ItemSlot.class)
public abstract class LDLibItemSlotPerkTooltipMixin {
    @Shadow public abstract ItemStack getValue();
    @Shadow public abstract Slot getSlot();

    @Inject(method = "onHoverTooltips", at = @At("TAIL"), remap = false)
    private void destiny2$replaceItemSlotTooltip(UIEvent event, CallbackInfo callback) {
        if (DestinyPerkTooltipController.isPinned()) {
            event.hoverTooltips = HoverTooltips.empty();
            return;
        }
        ItemStack stack = getValue();
        // Director slots use one screen-level card rendered after the complete
        // LDLib tree. Suppress the native tooltip only when that card supports
        // the hovered stack; unrelated stacks retain their normal tooltip.
        if ((Object) this instanceof DestinyEquipmentTooltipSlot
                && DestinyPerkTooltipController.hasTooltipData(stack)) {
            event.hoverTooltips = HoverTooltips.empty();
            return;
        }
        Slot slot = getSlot();
        if (slot != null && Minecraft.getInstance().player != null) {
            int targetSlot = slot.index;
            if (slot.container == Minecraft.getInstance().player.getInventory()) {
                targetSlot = -slot.getContainerSlot() - 1;
            }
            DestinyPerkTooltipController.setHoverTarget(
                    Minecraft.getInstance().player.containerMenu.containerId,
                    targetSlot
            );
        } else {
            DestinyPerkTooltipController.setTransientHoverTarget();
        }
        DestinyPerkTooltipComponent component = DestinyPerkTooltipController.componentFor(stack);
        if (component != null) {
            event.hoverTooltips = HoverTooltips.empty().tooltipComponent(component).stack(stack);
        }
    }
}
