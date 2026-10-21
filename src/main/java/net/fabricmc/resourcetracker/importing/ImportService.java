package net.fabricmc.resourcetracker.importing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Strict, bounded imports. Invalid input is a non-importable preview, never a partial list. */
public final class ImportService {
    static final long MAX_VOLUME = 64_000_000L;
    private static final long MAX_TEXT_BYTES = 16L * 1024 * 1024;

    private ImportService() { }

    public static ImportResult read(Path path) throws IOException {
        return read(path, new MinecraftImportEnvironment());
    }

    interface Environment {
        boolean validItem(String id);
        int dataVersion();
        Map<String, Integer> resolve(String id, Map<String, String> properties, int sourceVersion) throws IOException;
    }

    static ImportResult read(Path path, Environment environment) throws IOException {
        checkInterrupted();
        Path source = path.toAbsolutePath().normalize();
        String fileName = source.getFileName().toString();
        String name = fileName.replaceFirst("(?i)\\.(litematic|json|txt)$", "");
        if (!Files.isRegularFile(source)) throw new IOException("Import source is not a regular file: " + source);
        List<String> diagnostics = new ArrayList<>();
        Map<String, Integer> materials = new LinkedHashMap<>();
        try {
            String extension = fileName.toLowerCase(Locale.ROOT);
            if (extension.endsWith(".litematic")) name = litematic(source, name, materials, diagnostics, environment);
            else if (extension.endsWith(".json")) name = json(source, name, materials, environment);
            else if (extension.endsWith(".txt")) name = text(source, name, materials, environment);
            else throw new IOException("Unsupported file type; choose .litematic, .json or .txt");
            if (materials.isEmpty()) throw new IOException("The file contains no importable materials");
            return new ImportResult(name, source, materials, diagnostics, true);
        } catch (IOException | IllegalArgumentException | ArithmeticException exception) {
            diagnostics.add(exception.getMessage() == null ? "Malformed import file" : exception.getMessage());
            return new ImportResult(name, source, Map.of(), diagnostics, false);
        }
    }

    private static String text(Path path, String name, Map<String, Integer> materials, Environment environment) throws IOException {
        String content = textContent(path);
        boolean inItems = false;
        boolean sawItems = false;
        boolean header = true;
        int lineNumber = 0;
        for (String raw : content.split("\\R", -1)) {
            checkInterrupted();
            lineNumber++;
            String line = raw.strip();
            if (lineNumber == 1) line = line.replace("\ufeff", "");
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                header = false;
                inItems = line.equalsIgnoreCase("[items]");
                sawItems |= inItems;
                continue;
            }
            int separator = line.indexOf('=');
            if (!inItems) {
                if (header && separator > 0 && line.substring(0, separator).strip().equalsIgnoreCase("name")) {
                    String candidate = line.substring(separator + 1).strip();
                    if (!candidate.isEmpty()) name = candidate;
                }
                continue;
            }
            if (separator < 1) throw new IOException("Invalid item entry on TXT line " + lineNumber);
            String id = itemId(line.substring(0, separator).strip(), environment);
            int count;
            try { count = Integer.parseInt(line.substring(separator + 1).strip()); }
            catch (NumberFormatException exception) { throw new IOException("Invalid item count on TXT line " + lineNumber); }
            merge(materials, id, count);
        }
        if (!sawItems) throw new IOException("Not a ResourceTracker TXT list: missing [items] section");
        return name;
    }

    private static String json(Path path, String name, Map<String, Integer> materials, Environment environment) throws IOException {
        JsonElement parsed;
        try { parsed = JsonParser.parseString(textContent(path)); }
        catch (RuntimeException exception) { throw new IOException("Malformed materials JSON: " + exception.getMessage()); }
        if (!parsed.isJsonObject()) throw new IOException("Materials JSON root must be an object");
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("Materials") || !root.get("Materials").isJsonArray())
            throw new IOException("Not a Litematica materials export: missing Materials array");
        if (root.has("Name") && root.get("Name").isJsonPrimitive() && root.get("Name").getAsJsonPrimitive().isString()) name = root.get("Name").getAsString();
        JsonArray entries = root.getAsJsonArray("Materials");
        for (int index = 0; index < entries.size(); index++) {
            checkInterrupted();
            JsonElement entry = entries.get(index);
            if (!entry.isJsonObject()) throw new IOException("Invalid materials entry " + (index + 1));
            JsonObject object = entry.getAsJsonObject();
            if (!object.has("Item") || !object.has("Total")) throw new IOException("Materials entry needs Item and Total: " + (index + 1));
            String id = jsonItem(object.get("Item"));
            int count = positiveJsonInt(object.get("Total"));
            // Exported Total already includes Litematica's multiplier. Missing and Available are informational.
            merge(materials, itemId(id, environment), count);
        }
        return name;
    }

    private static String jsonItem(JsonElement value) throws IOException {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return value.getAsString();
        if (value.isJsonObject()) {
            JsonObject item = value.getAsJsonObject();
            for (var entry : item.entrySet()) {
                if (!List.of("id", "Id", "Count", "count").contains(entry.getKey()))
                    throw new IOException("Component-sensitive Item cannot be tracked by item ID: " + entry.getKey());
            }
            JsonElement id = item.has("id") ? item.get("id") : item.get("Id");
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) return id.getAsString();
        }
        throw new IOException("Unsupported Item encoding in materials JSON");
    }

    private static int positiveJsonInt(JsonElement value) throws IOException {
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new NumberFormatException();
            int result = new BigDecimal(value.getAsString()).intValueExact();
            if (result < 1) throw new NumberFormatException();
            return result;
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IOException("Material Total must be a positive 32-bit integer");
        }
    }

    private static String textContent(Path path) throws IOException {
        if (Files.size(path) > MAX_TEXT_BYTES) throw new IOException("Text import exceeds 16 MiB limit");
        try (InputStream input = Files.newInputStream(path)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkInterrupted();
                if ((long) bytes.size() + count > MAX_TEXT_BYTES) throw new IOException("Text import exceeds 16 MiB limit");
                bytes.write(buffer, 0, count);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }

    private static String litematic(Path path, String name, Map<String, Integer> materials,
                                    List<String> diagnostics, Environment environment) throws IOException {
        if (Files.size(path) > BoundedNbtReader.MAX_BYTES) throw new IOException("Compressed schematic exceeds 256 MiB limit");
        Map<String, Object> root;
        try (InputStream file = Files.newInputStream(path); InputStream input = new GZIPInputStream(file)) {
            root = BoundedNbtReader.read(input);
        }
        int format = integer(root.get("Version"), "Version");
        if (format < 5 || format > 7) throw new IOException("Unsupported litematic format " + format + "; supported versions are 5–7");
        int dataVersion = integer(root.get("MinecraftDataVersion"), "MinecraftDataVersion");
        if (dataVersion < 0) throw new IOException("Invalid MinecraftDataVersion");
        if (dataVersion > environment.dataVersion()) throw new IOException("Schematic uses a future Minecraft data version: " + dataVersion);
        Map<String, Object> metadata = optionalCompound(root.get("Metadata"));
        if (metadata.get("Name") instanceof String title && !title.isBlank()) name = title;
        Map<String, Object> regions = compound(root.get("Regions"), "Regions");
        if (regions.isEmpty()) throw new IOException("Schematic has no regions");
        diagnostics.add("Block entity contents and decorative NBT are excluded");
        diagnostics.add("Entities are excluded; only block placement materials are counted");
        long totalVolume = 0;
        for (var regionEntry : regions.entrySet()) {
            checkInterrupted();
            Map<String, Object> region = compound(regionEntry.getValue(), "Region " + regionEntry.getKey());
            Map<String, Object> size = compound(region.get("Size"), "Size");
            long x = dimension(size.get("x")), y = dimension(size.get("y")), z = dimension(size.get("z"));
            long volume;
            try { volume = Math.multiplyExact(Math.multiplyExact(x, y), z); totalVolume = Math.addExact(totalVolume, volume); }
            catch (ArithmeticException exception) { throw new IOException("Schematic region volume overflow"); }
            if (volume == 0 || totalVolume > MAX_VOLUME) throw new IOException("Schematic exceeds total volume limit of 64 million blocks");
            List<?> palette = list(region.get("BlockStatePalette"), "BlockStatePalette");
            if (palette.isEmpty() || palette.size() > 1_000_000) throw new IOException("Invalid block-state palette size");
            if (!(region.get("BlockStates") instanceof long[] packed)) throw new IOException("Missing BlockStates long array");
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long requiredLongs = (volume * bits + 63) / 64;
            if (packed.length != requiredLongs) throw new IOException("BlockStates length does not match region volume and palette");
            long[] occurrences = new long[palette.size()];
            for (long index = 0; index < volume; index++) {
                if ((index & 4095) == 0) checkInterrupted();
                int paletteIndex = packedIndex(packed, bits, index);
                if (paletteIndex >= palette.size()) throw new IOException("BlockStates contains out-of-range palette index " + paletteIndex);
                occurrences[paletteIndex]++;
            }
            for (int index = 0; index < palette.size(); index++) {
                checkInterrupted();
                Map<String, Object> state = compound(palette.get(index), "Palette state");
                if (!(state.get("Name") instanceof String id)) throw new IOException("Palette state is missing Name");
                for (String key : state.keySet()) if (!key.equals("Name") && !key.equals("Properties"))
                    throw new IOException("Unsupported block-state data: " + key);
                Map<String, String> properties = new LinkedHashMap<>();
                for (var property : optionalCompound(state.get("Properties")).entrySet()) {
                    if (!(property.getValue() instanceof String value)) throw new IOException("Invalid block property: " + property.getKey());
                    properties.put(property.getKey(), value);
                }
                // Validate every palette entry, including unused entries, so corrupt/unknown states are never silently hidden.
                Map<String, Integer> resolved = environment.resolve(id, properties, dataVersion);
                for (var material : resolved.entrySet()) {
                    if (occurrences[index] != 0) merge(materials, itemId(material.getKey(), environment), Math.toIntExact(occurrences[index] * material.getValue()));
                }
            }
            if (region.containsKey("TileEntities")) list(region.get("TileEntities"), "TileEntities");
            if (region.containsKey("Entities")) list(region.get("Entities"), "Entities");
        }
        if (dataVersion < environment.dataVersion()) diagnostics.add("Block states were upgraded from Minecraft data version " + dataVersion);
        return name;
    }

    static int packedIndex(long[] data, int bits, long index) {
        long bit = index * bits;
        int offset = (int) (bit & 63);
        int word = (int) (bit >>> 6);
        long value = data[word] >>> offset;
        if (offset + bits > 64) value |= data[word + 1] << (64 - offset);
        return (int) (value & ((1L << bits) - 1));
    }

    private static long dimension(Object value) throws IOException {
        long dimension = integer(value, "Size coordinate");
        // A negative litematic size includes its origin exactly as a positive size does.
        return Math.abs(dimension);
    }

    private static int integer(Object value, String field) throws IOException {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer)) throw new IOException("Missing or invalid integer " + field);
        return ((Number) value).intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> compound(Object value, String field) throws IOException {
        if (!(value instanceof Map<?, ?>)) throw new IOException("Missing or invalid compound " + field);
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> optionalCompound(Object value) throws IOException {
        return value == null ? Map.of() : compound(value, "compound");
    }

    private static List<?> list(Object value, String field) throws IOException {
        if (!(value instanceof List<?> result)) throw new IOException("Missing or invalid list " + field);
        return result;
    }

    private static String itemId(String raw, Environment environment) throws IOException {
        String id = raw.contains(":") ? raw : "minecraft:" + raw;
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || id.equals("minecraft:air") || !environment.validItem(id))
            throw new IOException("Unknown or unsupported item: " + raw);
        return id;
    }

    private static void merge(Map<String, Integer> materials, String id, int count) throws IOException {
        if (count < 1) throw new IOException("Material count must be a positive integer: " + id);
        try { materials.merge(id, count, Math::addExact); }
        catch (ArithmeticException exception) { throw new IOException("Combined material count exceeds 32-bit integer limit: " + id); }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Import cancelled");
    }
}
