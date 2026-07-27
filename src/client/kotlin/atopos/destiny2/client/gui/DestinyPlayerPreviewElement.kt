package atopos.destiny2.client.gui

import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.world.entity.LivingEntity
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.min

/**
 * Runtime-only LDLib element that renders the live client player.
 *
 * The editable template owns this element's host and framing. A live entity is
 * deliberately never serialized into the UITemplate NBT.
 */
class DestinyPlayerPreviewElement(
    private val entitySupplier: () -> LivingEntity?
) : UIElement() {
    init {
        setId("equipment_player_runtime")
        setAllowHitTest(false)
    }

    override fun drawBackgroundAdditional(context: GUIContext) {
        super.drawBackgroundAdditional(context)
        val entity = entitySupplier() ?: return
        if (contentWidth <= 0f || contentHeight <= 0f) return

        // LDLib already owns the nested viewport scissor. The vanilla
        // FollowsMouse helper opens and then unconditionally closes its own
        // scissor, which breaks LDLib's transformed clipping stack. Invoke the
        // lower-level renderer directly and keep the parent clip intact.
        val entityScale = entity.scale.coerceAtLeast(0.01f)
        val renderScale = (min(contentWidth / 2.25f, contentHeight / 2.55f) / entityScale)
            .coerceAtLeast(24f)
        val centerX = contentX + contentWidth / 2f
        val centerY = contentY + contentHeight * 0.53f
        val bodyRotation = entity.yBodyRot
        val yaw = entity.yRot
        val pitch = entity.xRot
        val oldHeadYaw = entity.yHeadRotO
        val headYaw = entity.yHeadRot

        try {
            entity.yBodyRot = 180f
            entity.yRot = 180f
            entity.xRot = 0f
            entity.yHeadRot = 180f
            entity.yHeadRotO = 180f
            InventoryScreen.renderEntityInInventory(
                context.graphics,
                centerX,
                centerY,
                renderScale,
                Vector3f(0f, entity.bbHeight / 2f, 0f),
                Quaternionf().rotateZ(Math.PI.toFloat()),
                Quaternionf(),
                entity
            )
        } finally {
            entity.yBodyRot = bodyRotation
            entity.yRot = yaw
            entity.xRot = pitch
            entity.yHeadRotO = oldHeadYaw
            entity.yHeadRot = headYaw
        }
    }
}
