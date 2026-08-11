package atopos.destiny2.common.ability

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared Destiny-style grenade launch calibration.
 *
 * Vanilla [Projectile.shootFromRotation] adds the thrower's full velocity and
 * its usual throwable values use visible random spread. That makes a sprinting
 * Guardian over-throw grenades and makes repeated throws land inconsistently.
 * These profiles keep a readable ballistic arc, very little spread, and only a
 * small amount of inherited movement while preserving each grenade archetype.
 */
object DestinyGrenadeThrow {
    enum class Profile(
        val speed: Float,
        val inaccuracy: Float,
        val pitchOffsetDegrees: Float,
        val movementInheritance: Double
    ) {
        /** Pulse, storm, vortex, solar and healing grenades. */
        AREA(1.10f, 0.05f, -2.5f, 0.25),

        /** Flashbang-style delayed grenades that may make one controlled ricochet. */
        FRAG(1.05f, 0.05f, -3.0f, 0.25),

        /** Lightning and fusion grenades where accurate surface placement matters. */
        ATTACHMENT(1.15f, 0.03f, -1.5f, 0.20),

        /** Firebolt-style grenades whose effect acquires targets after impact. */
        SEEKING(1.10f, 0.03f, -2.0f, 0.20)
    }

    fun launch(projectile: Projectile, owner: LivingEntity, profile: Profile) {
        val pitch = Math.toRadians((owner.xRot + profile.pitchOffsetDegrees).toDouble())
        val yaw = Math.toRadians(owner.yRot.toDouble())
        val horizontal = cos(pitch)

        projectile.shoot(
            -sin(yaw) * horizontal,
            -sin(pitch),
            cos(yaw) * horizontal,
            profile.speed,
            profile.inaccuracy
        )

        val ownerMotion = owner.deltaMovement
        val inheritedY = if (owner.onGround()) 0.0 else ownerMotion.y * profile.movementInheritance
        projectile.deltaMovement = projectile.deltaMovement.add(
            ownerMotion.x * profile.movementInheritance,
            inheritedY,
            ownerMotion.z * profile.movementInheritance
        )
    }

    /**
     * A single, damped frag ricochet. Wall hits never receive artificial lift;
     * the old implementation added +0.08 Y and looked like a rubber ball.
     */
    fun fragRicochet(velocity: Vec3, surfaceNormal: Vec3): Vec3 {
        val normalSpeed = velocity.dot(surfaceNormal)
        val normalVelocity = surfaceNormal.scale(normalSpeed)
        val tangentVelocity = velocity.subtract(normalVelocity)
        return tangentVelocity.scale(FRAG_TANGENT_RETENTION)
            .subtract(normalVelocity.scale(FRAG_NORMAL_RESTITUTION))
    }

    const val FRAG_NORMAL_RESTITUTION = 0.26
    const val FRAG_TANGENT_RETENTION = 0.64
}
