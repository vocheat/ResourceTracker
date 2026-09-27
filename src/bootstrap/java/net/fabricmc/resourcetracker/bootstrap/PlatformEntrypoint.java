package net.fabricmc.resourcetracker.bootstrap;

/** Stable boundary between the loader and a version-specific implementation. */
public interface PlatformEntrypoint {
    void initializeClient();
}
