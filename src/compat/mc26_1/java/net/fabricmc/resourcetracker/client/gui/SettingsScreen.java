package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.Map;

public class SettingsScreen extends Screen {
    private final Screen parent;

    private final EditBox[][] colorFields = new EditBox[3][4];
    private final Map<EditBox, Component> invalidFields = new IdentityHashMap<>();
    private int scrollY = 0;
    private int contentBottom = 0;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.invalidFields.clear();

        int centerX = this.width / 2;
        int y = 34 - scrollY;
        int labelX = centerX - 150;
        int fieldX = centerX + 35;

        addIntField(labelX, fieldX, y, "gui.resourcetracker.settings.default_x", TrackerConfig.INSTANCE.defaultX, null, null,
                Component.translatable("gui.resourcetracker.validation.integer"),
                value -> TrackerConfig.INSTANCE.defaultX = value);
        y += 22;
        addIntField(labelX, fieldX, y, "gui.resourcetracker.settings.default_y", TrackerConfig.INSTANCE.defaultY, null, null,
                Component.translatable("gui.resourcetracker.validation.integer"),
                value -> TrackerConfig.INSTANCE.defaultY = value);
        y += 22;
        addFloatField(labelX, fieldX, y, "gui.resourcetracker.settings.default_scale", TrackerConfig.INSTANCE.defaultScale,
                TrackerConfig.MIN_SCALE, TrackerConfig.MAX_SCALE,
                Component.translatable("gui.resourcetracker.validation.scale"),
                value -> TrackerConfig.INSTANCE.defaultScale = value);
        y += 22;
        addIntField(labelX, fieldX, y, "gui.resourcetracker.settings.default_columns", TrackerConfig.INSTANCE.defaultColumns, 0, TrackerConfig.MAX_COLUMNS,
                Component.translatable("gui.resourcetracker.validation.columns"),
                value -> TrackerConfig.INSTANCE.defaultColumns = value);
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

        Button listScreen = Button.builder(Component.translatable("gui.resourcetracker.settings.open_lists_screen"), button ->
                        this.minecraft.setScreen(new MainScreen(parent)))
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

        this.contentBottom = y + 54 + scrollY;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.done"), button -> {
                    TrackerConfig.saveGlobalSettingsOnly();
                    this.minecraft.setScreen(parent);
                })
                .bounds(centerX - 50, this.height - 30, 100, 20)
                .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // No-op: this screen draws its own translucent background.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xA0000000);
        context.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        super.extractRenderState(context, mouseX, mouseY, delta);
        showInvalidFieldTooltip(context, mouseX, mouseY);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentBottom - (this.height - 42));
        if (maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (verticalAmount * 18)));
        init();
        return true;
    }

    @Override
    public void onClose() {
        TrackerConfig.saveGlobalSettingsOnly();
        this.minecraft.setScreen(parent);
    }

    private EditBox addIntField(int labelX, int fieldX, int y, String labelKey, int value, Integer min, Integer max, Component tooltip, IntSetter setter) {
        addLabel(labelX, y + 4, labelKey);
        EditBox field = new EditBox(font, fieldX, y, 110, 16, Component.translatable(labelKey));
        field.setValue(String.valueOf(value));
        field.setTextColor(0xFFFFFFFF);
        field.setResponder(text -> {
            Integer parsed = parseInteger(text);
            boolean valid = parsed != null && (min == null || parsed >= min) && (max == null || parsed <= max);
            markFieldValidity(field, valid, tooltip);
            if (valid) {
                setter.set(parsed);
            }
        });
        this.addRenderableWidget(field);
        return field;
    }

    private EditBox addFloatField(int labelX, int fieldX, int y, String labelKey, float value, float min, float max, Component tooltip, FloatSetter setter) {
        addLabel(labelX, y + 4, labelKey);
        EditBox field = new EditBox(font, fieldX, y, 110, 16, Component.translatable(labelKey));
        field.setValue(String.valueOf(value));
        field.setTextColor(0xFFFFFFFF);
        field.setResponder(text -> {
            Float parsed = parseFloat(text);
            boolean valid = parsed != null && Float.isFinite(parsed) && parsed >= min && parsed <= max;
            markFieldValidity(field, valid, tooltip);
            if (valid) {
                setter.set(parsed);
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
                    context.text(this.font, Component.literal(label), lx, ly, color, true));

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
            Integer r = parseInteger(colorFields[index][0].getValue());
            Integer g = parseInteger(colorFields[index][1].getValue());
            Integer b = parseInteger(colorFields[index][2].getValue());
            Integer a = parseInteger(colorFields[index][3].getValue());
            Component tooltip = Component.translatable("gui.resourcetracker.validation.rgba");
            markFieldValidity(colorFields[index][0], isByte(r), tooltip);
            markFieldValidity(colorFields[index][1], isByte(g), tooltip);
            markFieldValidity(colorFields[index][2], isByte(b), tooltip);
            markFieldValidity(colorFields[index][3], isByte(a), tooltip);
            if (!(isByte(r) && isByte(g) && isByte(b) && isByte(a))) return;
            setColorForIndex(index, (a << 24) | (r << 16) | (g << 8) | b);
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

    private boolean isByte(Integer value) {
        return value != null && value >= 0 && value <= 255;
    }

    private Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Float parseFloat(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void markFieldValidity(EditBox field, boolean valid, Component tooltip) {
        field.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF5555);
        if (valid) invalidFields.remove(field);
        else invalidFields.put(field, tooltip);
    }

    private void showInvalidFieldTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        for (Map.Entry<EditBox, Component> entry : invalidFields.entrySet()) {
            EditBox field = entry.getKey();
            if (field.isFocused() || (field.visible && mouseX >= field.getX() && mouseX < field.getX() + field.getWidth()
                    && mouseY >= field.getY() && mouseY < field.getY() + field.getHeight())) {
                VersionCompat.setTooltip(context, font, entry.getValue(), mouseX, mouseY);
                return;
            }
        }
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
                context.text(this.font, Component.translatable(key), x, y, 0xFFFFFFFF, true));
    }

    private interface IntSetter {
        void set(int value);
    }

    private interface FloatSetter {
        void set(float value);
    }
}

