package net.fabricmc.resourcetracker.selection;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Inclusive, normalized block bounds for a world selection. */
public final class AreaBounds {
    public static final long MAX_VOLUME = 2_000_000L;

    private final BlockPos min;
    private final BlockPos max;
    private final long width;
    private final long depth;
    private final long volume;

    public AreaBounds(BlockPos first, BlockPos second) {
        if (first == null || second == null) throw new NullPointerException("Area corners cannot be null");
        this.min = new BlockPos(Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        this.max = new BlockPos(Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        this.width = (long) max.getX() - min.getX() + 1;
        long height = (long) max.getY() - min.getY() + 1;
        this.depth = (long) max.getZ() - min.getZ() + 1;
        try {
            this.volume = Math.multiplyExact(Math.multiplyExact(width, height), depth);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Area volume overflows", exception);
        }
        if (volume > MAX_VOLUME) throw new IllegalArgumentException("Area exceeds maximum of " + MAX_VOLUME + " blocks");
    }

    public BlockPos min() { return min.immutable(); }
    public BlockPos max() { return max.immutable(); }
    public long volume() { return volume; }

    public AABB toAabb() {
        return new AABB(min.getX(), min.getY(), min.getZ(),
                (double) max.getX() + 1.0, (double) max.getY() + 1.0, (double) max.getZ() + 1.0);
    }

    long xAt(long index) { return min.getX() + index % width; }
    long yAt(long index) { return min.getY() + index / (width * depth); }
    long zAt(long index) { return min.getZ() + (index / width) % depth; }
}
