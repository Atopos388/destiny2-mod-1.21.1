package atopos.destiny2.common.weapon

/**
 * Client presentation values for a ranged weapon's aim-down-sights state.
 * Model offsets are expressed in first-person render-space blocks, rotations
 * in degrees, and zoom as optical magnification (1.35 = 1.35x).
 */
data class WeaponAimProfile(
    val enabled: Boolean = true,
    val aimTimeSeconds: Float = 0.25f,
    val zoom: Float = 1.35f,
    val modelOffsetX: Double = 0.0,
    val modelOffsetY: Double = 0.0,
    val modelOffsetZ: Double = 0.0,
    val modelRotationX: Float = 0.0f,
    val modelRotationY: Float = 0.0f,
    val modelRotationZ: Float = 0.0f,
    val hipCameraAnimationScale: Float = 1.0f,
    val aimedCameraAnimationScale: Float = 0.72f,
    /** Use the renderer-independent circular optic fallback for built-in scopes. */
    val scopeOverlay: Boolean = false
) {
    companion object {
        val DISABLED = WeaponAimProfile(enabled = false, zoom = 1.0f)
    }
}
