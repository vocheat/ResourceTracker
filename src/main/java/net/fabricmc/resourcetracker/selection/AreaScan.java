package net.fabricmc.resourcetracker.selection;

import net.fabricmc.resourcetracker.importing.BlockMaterialResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.multiplayer.ClientLevel;

import java.io.IOException;
import java.util.HashMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Incrementally derives placement materials from a loaded client world area. */
public final class AreaScan {
    private final ClientLevel level;
    private final AreaBounds bounds;
    private final Map<BlockState, Conversion> conversionCache = new HashMap<>();
    private final Map<String, Integer> materials = new LinkedHashMap<>();
    private final Map<String, Integer> unsupportedBlockIds = new LinkedHashMap<>();
    private long scannedBlocks;
    private long skippedBlocks;
    private long excludedBlocks;

    public AreaScan(ClientLevel level, AreaBounds bounds) {
        if (level == null || bounds == null) throw new NullPointerException("Level and bounds are required");
        if (!level.isInWorldBounds(bounds.min()) || !level.isInWorldBounds(bounds.max()))
            throw new IllegalArgumentException("Area extends outside the world's bounds");
        this.level = level;
        this.bounds = bounds;
    }

    /** Scans at most {@code maxBlocks} positions on the calling thread. */
    public void step(int maxBlocks) {
        if (maxBlocks < 0) throw new IllegalArgumentException("maxBlocks must not be negative");
        long stop = Math.min(bounds.volume(), scannedBlocks + (long) maxBlocks);
        while (scannedBlocks < stop) {
            int x = (int) bounds.xAt(scannedBlocks);
            int y = (int) bounds.yAt(scannedBlocks);
            int z = (int) bounds.zAt(scannedBlocks);
            // ClientLevel.hasChunk always returns true; the cache knows what was received.
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4))
                throw new UnloadedChunkException(x >> 4, z >> 4);

            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            Conversion conversion = conversionCache.computeIfAbsent(state, AreaScan::convert);
            if (conversion.unsupported()) {
                skippedBlocks++;
                String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                unsupportedBlockIds.merge(blockId, 1, Math::addExact);
            } else if (conversion.materials().isEmpty()) {
                excludedBlocks++;
            } else {
                conversion.materials().forEach((id, count) -> materials.merge(id, count, Math::addExact));
            }
            scannedBlocks++;
        }
    }

    public boolean done() { return scannedBlocks == bounds.volume(); }
    public long scannedBlocks() { return scannedBlocks; }
    public long totalBlocks() { return bounds.volume(); }
    public long skippedBlocks() { return skippedBlocks; }
    public long excludedBlocks() { return excludedBlocks; }
    public Map<String, Integer> materials() { return Collections.unmodifiableMap(new LinkedHashMap<>(materials)); }
    public Map<String, Integer> unsupportedBlockIds() { return Collections.unmodifiableMap(new LinkedHashMap<>(unsupportedBlockIds)); }
    public Map<String, Integer> unsupportedBlocks() { return unsupportedBlockIds(); }

    private static Conversion convert(BlockState state) {
        try {
            return new Conversion(BlockMaterialResolver.resolveState(state), false);
        } catch (IOException | ArithmeticException exception) {
            return new Conversion(Map.of(), true);
        }
    }

    private record Conversion(Map<String, Integer> materials, boolean unsupported) { }

    public static final class UnloadedChunkException extends IllegalStateException {
        private final int chunkX;
        private final int chunkZ;

        private UnloadedChunkException(int chunkX, int chunkZ) {
            super("Area scan stopped: chunk " + chunkX + ", " + chunkZ + " is not loaded");
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        public int chunkX() { return chunkX; }
        public int chunkZ() { return chunkZ; }
    }
}
