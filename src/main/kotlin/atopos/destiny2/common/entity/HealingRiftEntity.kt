package atopos.destiny2.common.entity

import atopos.destiny2.common.effect.DestinyStatusRules
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import java.util.UUID

/**
 * Warlock healing rift. The server owns healing; the client owns the visual aura.
 */
class HealingRiftEntity(
    entityType: EntityType<*>,
    level: Level
) : Entity(entityType, level) {

    constructor(entityType: EntityType<*>, level: Level, x: Double, y: Double, z: Double, owner: LivingEntity?) : this(entityType, level) {
        this.setPos(x, y, z)
        this.owner = owner
    }

    private var duration = 300
    var owner: LivingEntity? = null
    private var ownerUuid: UUID? = null

    override fun tick() {
        super.tick()

        if (this.level().isClientSide) {
            return
        }

        val savedOwner = ownerUuid
        if (owner == null && savedOwner != null) {
            owner = (level() as? net.minecraft.server.level.ServerLevel)?.getEntity(savedOwner) as? LivingEntity
        }

        if (duration-- <= 0) {
            this.discard()
            return
        }

        if (duration % 20 == 0) {
            val radius = 6.0
            val entities = this.level().getEntities(this, AABB(
                this.x - radius, this.y - 1, this.z - radius,
                this.x + radius, this.y + 2, this.z + radius
            ))

            for (entity in entities) {
                val dx = entity.x - this.x
                val dz = entity.z - this.z
                if (entity is Player && dx * dx + dz * dz <= radius * radius) {
                    entity.heal(4.0f)
                    DestinyStatusRules.applyRestoration(entity, 70)
                }
            }
        }
    }

    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) {}
    override fun readAdditionalSaveData(compound: CompoundTag) {
        duration = compound.getInt("Duration")
        ownerUuid = if (compound.hasUUID("Owner")) compound.getUUID("Owner") else null
    }
    override fun addAdditionalSaveData(compound: CompoundTag) {
        compound.putInt("Duration", duration)
        (owner?.uuid ?: ownerUuid)?.let { compound.putUUID("Owner", it) }
    }

}
