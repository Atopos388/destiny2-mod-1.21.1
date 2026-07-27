package atopos.destiny2.mixin.client;

import atopos.destiny2.client.LDLibChineseText;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * UIHierarchy builds the component library through TreeBuilder.Menu.  Changing
 * the node label here is the final display path used by every nested submenu.
 */
@Mixin(TreeBuilder.Menu.class)
public abstract class LDLibTreeMenuChineseMixin {
    @ModifyVariable(
            method = "branch(Ljava/lang/String;Ljava/util/function/Consumer;)Lcom/lowdragmc/lowdraglib2/gui/util/TreeBuilder$Menu;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static String destiny2$translateBranchLabel(String original) {
        return LDLibChineseText.translate(original);
    }

    @ModifyVariable(
            method = "leaf(Ljava/lang/String;Ljava/lang/Runnable;)Lcom/lowdragmc/lowdraglib2/gui/util/TreeBuilder$Menu;",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static String destiny2$translateLeafLabel(String original) {
        return LDLibChineseText.translate(original);
    }
}
