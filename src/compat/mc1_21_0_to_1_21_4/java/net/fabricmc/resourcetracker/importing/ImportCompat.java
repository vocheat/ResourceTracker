package net.fabricmc.resourcetracker.importing;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** The only version-specific registry and data-version calls used by the importer. */
final class ImportCompat {
    private ImportCompat() { }
    static int dataVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }
    static Block block(String name) {
        try {
            ResourceLocation id = ResourceLocation.parse(name);
            return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
