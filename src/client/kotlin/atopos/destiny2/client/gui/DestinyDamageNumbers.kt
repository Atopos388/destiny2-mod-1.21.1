package atopos.destiny2.client.gui

import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.slf4j.LoggerFactory
import org.joml.Matrix4f
import org.joml.Vector4f
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlin.random.Random

object DestinyDamageNumbers {
    private val logger = LoggerFactory.getLogger("DestinyDamageNumbers")
    private val loggedFirstReception = AtomicBoolean(false)
    private val viewMatrix = Matrix4f()
    private val projectionMatrix = Matrix4f()
    private var cameraPosition = Vec3.ZERO
    private var hasProjection = false

    private data class FloatingNumber(
        val text: String,
        val precision: Boolean,
        var previousPosition: Vec3,
        var position: Vec3,
        var velocity: Vec3,
        var age: Int = 0
    )

    private val active = mutableListOf<FloatingNumber>()

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.level == null) {
                active.clear()
                return@register
            }
            active.forEach { number ->
                number.previousPosition = number.position
                number.position = number.position.add(number.velocity)
                number.velocity = Vec3(number.velocity.x * 0.90, number.velocity.y * 0.88, number.velocity.z * 0.90)
                number.age++
            }
            active.removeIf { it.age >= LIFETIME_TICKS }
        }

        WorldRenderEvents.START.register { context ->
            viewMatrix.set(context.positionMatrix())
            projectionMatrix.set(context.projectionMatrix())
            cameraPosition = context.camera().position
            hasProjection = true
        }

        HudRenderCallback.EVENT.register { graphics, tickCounter ->
            if (!hasProjection || active.isEmpty()) return@register
            val client = Minecraft.getInstance()
            val font = client.font
            val partialTick = tickCounter.getGameTimeDeltaPartialTick(true)

            active.forEach { number ->
                val worldPosition = number.previousPosition.lerp(number.position, partialTick.toDouble())
                val relative = worldPosition.subtract(cameraPosition)
                val projected = Vector4f(relative.x.toFloat(), relative.y.toFloat(), relative.z.toFloat(), 1.0f)
                    .mul(viewMatrix)
                    .mul(projectionMatrix)
                if (projected.w <= 0.001f) return@forEach

                val ndcX = projected.x / projected.w
                val ndcY = projected.y / projected.w
                if (ndcX !in -1.15f..1.15f || ndcY !in -1.15f..1.15f) return@forEach

                val screenX = (ndcX * 0.5f + 0.5f) * graphics.guiWidth()
                val screenY = (0.5f - ndcY * 0.5f) * graphics.guiHeight()
                val fade = if (number.age < FADE_START_TICK) {
                    1.0f
                } else {
                    1.0f - (number.age - FADE_START_TICK + partialTick) / (LIFETIME_TICKS - FADE_START_TICK)
                }.coerceIn(0.0f, 1.0f)
                val alpha = (fade * 255.0f).roundToInt().coerceIn(0, 255)
                val rgb = if (number.precision) PRECISION_RGB else NORMAL_RGB
                val color = (alpha shl 24) or rgb
                val pop = Mth.lerp((number.age / 4.0f).coerceIn(0.0f, 1.0f), 1.28f, 1.0f)
                val scale = pop * if (number.precision) 1.15f else 1.0f

                graphics.pose().pushPose()
                graphics.pose().translate(screenX, screenY, 200.0f)
                graphics.pose().scale(scale, scale, 1.0f)
                graphics.drawString(font, number.text, -font.width(number.text) / 2, 0, color, true)
                graphics.pose().popPose()
            }
        }
    }

    fun spawn(payload: DestinyNetworking.DamageNumberPayload) {
        val amount = payload.amount.roundToInt().coerceAtLeast(1)
        val origin = Vec3(
            payload.x + Random.nextDouble(-0.16, 0.16),
            payload.y + Random.nextDouble(-0.04, 0.12),
            payload.z + Random.nextDouble(-0.16, 0.16)
        )
        val velocity = Vec3(
            Random.nextDouble(-0.018, 0.018),
            Random.nextDouble(0.035, 0.048),
            Random.nextDouble(-0.018, 0.018)
        )
        active += FloatingNumber(amount.toString(), payload.precision, origin, origin, velocity)
        if (loggedFirstReception.compareAndSet(false, true)) {
            logger.info(
                "Received first damage number: amount={}, precision={}, position=({}, {}, {})",
                payload.amount,
                payload.precision,
                payload.x,
                payload.y,
                payload.z
            )
        }
    }

    private const val LIFETIME_TICKS = 28
    private const val FADE_START_TICK = 14
    private const val NORMAL_RGB = 0xF4F7FB
    private const val PRECISION_RGB = 0xFF9D28
}
