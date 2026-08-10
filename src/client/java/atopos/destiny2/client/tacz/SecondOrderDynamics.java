// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.tacz;

/**
 * TaCZ Refabricated's second-order response filter, adapted at revision
 * 98ef5f4465bcbf185f6c570a178695d8929a2eac.
 */
public class SecondOrderDynamics {
    private final float k1;
    private final float k2;
    private final float k3;
    private float py;
    private float pyd;
    private float px;
    private long lastUpdateNanos;

    public SecondOrderDynamics(float f, float z, float r, float x0) {
        k1 = (float) (z / (Math.PI * f));
        k2 = (float) (1 / ((2 * Math.PI * f) * (2 * Math.PI * f)));
        k3 = (float) (r * z / (2 * Math.PI * f));
        py = px = x0;
        pyd = 0;
        lastUpdateNanos = System.nanoTime();
    }

    public float update(float x) {
        long now = System.nanoTime();
        float deltaSeconds = (now - lastUpdateNanos) / 1_000_000_000.0f;
        lastUpdateNanos = now;
        return update(x, deltaSeconds);
    }

    /**
     * Advances on the calling/render thread. The explicit overload keeps the
     * response deterministic in tests and avoids the old 6 ms worker loop
     * integrating a hard-coded 50 ms step.
     */
    public float update(float x, float deltaSeconds) {
        float t = Math.max(0.001f, Math.min(deltaSeconds, 0.05f));
        if (!Float.isFinite(py)) py = x;
        if (!Float.isFinite(pyd)) pyd = 0;

        float xd = (x - px) / t;
        float stableK2 = Math.max(k2, Math.max(
                t * t * 0.5f + t * k1 * 0.5f,
                t * k1
        ));
        py += t * pyd;
        pyd += t * (x + k3 * xd - py - k1 * pyd) / stableK2;
        px = x;
        return get();
    }

    public float get() {
        return Float.isFinite(py) ? py : 0;
    }

    public void stop() {
        // Retained for source compatibility; there is no background worker now.
    }
}
