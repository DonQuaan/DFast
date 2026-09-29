package com.donquaan.dfast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraPathTest {
    private final double[] pose = new double[5];

    @Test
    void passesThroughEveryControlPoint() {
        double[] xs = {0, 10, 20, 5, -3};
        double[] ys = {64, 70, 80, 75, 60};
        double[] zs = {0, 5, -5, 15, 8};
        CameraPath path = new CameraPath(xs, ys, zs, 30);
        for (int i = 0; i < xs.length; i++) {
            path.sample((double) i / xs.length, pose);
            assertEquals(xs[i], pose[0], 1e-9);
            assertEquals(ys[i], pose[1], 1e-9);
            assertEquals(zs[i], pose[2], 1e-9);
            assertEquals(30.0, pose[4], 1e-9);
        }
    }

    @Test
    void isClosedAndContinuousAcrossTheWrap() {
        CameraPath path = CameraPath.orbit(100, -50, 96, 140, 8, 25);
        double[] start = new double[5];
        path.sample(0.0, start);
        path.sample(1.0 - 1e-9, pose);
        assertEquals(start[0], pose[0], 1e-4);
        assertEquals(start[2], pose[2], 1e-4);
        path.sample(3.0, pose);
        assertEquals(start[0], pose[0], 1e-9);
        path.sample(-0.25, pose);
        double[] quarter = new double[5];
        path.sample(0.75, quarter);
        assertEquals(quarter[0], pose[0], 1e-9);
        assertEquals(quarter[2], pose[2], 1e-9);
    }

    @Test
    void orbitStaysCloseToTheCircle() {
        CameraPath path = CameraPath.orbit(0, 0, 96, 140, 8, 25);
        for (int i = 0; i < 400; i++) {
            path.sample(i / 400.0, pose);
            double r = Math.hypot(pose[0], pose[2]);
            assertTrue(Math.abs(r - 96) / 96 < 0.01, "radius " + r + " at step " + i);
            assertEquals(140.0, pose[1], 1e-9);
        }
    }

    @Test
    void yawFollowsMinecraftConvention() {
        CameraPath path = CameraPath.orbit(0, 0, 96, 140, 8, 25);
        path.sample(0.0, pose);
        assertEquals(0.0, pose[3], 1.0);
        path.sample(0.25, pose);
        assertEquals(90.0, pose[3], 1.0);
        path.sample(0.5, pose);
        assertEquals(180.0, Math.abs(pose[3]), 1.0);
    }

    @Test
    void unwrapKeepsYawContinuous() {
        assertEquals(190.0F, CameraPath.unwrap(170.0F, -170.0F), 1e-4F);
        assertEquals(-190.0F, CameraPath.unwrap(-170.0F, 170.0F), 1e-4F);
        assertEquals(20.0F, CameraPath.unwrap(10.0F, 20.0F), 1e-4F);
        assertEquals(365.0F, CameraPath.unwrap(350.0F, 5.0F), 1e-4F);
        assertEquals(725.0F, CameraPath.unwrap(720.0F, 5.0F), 1e-4F);
    }

    @Test
    void rejectsDegeneratePaths() {
        assertThrows(IllegalArgumentException.class, () -> new CameraPath(new double[3], new double[3], new double[3], 0));
        assertThrows(IllegalArgumentException.class, () -> new CameraPath(new double[4], new double[5], new double[4], 0));
    }
}
