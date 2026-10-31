package net.fabricmc.resourcetracker.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.resourcetracker.client.gui.AreaListScreen;
import net.fabricmc.resourcetracker.compat.SelectionCompat;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.selection.AreaBounds;
import net.fabricmc.resourcetracker.selection.AreaScan;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Client-only two-corner selection. World reads and counting happen on the client tick. */
public final class AreaSelectionController {
    private static final int BLOCKS_PER_TICK = 8192;
    private static final double PICK_DISTANCE = 128;
    private static KeyMapping key;
    private static ClientLevel selectionLevel;
    private static BlockPos first;
    private static BlockPos second;
    private static BlockPos aimed;
    private static AreaScan scan;
    private static int ticks;

    private AreaSelectionController() { }

    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<FabricClientCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public static void initialize() {
        if (key != null) return;
        key = VersionCompat.registerAreaSelectionKey();
        SelectionCompat.register(AreaSelectionController::previewBounds);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("rtarea")
                        .executes(context -> { message("help"); return 1; })
                        .then(cornerCommand("pos1", true))
                        .then(cornerCommand("pos2", false))
                        .then(literal("cancel").executes(context -> { clear(); message("cancelled"); return 1; }))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> cornerCommand(String name, boolean start) {
        return literal(name)
                .executes(context -> chooseCorner(start, targetBlock(Minecraft.getInstance())))
                .then(argument("x", IntegerArgumentType.integer(-30_000_000, 29_999_999))
                        .then(argument("y", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer(-30_000_000, 29_999_999))
                                        .executes(context -> chooseCorner(start, new BlockPos(
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "y"),
                                                IntegerArgumentType.getInteger(context, "z")))))));
    }

    /** Called after the main entry point has updated the active world/server list context. */
    public static void tick(Minecraft client) {
        if (selectionLevel != null && selectionLevel != client.level) clear();
        while (key != null && key.consumeClick()) {
            if (VersionCompat.currentScreen(client) == null) {
                if (scan != null && !scan.done()) message("busy");
                else chooseCorner(first == null, targetBlock(client));
            }
        }
        if (first == null || client.player == null || client.level == null) return;
        aimed = second == null ? targetBlock(client) : second;
        ticks++;
        if (scan != null) {
            if (!scan.done()) {
                try {
                    scan.step(BLOCKS_PER_TICK);
                } catch (AreaScan.UnloadedChunkException exception) {
                    scan = null;
                    second = null;
                    message("unloaded");
                    return;
                }
            }
            if (scan.done() && VersionCompat.currentScreen(client) == null) {
                AreaScan completed = scan;
                clear();
                VersionCompat.setScreen(client, new AreaListScreen(completed, client.level,
                        TrackerConfig.getActiveContextKey()));
            } else if (ticks % 10 == 0) {
                overlay(client, "scanning", scan.scannedBlocks(), scan.totalBlocks());
            }
        } else if (ticks % 10 == 0) {
            overlay(client, "preview", key.getTranslatedKeyMessage());
        }
    }

    private static int chooseCorner(boolean start, BlockPos pos) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || !TrackerConfig.hasActiveContext()) {
            message("no_world");
            return 0;
        }
        if (pos == null) {
            message("no_target");
            return 0;
        }
        if (!client.level.isInWorldBounds(pos)) {
            message("invalid_height");
            return 0;
        }
        if (start) {
            clear();
            selectionLevel = client.level;
            first = pos.immutable();
            aimed = first;
            message("first", first.getX(), first.getY(), first.getZ(), key.getTranslatedKeyMessage());
            return 1;
        }
        if (first == null || selectionLevel != client.level) {
            message("missing_first");
            return 0;
        }
        if (scan != null && !scan.done()) {
            message("busy");
            return 0;
        }
        AreaBounds bounds;
        try {
            bounds = new AreaBounds(first, pos);
        } catch (IllegalArgumentException exception) {
            message("too_large", AreaBounds.MAX_VOLUME);
            return 0;
        }
        scan = new AreaScan(client.level, bounds);
        second = pos.immutable();
        message("started", bounds.volume());
        return 1;
    }

    private static BlockPos targetBlock(Minecraft client) {
        if (client.player == null || client.level == null) return null;
        HitResult hit = client.player.pick(PICK_DISTANCE, 1, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos().immutable() : null;
    }

    private static AABB previewBounds() {
        Minecraft client = Minecraft.getInstance();
        if (first == null || client.level != selectionLevel || client.player == null) return null;
        BlockPos end = aimed == null ? first : aimed;
        return new AABB(Math.min(first.getX(), end.getX()), Math.min(first.getY(), end.getY()), Math.min(first.getZ(), end.getZ()),
                (double) Math.max(first.getX(), end.getX()) + 1, (double) Math.max(first.getY(), end.getY()) + 1,
                (double) Math.max(first.getZ(), end.getZ()) + 1);
    }

    private static void clear() {
        first = null;
        second = null;
        aimed = null;
        scan = null;
        selectionLevel = null;
        ticks = 0;
    }

    private static void message(String key, Object... args) {
        Minecraft client = Minecraft.getInstance();
        VersionCompat.showChatMessage(client, Component.translatable("gui.resourcetracker.area." + key, args));
    }

    private static void overlay(Minecraft client, String key, Object... args) {
        VersionCompat.showCompletionMessage(client, Component.translatable("gui.resourcetracker.area." + key, args));
    }
}
