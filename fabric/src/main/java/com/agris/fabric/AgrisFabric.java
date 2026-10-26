package com.agris.fabric;

import com.agris.Agris;
import com.agris.platform.PlatformServices;
import com.agris.util.AgrisLog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric client entrypoint for Agris.
 */
public final class AgrisFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        final FabricLoader loader = FabricLoader.getInstance();
        PlatformServices.install(new FabricPlatformAdapter(loader));
        AgrisLog.info("Agris {} starting on Fabric Loader {} (MC {})",
                Agris.VERSION,
                loader.getModContainer("fabricloader")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"),
                loader.getModContainer("minecraft")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"));
        Agris.initialize();
    }
}
