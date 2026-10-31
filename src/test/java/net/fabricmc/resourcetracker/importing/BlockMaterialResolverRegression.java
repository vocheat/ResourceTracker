package net.fabricmc.resourcetracker.importing;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.Map;

/** Exercise non-vanilla registry identities with the same state classes that mods inherit. */
public final class BlockMaterialResolverRegression {
    private BlockMaterialResolverRegression() { }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        var slab = Blocks.OAK_SLAB.defaultBlockState();
        require(BlockMaterialResolver.resolveState("example:slab", slab.setValue(BlockStateProperties.SLAB_TYPE, SlabType.DOUBLE))
                .equals(Map.of("minecraft:oak_slab", 2)), "Non-vanilla double slab counts two placement items");
        require(BlockMaterialResolver.resolveState("example:slab", slab.setValue(BlockStateProperties.WATERLOGGED, true))
                .equals(Map.of("minecraft:oak_slab", 1, "minecraft:water_bucket", 1)), "Non-vanilla waterlogged slab retains its water");

        var door = Blocks.OAK_DOOR.defaultBlockState();
        require(BlockMaterialResolver.resolveState("example:door", door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER))
                .equals(Map.of("minecraft:oak_door", 1)), "Non-vanilla lower door counts one item");
        require(BlockMaterialResolver.resolveState("example:door", door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER))
                .isEmpty(), "Non-vanilla upper door does not count again");
        require(BlockMaterialResolver.resolveState("example:bed", Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.BED_PART, BedPart.HEAD)).isEmpty(), "Non-vanilla bed head does not count again");
        require(BlockMaterialResolver.resolveState("example:plant", Blocks.SUNFLOWER.defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER)).isEmpty(), "Non-vanilla upper plant does not count again");

        require(BlockMaterialResolver.resolveState("example:candle", Blocks.CANDLE.defaultBlockState()
                .setValue(BlockStateProperties.CANDLES, 4)).equals(Map.of("minecraft:candle", 4)), "Non-vanilla candles retain their amount");
        require(BlockMaterialResolver.resolveState("example:snow", Blocks.SNOW.defaultBlockState()
                .setValue(BlockStateProperties.LAYERS, 7)).equals(Map.of("minecraft:snow", 7)), "Non-vanilla snow retains its layers");
        require(BlockMaterialResolver.resolveState("example:eggs", Blocks.TURTLE_EGG.defaultBlockState()
                .setValue(BlockStateProperties.EGGS, 3)).equals(Map.of("minecraft:turtle_egg", 3)), "Non-vanilla eggs retain their amount");
        require(BlockMaterialResolver.resolveState("example:pickles", Blocks.SEA_PICKLE.defaultBlockState()
                .setValue(BlockStateProperties.PICKLES, 4).setValue(BlockStateProperties.WATERLOGGED, false))
                .equals(Map.of("minecraft:sea_pickle", 4)), "Non-vanilla pickles retain their amount");
        require(BlockMaterialResolver.resolveState("example:lichen", Blocks.GLOW_LICHEN.defaultBlockState()
                .setValue(BlockStateProperties.NORTH, true).setValue(BlockStateProperties.EAST, true))
                .equals(Map.of("minecraft:glow_lichen", 2)), "Non-vanilla multiface block counts its occupied faces");

        require(BlockMaterialResolver.resolveState("example:water", Blocks.STONE.defaultBlockState())
                .equals(Map.of("minecraft:stone", 1)), "A modded path does not trigger vanilla-specific aliases");
        require(BlockMaterialResolver.resolveState("example:wheat", Blocks.STONE.defaultBlockState())
                .equals(Map.of("minecraft:stone", 1)), "Vanilla crop overrides stay in their namespace");
        System.out.println("Block material state regression checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
