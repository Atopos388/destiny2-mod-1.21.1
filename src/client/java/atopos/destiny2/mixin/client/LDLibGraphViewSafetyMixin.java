package atopos.destiny2.mixin.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LDLib 2.5 can request an insertion index from a stale hierarchy selection
 * while the graph has no children yet, or request that a child already on
 * the canvas be added again. Both cases otherwise crash the entire client on
 * mouse release. Appending is the editor's intended fallback for an invalid
 * insertion point; duplicate drops are safely ignored.
 */
@Mixin(GraphView.class)
public abstract class LDLibGraphViewSafetyMixin {
    @Shadow(remap = false)
    private UIElement contentRoot;

    @Inject(
            method = "addEditorChild(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void destiny2$appendInvalidInsertion(UIElement child, int index, CallbackInfo ci) {
        if (contentRoot.getChildren().contains(child)) {
            ci.cancel();
            return;
        }

        int childCount = contentRoot.getChildren().size();
        if (index >= 0 && index > childCount) {
            contentRoot.addChild(child);
            ci.cancel();
        }
    }
}
