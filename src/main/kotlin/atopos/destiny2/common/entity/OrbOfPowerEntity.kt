package atopos.destiny2.common.entity

import atopos.destiny2.common.gear.ArmorModRuntime
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/** A physical world pickup. This is deliberately not backed by an ItemStack. */
class OrbOfPowerEntity(type: EntityType<out OrbOfPowerEntity>, level: Level) : Entity(type, level) {
    private var remainingTicks = OrbOfPowerRules.LIFETIME_TICKS

    constructor(level: Level, position: Vec3) : this(DestinyEntities.ORB_OF_POWER, level) {
        setPos(position.x, position.y, position.z)
        deltaMovement = Vec3(
            (random.nextDouble() - 0.5) * 0.12,
            0.16 + random.nextDouble() * 0.08,
            (random.nextDouble() - 0.5) * 0.12
        )
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        if (remainingTicks-- <= 0) {
            discard()
            return
        }

        applyPhysics()
        if (tickCount < OrbOfPowerRules.PICKUP_DELAY_TICKS) return

        val player = level().getEntitiesOfClass(
            net.minecraft.server.level.ServerPlayer::class.java,
            boundingBox.inflate(OrbOfPowerRules.PICKUP_RADIUS)
        ) { it.isAlive && !it.isSpectator }.minByOrNull { distanceToSqr(it) } ?: return

        if (ArmorModRuntime.onOrbPickup(player)) discard()
    }

    private fun applyPhysics() {
        val falling = deltaMovement.add(0.0, -0.04, 0.0)
        move(MoverType.SELF, falling)
        deltaMovement = if (onGround()) {
            Vec3(
                falling.x * 0.58,
                if (abs(falling.y) > 0.18) -falling.y * 0.12 else 0.0,
                falling.z * 0.58
            )
        } else {
            falling.scale(0.98)
        }
    }

    override fun isPickable(): Boolean = false

    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) = Unit

    override fun readAdditionalSaveData(tag: CompoundTag) {
        remainingTicks = tag.getInt("RemainingTicks").takeIf { it > 0 } ?: OrbOfPowerRules.LIFETIME_TICKS
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putInt("RemainingTicks", remainingTicks)
    }

    companion object {
        fun spawn(level: ServerLevel, position: Vec3): OrbOfPowerEntity {
            val orb = OrbOfPowerEntity(level, position)
            level.addFreshEntity(orb)
            return orb
        }
    }
}
