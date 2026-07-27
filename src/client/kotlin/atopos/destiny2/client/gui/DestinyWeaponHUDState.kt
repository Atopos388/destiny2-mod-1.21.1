package atopos.destiny2.client.gui

import atopos.destiny2.common.network.DestinyNetworking
import atopos.destiny2.common.weapon.DestinyAmmoType
import atopos.destiny2.common.weapon.WeaponFireMode
import atopos.destiny2.common.weapon.WeaponReloadPhase
import atopos.destiny2.common.weapon.WeaponCrosshairProfile

object DestinyWeaponHUDState {
    data class Snapshot(
        val weaponId: String,
        val ammoType: DestinyAmmoType,
        val magazine: Int,
        val capacity: Int,
        val reserve: Int,
        val reloadRemaining: Int,
        val reloadTotal: Int,
        val reloadPhase: WeaponReloadPhase,
        val fireMode: WeaponFireMode,
        val chamberEmpty: Boolean,
        val boltRemaining: Int,
        val crosshair: WeaponCrosshairProfile,
        val receivedAt: Long
    )

    var snapshot: Snapshot? = null
        private set
    var precisionHitUntil = 0L
        private set
    var precisionDamage = 0f
        private set
    var hitMarkerUntil = 0L
        private set
    var killMarkerUntil = 0L
        private set
    var lastHitPrecision = false
        private set

    fun update(payload: DestinyNetworking.SyncWeaponStatePayload) {
        snapshot = if (payload.weaponId.isBlank()) null else Snapshot(
            payload.weaponId, payload.ammoType, payload.magazine, payload.capacity, payload.reserve,
            payload.reloadRemaining, payload.reloadTotal, payload.reloadPhase, payload.fireMode,
            payload.chamberEmpty, payload.boltRemaining, payload.crosshair, System.currentTimeMillis()
        )
    }

    fun precisionHit(payload: DestinyNetworking.PrecisionHitPayload) {
        precisionDamage = payload.damage
        precisionHitUntil = System.currentTimeMillis() + 220L
    }

    fun weaponHit(hit: Boolean, precision: Boolean, killed: Boolean) {
        if (!hit) return
        val now = System.currentTimeMillis()
        hitMarkerUntil = now + 300L
        if (killed) killMarkerUntil = now + 360L
        lastHitPrecision = precision
    }

    fun reloadProgress(state: Snapshot): Float {
        if (state.reloadTotal <= 0 || state.reloadRemaining <= 0) return 0f
        val elapsedTicks = ((System.currentTimeMillis() - state.receivedAt) / 50L).toInt()
        val remaining = (state.reloadRemaining - elapsedTicks).coerceAtLeast(0)
        return 1f - remaining.toFloat() / state.reloadTotal.toFloat()
    }

}
