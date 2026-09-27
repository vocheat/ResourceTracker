package net.fabricmc.resourcetracker.bootstrap;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;

public final class VersionDispatchLanguageAdapter implements LanguageAdapter {
    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type)
            throws LanguageAdapterException {
        if (!"modmenu".equals(value)) {
            throw new LanguageAdapterException("Unknown ResourceTracker entrypoint: " + value);
        }
        String version = ResourceTrackerBootstrap.minecraftVersion();
        String className = VersionMatrix.modMenuClass(version);
        try {
            return type.cast(Class.forName(className, true, getClass().getClassLoader())
                    .getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            throw new LanguageAdapterException("ResourceTracker failed to load " + className
                    + " for Minecraft " + version, e);
        }
    }
}
