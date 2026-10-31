package net.fabricmc.resourcetracker.importing;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Converts block states to placement materials, rejecting states that need unsupported components. */
public final class BlockMaterialResolver {
    private static final Set<String> SKIPPED = Set.of("air", "cave_air", "void_air", "piston_head", "moving_piston", "nether_portal", "end_portal", "end_gateway", "bubble_column");
    private static final Map<String, String> OVERRIDES = Map.ofEntries(
            Map.entry("farmland", "dirt"), Map.entry("dirt_path", "dirt"),
            Map.entry("wheat", "wheat_seeds"), Map.entry("carrots", "carrot"), Map.entry("potatoes", "potato"),
            Map.entry("beetroots", "beetroot_seeds"), Map.entry("nether_wart", "nether_wart"),
            Map.entry("melon_stem", "melon_seeds"), Map.entry("attached_melon_stem", "melon_seeds"),
            Map.entry("pumpkin_stem", "pumpkin_seeds"), Map.entry("attached_pumpkin_stem", "pumpkin_seeds"),
            Map.entry("cocoa", "cocoa_beans"), Map.entry("sweet_berry_bush", "sweet_berries"),
            Map.entry("redstone_wire", "redstone"), Map.entry("tripwire", "string"),
            Map.entry("kelp_plant", "kelp"), Map.entry("bamboo_sapling", "bamboo"),
            Map.entry("tall_seagrass", "seagrass"), Map.entry("torchflower_crop", "torchflower_seeds"),
            Map.entry("pitcher_crop", "pitcher_pod"),
            Map.entry("cave_vines", "glow_berries"), Map.entry("cave_vines_plant", "glow_berries"),
            Map.entry("wall_torch", "torch"), Map.entry("redstone_wall_torch", "redstone_torch"),
            Map.entry("soul_wall_torch", "soul_torch"), Map.entry("powder_snow", "powder_snow_bucket"));

    private BlockMaterialResolver() { }

    /** Converts a live block state into the placement items that represent it. */
    public static Map<String, Integer> resolveState(BlockState state) throws IOException {
        return resolveState(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), state);
    }

    static Map<String, Integer> resolve(String id, Map<String, String> properties) throws IOException {
        if (!id.startsWith("minecraft:")) throw new IOException("Modded block is unsupported: " + id);
        Block block = ImportCompat.block(id);
        if (block == null) throw new IOException("Unknown block: " + id);
        BlockState state = block.defaultBlockState();
        for (var entry : properties.entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) throw new IOException("Unknown property " + entry.getKey() + " for " + id);
            state = withValue(state, property, entry.getValue(), id);
        }
        return resolveState(id, state);
    }

    /** Shared state rules also apply to modded subclasses of vanilla block types. */
    static Map<String, Integer> resolveState(String id, BlockState state) throws IOException {
        Block block = state.getBlock();
        Map<String, String> values = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) values.put(property.getName(), propertyValue(state, property));
        boolean vanilla = id.startsWith("minecraft:");
        String path = vanilla ? id.substring(10) : "";
        if (state.isAir() || SKIPPED.contains(path)) return Map.of();
        if ((block instanceof DoorBlock || block instanceof DoublePlantBlock) && "upper".equals(values.get("half"))) return Map.of();
        if (block instanceof BedBlock && "head".equals(values.get("part"))) return Map.of();
        if (path.equals("player_head") || path.equals("player_wall_head"))
            throw new IOException("Component-sensitive block requires an exported materials list: " + id);
        if (path.equals("water") || path.equals("lava"))
            return "0".equals(values.get("level")) ? Map.of("minecraft:" + path + "_bucket", 1) : Map.of();
        Map<String, Integer> result = new LinkedHashMap<>();
        if (vanilla && block instanceof CandleCakeBlock) {
            add(result, "minecraft:cake", 1);
            add(result, "minecraft:" + path.substring(0, path.length() - "_cake".length()), 1);
            return result;
        }
        if (vanilla && block instanceof FlowerPotBlock pot && !path.equals("flower_pot")) {
            add(result, "minecraft:flower_pot", 1);
            add(result, VersionCompat.getItemId(pot.getPotted().asItem()), 1);
            return result;
        }
        if (path.endsWith("_cauldron")) {
            add(result, "minecraft:cauldron", 1);
            if (path.equals("water_cauldron") && !"3".equals(values.get("level")))
                throw new IOException("Partly filled water cauldron requires component-sensitive water potions");
            add(result, "minecraft:" + path.replace("_cauldron", "_bucket"), 1);
            return result;
        }
        int count = 1;
        if (block instanceof SlabBlock && "double".equals(values.get("type"))) count = 2;
        else if (block instanceof SnowLayerBlock) count = Integer.parseInt(values.get("layers"));
        else if (block instanceof TurtleEggBlock) count = Integer.parseInt(values.get("eggs"));
        else if (block instanceof SeaPickleBlock) count = Integer.parseInt(values.get("pickles"));
        else if (block instanceof CandleBlock) count = Integer.parseInt(values.get("candles"));
        else if (block instanceof MultifaceBlock) {
            count = 0;
            for (String face : Set.of("up", "down", "north", "south", "east", "west"))
                if ("true".equals(values.get(face))) count++;
        } else if (path.equals("pink_petals") || path.equals("wildflowers")) count = Integer.parseInt(values.get("flower_amount"));
        else if (path.equals("leaf_litter")) count = Integer.parseInt(values.get("segment_amount"));
        String item = OVERRIDES.containsKey(path) ? "minecraft:" + OVERRIDES.get(path) : VersionCompat.getItemId(block.asItem());
        if (count > 0) add(result, item, count);
        if ("true".equals(values.get("waterlogged"))) add(result, "minecraft:water_bucket", 1);
        return result;
    }

    private static void add(Map<String, Integer> result, String id, int count) throws IOException {
        if (id.equals("minecraft:air") || !VersionCompat.isValidItemId(id)) throw new IOException("No supported placement item for block (item " + id + ")");
        result.merge(id, count, Math::addExact);
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value, String id) throws IOException {
        T parsed = property.getValue(value).orElse(null);
        if (parsed == null) throw new IOException("Invalid " + property.getName() + "=" + value + " for " + id);
        return state.setValue(property, parsed);
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
