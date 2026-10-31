package net.fabricmc.resourcetracker.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Extract immutable bounds before submitting the camera-relative wireframe. */
public final class SelectionCompat {
    private static final RenderStateDataKey<AABB> BOUNDS = RenderStateDataKey.create(() -> "resourcetracker:selection");

    private SelectionCompat() { }

    public static void register(Supplier<AABB> selection) {
        WorldRenderEvents.END_EXTRACTION.register(context ->
                ((FabricRenderState) context.worldState()).setData(BOUNDS, selection.get()));
        WorldRenderEvents.BEFORE_ENTITIES.register(context -> {
            AABB bounds = ((FabricRenderState) context.worldState()).getData(BOUNDS);
            if (bounds == null) return;
            Vec3 camera = context.worldState().cameraRenderState.pos;
            AABB relative = bounds.move(-camera.x, -camera.y, -camera.z);
            context.commandQueue().submitCustomGeometry(context.matrices(), RenderTypes.lines(),
                    (pose, vertices) -> draw(pose, vertices, relative));
        });
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer vertices, AABB box) {
        // Inflate slightly so lines do not fight the selected blocks' outer faces.
        AABB b = box.inflate(0.002);
        for (int side = 0; side < 2; side++) {
            double y = side == 0 ? b.minY : b.maxY;
            line(pose, vertices, b.minX, y, b.minZ, b.maxX, y, b.minZ, 0xEFFF6868);
            line(pose, vertices, b.minX, y, b.maxZ, b.maxX, y, b.maxZ, 0xEFFF6868);
            line(pose, vertices, b.minX, y, b.minZ, b.minX, y, b.maxZ, 0xEF68A8FF);
            line(pose, vertices, b.maxX, y, b.minZ, b.maxX, y, b.maxZ, 0xEF68A8FF);
        }
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                double px = x == 0 ? b.minX : b.maxX;
                double pz = z == 0 ? b.minZ : b.maxZ;
                line(pose, vertices, px, b.minY, pz, px, b.maxY, pz, 0xEF78EB88);
            }
        }
    }

    private static void line(PoseStack.Pose pose, VertexConsumer vertices,
                             double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= length;
        ny /= length;
        nz /= length;
        vertices.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color)
                .setNormal(pose, nx, ny, nz).setLineWidth(2.5f);
        vertices.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color)
                .setNormal(pose, nx, ny, nz).setLineWidth(2.5f);
    }
}
