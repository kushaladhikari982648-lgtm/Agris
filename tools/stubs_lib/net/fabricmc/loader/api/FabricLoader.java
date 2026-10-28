package net.fabricmc.loader.api;
import java.nio.file.Path; import java.util.Optional;
public interface FabricLoader { static FabricLoader getInstance() { return null; } Path getGameDir(); Path getConfigDir(); boolean isModLoaded(String id); Optional<ModContainer> getModContainer(String id); boolean isDevelopmentEnvironment(); }
