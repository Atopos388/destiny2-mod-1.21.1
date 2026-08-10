package atopos.destiny2.mixin.client

import atopos.destiny2.client.cinematic.CinematicCameraClient
import atopos.destiny2.client.camera.VoidHunterSuperCameraClient
import atopos.destiny2.client.camera.ThunderclapCameraClient
import atopos.destiny2.client.combat.HunterMeleeFirstPersonClient
import atopos.destiny2.client.weapon.DestinyWeaponAimClient
import net.minecraft.client.Camera
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.Unique
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(Camera::class)
abstract class CameraMixin {

    @Shadow private lateinit var position: Vec3
    @Shadow private var yRot: Float = 0f
    @Shadow private var xRot: Float = 0f
    @Shadow @Final private lateinit var rotation: Quaternionf
    @Shadow @Final private lateinit var forwards: Vector3f
    @Shadow @Final private lateinit var up: Vector3f
    @Shadow @Final private lateinit var left: Vector3f

    @Shadow protected abstract fun setPosition(pos: Vec3)
    @Shadow protected abstract fun setRotation(yRot: Float, xRot: Float)

    @Unique private var d2Initialized = false
    @Unique private var d2WasDetached = false
    @Unique private var d2IsTransitioning = false
    @Unique private var d2TransitionStart = 0L
    
    // 过渡起点
    @Unique private lateinit var d2StartPos: Vec3
    @Unique private var d2StartYRot = 0f
    @Unique private var d2StartXRot = 0f
    
    // 上一帧的渲染位置 (用于无缝衔接)
    @Unique private lateinit var d2PrevPos: Vec3
    @Unique private var d2PrevYRot = 0f
    @Unique private var d2PrevXRot = 0f
    
    @Unique private val TRANSITION_DURATION = 300.0f // 过渡时间 (毫秒)

    // 1. 在原版计算之前，保存上一帧的最终位置
    @Inject(method = ["setup"], at = [At("HEAD")])
    private fun onSetupHead(level: BlockGetter, entity: Entity, detached: Boolean, mirrored: Boolean, partialTick: Float, ci: CallbackInfo) {
        // 在第一次运行时初始化
        if (::position.isInitialized) {
            this.d2PrevPos = this.position
            this.d2PrevYRot = this.yRot
            this.d2PrevXRot = this.xRot
        }
    }

    // 2. 在原版计算之后，检查是否需要过渡并覆盖位置
    @Inject(method = ["setup"], at = [At("RETURN")])
    private fun onSetupReturn(level: BlockGetter, entity: Entity, detached: Boolean, mirrored: Boolean, partialTick: Float, ci: CallbackInfo) {
        if (!d2Initialized) {
            d2WasDetached = detached
            d2Initialized = true
            d2ApplyWeaponCamera(entity, detached, partialTick)
            return
        }

        // 检测视角变化 (第一人称 <-> 第三人称)
        if (detached != d2WasDetached) {
            if (VoidHunterSuperCameraClient.shouldUseInstantPerspectiveChange() ||
                ThunderclapCameraClient.shouldUseInstantPerspectiveChange()
            ) {
                d2IsTransitioning = false
                d2WasDetached = detached
                d2ApplyWeaponCamera(entity, detached, partialTick)
                return
            }
            d2IsTransitioning = true
            d2TransitionStart = System.currentTimeMillis()
            
            // 从上一帧实际渲染的位置开始过渡
            // 如果上一帧位置还没初始化，就用当前的
            if (::d2PrevPos.isInitialized) {
                d2StartPos = d2PrevPos
                d2StartYRot = d2PrevYRot
                d2StartXRot = d2PrevXRot
            } else {
                d2StartPos = this.position
                d2StartYRot = this.yRot
                d2StartXRot = this.xRot
            }
            
            d2WasDetached = detached
        }

        if (d2IsTransitioning) {
            val progress = (System.currentTimeMillis() - d2TransitionStart) / TRANSITION_DURATION
            
            if (progress >= 1.0f) {
                d2IsTransitioning = false
                d2ApplyWeaponCamera(entity, detached, partialTick)
                return // 过渡结束，使用原版计算的目标位置
            }

            // 计算插值进度
            val t = progress.toFloat()

            // 插值位置: startPos -> targetPos (原版计算出的当前帧目标)
            val targetPos = this.position
            val newPos = d2StartPos.lerp(targetPos, t.toDouble())
            this.setPosition(newPos)

            // 插值旋转
            val targetYRot = this.yRot
            val targetXRot = this.xRot
            
            val newYRot = d2RotLerp(t, d2StartYRot, targetYRot)
            val newXRot = Mth.lerp(t, d2StartXRot, targetXRot)
            
            this.setRotation(newYRot, newXRot)
        }

        d2ApplyWeaponCamera(entity, detached, partialTick)
    }

    @Unique
    private fun d2ApplyWeaponCamera(entity: Entity, detached: Boolean, partialTick: Float) {
        val cinematic = CinematicCameraClient.currentPose(partialTick)
        if (cinematic != null) {
            this.setPosition(cinematic.position)
            this.setRotation(cinematic.yaw, cinematic.pitch)
            d2ApplyRoll(cinematic.roll)
            return
        }
        val thunderclap = ThunderclapCameraClient.currentPose(partialTick)
        if (thunderclap != null) {
            this.setPosition(thunderclap.position)
            this.setRotation(thunderclap.yaw, thunderclap.pitch)
            d2ApplyRoll(thunderclap.roll)
            return
        }
        val localPlayer = net.minecraft.client.Minecraft.getInstance().player
        if (entity === localPlayer && detached) {
            val rightOffset = VoidHunterSuperCameraClient.rightOffsetBlocks()
            if (rightOffset != 0.0) {
                this.setPosition(
                    this.position.add(
                        -left.x * rightOffset,
                        -left.y * rightOffset,
                        -left.z * rightOffset
                    )
                )
            }
            return
        }
        if (detached || entity !== localPlayer) return

        val hunterMeleeCamera = HunterMeleeFirstPersonClient.cameraTransform(partialTick)
        if (hunterMeleeCamera != null) {
            if (hunterMeleeCamera.pitch != 0.0f || hunterMeleeCamera.yaw != 0.0f) {
                this.setRotation(
                    this.yRot + hunterMeleeCamera.yaw,
                    this.xRot + hunterMeleeCamera.pitch
                )
            }
            d2ApplyRoll(hunterMeleeCamera.roll)
            if (hunterMeleeCamera.positionPixels != Vec3.ZERO) {
                val position = hunterMeleeCamera.positionPixels.scale(1.0 / 16.0)
                val worldOffset = Vec3(
                    (-left.x * position.x) + (up.x * position.y) + (forwards.x * position.z),
                    (-left.y * position.x) + (up.y * position.y) + (forwards.y * position.z),
                    (-left.z * position.x) + (up.z * position.y) + (forwards.z * position.z)
                )
                this.setPosition(this.position.add(worldOffset))
            }
            return
        }

        val animation = DestinyWeaponAimClient.cameraTransform(partialTick)
        if (animation.pitch != 0.0f || animation.yaw != 0.0f) {
            this.setRotation(this.yRot + animation.yaw, this.xRot + animation.pitch)
        }

        d2ApplyRoll(animation.roll)

        if (animation.position != Vec3.ZERO) {
            // Blockbench camera X points right, while Camera exposes its left vector.
            val worldOffset = Vec3(
                (-left.x * animation.position.x) + (up.x * animation.position.y) + (forwards.x * animation.position.z),
                (-left.y * animation.position.x) + (up.y * animation.position.y) + (forwards.y * animation.position.z),
                (-left.z * animation.position.x) + (up.z * animation.position.y) + (forwards.z * animation.position.z)
            )
            this.setPosition(this.position.add(worldOffset))
        }
    }

    @Unique
    private fun d2ApplyRoll(roll: Float) {
        if (roll == 0.0f) return
        val radians = roll * Mth.DEG_TO_RAD
        this.rotation.rotateZ(radians)
        this.up.rotateAxis(radians, this.forwards.x, this.forwards.y, this.forwards.z)
        this.left.rotateAxis(radians, this.forwards.x, this.forwards.y, this.forwards.z)
    }
    
    // 处理角度循环 (例如 350度 -> 10度 应该是 +20度 而不是 -340度)
    @Unique
    private fun d2RotLerp(t: Float, start: Float, end: Float): Float {
        var diff = end - start
        while (diff < -180.0f) diff += 360.0f
        while (diff >= 180.0f) diff -= 360.0f
        return start + t * diff
    }
}
