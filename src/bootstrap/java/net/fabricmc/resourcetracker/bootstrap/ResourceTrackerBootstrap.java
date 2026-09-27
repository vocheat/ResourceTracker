package net.fabricmc.resourcetracker.bootstrap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class ResourceTrackerBootstrap implements ClientModInitializer {
    static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .orElseThrow(() -> new IllegalStateException("Minecraft mod container is unavailable"))
                .getMetadata().getVersion().getFriendlyString();
    }

    @Override
    public void onInitializeClient() {
        String version = minecraftVersion();
        String className = VersionMatrix.platformClass(version);
        try {
            Class<?> loaded = Class.forName(className, true, getClass().getClassLoader());
            PlatformEntrypoint platform = loaded.asSubclass(PlatformEntrypoint.class)
                    .getDeclaredConstructor().newInstance();
            platform.initializeClient();
            System.out.println("[ResourceTracker] UNIVERSAL_READY mc=" + version
                    + " platform=" + VersionMatrix.platformId(version));
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("ResourceTracker failed to load " + className
                    + " for Minecraft " + version, e);
        }
    }
}
