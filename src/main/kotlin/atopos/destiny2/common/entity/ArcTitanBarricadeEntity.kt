// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Networked marker for the procedural Towering Barricade renderer. */
class ArcTitanBarricadeEntity(
    type: EntityType<out ArcTitanBarricadeEntity>,
    level: Level
) : Entity(type, level) {
    private var duration = LIFETIME_TICKS
    private var ownerId: UUID? = null

    constructor(level: Level, position: Vec3, yaw: Float, ownerId: UUID) :
        this(DestinyEntities.ARC_TITAN_BARRICADE, level) {
        setPos(position)
        setYRot(yaw)
        yRotO = yaw
        this.ownerId = ownerId
    }

    override fun tick() {
        super.tick()
        noPhysics = true
        isNoGravity = true
        deltaMovement = Vec3.ZERO
        if (!level().isClientSide && duration-- <= 0) discard()
    }

    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) = Unit

    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putInt("Duration", duration)
        ownerId?.let { tag.putUUID("Owner", it) }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        duration = tag.getInt("Duration").takeIf { it > 0 } ?: LIFETIME_TICKS
        ownerId = if (tag.hasUUID("Owner")) tag.getUUID("Owner") else null
    }

    companion object {
        const val LIFETIME_TICKS = 20 * 20
    }
}
