package com.donquaan.dfast.core;

public final class CameraPath {
    private final double[] xs;
    private final double[] ys;
    private final double[] zs;
    private final float pitch;

    public CameraPath(double[] xs, double[] ys, double[] zs, float pitch) {
        if (xs.length < 4 || xs.length != ys.length || xs.length != zs.length) {
            throw new IllegalArgumentException("need at least 4 control points with matching coordinates");
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.zs = zs.clone();
        this.pitch = pitch;
    }

    public static CameraPath orbit(double centerX, double centerZ, double radius, double y, int points, float pitch) {
        double[] xs = new double[points];
        double[] ys = new double[points];
        double[] zs = new double[points];
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            xs[i] = centerX + radius * Math.cos(angle);
            ys[i] = y;
            zs[i] = centerZ + radius * Math.sin(angle);
        }
        return new CameraPath(xs, ys, zs, pitch);
    }

    public void sample(double u, double[] out) {
        int n = xs.length;
        double s = (u - Math.floor(u)) * n;
        int i = Math.min((int) s, n - 1);
        double t = s - i;
        int a = (i + n - 1) % n;
        int c = (i + 1) % n;
        int d = (i + 2) % n;
        out[0] = point(xs[a], xs[i], xs[c], xs[d], t);
        out[1] = point(ys[a], ys[i], ys[c], ys[d], t);
        out[2] = point(zs[a], zs[i], zs[c], zs[d], t);
        double dx = slope(xs[a], xs[i], xs[c], xs[d], t);
        double dz = slope(zs[a], zs[i], zs[c], zs[d], t);
        out[3] = Math.toDegrees(Math.atan2(-dx, dz));
        out[4] = pitch;
    }

    public static float unwrap(float previous, float next) {
        float delta = (next - previous) % 360.0F;
        if (delta >= 180.0F) {
            delta -= 360.0F;
        } else if (delta < -180.0F) {
            delta += 360.0F;
        }
        return previous + delta;
    }

    private static double point(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * (2.0 * p1 + (p2 - p0) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t * t
                + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t * t * t);
    }

    private static double slope(double p0, double p1, double p2, double p3, double t) {
        return 0.5 * ((p2 - p0) + 2.0 * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t
                + 3.0 * (3.0 * p1 - p0 - 3.0 * p2 + p3) * t * t);
    }
}
