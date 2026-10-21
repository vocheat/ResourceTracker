package net.fabricmc.resourcetracker.importing;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** An immutable preview. Reading a file never changes tracker configuration. */
public record ImportResult(String name, Path source, Map<String, Integer> materials,
                           List<String> diagnostics, boolean valid) {
    public ImportResult {
        materials = Collections.unmodifiableMap(new LinkedHashMap<>(materials));
        diagnostics = List.copyOf(diagnostics);
    }

    public ImportResult(String name, Path source, Map<String, Integer> materials, List<String> diagnostics) {
        this(name, source, materials, diagnostics, diagnostics.isEmpty());
    }

    public boolean canImport() {
        return valid && !materials.isEmpty();
    }
}
