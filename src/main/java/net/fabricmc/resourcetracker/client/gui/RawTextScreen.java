package net.fabricmc.resourcetracker.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Read-only, pixel-wrapped diagnostics. Pagination scrolls text rather than presenting it as actions. */
final class RawTextScreen extends PagedDataScreen {
    private final List<Component> paragraphs;
    RawTextScreen(Screen parent, Component title, List<Component> paragraphs) {
        super(parent, title); this.paragraphs = List.copyOf(paragraphs);
    }
    @Override protected void init() {
        layout(34);
        List<String> lines = new ArrayList<>();
        for (Component paragraph : paragraphs) {
            lines.addAll(wrap(font, paragraph.getString(), contentWidth));
            lines.add("");
        }
        if (lines.isEmpty()) lines.add(Component.translatable("gui.resourcetracker.raw.no_notes").getString());
        pageSize = Math.max(1, (height - rowTop - 62) / 12);
        pagination(lines.size());
        for (int i = page * pageSize; i < Math.min(lines.size(), (page + 1) * pageSize); i++)
            label(Component.literal(lines.get(i)), left, rowTop + (i % pageSize) * 12, contentWidth);
        button(Component.translatable("gui.resourcetracker.done"), left, height - 26, contentWidth, b -> onClose());
    }
    static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) lines.add("");
            while (!remaining.isEmpty()) {
                String part = font.plainSubstrByWidth(remaining, Math.max(1, width));
                if (part.isEmpty()) part = remaining.substring(0, 1);
                if (part.length() < remaining.length()) {
                    int space = part.lastIndexOf(' ');
                    if (space > 0) part = part.substring(0, space);
                }
                lines.add(part);
                remaining = remaining.substring(part.length()).stripLeading();
            }
        }
        return lines;
    }
}
