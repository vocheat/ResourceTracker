package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Preview and explicitly apply shape-preserving wood replacement in editors and area drafts. */
public final class FamilyReplacementScreen extends PagedDataScreen {
    private final TrackerConfig.TrackingList list;
    private final int displayMultiplier;
    private final ClientLevel level;
    private final String contextKey;
    private FamilyReplacement.Family source;
    private FamilyReplacement.Family target;
    private Component error;

    public FamilyReplacementScreen(Screen parent, TrackerConfig.TrackingList list) {
        this(parent, list, 1);
    }

    public FamilyReplacementScreen(Screen parent, TrackerConfig.TrackingList list, int displayMultiplier) {
        super(parent, Component.translatable("gui.resourcetracker.family.title"));
        this.list = list;
        this.displayMultiplier = displayMultiplier;
        this.level = Minecraft.getInstance().level;
        this.contextKey = TrackerConfig.getActiveContextKey();
        List<FamilyReplacement.Family> sources = FamilyReplacement.sourceFamilies(list);
        if (!sources.isEmpty()) source = sources.getFirst();
        target = Arrays.stream(FamilyReplacement.Family.values()).filter(family -> family != source
                && FamilyReplacement.exists(family.planksId())).findFirst().orElse(null);
    }

    @Override
    protected void init() {
        layout(120);
        label(Component.translatable("gui.resourcetracker.family.hint"), left, 30, contentWidth);
        int half = (contentWidth - 6) / 2;
        button(Component.translatable("gui.resourcetracker.family.from", familyName(source)), left, 50, half,
                b -> choose(true));
        button(Component.translatable("gui.resourcetracker.family.to", familyName(target)), left + half + 6, 50, half,
                b -> choose(false));
        FamilyReplacement.Preview preview = FamilyReplacement.preview(list, source, target, displayMultiplier);
        long quantity = preview.rows().stream().filter(FamilyReplacement.Row::matched)
                .mapToLong(row -> (long) row.count() * displayMultiplier).sum();
        label(Component.translatable("gui.resourcetracker.family.summary", preview.matched(), quantity,
                preview.unmatched()), left, 76, contentWidth);
        Component message = error != null ? error : preview.overflow()
                ? Component.translatable("gui.resourcetracker.edit.replace_overflow", TrackerConfig.MAX_TARGET_COUNT)
                : Component.translatable("gui.resourcetracker.family.unmatched_hint");
        var hint = label(message, left, 98, contentWidth);
        if (error != null || preview.overflow()) hint.setTextColorUneditable(0xFFFF7777);
        pagination(preview.rows().size());
        for (int i = page * pageSize; i < Math.min(preview.rows().size(), (page + 1) * pageSize); i++) {
            FamilyReplacement.Row row = preview.rows().get(i);
            Component destination = row.matched() ? itemName(row.targetId())
                    : Component.translatable("gui.resourcetracker.family.no_counterpart");
            var label = label(Component.translatable("gui.resourcetracker.family.row", itemName(row.sourceId()),
                    destination, (long) row.count() * displayMultiplier), left, rowTop + (i % pageSize) * 24,
                    contentWidth);
            if (!row.matched()) label.setTextColorUneditable(0xFFFFBB77);
        }
        if (preview.rows().isEmpty()) label(Component.translatable("gui.resourcetracker.family.empty"),
                left, rowTop + 4, contentWidth);
        button(CommonComponents.GUI_CANCEL, left, height - 26, half, b -> onClose());
        var apply = button(Component.translatable("gui.resourcetracker.family.apply"), left + half + 6,
                height - 26, half, b -> apply());
        apply.active = preview.matched() > 0 && !preview.overflow();
        apply.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.family.apply_hint")));
    }

    private void choose(boolean choosingSource) {
        if (!ensureSession()) return;
        List<FamilyReplacement.Family> choices = choosingSource ? FamilyReplacement.sourceFamilies(list)
                : Arrays.stream(FamilyReplacement.Family.values())
                .filter(family -> FamilyReplacement.exists(family.planksId())).toList();
        Consumer<FamilyReplacement.Family> select = family -> {
            if (choosingSource) source = family;
            else target = family;
            page = 0;
            error = null;
            VersionCompat.setScreen(minecraft, this);
        };
        VersionCompat.setScreen(minecraft, new FamilyPicker(choices, select));
    }

    private void apply() {
        if (!ensureSession()) return;
        FamilyReplacement.Result result = FamilyReplacement.replace(list, source, target, displayMultiplier);
        if (result == FamilyReplacement.Result.OVERFLOW) {
            error = Component.translatable("gui.resourcetracker.edit.replace_overflow", TrackerConfig.MAX_TARGET_COUNT);
            init();
        } else onClose();
    }

    private static Component familyName(FamilyReplacement.Family family) {
        return family == null ? Component.translatable("gui.resourcetracker.family.none") : itemName(family.planksId());
    }

    private static Component itemName(String id) {
        var item = VersionCompat.getItem(id);
        return item == null ? Component.literal(id) : Component.literal(VersionCompat.getItemName(item));
    }

    private boolean ensureSession() {
        if (minecraft != null && level != null && minecraft.level == level
                && minecraft.player != null && minecraft.player.isAlive()
                && Objects.equals(contextKey, TrackerConfig.getActiveContextKey())) return true;
        if (minecraft != null) VersionCompat.setScreen(minecraft, null);
        return false;
    }

    @Override public void tick() { if (ensureSession()) super.tick(); }
    @Override public void onClose() { if (ensureSession()) super.onClose(); }

    private final class FamilyPicker extends PagedDataScreen {
        private final List<FamilyReplacement.Family> choices;
        private final Consumer<FamilyReplacement.Family> select;

        FamilyPicker(List<FamilyReplacement.Family> choices, Consumer<FamilyReplacement.Family> select) {
            super(FamilyReplacementScreen.this, Component.translatable("gui.resourcetracker.family.choose"));
            this.choices = choices;
            this.select = select;
        }

        @Override protected void init() {
            layout(40);
            pagination(choices.size());
            for (int i = page * pageSize; i < Math.min(choices.size(), (page + 1) * pageSize); i++) {
                FamilyReplacement.Family family = choices.get(i);
                button(familyName(family), left, rowTop + (i % pageSize) * 24, contentWidth,
                        b -> { if (ensureSession()) select.accept(family); });
            }
            button(CommonComponents.GUI_CANCEL, left, height - 26, contentWidth, b -> onClose());
        }
        @Override public void tick() { if (ensureSession()) super.tick(); }
        @Override public void onClose() { if (ensureSession()) super.onClose(); }
    }
}
