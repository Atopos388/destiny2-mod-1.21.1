package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.PerfectRetrogradeItemModel
import atopos.destiny2.common.item.PerfectRetrogradeItem
import software.bernie.geckolib.renderer.GeoItemRenderer
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemDisplayContext
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource

class PerfectRetrogradeItemRenderer : GeoItemRenderer<PerfectRetrogradeItem>(PerfectRetrogradeItemModel()), BuiltinItemRendererRegistry.DynamicItemRenderer {
    
    init {
        // 调整显示变换，使其在手中看起来正确
        // 这里需要根据模型实际情况微调
        // 示例值：
        // withScale(1.0f)
    }

    override fun render(stack: ItemStack, mode: ItemDisplayContext, matrices: PoseStack, vertexConsumers: MultiBufferSource, light: Int, overlay: Int) {
        if (mode == ItemDisplayContext.GUI) {
            GuiItemModelFit.render("PerfectRetrogradeItemRenderer:" + stack.item.toString() + ":" + stack.components.toString(), matrices, vertexConsumers) { pose, buffers ->
                renderUnfitted(stack, mode, pose, buffers, light, overlay)
            }
        } else renderUnfitted(stack, mode, matrices, vertexConsumers, light, overlay)
    }

    private fun renderUnfitted(stack: ItemStack, mode: ItemDisplayContext, matrices: PoseStack, vertexConsumers: MultiBufferSource, light: Int, overlay: Int) {
        // 隐藏/显示骨骼逻辑
        // 在非第一人称视角下隐藏 "shou" 骨骼
        // 使用 getGeoModel() 获取模型实例
        val model = this.geoModel
        // getBone 可能需要先获取 BakedModel，或者 GeoModel 提供了便捷方法
        // 注意：getBone 通常返回 Optional<GeoBone>
        val shouBone = model.getBone("shou")
        
        if (shouBone.isPresent) {
            val isFirstPerson = mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
            // 使用 setHidden 方法
            shouBone.get().setHidden(!isFirstPerson)
        }

        // GeckoLib 4.x/Fabric 通常会自动处理 display settings (如果 .json 文件正确)
        // 但如果需要手动调整，可以在这里进行矩阵变换。
        // 由于用户已经在 json 文件中定义了 display，我们尽量让 json 生效，或者仅做微调。
        // 如果我们在这里做了 pushPose/popPose 但没有应用变换，可能会导致重复变换或无效果。
        
        // 目前先移除硬编码的变换，让 .json 文件生效。
        // 如果 .json 文件不生效，可能是因为 GeoItemRenderer 默认行为差异。
        
        this.renderByItem(stack, mode, matrices, vertexConsumers, light, overlay)
    }
}
