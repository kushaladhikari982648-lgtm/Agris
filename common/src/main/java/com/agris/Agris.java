package com.agris;

import com.agris.compat.AetheriumBridge;
import com.agris.compat.ShaderConflictScanner;
import com.agris.config.AgrisConfig;
import com.agris.config.ConfigStore;
import com.agris.pack.ShaderPackDiscovery;
import com.agris.pipeline.PipelineManager;
import com.agris.platform.PlatformAdapter;
import com.agris.platform.PlatformServices;
import com.agris.util.AgrisLog;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Central lifecycle and subsystem coordinator for <b>Agris</b> — the Iris-compatible
 * OpenGL shader pack engine built natively for <b>Aetherium</b>.
 */
public final class Agris {
    public static final String MOD_ID = "agris";
    public static final String MOD_NAME = "Agris";
    public static final String VERSION = "1.1.0";

    public enum State {
        OFF,
        ACTIVE,
        STANDBY,
        CONFLICT_BLOCKED,
        CLOSED
    }

    public static final class Subsystems {
        public final PlatformAdapter platform;
        public final AgrisConfig config;
        public final ConfigStore store;
        public final ShaderConflictScanner.ScanReport conflicts;
        public final AetheriumBridge aetherium;
        public final ShaderPackDiscovery packs;
        public final PipelineManager pipeline;

        Subsystems(final PlatformAdapter platform,
                   final AgrisConfig config,
                   final ConfigStore store,
                   final ShaderConflictScanner.ScanReport conflicts,
                   final AetheriumBridge aetherium,
                   final ShaderPackDiscovery packs,
                   final PipelineManager pipeline) {
            this.platform = platform;
            this.config = config;
            this.store = store;
            this.conflicts = conflicts;
            this.aetherium = aetherium;
            this.packs = packs;
            this.pipeline = pipeline;
        }
    }

    private static volatile State state = State.OFF;
    private static volatile Subsystems subsystems;
    private static volatile Consumer<Object> screenOpener;

    private Agris() {
    }

    public static synchronized boolean initialize() {
        if (subsystems != null && state != State.CLOSED) {
            return state == State.ACTIVE || state == State.STANDBY;
        }
        final PlatformAdapter platform = PlatformServices.get();
        if (!platform.isClient()) {
            state = State.OFF;
            AgrisLog.info("Dedicated server environment detected; Agris client shader pipeline remains off.");
            return false;
        }

        final AgrisConfig config = AgrisConfig.createDefaults();
        final Path configFile = platform.configDirectory().resolve("agris.json");
        final ConfigStore store = new ConfigStore(configFile, config);
        store.load();

        final ShaderConflictScanner.ScanReport conflicts = ShaderConflictScanner.scan(platform);
        final AetheriumBridge aetherium = new AetheriumBridge();
        aetherium.probe();

        final ShaderPackDiscovery packs = new ShaderPackDiscovery(platform.shaderpacksDirectory());
        packs.ensureDirectoryExists();

        final PipelineManager pipeline = new PipelineManager(
                config, packs, aetherium, conflicts.blocksPipeline());

        subsystems = new Subsystems(platform, config, store, conflicts, aetherium, packs, pipeline);

        if (conflicts.blocksPipeline()) {
            state = State.CONFLICT_BLOCKED;
            if (config.notifyConflicts.get()) {
                AgrisLog.warn("Standing down due to incompatible mod(s): {}", conflicts.summary());
            }
            return false;
        }

        refreshState();
        AgrisLog.info("Initialized {} {} on {} (MC {}) | {} | {}",
                MOD_NAME, VERSION, platform.platformName(), platform.minecraftVersion(),
                aetherium.describeStatus(), conflicts.summary());
        return true;
    }

    public static void refreshState() {
        final Subsystems sub = subsystems;
        if (sub == null) {
            return;
        }
        if (sub.conflicts.blocksPipeline()) {
            state = State.CONFLICT_BLOCKED;
        } else if (sub.config.enabled.get() && !sub.config.shaderPack.get().isBlank()) {
            state = State.ACTIVE;
        } else {
            state = State.STANDBY;
        }
    }

    public static void saveConfigAndReload(final Runnable extraCallback) {
        final Subsystems sub = ensureSubsystems();
        sub.store.save();
        refreshState();
        sub.pipeline.requestReload();
        if (extraCallback != null) {
            extraCallback.run();
        }
    }

    public static synchronized void shutdown() {
        final Subsystems sub = subsystems;
        if (sub != null) {
            sub.pipeline.destroyPipeline();
            sub.aetherium.syncShaderPackActive(false, sub.config);
            sub.store.save();
        }
        state = State.CLOSED;
        subsystems = null;
    }

    public static Subsystems ensureSubsystems() {
        Subsystems sub = subsystems;
        if (sub == null) {
            initialize();
            sub = subsystems;
        }
        if (sub == null) {
            // Headless fallback (e.g. unit tests running on dedicated-server mock).
            final PlatformAdapter platform = PlatformServices.get();
            final AgrisConfig cfg = AgrisConfig.createDefaults();
            final ConfigStore store = new ConfigStore(platform.configDirectory().resolve("agris.json"), cfg);
            final ShaderConflictScanner.ScanReport scan = ShaderConflictScanner.scan(platform);
            final AetheriumBridge bridge = new AetheriumBridge();
            final ShaderPackDiscovery disc = new ShaderPackDiscovery(platform.shaderpacksDirectory());
            final PipelineManager mgr = new PipelineManager(cfg, disc, bridge, scan.blocksPipeline());
            sub = new Subsystems(platform, cfg, store, scan, bridge, disc, mgr);
            subsystems = sub;
        }
        return sub;
    }

    public static Subsystems subsystemsOrNull() {
        return subsystems;
    }

    public static State getState() {
        return state;
    }

    public static AgrisConfig config() {
        return ensureSubsystems().config;
    }

    public static ConfigStore store() {
        return ensureSubsystems().store;
    }

    public static ShaderPackDiscovery packs() {
        return ensureSubsystems().packs;
    }

    public static PipelineManager pipeline() {
        return ensureSubsystems().pipeline;
    }

    public static AetheriumBridge aetherium() {
        return ensureSubsystems().aetherium;
    }

    public static ShaderConflictScanner.ScanReport conflicts() {
        return ensureSubsystems().conflicts;
    }

    public static void installScreenOpener(final Consumer<Object> opener) {
        screenOpener = Objects.requireNonNull(opener, "opener");
    }

    public static void openShaderPackScreen(final Object parentMinecraftScreen) {
        final Consumer<Object> opener = screenOpener;
        if (opener != null) {
            opener.accept(parentMinecraftScreen);
        }
    }

    public static String describePipelineShort() {
        final Subsystems sub = subsystems;
        if (sub == null) {
            return "Uninitialized";
        }
        if (sub.conflicts.blocksPipeline()) {
            return "Blocked (" + sub.conflicts.summary() + ')';
        }
        if (!sub.config.enabled.get()) {
            return "Off (" + sub.aetherium.describeStatus() + ')';
        }
        final String err = sub.pipeline.lastErrorMessage();
        if (err != null && !err.isEmpty()) {
            return "Fallback (" + err + ')';
        }
        final var pack = sub.pipeline.loadedPackOrNull();
        final String packTitle = pack != null ? pack.displayName() : sub.config.shaderPack.get();
        final var active = sub.pipeline.activePipelineOrNull();
        if (active != null && active.isMobileOptimizationActive()) {
            return "Active: " + packTitle + " [" + active.activeScalePercent() + "% Mobile]";
        }
        return "Active: " + packTitle;
    }
}
