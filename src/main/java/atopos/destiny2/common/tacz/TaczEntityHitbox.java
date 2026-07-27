// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.common.tacz;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Default TaCZ headshot rule adapted from EntityUtil#getHitResult.
 * Source revision: 98ef5f4465bcbf185f6c570a178695d8929a2eac.
 */
public final class TaczEntityHitbox {
    private TaczEntityHitbox() {
    }

    public static boolean isHeadshot(Entity entity, Vec3 hitPos) {
        Vec3 relativeHit = hitPos.subtract(entity.position());
        return isHeadshot(relativeHit.y, entity.getEyeHeight());
    }

    public static boolean isHeadshot(double relativeHitY, double eyeHeight) {
        return eyeHeight - 0.25 < relativeHitY && relativeHitY < eyeHeight + 0.25;
    }
}
