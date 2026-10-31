package net.fabricmc.resourcetracker.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.RenderType;

/** World-space selection preview, with red X, green Y and blue Z edges. */
public final class SelectionCompat {
    private SelectionCompat() { }

    public static void register(Supplier<AABB> selection) {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            AABB bounds = selection.get();
            if (bounds == null || context.matrixStack() == null || context.consumers() == null) return;
            Vec3 camera = context.camera().getPosition();
            PoseStack matrices = context.matrixStack();
            matrices.pushPose();
            matrices.translate(-camera.x, -camera.y, -camera.z);
            draw(matrices.last(), context.consumers().getBuffer(RenderType.lines()), bounds);
            matrices.popPose();
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
                .setNormal(pose, nx, ny, nz);
        vertices.addVertex(pose, (float) x2, (float) y2, (float) z2).setColor(color)
                .setNormal(pose, nx, ny, nz);
    }
}
