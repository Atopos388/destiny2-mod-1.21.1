package atopos.destiny2.common.weapon

import atopos.destiny2.common.network.DestinyNetworking
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/** Carries per-hit presentation metadata through LivingEntity.hurt without changing damage rules. */
object DamageNumberRuntime {
    private val logger = LoggerFactory.getLogger("DestinyDamageNumbers")
    private val loggedFirstDelivery = AtomicBoolean(false)

    data class HitContext(
        val targetId: Int,
        val amount: Float,
        val precision: Boolean,
        var delivered: Boolean = false
    )

    private val activeHit = ThreadLocal<HitContext?>()

    fun <T> withHit(
        attacker: ServerPlayer?,
        target: LivingEntity,
        amount: Float,
        precision: Boolean,
        action: () -> T
    ): T {
        val previous = activeHit.get()
        val context = HitContext(target.id, amount, precision)
        activeHit.set(context)
        return try {
            val result = action()
            // The LivingEntity mixin is the normal delivery path. Explicit weapon hits retain
            // this fallback so a custom/indirect DamageSource cannot silently lose the number.
            if (result == true && attacker != null && !context.delivered) {
                deliver(attacker, target, amount, precision)
            }
            result
        } finally {
            activeHit.set(previous)
        }
    }

    fun contextFor(target: LivingEntity): HitContext? = activeHit.get()?.takeIf { it.targetId == target.id }

    /**
     * Called from the common LivingEntity damage return hook, which also sees
     * vanilla melee and indirect ability damage. Fabric AFTER_DAMAGE did not
     * cover every active hurt path in this runtime, while explicit gun hits
     * only appeared to work because withHit had its own fallback.
     */
    fun afterDamage(target: LivingEntity, source: DamageSource, actualLoss: Float) {
        val context = contextFor(target)
        if (context?.delivered == true) return
        val attacker = source.entity as? ServerPlayer ?: return
        if (attacker === target) return
        val amount = context?.amount ?: actualLoss
        deliver(attacker, target, amount, context?.precision == true)
    }

    fun deliver(attacker: ServerPlayer, target: LivingEntity, amount: Float, precision: Boolean) {
        if (amount <= 0.0f || !amount.isFinite()) return
        contextFor(target)?.delivered = true
        ServerPlayNetworking.send(
            attacker,
            DestinyNetworking.DamageNumberPayload(
                target.x,
                target.y + target.bbHeight * 0.78,
                target.z,
                amount,
                precision
            )
        )
        if (loggedFirstDelivery.compareAndSet(false, true)) {
            logger.info(
                "Sent first damage number to {}: amount={}, precision={}, target={}",
                attacker.scoreboardName,
                amount,
                precision,
                target.type.descriptionId
            )
        }
    }
}
