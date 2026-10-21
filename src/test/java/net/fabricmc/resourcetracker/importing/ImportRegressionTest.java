package net.fabricmc.resourcetracker.importing;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/** Standalone regression runner; no test framework or external schematic files required. */
public final class ImportRegressionTest {
    private static final ImportService.Environment ENVIRONMENT = new ImportService.Environment() {
        @Override public boolean validItem(String id) { return Set.of("minecraft:stone", "minecraft:dirt", "minecraft:oak_slab").contains(id); }
        @Override public int dataVersion() { return 5000; }
        @Override public Map<String, Integer> resolve(String id, Map<String, String> properties, int version) throws IOException {
            if (id.equals("minecraft:air")) return Map.of();
            if (!validItem(id)) throw new IOException("Unknown block: " + id);
            return Map.of(id, "double".equals(properties.get("type")) ? 2 : 1);
        }
    };

    private ImportRegressionTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("resourcetracker-import-test-");
        try {
            textAndJson(directory);
            packedStates();
            schematicFiles(directory);
            boundedNbt();
            Thread.currentThread().interrupt();
            try { ImportService.read(directory.resolve("native.txt"), ENVIRONMENT); throw new AssertionError("Interrupted import continued"); }
            catch (java.io.InterruptedIOException expected) { }
            finally { Thread.interrupted(); }
            if (List.of(args).contains("--minecraft")) {
                minecraftMaterials();
                minecraftSchematics(directory);
            }
            System.out.println("Importer regression checks passed");
        } finally {
            try (var files = Files.list(directory)) { for (Path path : files.toList()) Files.delete(path); }
            Files.delete(directory);
        }
    }

    private static void textAndJson(Path directory) throws Exception {
        Path text = directory.resolve("native.txt");
        Files.writeString(text, "# ResourceTracker list v1\nname=Native\nx=99\n[flags]\nignored=true\n[items]\nminecraft:stone=2\nstone=3\n");
        ImportResult result = ImportService.read(text, ENVIRONMENT);
        require(result.canImport() && result.name().equals("Native") && result.materials().get("minecraft:stone") == 5, "TXT compatibility and duplicate merge");
        try { result.materials().put("minecraft:dirt", 1); throw new AssertionError("Mutable result"); }
        catch (UnsupportedOperationException expected) { }
        for (String count : List.of("0", "-1", "1.2", "2147483648", "garbage")) {
            Files.writeString(text, "[items]\nminecraft:stone=" + count);
            rejected(ImportService.read(text, ENVIRONMENT), "TXT invalid count " + count);
        }
        Files.writeString(text, "[items]\nminecraft:stone=2147483647\nminecraft:stone=1");
        rejected(ImportService.read(text, ENVIRONMENT), "Combined overflow");
        Files.writeString(text, "[items]\nnot_real=3");
        rejected(ImportService.read(text, ENVIRONMENT), "Unknown TXT item");
        Path json = directory.resolve("materials.json");
        Files.writeString(json, "{\"Multiplier\":7,\"Materials\":[{\"Item\":\"minecraft:stone\",\"Total\":12,\"Missing\":2,\"Available\":999},{\"Item\":{\"id\":\"minecraft:stone\"},\"Total\":3}]}");
        result = ImportService.read(json, ENVIRONMENT);
        require(result.canImport() && result.materials().get("minecraft:stone") == 15, "JSON totals are already multiplied");
        for (String total : List.of("0", "-1", "1.5", "2147483648", "\"3\"", "null")) {
            Files.writeString(json, "{\"Materials\":[{\"Item\":\"minecraft:stone\",\"Total\":" + total + "}]}");
            rejected(ImportService.read(json, ENVIRONMENT), "JSON invalid Total " + total);
        }
        Files.writeString(json, "{\"Materials\":[{\"Item\":{\"id\":\"minecraft:stone\",\"components\":{\"minecraft:custom_name\":\"x\"}},\"Total\":1}]}");
        rejected(ImportService.read(json, ENVIRONMENT), "Component-sensitive JSON");
        Files.writeString(json, "{\"Materials\":[{\"Item\":\"minecraft:stone\",\"Total\":1},{\"Item\":\"minecraft:unknown\",\"Total\":2}]}");
        rejected(ImportService.read(json, ENVIRONMENT), "Unknown row blocks partial import");
    }

    private static void packedStates() {
        int[] sequence = new int[91];
        for (int index = 0; index < sequence.length; index++) sequence[index] = index % 5;
        long[] packed = pack(sequence, 3);
        for (int index = 0; index < sequence.length; index++) require(ImportService.packedIndex(packed, 3, index) == sequence[index], "Continuous packed index " + index);
    }

    private static void schematicFiles(Path directory) throws Exception {
        Path path = directory.resolve("fixture.litematic");
        List<Object> palette = List.of(state("minecraft:air"), state("minecraft:stone"), state("minecraft:dirt"),
                state("minecraft:oak_slab", Map.of("type", "double")), state("minecraft:stone"));
        int[] sequence = new int[43];
        for (int index = 0; index < sequence.length; index++) sequence[index] = index % 5;
        for (int format = 5; format <= 7; format++) {
            Map<String, Object> root = schematic(format, 4000, -43, 1, 1, palette, pack(sequence, 3));
            writeNbt(path, root);
            ImportResult result = ImportService.read(path, ENVIRONMENT);
            require(result.canImport() && result.name().equals("Fixture"), "Supported format " + format);
            require(result.materials().get("minecraft:stone") == 17 && result.materials().get("minecraft:dirt") == 9 && result.materials().get("minecraft:oak_slab") == 16, "Signed-size material counts " + format);
            require(!result.diagnostics().isEmpty(), "DFU conversion diagnostic");
        }
        writeNbt(path, schematic(8, 4000, 1, 1, 1, palette, pack(new int[] {0}, 3)));
        rejected(ImportService.read(path, ENVIRONMENT), "Future format");
        writeNbt(path, schematic(7, 5001, 1, 1, 1, palette, pack(new int[] {0}, 3)));
        rejected(ImportService.read(path, ENVIRONMENT), "Future Minecraft version");
        writeNbt(path, schematic(7, 5000, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, palette, new long[0]));
        rejected(ImportService.read(path, ENVIRONMENT), "Volume overflow");
        writeNbt(path, schematic(7, 5000, 400, 400, 401, palette, new long[0]));
        rejected(ImportService.read(path, ENVIRONMENT), "Volume limit");
        writeNbt(path, schematic(7, 5000, 3, 1, 1, List.of(state("minecraft:air"), state("minecraft:stone")), pack(new int[] {0, 2, 1}, 2)));
        rejected(ImportService.read(path, ENVIRONMENT), "Invalid palette index");
        writeNbt(path, schematic(7, 5000, 1, 1, 1, List.of(state("minecraft:air"), state("example:modded")), pack(new int[] {0}, 2)));
        rejected(ImportService.read(path, ENVIRONMENT), "Unused unknown palette entry");
        Map<String, Object> root = schematic(7, 5000, 1, 1, 1, List.of(state("minecraft:stone")), pack(new int[] {0}, 2));
        region(root).put("TileEntities", List.of(Map.of("components", Map.of("minecraft:custom_name", "Name"))));
        writeNbt(path, root);
        ImportResult decorated = ImportService.read(path, ENVIRONMENT);
        require(decorated.canImport() && !decorated.diagnostics().isEmpty(), "Block entity decoration ignored with diagnostic");
        root = schematic(7, 5000, 1, 1, 1, List.of(state("minecraft:stone")), pack(new int[] {0}, 2));
        region(root).put("Entities", List.of(Map.of("id", "minecraft:pig")));
        writeNbt(path, root);
        require(ImportService.read(path, ENVIRONMENT).canImport(), "Entity exclusion remains importable");
        Files.write(path, new byte[] {1, 2, 3});
        rejected(ImportService.read(path, ENVIRONMENT), "Invalid gzip");
    }

    private static void boundedNbt() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(12); out.writeUTF("huge"); out.writeInt(Integer.MAX_VALUE); out.writeByte(0);
        }
        try { BoundedNbtReader.read(new java.io.ByteArrayInputStream(bytes.toByteArray())); throw new AssertionError("Unbounded array accepted"); }
        catch (IOException expected) { }
    }

    public static void minecraftMaterials() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        require(BlockMaterialResolver.resolve("minecraft:oak_slab", Map.of("type", "double")).equals(Map.of("minecraft:oak_slab", 2)), "Double slabs");
        require(BlockMaterialResolver.resolve("minecraft:oak_door", Map.of("half", "upper")).isEmpty(), "Door upper");
        require(BlockMaterialResolver.resolve("minecraft:red_bed", Map.of("part", "head")).isEmpty(), "Bed head");
        require(BlockMaterialResolver.resolve("minecraft:snow", Map.of("layers", "7")).equals(Map.of("minecraft:snow", 7)), "Snow layers");
        require(BlockMaterialResolver.resolve("minecraft:wheat", Map.of()).equals(Map.of("minecraft:wheat_seeds", 1)), "Wheat seeds");
        require(BlockMaterialResolver.resolve("minecraft:potted_dandelion", Map.of()).equals(Map.of("minecraft:flower_pot", 1, "minecraft:dandelion", 1)), "Potted plants");
        require(BlockMaterialResolver.resolve("minecraft:water", Map.of("level", "1")).isEmpty(), "Flowing liquids");
        require(BlockMaterialResolver.resolve("minecraft:water", Map.of("level", "0")).equals(Map.of("minecraft:water_bucket", 1)), "Source liquids");
        require(BlockMaterialResolver.resolve("minecraft:sea_pickle", Map.of("pickles", "4", "waterlogged", "false")).equals(Map.of("minecraft:sea_pickle", 4)), "Pickle multiplier");
        require(BlockMaterialResolver.resolve("minecraft:decorated_pot", Map.of()).equals(Map.of("minecraft:decorated_pot", 1)), "Decorated pot uses plain item");
        require(BlockMaterialResolver.resolve("minecraft:oak_slab", Map.of("waterlogged", "true")).equals(Map.of("minecraft:oak_slab", 1, "minecraft:water_bucket", 1)), "Waterlogged block adds water bucket");
        require(BlockMaterialResolver.resolve("minecraft:red_candle_cake", Map.of()).equals(Map.of("minecraft:cake", 1, "minecraft:red_candle", 1)), "Candle cake uses both ingredients");
        MinecraftImportEnvironment environment = new MinecraftImportEnvironment();
        require(environment.resolve("minecraft:grass", Map.of(), 3000).equals(Map.of("minecraft:short_grass", 1)), "DFU upgrades renamed old block states");
        require(environment.resolve("minecraft:grass_path", Map.of(), 2500).equals(Map.of("minecraft:dirt", 1)), "DFU upgrades older renamed path");
        require(environment.resolve("minecraft:cauldron", Map.of("level", "3"), 2500).equals(Map.of("minecraft:cauldron", 1, "minecraft:water_bucket", 1)), "DFU upgrades block and property together");
        for (int version : new int[] {3000, environment.dataVersion() - 1, environment.dataVersion()}) {
            require(environment.resolve("minecraft:oak_slab", Map.of("type", "double", "waterlogged", "true"), version)
                    .equals(Map.of("minecraft:oak_slab", 2, "minecraft:water_bucket", 1)), "DFU preserves state properties from " + version);
            unresolved(environment, "minecraft:unknown", Map.of(), version);
            unresolved(environment, "minecraft:stone", Map.of("bogus", "x"), version);
            unresolved(environment, "minecraft:oak_slab", Map.of("type", "invalid"), version);
        }
        try { BlockMaterialResolver.resolve("minecraft:stone", Map.of("bogus", "x")); throw new AssertionError("Unknown property accepted"); }
        catch (IOException expected) { }
        try { BlockMaterialResolver.resolve("minecraft:player_head", Map.of()); throw new AssertionError("Sensitive block accepted"); }
        catch (IOException expected) { }
    }

    private static void minecraftSchematics(Path directory) throws Exception {
        MinecraftImportEnvironment environment = new MinecraftImportEnvironment();
        Path path = directory.resolve("historic.litematic");
        List<Object> palette = List.of(state("minecraft:grass"), state("minecraft:oak_slab", Map.of("type", "double", "waterlogged", "true")));
        for (int format = 5; format <= 7; format++) {
            writeNbt(path, schematic(format, 3000, 2, 1, 1, palette, pack(new int[] {0, 1}, 2)));
            ImportResult result = ImportService.read(path, environment);
            require(result.canImport() && result.materials().equals(Map.of("minecraft:short_grass", 1, "minecraft:oak_slab", 2, "minecraft:water_bucket", 1)), "Historic schematic DFU format " + format);
        }
    }

    private static void unresolved(MinecraftImportEnvironment environment, String id, Map<String, String> properties, int version) throws Exception {
        try { environment.resolve(id, properties, version); throw new AssertionError("Invalid state accepted from " + version + ": " + id + properties); }
        catch (IOException expected) { }
    }

    private static void rejected(ImportResult result, String label) {
        require(!result.canImport() && result.materials().isEmpty() && !result.diagnostics().isEmpty(), label);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Map<String, Object> state(String id) { return state(id, Map.of()); }
    private static Map<String, Object> state(String id, Map<String, String> properties) { return Map.of("Name", id, "Properties", properties); }
    private static Map<String, Object> schematic(int format, int dataVersion, int x, int y, int z, List<Object> palette, long[] packed) {
        Map<String, Object> region = new LinkedHashMap<>();
        region.put("Size", Map.of("x", x, "y", y, "z", z));
        region.put("BlockStatePalette", palette);
        region.put("BlockStates", packed);
        return new LinkedHashMap<>(Map.of("Version", format, "MinecraftDataVersion", dataVersion, "Metadata", Map.of("Name", "Fixture"), "Regions", Map.of("test", region)));
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> region(Map<String, Object> root) { return (Map<String, Object>) ((Map<?, ?>) root.get("Regions")).get("test"); }
    private static long[] pack(int[] values, int bits) {
        long[] data = new long[(values.length * bits + 63) / 64];
        for (int index = 0; index < values.length; index++) {
            int bit = index * bits, offset = bit & 63, word = bit >>> 6;
            data[word] |= (long) values[index] << offset;
            if (offset + bits > 64) data[word + 1] |= (long) values[index] >>> (64 - offset);
        }
        return data;
    }
    private static void writeNbt(Path path, Map<String, Object> root) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeByte(10); out.writeUTF(""); payload(out, root);
        }
    }
    private static int type(Object value) {
        if (value instanceof Integer) return 3;
        if (value instanceof String) return 8;
        if (value instanceof List<?>) return 9;
        if (value instanceof Map<?, ?>) return 10;
        if (value instanceof long[]) return 12;
        throw new IllegalArgumentException("Unsupported test NBT value");
    }
    private static void payload(DataOutputStream out, Object value) throws IOException {
        switch (type(value)) {
            case 3 -> out.writeInt((Integer) value);
            case 8 -> out.writeUTF((String) value);
            case 9 -> {
                List<?> list = (List<?>) value;
                out.writeByte(list.isEmpty() ? 0 : type(list.getFirst())); out.writeInt(list.size());
                for (Object element : list) payload(out, element);
            }
            case 10 -> {
                for (var entry : ((Map<?, ?>) value).entrySet()) { out.writeByte(type(entry.getValue())); out.writeUTF((String) entry.getKey()); payload(out, entry.getValue()); }
                out.writeByte(0);
            }
            case 12 -> { long[] longs = (long[]) value; out.writeInt(longs.length); for (long element : longs) out.writeLong(element); }
            default -> throw new AssertionError();
        }
    }
}
