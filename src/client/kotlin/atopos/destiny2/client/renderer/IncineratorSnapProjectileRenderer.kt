package atopos.destiny2.client.renderer

import atopos.destiny2.client.model.IncineratorSnapProjectileModel
import atopos.destiny2.common.entity.IncineratorSnapProjectile
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import software.bernie.geckolib.renderer.GeoEntityRenderer
import kotlin.math.atan2
import kotlin.math.sqrt

class IncineratorSnapProjectileRenderer(renderManager: EntityRendererProvider.Context) :
    GeoEntityRenderer<IncineratorSnapProjectile>(renderManager, IncineratorSnapProjectileModel()) {
    init {
        shadowRadius = 0.0f
    }

    override fun getRenderType(
        animatable: IncineratorSnapProjectile,
        texture: ResourceLocation,
        bufferSource: MultiBufferSource?,
        partialTick: Float
    ): RenderType = RenderType.entityTranslucentEmissive(texture)

    override fun applyRotations(
        animatable: IncineratorSnapProjectile,
        poseStack: PoseStack,
        ageInTicks: Float,
        rotationYaw: Float,
        partialTick: Float,
        nativeScale: Float
    ) {
        val velocity = animatable.deltaMovement
        val horizontal = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        val yaw = Math.toDegrees(atan2(velocity.x, velocity.z)).toFloat()
        val pitch = Math.toDegrees(atan2(velocity.y, horizontal)).toFloat()
        // The fireball geometry points forward along local -Z; the two tail
        // cubes and trail locator sit on +Z.  The velocity yaw is defined for
        // local +Z, so turn the model half a revolution to keep its tail behind.
        // Local -Z gains positive Y under a positive X rotation, so the model
        // pitch uses velocity.y directly to follow the trajectory tangent.
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f))
        poseStack.mulPose(Axis.XP.rotationDegrees(pitch))
    }
}
