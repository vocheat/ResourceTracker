package net.fabricmc.resourcetracker.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Readable, paginated notes rather than one action-looking button per diagnostic. */
final class NotesScreen extends PagedDataScreen {
    private final List<Component> notes;

    NotesScreen(Screen parent, Component title, List<Component> notes) {
        super(parent, title);
        this.notes = List.copyOf(notes);
    }

    @Override protected void init() {
        layout(36);
        List<String> lines = new ArrayList<>();
        for (Component note : notes) {
            for (String paragraph : note.getString().split("\\n", -1)) {
                String rest = paragraph;
                while (!rest.isEmpty()) {
                    String line = font.plainSubstrByWidth(rest, contentWidth);
                    if (line.isEmpty()) line = rest.substring(0, 1);
                    int space = line.lastIndexOf(' ');
                    if (line.length() < rest.length() && space > 0) line = line.substring(0, space);
                    lines.add(line);
                    rest = rest.substring(line.length()).stripLeading();
                }
            }
            lines.add("");
        }
        pageSize = Math.max(1, (height - rowTop - 62) / 14);
        pagination(lines.size());
        for (int i = page * pageSize; i < Math.min(lines.size(), (page + 1) * pageSize); i++)
            label(Component.literal(lines.get(i)), left, rowTop + (i % pageSize) * 14, contentWidth);
        button(Component.translatable("gui.back"), left, height - 26, contentWidth, b -> onClose());
    }
}
