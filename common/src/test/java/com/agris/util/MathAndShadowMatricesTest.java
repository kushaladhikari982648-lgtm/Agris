package com.agris.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MathAndShadowMatricesTest {

    @Test
    public void matrixInvertAndMultiplyRecoverIdentity() {
        final float[] m = MathUtil.identity4(new float[16]);
        MathUtil.translate4(m, 12.5f, -4.0f, 33.25f);
        MathUtil.rotateYDegrees(m, 37.0f);
        MathUtil.rotateXDegrees(m, -22.5f);

        final float[] inv = new float[16];
        assertTrue(MathUtil.invert4(m, inv));

        final float[] prod = MathUtil.mul4(m, inv, new float[16]);
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                final float expected = (c == r) ? 1.0f : 0.0f;
                assertEquals(expected, prod[c * 4 + r], 1e-4f, "Mismatch at (" + r + "," + c + ")");
            }
        }
    }

    @Test
    public void shadowOrthoAndModelViewSnapToIntervalAndInvertCleanly() {
        final float[] ortho = MathUtil.createShadowOrthoMatrix(128.0f, new float[16]);
        final float[] orthoInv = new float[16];
        assertTrue(MathUtil.invert4(ortho, orthoInv));

        final float[] prod = MathUtil.mul4(ortho, orthoInv, new float[16]);
        assertEquals(1.0f, prod[0], 1e-4f);
        assertEquals(1.0f, prod[5], 1e-4f);
        assertEquals(1.0f, prod[10], 1e-4f);
        assertEquals(1.0f, prod[15], 1e-4f);

        final float[] mv1 = MathUtil.createShadowModelView(
                0.25f, -40.0f, 2.0f, 10.02f, 64.01f, -20.03f, new float[16]);
        final float[] mv2 = MathUtil.createShadowModelView(
                0.25f, -40.0f, 2.0f, 10.021f, 64.011f, -20.031f, new float[16]);

        final float[] invMv = new float[16];
        assertTrue(MathUtil.invert4(mv1, invMv));
        assertEquals(mv1[12], mv2[12], 0.25f);
    }

    @Test
    public void versionRangeComparisonsHandleReleaseAndCalendarVersions() {
        final VersionRange legacy = VersionRange.parse(">=1.16.5 <=1.20.4");
        assertTrue(legacy.contains("1.16.5"));
        assertTrue(legacy.contains("1.19.2"));
        assertFalse(legacy.contains("1.21.1"));

        final VersionRange modern = VersionRange.parse(">=1.21");
        assertTrue(modern.contains("1.21.1"));
        assertTrue(modern.contains("26.1"));
        assertFalse(modern.contains("1.20.6"));
    }
}
