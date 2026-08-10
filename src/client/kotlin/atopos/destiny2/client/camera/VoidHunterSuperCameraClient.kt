// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.camera

import atopos.destiny2.Destiny2MODClient
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Local shoulder camera used only for the opening of the Shadowshot cast. */
object VoidHunterSuperCameraClient {
    private data class Session(
        val token: Long,
        val startedAtNanos: Long,
        val returnAtNanos: Long,
        val rightOffsetBlocks: Double
    )

    private var session: Session? = null
    private var suppressPerspectiveTransitionUntilNanos = 0L
    private var nextToken = 0L

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear() }
    }

    fun start(durationMs: Long, rightOffsetBlocks: Double) {
        val client = Minecraft.getInstance()
        if (client.player == null || client.level == null) return

        val now = System.nanoTime()
        val token = ++nextToken
        session = Session(
            token = token,
            startedAtNanos = now,
            returnAtNanos = now + durationMs.coerceAtLeast(1L) * NANOS_PER_MILLISECOND,
            rightOffsetBlocks = rightOffsetBlocks.coerceIn(-1.25, 1.25)
        )
        suppressPerspectiveTransitionUntilNanos = 0L

        // Shadowshot owns the camera during its opening, so cancel an older generic reset.
        Destiny2MODClient.cameraResetTime = -1L
        Destiny2MODClient.shouldResetToFirstPerson = false
        client.options.cameraType = CameraType.THIRD_PERSON_BACK

        // Client ticks are only 50 ms apart. Post the authored 0.92 s cut back to
        // the render thread so it can land on the next frame instead of a coarse tick.
        CompletableFuture.delayedExecutor(durationMs, TimeUnit.MILLISECONDS).execute {
            client.execute { finish(client, token) }
        }
    }

    fun rightOffsetBlocks(): Double {
        val active = session ?: return 0.0
        val now = System.nanoTime()
        if (now >= active.returnAtNanos) return 0.0

        val elapsedMs = (now - active.startedAtNanos).toDouble() / NANOS_PER_MILLISECOND
        val t = (elapsedMs / SHOULDER_EASE_IN_MS).coerceIn(0.0, 1.0)
        val eased = t * t * (3.0 - 2.0 * t)
        return active.rightOffsetBlocks * eased
    }

    fun shouldUseInstantPerspectiveChange(): Boolean =
        session != null || System.nanoTime() < suppressPerspectiveTransitionUntilNanos

    private fun tick(client: Minecraft) {
        val active = session ?: return
        if (client.player == null || client.level == null) {
            clear()
            return
        }

        val now = System.nanoTime()
        if (now < active.returnAtNanos) {
            if (client.options.cameraType != CameraType.THIRD_PERSON_BACK) {
                client.options.cameraType = CameraType.THIRD_PERSON_BACK
            }
            return
        }

        // 0.92 s is the authored camera cut. Do not let the generic 300 ms camera
        // interpolation extend the third-person shot beyond that point.
        finish(client, active.token)
    }

    private fun finish(client: Minecraft, token: Long) {
        val active = session ?: return
        if (active.token != token) return
        session = null
        suppressPerspectiveTransitionUntilNanos =
            System.nanoTime() + RETURN_GUARD_MS * NANOS_PER_MILLISECOND
        client.options.cameraType = CameraType.FIRST_PERSON
        Destiny2MODClient.firstPersonReturnVisualUntil =
            System.currentTimeMillis() + FIRST_PERSON_HAND_TRANSITION_MS
    }

    private fun clear() {
        session = null
        suppressPerspectiveTransitionUntilNanos = 0L
        nextToken++
    }

    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val SHOULDER_EASE_IN_MS = 120.0
    private const val RETURN_GUARD_MS = 250L
    private const val FIRST_PERSON_HAND_TRANSITION_MS = 300L
}
