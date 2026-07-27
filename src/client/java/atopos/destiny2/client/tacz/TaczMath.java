// SPDX-License-Identifier: GPL-3.0-only
package atopos.destiny2.client.tacz;

/**
 * ADS math adapted from TaCZ Refabricated's MathUtil at revision
 * 98ef5f4465bcbf185f6c570a178695d8929a2eac.
 */
public final class TaczMath {
    private TaczMath() {
    }

    public static double magnificationToFovMultiplier(double magnification, double currentFov) {
        return magnificationToFov(magnification, currentFov) / currentFov;
    }

    public static double magnificationToFov(double magnification, double currentFov) {
        double currentTan = Math.tan(Math.toRadians(currentFov / 2));
        double newTan = currentTan / magnification;
        return Math.toDegrees(Math.atan(newTan)) * 2;
    }

    public static double fovToMagnification(double currentFov, double originFov) {
        return Math.tan(Math.toRadians(originFov / 2)) / Math.tan(Math.toRadians(currentFov / 2));
    }

    public static double zoomSensitivityRatio(double currentFov, double originFov, double coefficient) {
        return Math.atan(Math.tan(Math.toRadians(currentFov / 2)) * coefficient) /
                Math.atan(Math.tan(Math.toRadians(originFov / 2)) * coefficient);
    }
}
