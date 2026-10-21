package net.fabricmc.resourcetracker.importing;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** The only version-specific registry and data-version calls used by the importer. */
final class ImportCompat {
    private ImportCompat() { }
    static int dataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }
    static Block block(String name) {
        try {
            Identifier id = Identifier.parse(name);
            return BuiltInRegistries.BLOCK.containsKey(id) ? BuiltInRegistries.BLOCK.getValue(id) : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
