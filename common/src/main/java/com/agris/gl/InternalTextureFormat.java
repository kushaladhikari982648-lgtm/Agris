package com.agris.gl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Complete OptiFine / Iris {@code colortexNFormat} internal OpenGL texture format registry,
 * with numeric GL constants, upload pixel format/type, floating-point FBO classification,
 * and automatic mobile/GL4ES fallback formats.
 */
public enum InternalTextureFormat {
    // 8-bit normalized
    R8(0x8229, 0x1903, 0x1401, false),
    RG8(0x822B, 0x8227, 0x1401, false),
    RGB8(0x8051, 0x1907, 0x1401, false),
    RGBA(0x1908, 0x1908, 0x1401, false),
    RGBA8(0x8058, 0x1908, 0x1401, false),

    // Signed normalized
    R8_SNORM(0x8F94, 0x1903, 0x1400, false),
    RG8_SNORM(0x8F95, 0x8227, 0x1400, false),
    RGB8_SNORM(0x8F96, 0x1907, 0x1400, false),
    RGBA8_SNORM(0x8F97, 0x1908, 0x1400, false),

    // 16-bit normalized
    R16(0x822A, 0x1903, 0x1403, false),
    RG16(0x822C, 0x8227, 0x1403, false),
    RGB16(0x8054, 0x1907, 0x1403, false),
    RGBA16(0x805B, 0x1908, 0x1403, false),
    R16_SNORM(0x8F98, 0x1903, 0x1402, false),
    RG16_SNORM(0x8F99, 0x8227, 0x1402, false),
    RGB16_SNORM(0x8F9A, 0x1907, 0x1402, false),
    RGBA16_SNORM(0x8F9B, 0x1908, 0x1402, false),

    // Packed formats
    RGB5_A1(0x8057, 0x1908, 0x1401, false),
    RGB10_A2(0x8059, 0x1908, 0x8368, false),
    R3_G3_B2(0x2A10, 0x1907, 0x1401, false),
    RGB4(0x804F, 0x1907, 0x1401, false),

    // Half-float (16F) and full-float (32F)
    R16F(0x822D, 0x1903, 0x140B, true),
    RG16F(0x822F, 0x8227, 0x140B, true),
    RGB16F(0x881B, 0x1907, 0x140B, true),
    RGBA16F(0x881A, 0x1908, 0x140B, true),
    R32F(0x822E, 0x1903, 0x1406, true),
    RG32F(0x8230, 0x8227, 0x1406, true),
    RGB32F(0x8815, 0x1907, 0x1406, true),
    RGBA32F(0x8814, 0x1908, 0x1406, true),
    R11F_G11F_B10F(0x8C3A, 0x1907, 0x8C3B, true),
    RGB9_E5(0x8C3D, 0x1907, 0x8C3E, true),

    // Integer formats
    R8I(0x8231, 0x8D94, 0x1400, false),
    R8UI(0x8232, 0x8D94, 0x1401, false),
    R16I(0x8233, 0x8D94, 0x1402, false),
    R16UI(0x8234, 0x8D94, 0x1403, false),
    R32I(0x8235, 0x8D94, 0x1404, false),
    R32UI(0x8236, 0x8D94, 0x1405, false),
    RG8I(0x8237, 0x8228, 0x1400, false),
    RG8UI(0x8238, 0x8228, 0x1401, false),
    RG16I(0x8239, 0x8228, 0x1402, false),
    RG16UI(0x823A, 0x8228, 0x1403, false),
    RG32I(0x823B, 0x8228, 0x1404, false),
    RG32UI(0x823C, 0x8228, 0x1405, false),
    RGBA8I(0x8D8E, 0x8D99, 0x1400, false),
    RGBA8UI(0x8D7C, 0x8D99, 0x1401, false),
    RGBA16I(0x8D88, 0x8D99, 0x1402, false),
    RGBA16UI(0x8D76, 0x8D99, 0x1403, false),
    RGBA32I(0x8D82, 0x8D99, 0x1404, false),
    RGBA32UI(0x8D70, 0x8D99, 0x1405, false);

    private static final Map<String, InternalTextureFormat> BY_NAME;

    static {
        final Map<String, InternalTextureFormat> map = new HashMap<>();
        for (final InternalTextureFormat fmt : values()) {
            map.put(fmt.name(), fmt);
        }
        BY_NAME = Collections.unmodifiableMap(map);
    }

    private final int glInternalFormat;
    private final int glPixelFormat;
    private final int glPixelType;
    private final boolean floatingPoint;

    InternalTextureFormat(final int glInternalFormat, final int glPixelFormat,
                          final int glPixelType, final boolean floatingPoint) {
        this.glInternalFormat = glInternalFormat;
        this.glPixelFormat = glPixelFormat;
        this.glPixelType = glPixelType;
        this.floatingPoint = floatingPoint;
    }

    public int glInternalFormat() {
        return this.glInternalFormat;
    }

    public int glPixelFormat() {
        return this.glPixelFormat;
    }

    public int glPixelType() {
        return this.glPixelType;
    }

    public boolean isFloatingPoint() {
        return this.floatingPoint;
    }

    public boolean isIntegerFormat() {
        return this.glPixelFormat == 0x8D94 || this.glPixelFormat == 0x8228 || this.glPixelFormat == 0x8D99;
    }

    /** Safe normalized fallback when a mobile GL translator lacks floating-point FBO color renderability. */
    public InternalTextureFormat mobileSafeFallback() {
        if (!this.floatingPoint && !isIntegerFormat()) {
            return this;
        }
        return RGBA8;
    }

    /**
     * Bandwidth-halving HDR compaction for TBDR mobile GPUs (Adreno GMEM, Mali Tile Buffer,
     * Apple Silicon): converts 64-bit/128-bit {@code RGB16F/RGBA16F/RGB32F/RGBA32F} into
     * 32-bit packed {@code R11F_G11F_B10F} when the shader pack does not read the buffer's
     * alpha channel, cutting tile &amp; LPDDR memory traffic by 50%–75%.
     */
    public InternalTextureFormat mobileBandwidthCompact(final boolean needsAlpha) {
        return switch (this) {
            case RGB16F, RGB32F -> R11F_G11F_B10F;
            case RGBA16F -> needsAlpha ? RGBA16F : R11F_G11F_B10F;
            case RGBA32F -> needsAlpha ? RGBA16F : R11F_G11F_B10F;
            case RG32F -> RG16F;
            case R32F -> R16F;
            default -> this;
        };
    }

    public static Optional<InternalTextureFormat> byName(final String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(name.trim().toUpperCase(Locale.ROOT)));
    }
}
