package com.agris.util;

/**
 * Allocation-free column-major {@code float[16]} / {@code float[9]} linear algebra,
 * celestial vector math, and Iris/OptiFine-exact shadow matrix construction.
 *
 * <p>Kept pure Java with no JOML dependency so every shadow projection, inverse
 * matrix, normal matrix, and texel-grid snap calculation runs directly in headless
 * unit tests as well as on the OpenGL render thread.</p>
 */
public final class MathUtil {
    public static final float SHADOW_NEAR = -100.05f;
    public static final float SHADOW_FAR = 156.0f;
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    private MathUtil() {
    }

    public static float clamp(final float v, final float min, final float max) {
        return Math.max(min, Math.min(max, v));
    }

    public static int clamp(final int v, final int min, final int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Exponential smoothing matching OptiFine/Iris {@code halfLife} decay semantics. */
    public static float smoothHalfLife(final float current, final float target,
                                       final float frameTimeSeconds, final float halfLifeSeconds) {
        if (halfLifeSeconds <= 1.0e-5f || frameTimeSeconds <= 0.0f) {
            return target;
        }
        final float decay = (float) Math.pow(0.5, frameTimeSeconds / halfLifeSeconds);
        return target + (current - target) * decay;
    }

    /** Writes the 4x4 identity matrix into {@code out} (length >= 16) and returns {@code out}. */
    public static float[] identity4(final float[] out) {
        for (int i = 0; i < 16; i++) {
            out[i] = 0.0f;
        }
        out[0] = 1.0f;
        out[5] = 1.0f;
        out[10] = 1.0f;
        out[15] = 1.0f;
        return out;
    }

    /** Writes the 3x3 identity matrix into {@code out} (length >= 9) and returns {@code out}. */
    public static float[] identity3(final float[] out) {
        for (int i = 0; i < 9; i++) {
            out[i] = 0.0f;
        }
        out[0] = 1.0f;
        out[4] = 1.0f;
        out[8] = 1.0f;
        return out;
    }

    /** Column-major 4x4 multiplication {@code out = a * b} (safe when {@code out} aliases {@code a} or {@code b}). */
    public static float[] mul4(final float[] a, final float[] b, final float[] out) {
        final float m00 = a[0] * b[0] + a[4] * b[1] + a[8] * b[2] + a[12] * b[3];
        final float m01 = a[1] * b[0] + a[5] * b[1] + a[9] * b[2] + a[13] * b[3];
        final float m02 = a[2] * b[0] + a[6] * b[1] + a[10] * b[2] + a[14] * b[3];
        final float m03 = a[3] * b[0] + a[7] * b[1] + a[11] * b[2] + a[15] * b[3];

        final float m10 = a[0] * b[4] + a[4] * b[5] + a[8] * b[6] + a[12] * b[7];
        final float m11 = a[1] * b[4] + a[5] * b[5] + a[9] * b[6] + a[13] * b[7];
        final float m12 = a[2] * b[4] + a[6] * b[5] + a[10] * b[6] + a[14] * b[7];
        final float m13 = a[3] * b[4] + a[7] * b[5] + a[11] * b[6] + a[15] * b[7];

        final float m20 = a[0] * b[8] + a[4] * b[9] + a[8] * b[10] + a[12] * b[11];
        final float m21 = a[1] * b[8] + a[5] * b[9] + a[9] * b[10] + a[13] * b[11];
        final float m22 = a[2] * b[8] + a[6] * b[9] + a[10] * b[10] + a[14] * b[11];
        final float m23 = a[3] * b[8] + a[7] * b[9] + a[11] * b[10] + a[15] * b[11];

        final float m30 = a[0] * b[12] + a[4] * b[13] + a[8] * b[14] + a[12] * b[15];
        final float m31 = a[1] * b[12] + a[5] * b[13] + a[9] * b[14] + a[13] * b[15];
        final float m32 = a[2] * b[12] + a[6] * b[13] + a[10] * b[14] + a[14] * b[15];
        final float m33 = a[3] * b[12] + a[7] * b[13] + a[11] * b[14] + a[15] * b[15];

        out[0] = m00;
        out[1] = m01;
        out[2] = m02;
        out[3] = m03;
        out[4] = m10;
        out[5] = m11;
        out[6] = m12;
        out[7] = m13;
        out[8] = m20;
        out[9] = m21;
        out[10] = m22;
        out[11] = m23;
        out[12] = m30;
        out[13] = m31;
        out[14] = m32;
        out[15] = m33;
        return out;
    }

    /** Transforms {@code (x, y, z, w)} by column-major 4x4 {@code m} into {@code out4}. */
    public static float[] transform4(final float[] m, final float x, final float y, final float z, final float w,
                                     final float[] out4) {
        out4[0] = m[0] * x + m[4] * y + m[8] * z + m[12] * w;
        out4[1] = m[1] * x + m[5] * y + m[9] * z + m[13] * w;
        out4[2] = m[2] * x + m[6] * y + m[10] * z + m[14] * w;
        out4[3] = m[3] * x + m[7] * y + m[11] * z + m[15] * w;
        return out4;
    }

    /** Post-multiplies a translation {@code (tx, ty, tz)} onto column-major 4x4 {@code m}. */
    public static float[] translate4(final float[] m, final float tx, final float ty, final float tz) {
        m[12] += m[0] * tx + m[4] * ty + m[8] * tz;
        m[13] += m[1] * tx + m[5] * ty + m[9] * tz;
        m[14] += m[2] * tx + m[6] * ty + m[10] * tz;
        m[15] += m[3] * tx + m[7] * ty + m[11] * tz;
        return m;
    }

    /** Post-multiplies a rotation of {@code degrees} around the X axis onto column-major 4x4 {@code m}. */
    public static float[] rotateXDegrees(final float[] m, final float degrees) {
        final float rad = degrees * DEG_TO_RAD;
        final float c = (float) Math.cos(rad);
        final float s = (float) Math.sin(rad);
        for (int row = 0; row < 4; row++) {
            final float c1 = m[4 + row];
            final float c2 = m[8 + row];
            m[4 + row] = c1 * c + c2 * s;
            m[8 + row] = c2 * c - c1 * s;
        }
        return m;
    }

    /** Post-multiplies a rotation of {@code degrees} around the Y axis onto column-major 4x4 {@code m}. */
    public static float[] rotateYDegrees(final float[] m, final float degrees) {
        final float rad = degrees * DEG_TO_RAD;
        final float c = (float) Math.cos(rad);
        final float s = (float) Math.sin(rad);
        for (int row = 0; row < 4; row++) {
            final float c0 = m[row];
            final float c2 = m[8 + row];
            m[row] = c0 * c - c2 * s;
            m[8 + row] = c0 * s + c2 * c;
        }
        return m;
    }

    /** Post-multiplies a rotation of {@code degrees} around the Z axis onto column-major 4x4 {@code m}. */
    public static float[] rotateZDegrees(final float[] m, final float degrees) {
        final float rad = degrees * DEG_TO_RAD;
        final float c = (float) Math.cos(rad);
        final float s = (float) Math.sin(rad);
        for (int row = 0; row < 4; row++) {
            final float c0 = m[row];
            final float c1 = m[4 + row];
            m[row] = c0 * c + c1 * s;
            m[4 + row] = c1 * c - c0 * s;
        }
        return m;
    }

    /** Computes the inverse of column-major 4x4 {@code m} into {@code out}; writes identity if singular. */
    public static boolean invert4(final float[] m, final float[] out) {
        final float a00 = m[0], a01 = m[1], a02 = m[2], a03 = m[3];
        final float a10 = m[4], a11 = m[5], a12 = m[6], a13 = m[7];
        final float a20 = m[8], a21 = m[9], a22 = m[10], a23 = m[11];
        final float a30 = m[12], a31 = m[13], a32 = m[14], a33 = m[15];

        final float b00 = a00 * a11 - a01 * a10;
        final float b01 = a00 * a12 - a02 * a10;
        final float b02 = a00 * a13 - a03 * a10;
        final float b03 = a01 * a12 - a02 * a11;
        final float b04 = a01 * a13 - a03 * a11;
        final float b05 = a02 * a13 - a03 * a12;
        final float b06 = a20 * a31 - a21 * a30;
        final float b07 = a20 * a32 - a22 * a30;
        final float b08 = a20 * a33 - a23 * a30;
        final float b09 = a21 * a32 - a22 * a31;
        final float b10 = a21 * a33 - a23 * a31;
        final float b11 = a22 * a33 - a23 * a32;

        final float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (Math.abs(det) < 1.0e-12f) {
            identity4(out);
            return false;
        }
        final float invDet = 1.0f / det;
        out[0] = (a11 * b11 - a12 * b10 + a13 * b09) * invDet;
        out[1] = (-a01 * b11 + a02 * b10 - a03 * b09) * invDet;
        out[2] = (a31 * b05 - a32 * b04 + a33 * b03) * invDet;
        out[3] = (-a21 * b05 + a22 * b04 - a23 * b03) * invDet;
        out[4] = (-a10 * b11 + a12 * b08 - a13 * b07) * invDet;
        out[5] = (a00 * b11 - a02 * b08 + a03 * b07) * invDet;
        out[6] = (-a30 * b05 + a32 * b02 - a33 * b01) * invDet;
        out[7] = (a20 * b05 - a22 * b02 + a23 * b01) * invDet;
        out[8] = (a10 * b10 - a11 * b08 + a13 * b06) * invDet;
        out[9] = (-a00 * b10 + a01 * b08 - a03 * b06) * invDet;
        out[10] = (a30 * b04 - a31 * b02 + a33 * b00) * invDet;
        out[11] = (-a20 * b04 + a21 * b02 - a23 * b00) * invDet;
        out[12] = (-a10 * b09 + a11 * b07 - a12 * b06) * invDet;
        out[13] = (a00 * b09 - a01 * b07 + a02 * b06) * invDet;
        out[14] = (-a30 * b03 + a31 * b01 - a32 * b00) * invDet;
        out[15] = (a20 * b03 - a21 * b01 + a22 * b00) * invDet;
        return true;
    }

    /** Computes column-major 3x3 normal matrix (inverse-transpose of upper-left 3x3 of {@code modelView}). */
    public static float[] normalMatrix3(final float[] modelView, final float[] out9) {
        final float m00 = modelView[0], m01 = modelView[1], m02 = modelView[2];
        final float m10 = modelView[4], m11 = modelView[5], m12 = modelView[6];
        final float m20 = modelView[8], m21 = modelView[9], m22 = modelView[10];

        final float c00 = m11 * m22 - m12 * m21;
        final float c01 = m12 * m20 - m10 * m22;
        final float c02 = m10 * m21 - m11 * m20;

        final float det = m00 * c00 + m01 * c01 + m02 * c02;
        if (Math.abs(det) < 1.0e-10f) {
            return identity3(out9);
        }
        final float invDet = 1.0f / det;
        // Inverse-transpose in column-major means cofactor / det placed directly into columns.
        out9[0] = c00 * invDet;
        out9[1] = c01 * invDet;
        out9[2] = c02 * invDet;
        out9[3] = (m02 * m21 - m01 * m22) * invDet;
        out9[4] = (m00 * m22 - m02 * m20) * invDet;
        out9[5] = (m01 * m20 - m00 * m21) * invDet;
        out9[6] = (m01 * m12 - m02 * m11) * invDet;
        out9[7] = (m02 * m10 - m00 * m12) * invDet;
        out9[8] = (m00 * m11 - m01 * m10) * invDet;
        return out9;
    }

    /**
     * Builds the Iris/OptiFine orthographic shadow projection matrix for {@code halfPlaneLength}
     * ({@code shadowDistance}). Matches {@code ShadowMatrices.createOrthoMatrix} byte-for-byte.
     */
    public static float[] createShadowOrthoMatrix(final float halfPlaneLength, final float[] out) {
        final float safeHalf = Math.max(1.0f, halfPlaneLength);
        identity4(out);
        out[0] = 1.0f / safeHalf;
        out[5] = 1.0f / safeHalf;
        out[10] = 2.0f / (SHADOW_NEAR - SHADOW_FAR);
        out[14] = -(SHADOW_FAR + SHADOW_NEAR) / (SHADOW_FAR - SHADOW_NEAR);
        return out;
    }

    /**
     * Builds the Iris/OptiFine shadow model-view matrix, including sun/moon celestial
     * orientation, {@code sunPathRotation}, and {@code shadowIntervalSize} grid snapping
     * to prevent shadow-map shimmering as the camera moves.
     */
    public static float[] createShadowModelView(final float shadowAngle, final float sunPathRotation,
                                                final float intervalSize,
                                                final double cameraX, final double cameraY, final double cameraZ,
                                                final float[] out) {
        final float skyAngle = shadowAngle < 0.25f ? shadowAngle + 0.75f : shadowAngle - 0.25f;
        identity4(out);
        out[14] = -100.0f;
        rotateXDegrees(out, 90.0f);
        rotateZDegrees(out, skyAngle * -360.0f);
        rotateXDegrees(out, sunPathRotation);
        if (intervalSize > 0.0f) {
            final float offsetX = (float) positiveMod(cameraX, intervalSize) - intervalSize * 0.5f;
            final float offsetY = (float) positiveMod(cameraY, intervalSize) - intervalSize * 0.5f;
            final float offsetZ = (float) positiveMod(cameraZ, intervalSize) - intervalSize * 0.5f;
            translate4(out, offsetX, offsetY, offsetZ);
        }
        return out;
    }

    /**
     * Computes OptiFine {@code sunAngle} (0.0 = sunrise, 0.25 = noon, 0.5 = sunset, 0.75 = midnight)
     * from Minecraft's celestial time-of-day angle {@code skyAngle} (0.0 = noon, 0.5 = midnight).
     */
    public static float computeSunAngle(final float skyAngle) {
        return skyAngle < 0.75f ? skyAngle + 0.25f : skyAngle - 0.75f;
    }

    /** Computes OptiFine {@code shadowAngle} in {@code [0, 0.5)} (folds night onto the moon path). */
    public static float computeShadowAngle(final float sunAngle) {
        return sunAngle < 0.5f ? sunAngle : sunAngle - 0.5f;
    }

    /**
     * Computes view-space {@code sunPosition} (length 100, matching OptiFine/Iris convention),
     * {@code moonPosition = -sunPosition}, and {@code upPosition} from {@code gbufferModelView}.
     */
    public static void computeCelestialPositions(final float[] gbufferModelView, final float skyAngle,
                                                 final float sunPathRotation,
                                                 final float[] sunPos3, final float[] moonPos3,
                                                 final float[] shadowLightPos3, final float[] upPos3) {
        final float[] celestial = new float[16];
        System.arraycopy(gbufferModelView, 0, celestial, 0, 16);
        // Strip view translation so vectors represent pure view-space directions of length 100.
        celestial[12] = 0.0f;
        celestial[13] = 0.0f;
        celestial[14] = 0.0f;

        // Up vector is world +Y * 100 before celestial rotation.
        final float[] tmp4 = new float[4];
        transform4(celestial, 0.0f, 100.0f, 0.0f, 0.0f, tmp4);
        upPos3[0] = tmp4[0];
        upPos3[1] = tmp4[1];
        upPos3[2] = tmp4[2];

        rotateYDegrees(celestial, -90.0f);
        rotateZDegrees(celestial, sunPathRotation);
        rotateXDegrees(celestial, skyAngle * 360.0f);

        transform4(celestial, 0.0f, 100.0f, 0.0f, 0.0f, tmp4);
        sunPos3[0] = tmp4[0];
        sunPos3[1] = tmp4[1];
        sunPos3[2] = tmp4[2];

        moonPos3[0] = -tmp4[0];
        moonPos3[1] = -tmp4[1];
        moonPos3[2] = -tmp4[2];

        final float sunAngle = computeSunAngle(skyAngle);
        if (sunAngle < 0.5f) {
            System.arraycopy(sunPos3, 0, shadowLightPos3, 0, 3);
        } else {
            System.arraycopy(moonPos3, 0, shadowLightPos3, 0, 3);
        }
    }

    private static double positiveMod(final double value, final double modulus) {
        final double r = value % modulus;
        return r < 0.0 ? r + modulus : r;
    }
}
