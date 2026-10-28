package com.agris.compat;

import com.agris.Agris;
import com.agris.config.AgrisConfig;
import com.agris.pack.ShaderPackDiscovery;
import com.agris.util.AgrisLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Reflectively constructs and appends a native <b>Shaders</b> {@code com.aetherium.gui.Page}
 * inside Aetherium {@code 1.x}'s {@code AetheriumPages.build()} tab bar.
 *
 * <p>Gives Aetherium users a seamless, native Shaders tab inside {@code AetheriumScreen}
 * (matching Aetherium's typography, sliders, dropdowns, Apply/Undo workflow, and icon art)
 * while also offering one-click buttons to open Agris's full {@code AgrisShaderPackScreen}
 * and {@code AgrisShaderOptionsScreen}.</p>
 */
public final class AetheriumTabInjector {
    private static final String[] PRISM_ICON_ROWS = {
            ".....##.....",
            "....####....",
            "...##..##...",
            "...#.##.#...",
            "..##.##.##..",
            ".##.####.##.",
            ".##.#..#.##.",
            "############",
            "...##..##...",
            "..##.##.##..",
            ".....##.....",
            "............"
    };

    private AetheriumTabInjector() {
    }

    /**
     * Appends the Agris {@code Shaders} page to the mutable/immutable {@code List<Page>}
     * returned by {@code AetheriumPages.build()}, returning a new mutable list when needed.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<?> appendShadersPage(final List<?> existingPages,
                                            final Runnable openPackScreenAction,
                                            final Runnable openPackOptionsAction,
                                            final Runnable reloadPipelineAction,
                                            final Supplier<String> statusSupplier) {
        if (existingPages == null || !Agris.config().integrateAetheriumUi.get()) {
            return existingPages;
        }
        // Avoid duplicate injection if already present.
        for (final Object item : existingPages) {
            if (item != null && "Shaders".equals(readPageName(item))) {
                return existingPages;
            }
        }
        final ClassLoader loader = existingPages.isEmpty() || existingPages.get(0) == null
                ? AetheriumTabInjector.class.getClassLoader()
                : existingPages.get(0).getClass().getClassLoader();
        final Object shadersPage = buildShadersPage(
                loader, openPackScreenAction, openPackOptionsAction, reloadPipelineAction, statusSupplier);
        if (shadersPage == null) {
            return existingPages;
        }
        final java.util.ArrayList copy = new java.util.ArrayList<>(existingPages.size() + 1);
        copy.addAll(existingPages);
        // Insert right after Quality (or before About) when present so Shaders sits naturally in video tabs.
        int insertIndex = copy.size();
        for (int i = 0; i < copy.size(); i++) {
            final String name = readPageName(copy.get(i));
            if ("About".equalsIgnoreCase(name)) {
                insertIndex = i;
                break;
            }
        }
        copy.add(insertIndex, shadersPage);
        return copy;
    }

    public static Object buildShadersPage(final ClassLoader loader,
                                          final Runnable openPackScreenAction,
                                          final Runnable openPackOptionsAction,
                                          final Runnable reloadPipelineAction,
                                          final Supplier<String> statusSupplier) {
        try {
            final Class<?> pageClass = Class.forName("com.aetherium.gui.Page", false, loader);
            final Class<?> settingClass = Class.forName("com.aetherium.gui.Setting", false, loader);
            final Class<?> kindClass = Class.forName("com.aetherium.gui.Setting$Kind", false, loader);
            final Class<?> pixelArtClass = Class.forName("com.aetherium.gui.PixelArt", false, loader);
            final Class<?> bitmapClass = Class.forName("com.aetherium.gui.PixelArt$Bitmap", false, loader);

            Object dropdownKind = null;
            for (final Object c : kindClass.getEnumConstants()) {
                if ("DROPDOWN".equals(((Enum<?>) c).name())) {
                    dropdownKind = c;
                    break;
                }
            }

            final Object icon = resolvePrismIcon(pixelArtClass, bitmapClass);
            final Constructor<?> pageCtor = pageClass.getConstructor(
                    String.class, String.class, String.class, bitmapClass);
            final Method addSetting = pageClass.getMethod("add", settingClass);

            final Method toggleFactory = settingClass.getMethod(
                    "toggle", String.class, String.class, String.class,
                    IntSupplier.class, IntConsumer.class);
            final Method choiceFactory = settingClass.getMethod(
                    "choice", String.class, kindClass, String.class, String.class,
                    String[].class, IntSupplier.class, IntConsumer.class);
            final Method sliderFactory = settingClass.getMethod(
                    "slider", String.class, String.class, String.class,
                    int.class, int.class, int.class,
                    IntFunction.class, IntSupplier.class, IntConsumer.class);
            final Method buttonFactory = settingClass.getMethod(
                    "button", String.class, String.class, String.class,
                    String.class, Runnable.class);
            final Method infoFactory = settingClass.getMethod(
                    "info", String.class, String.class, String.class, Supplier.class);
            final Method availableWhen = settingClass.getMethod(
                    "availableWhen", BooleanSupplier.class, String.class);

            final Object page = pageCtor.newInstance(
                    "Shaders",
                    "Agris Shaders",
                    "Iris/OptiFine shader packs built natively for Aetherium — real-time shadows, GBuffer & composite passes.",
                    icon);

            final AgrisConfig cfg = Agris.config();
            final List<ShaderPackDiscovery.PackEntry> packs = Agris.packs().discoverPacks();
            final String[] packChoices = new String[packs.size() + 1];
            packChoices[0] = "Off";
            for (int i = 0; i < packs.size(); i++) {
                packChoices[i + 1] = packs.get(i).displayName();
            }

            // 1. Live pipeline & Aetherium bridge status
            final Supplier<String> statusFn =
                    statusSupplier != null ? statusSupplier : Agris::describePipelineShort;
            addSetting.invoke(page, infoFactory.invoke(null,
                    "agris.status",
                    "Shader pipeline",
                    "Current Agris OpenGL shader pipeline and Aetherium bridge status.",
                    statusFn));

            // 2. Master Enable Shaders toggle
            addSetting.invoke(page, toggleFactory.invoke(null,
                    "agris.enabled",
                    "Enable shaders",
                    "Master switch for Agris OpenGL shader packs (in-game shortcut: K). Applies live with no restart.",
                    (IntSupplier) () -> cfg.enabled.get() ? 1 : 0,
                    (IntConsumer) v -> {
                        cfg.enabled.set(v != 0);
                        Agris.saveConfigAndReload(reloadPipelineAction);
                    }));

            // 3. Active Shader Pack dropdown
            addSetting.invoke(page, choiceFactory.invoke(null,
                    "agris.pack",
                    dropdownKind,
                    "Shader pack",
                    "Choose from built-in Agris presets (Aetherial, Complementary Lite) or any .zip/folder pack in shaderpacks/.",
                    packChoices,
                    (IntSupplier) () -> {
                        if (!cfg.enabled.get() || cfg.shaderPack.get().isBlank()) {
                            return 0;
                        }
                        final String activeId = cfg.shaderPack.get();
                        for (int i = 0; i < packs.size(); i++) {
                            if (packs.get(i).id().equalsIgnoreCase(activeId)) {
                                return i + 1;
                            }
                        }
                        return 0;
                    },
                    (IntConsumer) idx -> {
                        if (idx <= 0 || idx > packs.size()) {
                            cfg.enabled.set(false);
                        } else {
                            cfg.shaderPack.set(packs.get(idx - 1).id());
                            cfg.enabled.set(true);
                        }
                        Agris.saveConfigAndReload(reloadPipelineAction);
                    }));

            // 4. Open full Shader Pack Manager Screen
            if (openPackScreenAction != null) {
                addSetting.invoke(page, buttonFactory.invoke(null,
                        "agris.open_packs",
                        "Shader pack manager",
                        "Browse shaderpacks/, inspect compile logs, switch packs, or open the shaderpacks folder (shortcut: O).",
                        "Open...",
                        openPackScreenAction));
            }

            // 5. Open Shader Pack Options Screen
            if (openPackOptionsAction != null) {
                final Object optBtn = buttonFactory.invoke(null,
                        "agris.open_options",
                        "Shader pack options",
                        "Customize profiles (Low / Medium / High / Ultra) and #define / slider parameters of the active shader pack.",
                        "Configure...",
                        openPackOptionsAction);
                availableWhen.invoke(optBtn,
                        (BooleanSupplier) () -> cfg.enabled.get() && !cfg.shaderPack.get().isBlank(),
                        "Enable a shader pack first.");
                addSetting.invoke(page, optBtn);
            }

            // 6. Shadow pass toggle
            final Object shadowToggle = toggleFactory.invoke(null,
                    "agris.shadow_enabled",
                    "Real-time shadows",
                    "Render directional sun and moon shadow maps when used by the active shader pack.",
                    (IntSupplier) () -> cfg.shadowEnabled.get() ? 1 : 0,
                    (IntConsumer) v -> {
                        cfg.shadowEnabled.set(v != 0);
                        Agris.saveConfigAndReload(reloadPipelineAction);
                    });
            availableWhen.invoke(shadowToggle, (BooleanSupplier) cfg.enabled::get, "Shaders are turned off.");
            addSetting.invoke(page, shadowToggle);

            // 7. Shadow max distance slider
            final Object shadowDist = sliderFactory.invoke(null,
                    "agris.shadow_distance",
                    "Shadow distance",
                    "Maximum chunk radius drawn into the shadow map. Lower values give a large FPS boost alongside Aetherium.",
                    2, 32, 1,
                    (IntFunction<String>) v -> v + " chunks",
                    (IntSupplier) cfg.shadowMaxDistance::get,
                    (IntConsumer) v -> {
                        cfg.shadowMaxDistance.set(v);
                        Agris.store().save();
                    });
            availableWhen.invoke(shadowDist,
                    (BooleanSupplier) () -> cfg.enabled.get() && cfg.shadowEnabled.get(),
                    "Real-time shadows are turned off.");
            addSetting.invoke(page, shadowDist);

            // 8. Internal shader resolution scale
            final Object scaleSlider = sliderFactory.invoke(null,
                    "agris.render_scale",
                    "Shader quality scale",
                    "Internal GBuffer & composite resolution multiplier. Works smoothly together with Aetherium's Render Scale.",
                    50, 150, 5,
                    (IntFunction<String>) v -> v + "%",
                    (IntSupplier) cfg.renderScale::get,
                    (IntConsumer) v -> {
                        cfg.renderScale.set(v);
                        Agris.saveConfigAndReload(reloadPipelineAction);
                    });
            availableWhen.invoke(scaleSlider, (BooleanSupplier) cfg.enabled::get, "Shaders are turned off.");
            addSetting.invoke(page, scaleSlider);

            // 9. Mobile TBDR & Tile Bandwidth Mode
            final String[] mobileModes = {"Auto (Detect Mobile/TBDR)", "Force On (Mobile TBDR)", "Off (Desktop Full)"};
            addSetting.invoke(page, choiceFactory.invoke(null,
                    "agris.mobile_mode",
                    dropdownKind,
                    "Mobile GPU optimization",
                    "Optimizes FBO tile bandwidth (R11G11B10F HDR compaction, unused depth/shadow buffer stripping, tile discard) for Adreno, Mali, Apple Silicon, and Android launchers.",
                    mobileModes,
                    (IntSupplier) () -> switch (cfg.mobileMode.get()) {
                        case ON -> 1;
                        case OFF -> 2;
                        case AUTO -> 0;
                    },
                    (IntConsumer) idx -> {
                        cfg.mobileMode.set(idx == 1
                                ? AgrisConfig.MobileMode.ON
                                : (idx == 2 ? AgrisConfig.MobileMode.OFF : AgrisConfig.MobileMode.AUTO));
                        Agris.saveConfigAndReload(reloadPipelineAction);
                    }));

            // 10. Mobile Dynamic Resolution Governor
            final Object dynScaleToggle = toggleFactory.invoke(null,
                    "agris.mobile_dyn_scale",
                    "Dynamic FPS resolution",
                    "Automatically steps internal shader scale in smooth 5% increments to hold your target FPS without thermal throttling.",
                    (IntSupplier) () -> cfg.mobileDynamicScale.get() ? 1 : 0,
                    (IntConsumer) v -> {
                        cfg.mobileDynamicScale.set(v != 0);
                        Agris.store().save();
                    });
            availableWhen.invoke(dynScaleToggle, (BooleanSupplier) cfg.enabled::get, "Shaders are turned off.");
            addSetting.invoke(page, dynScaleToggle);

            // 11. Mobile Target FPS Slider
            final Object targetFpsSlider = sliderFactory.invoke(null,
                    "agris.mobile_target_fps",
                    "Mobile target FPS",
                    "Target framerate used by the zero-allocation EMA resolution governor (e.g., 60 FPS balanced, 90/120 FPS flagship).",
                    30, 120, 15,
                    (IntFunction<String>) v -> v + " FPS",
                    (IntSupplier) cfg.mobileTargetFps::get,
                    (IntConsumer) v -> {
                        cfg.mobileTargetFps.set(v);
                        Agris.store().save();
                    });
            availableWhen.invoke(targetFpsSlider,
                    (BooleanSupplier) () -> cfg.enabled.get() && cfg.mobileDynamicScale.get(),
                    "Dynamic FPS resolution is turned off.");
            addSetting.invoke(page, targetFpsSlider);

            // 12. Mobile Shadow Update Cadence
            final String[] shadowCadenceChoices = {"Auto (Half-Rate on Mobile)", "Every Frame (1/1)", "Half-Rate World-Locked (1/2)", "Battery Saver (1/3)"};
            final Object shadowCadence = choiceFactory.invoke(null,
                    "agris.mobile_shadow_interval",
                    dropdownKind,
                    "Shadow update rate",
                    "Updates directional shadows every N frames with camera-relative matrix latching so shadows never swim when moving.",
                    shadowCadenceChoices,
                    (IntSupplier) () -> Math.max(0, Math.min(3, cfg.mobileShadowCadence.get())),
                    (IntConsumer) idx -> {
                        cfg.mobileShadowCadence.set(idx);
                        Agris.store().save();
                    });
            availableWhen.invoke(shadowCadence,
                    (BooleanSupplier) () -> cfg.enabled.get() && cfg.shadowEnabled.get(),
                    "Real-time shadows are turned off.");
            addSetting.invoke(page, shadowCadence);

            // 13. Shadow culling guard toggle
            addSetting.invoke(page, toggleFactory.invoke(null,
                    "agris.shadow_cull_guard",
                    "Shadow caster guard",
                    "Pause Aetherium's camera-facing SectionCull during the shadow pass so terrain behind you still casts shadows.",
                    (IntSupplier) () -> cfg.suspendCullInShadows.get() ? 1 : 0,
                    (IntConsumer) v -> {
                        cfg.suspendCullInShadows.set(v != 0);
                        Agris.store().save();
                    }));

            // 14. Reload shaders button
            if (reloadPipelineAction != null) {
                addSetting.invoke(page, buttonFactory.invoke(null,
                        "agris.reload",
                        "Reload shader pipeline",
                        "Re-read shaderpacks/ from disk and recompile all GLSL programs (shortcut: R).",
                        "Reload",
                        reloadPipelineAction));
            }

            return page;
        } catch (final Throwable error) {
            AgrisLog.warn("Unable to inject Agris Shaders tab into AetheriumScreen: {}", error.toString());
            return null;
        }
    }

    private static Object resolvePrismIcon(final Class<?> pixelArtClass, final Class<?> bitmapClass)
            throws ReflectiveOperationException {
        try {
            final Constructor<?> ctor = bitmapClass.getDeclaredConstructor(String[].class);
            ctor.setAccessible(true);
            return ctor.newInstance((Object) PRISM_ICON_ROWS);
        } catch (final Throwable ignored) {
            final Field sparkles = pixelArtClass.getField("SPARKLES");
            return sparkles.get(null);
        }
    }

    private static String readPageName(final Object page) {
        try {
            final Field f = page.getClass().getField("name");
            final Object v = f.get(page);
            return v == null ? "" : v.toString();
        } catch (final ReflectiveOperationException ignored) {
            return "";
        }
    }
}
