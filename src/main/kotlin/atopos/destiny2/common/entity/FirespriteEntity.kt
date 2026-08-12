package atopos.destiny2.common.entity

import atopos.destiny2.common.aspect.SolarWarlockFragmentRuntime
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Server-authoritative Solar subclass pickup. It is not an inventory item. */
class FirespriteEntity(type: EntityType<out FirespriteEntity>, level: Level) : Entity(type, level) {
    private var ownerUuid: UUID? = null
    private var remainingTicks = FirespriteRules.LIFETIME_TICKS

    constructor(level: Level, position: Vec3, owner: ServerPlayer?) : this(DestinyEntities.FIRESPRITE, level) {
        ownerUuid = owner?.uuid
        setPos(position.x, position.y, position.z)
        // Destiny's Firesprite forms out of a ground spark. It does not hop,
        // fall or bounce like a dropped item.
        deltaMovement = Vec3.ZERO
        isNoGravity = true
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return

        val pickupTicks = pickupAnimationTicks
        if (pickupTicks > 0) {
            if (pickupTicks >= PICKUP_ANIMATION_TICKS) discard()
            else entityData.set(DATA_PICKUP_TICKS, pickupTicks + 1)
            return
        }

        if (remainingTicks-- <= 0) {
            discard()
            return
        }

        if (tickCount < FirespriteRules.PICKUP_DELAY_TICKS) return

        val player = level().getEntitiesOfClass(
            ServerPlayer::class.java,
            boundingBox.inflate(FirespriteRules.PICKUP_RADIUS)
        ) { candidate ->
            candidate.isAlive && !candidate.isSpectator &&
                (ownerUuid == null || candidate.uuid == ownerUuid)
        }.minByOrNull { distanceToSqr(it) } ?: return

        SolarWarlockFragmentRuntime.onFirespritePickup(player)
        deltaMovement = Vec3.ZERO
        entityData.set(DATA_PICKUP_TICKS, 1)
    }

    override fun isPickable(): Boolean = false

    val pickupAnimationTicks: Int
        get() = entityData.get(DATA_PICKUP_TICKS)

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_PICKUP_TICKS, 0)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        ownerUuid = if (tag.hasUUID("Owner")) tag.getUUID("Owner") else null
        remainingTicks = tag.getInt("RemainingTicks").takeIf { it > 0 } ?: FirespriteRules.LIFETIME_TICKS
        entityData.set(DATA_PICKUP_TICKS, tag.getInt("PickupAnimationTicks").coerceAtLeast(0))
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        ownerUuid?.let { tag.putUUID("Owner", it) }
        tag.putInt("RemainingTicks", remainingTicks)
        tag.putInt("PickupAnimationTicks", pickupAnimationTicks)
    }

    companion object {
        const val PICKUP_ANIMATION_TICKS = 7
        private val DATA_PICKUP_TICKS: EntityDataAccessor<Int> =
            SynchedEntityData.defineId(FirespriteEntity::class.java, EntityDataSerializers.INT)

        fun spawn(level: ServerLevel, position: Vec3, owner: ServerPlayer?): FirespriteEntity {
            val firesprite = FirespriteEntity(level, position, owner)
            level.addFreshEntity(firesprite)
            return firesprite
        }
    }
}
