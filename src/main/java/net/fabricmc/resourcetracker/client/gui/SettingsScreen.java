package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SettingsScreen extends Screen {
    private final Screen parent;

    private final EditBox[][] colorFields = new EditBox[3][4];

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int centerX = this.width / 2;
        int y = 34;
        int labelX = centerX - 150;
        int fieldX = centerX + 35;

        addIntField(labelX, fieldX, y, "gui.resourcetracker.settings.default_x", TrackerConfig.INSTANCE.defaultX,
                value -> TrackerConfig.INSTANCE.defaultX = value);
        y += 22;
        addIntField(labelX, fieldX, y, "gui.resourcetracker.settings.default_y", TrackerConfig.INSTANCE.defaultY,
                value -> TrackerConfig.INSTANCE.defaultY = value);
        y += 22;
        addFloatField(labelX, fieldX, y, "gui.resourcetracker.settings.default_scale", TrackerConfig.INSTANCE.defaultScale,
                value -> TrackerConfig.INSTANCE.defaultScale = Math.max(0.25f, Math.min(4.0f, value)));
        y += 22;
        addIntField(labelX, fieldX, y, "gui.resourcetracker.settings.default_columns", TrackerConfig.INSTANCE.defaultColumns,
                value -> TrackerConfig.INSTANCE.defaultColumns = Math.max(0, Math.min(5, value)));
        y += 28;

        addColorEditor(centerX - 175, y, 0, "gui.resourcetracker.settings.default_text_color");
        y += 46;
        addColorEditor(centerX - 175, y, 1, "gui.resourcetracker.settings.default_title_color");
        y += 46;
        addColorEditor(centerX - 175, y, 2, "gui.resourcetracker.settings.default_background_color");
        y += 30;

        this.addRenderableWidget(Button.builder(Component.translatable(TrackerConfig.INSTANCE.defaultShowRemaining
                        ? "gui.resourcetracker.edit.mode_need"
                        : "gui.resourcetracker.edit.mode_count"), button -> {
                    TrackerConfig.INSTANCE.defaultShowRemaining = !TrackerConfig.INSTANCE.defaultShowRemaining;
                    button.setMessage(Component.translatable(TrackerConfig.INSTANCE.defaultShowRemaining
                            ? "gui.resourcetracker.edit.mode_need"
                            : "gui.resourcetracker.edit.mode_count"));
                    TrackerConfig.save();
                })
                .bounds(centerX - 155, y, 100, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable(TrackerConfig.INSTANCE.defaultShowIcons
                        ? "gui.resourcetracker.edit.icons_on"
                        : "gui.resourcetracker.edit.icons_off"), button -> {
                    TrackerConfig.INSTANCE.defaultShowIcons = !TrackerConfig.INSTANCE.defaultShowIcons;
                    button.setMessage(Component.translatable(TrackerConfig.INSTANCE.defaultShowIcons
                            ? "gui.resourcetracker.edit.icons_on"
                            : "gui.resourcetracker.edit.icons_off"));
                    TrackerConfig.save();
                })
                .bounds(centerX - 45, y, 90, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.settings.reset_defaults"), button -> {
                    TrackerConfig.resetDefaultListSettings();
                    this.init();
                })
                .bounds(centerX + 55, y, 100, 20)
                .build());
        y += 28;

        Button listScreen = Button.builder(Component.translatable("gui.resourcetracker.settings.open_lists_screen"), button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new MainScreen(parent));
                    }
                })
                .bounds(centerX - 50, y, 100, 20)
                .build();
        this.addRenderableWidget(listScreen);
        y += 24;

        Button openCurrent = Button.builder(Component.translatable("gui.resourcetracker.open_world_lists"),
                        button -> TrackerConfig.openActiveListsFolder())
                .bounds(centerX - 155, y, 150, 20)
                .build();
        openCurrent.active = TrackerConfig.hasActiveContext();
        this.addRenderableWidget(openCurrent);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.open_all_lists"),
                        button -> TrackerConfig.openListsRootFolder())
                .bounds(centerX + 5, y, 150, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.done"), button -> {
                    TrackerConfig.save();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(parent);
                    }
                })
                .bounds(centerX - 50, this.height - 30, 100, 20)
                .build());
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // No-op: custom background below.
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xA0000000);
        context.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        TrackerConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private EditBox addIntField(int labelX, int fieldX, int y, String labelKey, int value, IntSetter setter) {
        addLabel(labelX, y + 4, labelKey);
        EditBox field = new EditBox(font, fieldX, y, 110, 16, Component.translatable(labelKey));
        field.setValue(String.valueOf(value));
        field.setTextColor(0xFFFFFFFF);
        field.setResponder(text -> {
            try {
                setter.set(Integer.parseInt(text));
                TrackerConfig.save();
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(field);
        return field;
    }

    private EditBox addFloatField(int labelX, int fieldX, int y, String labelKey, float value, FloatSetter setter) {
        addLabel(labelX, y + 4, labelKey);
        EditBox field = new EditBox(font, fieldX, y, 110, 16, Component.translatable(labelKey));
        field.setValue(String.valueOf(value));
        field.setTextColor(0xFFFFFFFF);
        field.setResponder(text -> {
            try {
                setter.set(Float.parseFloat(text));
                TrackerConfig.save();
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(field);
        return field;
    }

    private void addColorEditor(int x, int y, int index, String labelKey) {
        addLabel(x, y + 18, labelKey);

        String[] labels = {"R", "G", "B", "Alpha"};
        int[] labelColors = {0xFFFF4444, 0xFF44FF44, 0xFF4488FF, 0xFFCCCCCC};
        int[] widths = {34, 34, 34, 48};
        int startX = x + 145;
        int[] values = colorToFields(getColorForIndex(index));

        int fieldX = startX;
        for (int i = 0; i < 4; i++) {
            String label = labels[i];
            int labelWidth = this.font.width(label);
            int lx = fieldX + (widths[i] - labelWidth) / 2;
            int ly = y;
            int color = labelColors[i];
            this.addRenderableOnly((context, mouseX, mouseY, delta) ->
                    context.drawString(this.font, Component.literal(label), lx, ly, color, true));

            EditBox field = new EditBox(this.font, fieldX, y + 12, widths[i], 16, Component.literal(label));
            field.setValue(String.valueOf(values[i]));
            field.setMaxLength(3);
            field.setTextColor(0xFFFFFFFF);
            int finalIndex = index;
            field.setResponder(text -> applyColorFields(finalIndex));
            this.addRenderableWidget(field);
            colorFields[index][i] = field;
            fieldX += widths[i] + 4;
        }
    }

    private void applyColorFields(int index) {
        try {
            int r = clampByte(Integer.parseInt(colorFields[index][0].getValue()));
            int g = clampByte(Integer.parseInt(colorFields[index][1].getValue()));
            int b = clampByte(Integer.parseInt(colorFields[index][2].getValue()));
            int a = clampByte(Integer.parseInt(colorFields[index][3].getValue()));
            setColorForIndex(index, (a << 24) | (r << 16) | (g << 8) | b);
            TrackerConfig.save();
        } catch (NumberFormatException | NullPointerException ignored) {
        }
    }

    private int[] colorToFields(int color) {
        return new int[] {
                (color >> 16) & 0xFF,
                (color >> 8) & 0xFF,
                color & 0xFF,
                (color >> 24) & 0xFF
        };
    }

    private int clampByte(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int getColorForIndex(int index) {
        return switch (index) {
            case 0 -> TrackerConfig.INSTANCE.defaultTextColor;
            case 1 -> TrackerConfig.INSTANCE.defaultNameColor;
            case 2 -> TrackerConfig.INSTANCE.defaultBackgroundColor;
            default -> 0xFFFFFFFF;
        };
    }

    private void setColorForIndex(int index, int color) {
        switch (index) {
            case 0 -> TrackerConfig.INSTANCE.defaultTextColor = color;
            case 1 -> TrackerConfig.INSTANCE.defaultNameColor = color;
            case 2 -> TrackerConfig.INSTANCE.defaultBackgroundColor = color;
            default -> {
            }
        }
    }

    private void addLabel(int x, int y, String key) {
        this.addRenderableOnly((context, mouseX, mouseY, delta) ->
                context.drawString(this.font, Component.translatable(key), x, y, 0xFFFFFFFF, true));
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface FloatSetter {
        void set(float value);
    }
}
