package net.fabricmc.resourcetracker.client.gui;

import net.minecraft.network.chat.Component;

/** Localizes domain diagnostics at the UI boundary, keeping parsers usable without Minecraft. */
final class DiagnosticText {
    private record Rule(String prefix, String key) { }
    private static final Rule[] RULES = {
            new Rule("No supported vanilla crafting recipe for ", "no_recipe"),
            new Rule("Crafting requires an existing seed item: ", "seed"),
            new Rule("Cyclic crafting dependency", "cycle"),
            new Rule("Recipe choice is unavailable for ", "recipe_choice"),
            new Rule("Ingredient choice is unavailable: ", "ingredient_choice"),
            new Rule("Vanilla crafting recipe index is unavailable", "no_index"),
            new Rule("Vanilla recipe resources are unavailable", "no_index"),
            new Rule("Cannot load vanilla recipes: ", "load_recipes"),
            new Rule("Invalid recipe ", "invalid_recipe"),
            new Rule("Invalid resource ", "invalid_resource"),
            new Rule("Crafting quantity exceeds the supported 64-bit range", "overflow"),
            new Rule("Invalid item quantity: ", "invalid_quantity"),
            new Rule("Entities are excluded; only block placement materials are counted", "entities"),
            new Rule("Block entity contents and decorative NBT are excluded", "decoration"),
            new Rule("Block states were upgraded from Minecraft data version ", "upgraded"),
            new Rule("Not a Litematica materials export: missing Materials array", "json_format"),
            new Rule("Not a ResourceTracker TXT list: missing [items] section", "txt_format"),
            new Rule("Malformed materials JSON: ", "invalid_json"),
            new Rule("Materials JSON root must be an object", "json_format"),
            new Rule("Material Total must be a positive 32-bit integer", "quantity"),
            new Rule("Material count must be a positive integer: ", "quantity"),
            new Rule("Combined material count exceeds 32-bit integer limit: ", "overflow"),
            new Rule("Unknown or unsupported item: ", "unknown_item"),
            new Rule("Unknown block: ", "unknown_block"),
            new Rule("Modded block is unsupported: ", "modded"),
            new Rule("Component-sensitive block requires an exported materials list: ", "components"),
            new Rule("Partly filled water cauldron requires component-sensitive water potions", "cauldron"),
            new Rule("Component-sensitive Item cannot be tracked by item ID: ", "components"),
            new Rule("Cannot upgrade block state ", "upgrade_failed"),
            new Rule("Unknown property ", "property"),
            new Rule("No supported placement item for block (item ", "placement"),
            new Rule("Unsupported litematic format ", "format"),
            new Rule("Schematic uses a future Minecraft data version: ", "future"),
            new Rule("Schematic exceeds total volume limit of 64 million blocks", "volume"),
            new Rule("Schematic region volume overflow", "volume"),
            new Rule("The file contains no importable materials", "empty"),
            new Rule("Unsupported file type; choose .litematic, .json or .txt", "file_type"),
            new Rule("Text import exceeds 16 MiB limit", "text_limit"),
            new Rule("Compressed schematic exceeds 256 MiB limit", "nbt_limit"),
            new Rule("NBT exceeds 256 MiB decompressed limit", "nbt_limit"),
            new Rule("NBT allocation exceeds 256 MiB limit", "nbt_limit"),
            new Rule("Import cancelled", "cancelled"),
            new Rule("BlockStates", "block_states"),
            new Rule("Missing BlockStates", "block_states"),
            new Rule("NBT ", "nbt"),
            new Rule("Invalid NBT ", "nbt"),
            new Rule("Missing or invalid ", "field"),
            new Rule("Invalid item entry on TXT line ", "txt_line"),
            new Rule("Invalid item count on TXT line ", "txt_line"),
            new Rule("Invalid materials entry ", "json_entry")
    };

    private DiagnosticText() { }

    static Component of(String message) {
        for (Rule rule : RULES) if (message.startsWith(rule.prefix)) {
            return Component.translatable("gui.resourcetracker.diag." + rule.key,
                    message.substring(rule.prefix.length()));
        }
        return Component.literal(message);
    }
}
