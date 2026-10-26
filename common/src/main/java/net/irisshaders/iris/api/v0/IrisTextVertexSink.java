package net.irisshaders.iris.api.v0;

import com.mojang.blaze3d.vertex.VertexFormat;

import java.nio.ByteBuffer;

/**
 * Text vertex sink exposed via {@link IrisApi#createTextVertexSink}.
 */
public interface IrisTextVertexSink {
    VertexFormat getUnderlyingVertexFormat();

    ByteBuffer getUnderlyingByteBuffer();

    void quad(float x1, float y1, float x2, float y2, float z,
              int color, float u1, float v1, float u2, float v2, int light);
}
