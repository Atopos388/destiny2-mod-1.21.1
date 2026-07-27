// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.weapon

import net.minecraft.world.phys.Vec3
import kotlin.math.cos

/** Server-side validation for the client camera direction used to launch a kinetic bullet. */
object TaczProjectileDirection {
    private val MIN_VALID_DIRECTION_DOT = cos(Math.toRadians(25.0))

    fun validated(serverDirection: Vec3, clientDirection: Vec3): Vec3 {
        if (!clientDirection.x.isFinite() || !clientDirection.y.isFinite() || !clientDirection.z.isFinite()) {
            return serverDirection.normalize()
        }
        val lengthSquared = clientDirection.lengthSqr()
        if (lengthSquared < 0.25 || lengthSquared > 2.25) return serverDirection.normalize()
        val client = clientDirection.normalize()
        val server = serverDirection.normalize()
        return if (client.dot(server) >= MIN_VALID_DIRECTION_DOT) client else server
    }
}
