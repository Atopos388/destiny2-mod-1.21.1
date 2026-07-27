// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.tacz;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * TaCZ Refabricated's second-order response filter, adapted at revision
 * 98ef5f4465bcbf185f6c570a178695d8929a2eac.
 */
public class SecondOrderDynamics {
    public static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(15, Thread::new);

    static {
        for (int i = 0; i < 15; i++) {
            executorService.execute(() -> {
            });
        }
    }

    private final float k1;
    private final float k2;
    private final float k3;
    private float py;
    private float pyd;
    private float px;
    private float target;
    private boolean stop = false;

    public SecondOrderDynamics(float f, float z, float r, float x0) {
        k1 = (float) (z / (Math.PI * f));
        k2 = (float) (1 / ((2 * Math.PI * f) * (2 * Math.PI * f)));
        k3 = (float) (r * z / (2 * Math.PI * f));
        py = px = x0;
        pyd = 0;
        target = x0;
        executorService.execute(this::update);
    }

    public float update(float x) {
        target = x;
        return get();
    }

    public float get() {
        if (Float.isNaN(py)) py = 0;
        if (Float.isNaN(pyd)) pyd = 0;
        return py + 0.05f * pyd;
    }

    public void stop() {
        this.stop = true;
    }

    private void update() {
        while (!stop) {
            if (Float.isNaN(py)) py = 0;
            if (Float.isNaN(pyd)) pyd = 0;
            float t = 0.05f;
            float xd = (target - px) / t;
            float y = py + t * pyd;
            pyd = pyd + t * (px + k3 * xd - py - k1 * pyd) / k2;
            px = target;
            py = y;
            try {
                Thread.sleep(6);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
