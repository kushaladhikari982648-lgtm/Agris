package com.agris.pack;

import com.agris.gl.InternalTextureFormat;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregated pack-level configuration merged from {@code shaders/shaders.properties}
 * and global GLSL constant declarations across all stages.
 */
public final class PackDirectives {
    public static final int MAX_COLOR_BUFFERS = 16;

    private boolean shadowEnabled = true;
    private int shadowMapResolution = 1024;
    private float shadowDistance = 120.0f;
    private float shadowIntervalSize = 2.0f;
    private float sunPathRotation = 0.0f;
    private float ambientOcclusionLevel = 1.0f;
    private float centerDepthHalfLife = 1.0f;
    private float wetnessHalfLife = 600.0f;
    private float drynessHalfLife = 200.0f;
    private float eyeBrightnessHalfLife = 10.0f;
    private int noiseTextureResolution = 256;

    private Boolean oldLighting;
    private boolean separateAo;
    private boolean rainDepth;
    private boolean vignette = true;
    private boolean underwaterOverlay = true;
    private boolean skyEnabled = true;
    private String cloudSetting = "default";

    private final InternalTextureFormat[] bufferFormats = new InternalTextureFormat[MAX_COLOR_BUFFERS];
    private final boolean[] bufferClears = new boolean[MAX_COLOR_BUFFERS];
    private final float[][] bufferClearColors = new float[MAX_COLOR_BUFFERS][];
    private final float[] bufferScales = new float[MAX_COLOR_BUFFERS];
    private final boolean[] bufferMipmaps = new boolean[MAX_COLOR_BUFFERS];

    private final InternalTextureFormat[] shadowColorFormats = {
            InternalTextureFormat.RGBA8,
            InternalTextureFormat.RGBA8
    };
    private final boolean[] shadowHardwareFiltering = {false, false};

    private final Map<String, Float> passAlphaRefs = new LinkedHashMap<>();
    private final Map<String, Float> passScales = new LinkedHashMap<>();
    private final Map<String, Map<Integer, Boolean>> explicitFlips = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> profiles = new LinkedHashMap<>();
    private final Map<String, List<String>> screens = new LinkedHashMap<>();
    private final Set<String> sliders = new LinkedHashSet<>();
    private final Map<String, UniformExpression> customVariables = new LinkedHashMap<>();
    private final Map<String, UniformExpression> customUniforms = new LinkedHashMap<>();
    private final Map<String, String> customTextures = new LinkedHashMap<>();

    public PackDirectives() {
        Arrays.fill(this.bufferFormats, InternalTextureFormat.RGBA8);
        Arrays.fill(this.bufferClears, true);
        Arrays.fill(this.bufferScales, 1.0f);
    }

    public boolean isShadowEnabled() {
        return this.shadowEnabled;
    }

    public void setShadowEnabled(final boolean shadowEnabled) {
        this.shadowEnabled = shadowEnabled;
    }

    public int shadowMapResolution() {
        return this.shadowMapResolution;
    }

    public void setShadowMapResolution(final int resolution) {
        this.shadowMapResolution = Math.max(64, Math.min(16384, resolution));
    }

    public float shadowDistance() {
        return this.shadowDistance;
    }

    public void setShadowDistance(final float distance) {
        this.shadowDistance = Math.max(8.0f, Math.min(1024.0f, distance));
    }

    public float shadowIntervalSize() {
        return this.shadowIntervalSize;
    }

    public void setShadowIntervalSize(final float intervalSize) {
        this.shadowIntervalSize = Math.max(0.001f, intervalSize);
    }

    public float sunPathRotation() {
        return this.sunPathRotation;
    }

    public void setSunPathRotation(final float rotation) {
        this.sunPathRotation = Math.max(-90.0f, Math.min(90.0f, rotation));
    }

    public float ambientOcclusionLevel() {
        return this.ambientOcclusionLevel;
    }

    public void setAmbientOcclusionLevel(final float ao) {
        this.ambientOcclusionLevel = Math.max(0.0f, Math.min(1.0f, ao));
    }

    public float centerDepthHalfLife() {
        return this.centerDepthHalfLife;
    }

    public void setCenterDepthHalfLife(final float seconds) {
        this.centerDepthHalfLife = Math.max(0.01f, seconds);
    }

    public float wetnessHalfLife() {
        return this.wetnessHalfLife;
    }

    public void setWetnessHalfLife(final float seconds) {
        this.wetnessHalfLife = Math.max(0.01f, seconds);
    }

    public float drynessHalfLife() {
        return this.drynessHalfLife;
    }

    public void setDrynessHalfLife(final float seconds) {
        this.drynessHalfLife = Math.max(0.01f, seconds);
    }

    public float eyeBrightnessHalfLife() {
        return this.eyeBrightnessHalfLife;
    }

    public void setEyeBrightnessHalfLife(final float seconds) {
        this.eyeBrightnessHalfLife = Math.max(0.01f, seconds);
    }

    public int noiseTextureResolution() {
        return this.noiseTextureResolution;
    }

    public void setNoiseTextureResolution(final int res) {
        this.noiseTextureResolution = Math.max(16, Math.min(2048, res));
    }

    public Boolean oldLighting() {
        return this.oldLighting;
    }

    public void setOldLighting(final Boolean oldLighting) {
        this.oldLighting = oldLighting;
    }

    public boolean separateAo() {
        return this.separateAo;
    }

    public void setSeparateAo(final boolean separateAo) {
        this.separateAo = separateAo;
    }

    public boolean rainDepth() {
        return this.rainDepth;
    }

    public void setRainDepth(final boolean rainDepth) {
        this.rainDepth = rainDepth;
    }

    public boolean vignette() {
        return this.vignette;
    }

    public void setVignette(final boolean vignette) {
        this.vignette = vignette;
    }

    public boolean underwaterOverlay() {
        return this.underwaterOverlay;
    }

    public void setUnderwaterOverlay(final boolean underwaterOverlay) {
        this.underwaterOverlay = underwaterOverlay;
    }

    public boolean skyEnabled() {
        return this.skyEnabled;
    }

    public void setSkyEnabled(final boolean skyEnabled) {
        this.skyEnabled = skyEnabled;
    }

    public String cloudSetting() {
        return this.cloudSetting;
    }

    public void setCloudSetting(final String cloudSetting) {
        this.cloudSetting = cloudSetting == null ? "default" : cloudSetting;
    }

    public InternalTextureFormat bufferFormat(final int index) {
        return this.bufferFormats[index];
    }

    public void setBufferFormat(final int index, final InternalTextureFormat format) {
        if (index >= 0 && index < MAX_COLOR_BUFFERS && format != null) {
            this.bufferFormats[index] = format;
        }
    }

    public boolean bufferClear(final int index) {
        return this.bufferClears[index];
    }

    public void setBufferClear(final int index, final boolean clear) {
        if (index >= 0 && index < MAX_COLOR_BUFFERS) {
            this.bufferClears[index] = clear;
        }
    }

    public float[] bufferClearColorOrNull(final int index) {
        final float[] c = this.bufferClearColors[index];
        return c == null ? null : c.clone();
    }

    public void setBufferClearColor(final int index, final float r, final float g, final float b, final float a) {
        if (index >= 0 && index < MAX_COLOR_BUFFERS) {
            this.bufferClearColors[index] = new float[]{r, g, b, a};
        }
    }

    public float bufferScale(final int index) {
        return this.bufferScales[index];
    }

    public void setBufferScale(final int index, final float scale) {
        if (index >= 0 && index < MAX_COLOR_BUFFERS && scale > 0.0f) {
            this.bufferScales[index] = Math.max(0.0625f, Math.min(4.0f, scale));
        }
    }

    public boolean bufferMipmap(final int index) {
        return this.bufferMipmaps[index];
    }

    public void setBufferMipmap(final int index, final boolean mipmap) {
        if (index >= 0 && index < MAX_COLOR_BUFFERS) {
            this.bufferMipmaps[index] = mipmap;
        }
    }

    public InternalTextureFormat shadowColorFormat(final int index) {
        return this.shadowColorFormats[index];
    }

    public void setShadowColorFormat(final int index, final InternalTextureFormat format) {
        if ((index == 0 || index == 1) && format != null) {
            this.shadowColorFormats[index] = format;
        }
    }

    public boolean shadowHardwareFiltering(final int index) {
        return this.shadowHardwareFiltering[index];
    }

    public void setShadowHardwareFiltering(final int index, final boolean enabled) {
        if (index == 0 || index == 1) {
            this.shadowHardwareFiltering[index] = enabled;
        }
    }

    public float passAlphaRef(final String passName, final float defaultVal) {
        return this.passAlphaRefs.getOrDefault(passName, defaultVal);
    }

    public void setPassAlphaRef(final String passName, final float alphaRef) {
        this.passAlphaRefs.put(passName, alphaRef);
    }

    public float passScale(final String passName, final float defaultVal) {
        return this.passScales.getOrDefault(passName, defaultVal);
    }

    public void setPassScale(final String passName, final float scale) {
        if (scale > 0.0f) {
            this.passScales.put(passName, Math.max(0.0625f, Math.min(4.0f, scale)));
        }
    }

    public Boolean explicitFlip(final String passName, final int bufferIndex) {
        final Map<Integer, Boolean> map = this.explicitFlips.get(passName);
        return map == null ? null : map.get(bufferIndex);
    }

    public void setExplicitFlip(final String passName, final int bufferIndex, final boolean flip) {
        this.explicitFlips.computeIfAbsent(passName, k -> new LinkedHashMap<>()).put(bufferIndex, flip);
    }

    public Map<String, Map<String, String>> profiles() {
        return Collections.unmodifiableMap(this.profiles);
    }

    public void putProfile(final String name, final Map<String, String> values) {
        this.profiles.put(name, Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }

    public Map<String, List<String>> screens() {
        return Collections.unmodifiableMap(this.screens);
    }

    public void putScreen(final String key, final List<String> tokens) {
        this.screens.put(key, List.copyOf(tokens));
    }

    public Set<String> sliders() {
        return Collections.unmodifiableSet(this.sliders);
    }

    public void addSlider(final String optionName) {
        this.sliders.add(optionName);
    }

    public Map<String, UniformExpression> customVariables() {
        return Collections.unmodifiableMap(this.customVariables);
    }

    public void putCustomVariable(final String name, final UniformExpression expr) {
        this.customVariables.put(name, expr);
    }

    public Map<String, UniformExpression> customUniforms() {
        return Collections.unmodifiableMap(this.customUniforms);
    }

    public void putCustomUniform(final String name, final UniformExpression expr) {
        this.customUniforms.put(name, expr);
    }

    public Map<String, String> customTextures() {
        return Collections.unmodifiableMap(this.customTextures);
    }

    public void putCustomTexture(final String samplerKey, final String texturePath) {
        this.customTextures.put(samplerKey, texturePath);
    }
}
