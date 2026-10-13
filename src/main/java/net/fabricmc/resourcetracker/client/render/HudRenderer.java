package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.util.PngIcons;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** One draw path shared by the overlay, positioning screen and editor preview. */
public final class HudRenderer {
    private HudRenderer() {}

    public static void render(GuiGraphics context, Font font, HudRenderModel model) {
        for (HudRenderModel.Fill fill : model.fills) {
            if ((fill.color() >>> 24) != 0)
                context.fill(fill.left(), fill.top(), fill.right(), fill.bottom(), fill.color());
        }
        context.drawString(font, model.title, 0, 0, model.titleColor);
        if (!model.summary.isEmpty()) context.drawString(font, model.summary, model.summaryX, 0, model.summaryColor);
        for (HudRenderModel.Row row : model.rows) {
            if (model.showIcons && row.stack != null) context.renderItem(row.stack, row.itemX, row.itemY);
            context.drawString(font, row.nameText, row.nameX, row.nameY, row.nameColor);
            if (row.completed) RenderUtils.drawPngIcon(context, PngIcons.Icon.CHECK_SMALL,
                    row.countX, row.countY + 1, row.countColor);
            int countTextX = row.countX + (row.completed ? PngIcons.SMALL_CHECK_ADVANCE : 0);
            context.drawString(font, row.countText, countTextX, row.countY, row.countColor);
        }
    }
}
