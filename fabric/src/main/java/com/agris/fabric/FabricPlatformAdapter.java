package com.agris.fabric;

import com.agris.platform.PlatformAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Fabric implementation of {@link PlatformAdapter}.
 */
public final class FabricPlatformAdapter implements PlatformAdapter {
    private final FabricLoader loader;
    private final String minecraftVersion;

    public FabricPlatformAdapter() {
        this(FabricLoader.getInstance());
    }

    public FabricPlatformAdapter(final FabricLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.minecraftVersion = loader.getGameDir() == null
                ? "unknown"
                : loader.getModContainer("minecraft")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown");
    }

    @Override
    public String platformName() {
        return "fabric";
    }

    @Override
    public String minecraftVersion() {
        return this.minecraftVersion;
    }

    @Override
    public Path gameDirectory() {
        return this.loader.getGameDir();
    }

    @Override
    public Path configDirectory() {
        return this.loader.getConfigDir();
    }

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return this.loader.isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(final String modId) {
        return this.loader.isModLoaded(modId);
    }

    @Override
    public boolean isExternalModLoaded(final String modId) {
        if ("agris".equals(modId)) {
            return false;
        }
        final Optional<ModContainer> container = this.loader.getModContainer(modId);
        return container.map(c -> !"agris".equals(c.getMetadata().getId())).orElse(false);
    }

    @Override
    public String getModVersion(final String modId) {
        return this.loader.getModContainer(modId)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
