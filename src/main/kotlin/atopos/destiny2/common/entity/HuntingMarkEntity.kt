package atopos.destiny2.common.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import java.util.UUID

/** 跟随被猎杀印记锁定目标的纯视觉实体。 */
class HuntingMarkEntity(type: EntityType<out HuntingMarkEntity>, level: Level) : Entity(type, level) {
    private var target: LivingEntity? = null
    private var targetUuid: UUID? = null
    private var remainingTicks = 100

    constructor(level: Level, target: LivingEntity, durationTicks: Int) : this(DestinyEntities.HUNTING_MARK, level) {
        this.target = target
        this.targetUuid = target.uuid
        this.remainingTicks = durationTicks
        followTarget()
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        val savedTarget = targetUuid
        if (target == null && savedTarget != null) {
            target = (level() as? net.minecraft.server.level.ServerLevel)?.getEntity(savedTarget) as? LivingEntity
        }
        if (remainingTicks-- <= 0 || target?.isAlive != true) {
            discard()
            return
        }
        followTarget()
    }

    private fun followTarget() {
        val entity = target ?: return
        setPos(entity.x, entity.y + entity.bbHeight + 0.45, entity.z)
    }

    fun refresh(durationTicks: Int) {
        remainingTicks = durationTicks.coerceAtLeast(1)
    }

    override fun isPickable(): Boolean = false
    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) = Unit
    override fun readAdditionalSaveData(tag: CompoundTag) {
        remainingTicks = tag.getInt("RemainingTicks")
        targetUuid = if (tag.hasUUID("Target")) tag.getUUID("Target") else null
    }
    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putInt("RemainingTicks", remainingTicks)
        targetUuid?.let { tag.putUUID("Target", it) }
    }
}
