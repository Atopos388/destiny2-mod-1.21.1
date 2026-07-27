package atopos.destiny2.common.entity

import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/** Visual anchor left behind when 抹除姓名 or a marked chain kill triggers. */
class ForgottenRemnantEntity(type: EntityType<out ForgottenRemnantEntity>, level: Level) : Entity(type, level) {
    private var remainingTicks = 80

    constructor(level: Level, position: Vec3, durationTicks: Int) : this(DestinyEntities.FORGOTTEN_REMNANT, level) {
        remainingTicks = durationTicks.coerceAtLeast(1)
        setPos(position.x, position.y + 0.12, position.z)
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) {
            if (tickCount % 2 == 0) {
                repeat(3) { index ->
                    val angle = tickCount * 0.24 + index * (Math.PI * 2.0 / 3.0)
                    val radius = 0.45 + index * 0.08
                    level().addParticle(
                        ParticleTypes.REVERSE_PORTAL,
                        x + cos(angle) * radius,
                        y + 0.18 + index * 0.11,
                        z + sin(angle) * radius,
                        0.0,
                        0.015,
                        0.0
                    )
                }
            }
            return
        }
        if (remainingTicks-- <= 0) discard()
    }

    override fun isPickable(): Boolean = false
    override fun defineSynchedData(builder: net.minecraft.network.syncher.SynchedEntityData.Builder) = Unit

    override fun readAdditionalSaveData(tag: CompoundTag) {
        remainingTicks = tag.getInt("RemainingTicks").coerceAtLeast(1)
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putInt("RemainingTicks", remainingTicks)
    }
}
