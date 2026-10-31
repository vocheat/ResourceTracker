package net.fabricmc.resourcetracker.model;

import java.util.HashSet;
import java.util.Set;

/** A vanilla wood family preference; AUTO keeps the planner's inventory-based selection. */
public enum WoodType {
    AUTO(null), OAK("oak"), SPRUCE("spruce"), BIRCH("birch"), JUNGLE("jungle"),
    ACACIA("acacia"), DARK_OAK("dark_oak"), MANGROVE("mangrove"), CHERRY("cherry"),
    PALE_OAK("pale_oak"), POPLAR("poplar"), CRIMSON("crimson"), WARPED("warped"), BAMBOO("bamboo");

    private final String planksId;
    private final Set<String> items;

    WoodType(String name) {
        if (name == null) {
            planksId = null;
            items = Set.of();
            return;
        }
        planksId = "minecraft:" + name + "_planks";
        Set<String> ids = new HashSet<>();
        for (String suffix : new String[]{"planks", "stairs", "slab", "fence", "fence_gate", "door",
                "trapdoor", "pressure_plate", "button", "sign", "hanging_sign", "shelf"}) {
            ids.add("minecraft:" + name + "_" + suffix);
        }
        if (name.equals("bamboo")) {
            for (String item : new String[]{"bamboo", "bamboo_block", "stripped_bamboo_block",
                    "bamboo_mosaic", "bamboo_mosaic_stairs", "bamboo_mosaic_slab", "bamboo_raft", "bamboo_chest_raft"}) {
                ids.add("minecraft:" + item);
            }
        } else {
            boolean nether = name.equals("crimson") || name.equals("warped");
            for (String suffix : nether ? new String[]{"stem", "hyphae"} : new String[]{"log", "wood"}) {
                ids.add("minecraft:" + name + "_" + suffix);
                ids.add("minecraft:stripped_" + name + "_" + suffix);
            }
            if (!nether) {
                ids.add("minecraft:" + name + "_boat");
                ids.add("minecraft:" + name + "_chest_boat");
            }
        }
        items = Set.copyOf(ids);
    }

    public String planksId() { return planksId; }

    /** Exact IDs only: mod namespaces and similarly named unrelated items are never substituted. */
    public boolean matches(String itemId) { return itemId != null && items.contains(itemId); }
}
