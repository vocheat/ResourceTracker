package net.fabricmc.resourcetracker.importing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class MinecraftImportEnvironment implements ImportService.Environment {
    @Override public boolean validItem(String id) { return VersionCompat.isValidItemId(id); }
    @Override public int dataVersion() { return ImportCompat.dataVersion(); }

    @Override public Map<String, Integer> resolve(String id, Map<String, String> properties, int sourceVersion) throws IOException {
        if (!id.startsWith("minecraft:")) throw new IOException("Modded block is unsupported: " + id);
        if (sourceVersion < dataVersion()) {
            JsonObject state = new JsonObject();
            state.addProperty("Name", id);
            JsonObject stateProperties = new JsonObject();
            properties.forEach(stateProperties::addProperty);
            state.add("Properties", stateProperties);
            try {
                JsonElement updated = DataFixers.getDataFixer().update(References.BLOCK_STATE,
                        new Dynamic<>(JsonOps.INSTANCE, state), sourceVersion, dataVersion()).getValue();
                if (!updated.isJsonObject()) throw new IllegalArgumentException("No upgraded block state");
                JsonObject object = updated.getAsJsonObject();
                // Minecraft 26.3's DFU renames the serialized state fields to id/properties.
                // Read the format produced by the game, including states predating that fix.
                String nameField = object.has("id") ? "id" : "Name";
                String propertiesField = object.has("id") ? "properties" : "Properties";
                if (!object.has(nameField)) throw new IllegalArgumentException("No upgraded block name");
                id = object.get(nameField).getAsString();
                properties = new LinkedHashMap<>();
                if (object.has(propertiesField)) for (var entry : object.getAsJsonObject(propertiesField).entrySet()) properties.put(entry.getKey(), entry.getValue().getAsString());
            } catch (RuntimeException exception) {
                throw new IOException("Cannot upgrade block state " + id + ": " + exception.getMessage(), exception);
            }
        }
        return BlockMaterialResolver.resolve(id, properties);
    }
}
