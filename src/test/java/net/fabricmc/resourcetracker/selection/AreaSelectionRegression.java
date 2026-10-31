package net.fabricmc.resourcetracker.selection;

import net.fabricmc.resourcetracker.importing.BlockMaterialResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.Map;
import java.util.HashSet;

/** Dependency-free geometry and live-state conversion regression checks. */
public final class AreaSelectionRegression {
    private AreaSelectionRegression() { }

    public static void main(String[] args) throws Exception {
        bounds();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        liveStateConversion();
        System.out.println("Area selection regression checks passed");
    }

    private static void bounds() {
        AreaBounds reversed = new AreaBounds(new BlockPos(8, -2, 4), new BlockPos(6, -4, 9));
        require(reversed.min().equals(new BlockPos(6, -4, 4)), "Reversed bounds normalize minimum");
        require(reversed.max().equals(new BlockPos(8, -2, 9)), "Reversed bounds normalize maximum");
        require(reversed.volume() == 3L * 3L * 6L, "Inclusive reversed volume");
        require(reversed.toAabb().maxX == 9.0 && reversed.toAabb().maxY == -1.0 && reversed.toAabb().maxZ == 10.0,
                "AABB covers inclusive maximum block");

        AreaBounds thin = new AreaBounds(new BlockPos(3, 5, -8), new BlockPos(3, 15, -8));
        require(thin.volume() == 11 && thin.yAt(10) == 15, "Thin region includes both ends");
        var visited = new HashSet<BlockPos>();
        for (long index = 0; index < reversed.volume(); index++) {
            var pos = new BlockPos((int) reversed.xAt(index), (int) reversed.yAt(index), (int) reversed.zAt(index));
            require(pos.getX() >= 6 && pos.getX() <= 8 && pos.getY() >= -4 && pos.getY() <= -2
                    && pos.getZ() >= 4 && pos.getZ() <= 9, "Scan positions stay within every axis");
            require(visited.add(pos), "Each scan position is visited once");
        }
        require(visited.size() == 54, "Scan traversal covers the entire volume");
        require(new AreaBounds(new BlockPos(0, 0, 0), new BlockPos(1999, 999, 0)).volume() == AreaBounds.MAX_VOLUME,
                "Maximum supported volume");
        rejected(new BlockPos(0, 0, 0), new BlockPos(2000, 999, 0), "Area cap");
        rejected(new BlockPos(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                new BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE), "Coordinate and volume overflow");
    }

    private static void liveStateConversion() throws Exception {
        require(BlockMaterialResolver.resolveState(Blocks.AIR.defaultBlockState()).isEmpty(), "Air is intentionally excluded");
        require(BlockMaterialResolver.resolveState(Blocks.OAK_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.DOUBLE)).equals(Map.of("minecraft:oak_slab", 2)),
                "Double slab maps to two placement items");
        require(BlockMaterialResolver.resolveState(Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)).isEmpty(),
                "Upper door half is intentionally excluded");
        require(BlockMaterialResolver.resolveState(Blocks.RED_BED.defaultBlockState()
                        .setValue(BedBlock.PART, BedPart.HEAD)).isEmpty(),
                "Bed head half is intentionally excluded");
        require(BlockMaterialResolver.resolveState(Blocks.WHEAT.defaultBlockState())
                        .equals(Map.of("minecraft:wheat_seeds", 1)),
                "Crop uses its placement item");
    }

    private static void rejected(BlockPos first, BlockPos second, String label) {
        try { new AreaBounds(first, second); throw new AssertionError(label + " accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
