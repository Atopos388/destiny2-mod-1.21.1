package atopos.destiny2.common.weapon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaczWeaponAnimationContractTest {
    @Test
    fun `full workflow requires camera and ads constraint bones`() {
        assertTrue(TaczWeaponAnimationContract.essentialBones.containsAll(
            setOf(
                TaczWeaponAnimationContract.ROOT_BONE,
                TaczWeaponAnimationContract.CAMERA_BONE,
                TaczWeaponAnimationContract.CONSTRAINT_BONE
            )
        ))
    }

    @Test
    fun `essential and optional animation names never overlap`() {
        assertTrue(TaczWeaponAnimationContract.essentialAnimations.intersect(
            TaczWeaponAnimationContract.optionalAnimations
        ).isEmpty())
        assertEquals("shoot", TaczWeaponAnimationContract.SHOOT)
        assertEquals("reload_empty", TaczWeaponAnimationContract.RELOAD_EMPTY)
    }
}
