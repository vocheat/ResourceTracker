package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicit confirmation; cancelling or closing never modifies a tracking list. */
public final class QuickAddScreen extends PagedDataScreen {
    private final String itemId;
    private final String contextKey;
    private final AbstractContainerMenu originalMenu;
    private final List<TrackerConfig.TrackingList> lists = new ArrayList<>();
    private String quantity;
    private String newListName;
    private int selected;
    private EditBox status;
    private Button accept;
    private boolean returningToParent;
    private boolean parentRemoved;

    public QuickAddScreen(AbstractContainerScreen<?> parent, String itemId, int count,
                          String contextKey, AbstractContainerMenu originalMenu) {
        super(parent, Component.translatable("gui.resourcetracker.quick_add.title"));
        this.itemId = itemId;
        this.quantity = Integer.toString(count);
        this.contextKey = contextKey;
        this.originalMenu = originalMenu;
        this.newListName = Component.translatable("gui.resourcetracker.quick_add.default_list").getString();
        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (list != null) lists.add(list);
        }
    }

    @Override
    protected void init() {
        layout(160);
        label(Component.literal(new TrackerConfig.TrackedItem(itemId, 1).getDisplayName()), left, 32, contentWidth);
        label(Component.translatable("gui.resourcetracker.quick_add.count"), left, 55, contentWidth);
        EditBox count = new EditBox(font, left, 73, contentWidth, 20,
                Component.translatable("gui.resourcetracker.quick_add.count"));
        count.setMaxLength(10);
        count.setValue(quantity);
        count.setResponder(value -> { quantity = value; refreshStatus(); });
        addRenderableWidget(count);
        if (lists.isEmpty()) {
            label(Component.translatable("gui.resourcetracker.quick_add.new_list"), left, 101, contentWidth);
            EditBox name = new EditBox(font, left, 119, contentWidth, 20,
                    Component.translatable("gui.resourcetracker.quick_add.new_list"));
            name.setMaxLength(120);
            name.setValue(newListName);
            name.setResponder(value -> { newListName = value; refreshStatus(); });
            addRenderableWidget(name);
        } else {
            label(Component.translatable("gui.resourcetracker.quick_add.list"), left, 101, contentWidth);
            button(Component.literal(lists.get(selected).name), left, 119, contentWidth, b -> {
                selected = (selected + 1) % lists.size();
                b.setMessage(Component.literal(lists.get(selected).name));
                refreshStatus();
            }).active = lists.size() > 1;
        }
        status = new EditBox(font, left, 150, contentWidth, 18, Component.empty());
        status.setMaxLength(32767);
        status.setBordered(false);
        status.setEditable(false);
        status.active = false;
        status.setTextColorUneditable(UiTheme.TEXT);
        addRenderableWidget(status);
        int half = (contentWidth - 6) / 2;
        accept = button(Component.translatable("gui.resourcetracker.quick_add.add"), left, height - 26,
                half, b -> confirm());
        button(Component.translatable("gui.cancel"), left + half + 6, height - 26,
                contentWidth - half - 6, b -> onClose());
        refreshStatus();
        setFocused(count);
    }

    private Integer amount() {
        try {
            int parsed = Integer.parseInt(quantity);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private TrackerConfig.TrackedItem existingItem() {
        if (lists.isEmpty()) return null;
        for (TrackerConfig.TrackedItem item : lists.get(selected).items) {
            if (item != null && itemId.equals(item.itemId)) return item;
        }
        return null;
    }

    private boolean validSession() {
        return minecraft != null && minecraft.player != null && minecraft.level != null
                && minecraft.player.isAlive() && originalMenu.stillValid(minecraft.player)
                && minecraft.player.containerMenu == originalMenu
                && Objects.equals(contextKey, TrackerConfig.getActiveContextKey());
    }

    private void refreshStatus() {
        if (status == null || accept == null) return;
        Integer amount = amount();
        Component message;
        boolean valid = validSession() && amount != null;
        if (amount == null) {
            message = Component.translatable("gui.resourcetracker.quick_add.invalid_count");
        } else if (lists.isEmpty() && newListName.isBlank()) {
            message = Component.translatable("gui.resourcetracker.quick_add.empty_name");
            valid = false;
        } else {
            TrackerConfig.TrackedItem existing = existingItem();
            long total = amount.longValue() + (existing == null ? 0 : existing.targetCount);
            if (total > TrackerConfig.MAX_TARGET_COUNT) {
                message = Component.translatable("gui.resourcetracker.quick_add.overflow");
                valid = false;
            } else {
                message = Component.translatable("gui.resourcetracker.quick_add.summary", amount, total);
            }
        }
        status.setValue(message.getString());
        status.setCursorPosition(0);
        status.setHighlightPos(0);
        status.setTooltip(net.minecraft.client.gui.components.Tooltip.create(message));
        accept.active = valid;
    }

    private void confirm() {
        refreshStatus();
        if (!accept.active || !validSession()) return;
        Integer amount = amount();
        if (amount == null) return;
        TrackerConfig.TrackingList list = lists.isEmpty()
                ? TrackerConfig.createList(newListName.trim()) : lists.get(selected);
        if (!lists.isEmpty() && !TrackerConfig.INSTANCE.lists.contains(list)) return;
        TrackerConfig.TrackedItem existing = existingItem();
        int target;
        try {
            target = Math.addExact(existing == null ? 0 : existing.targetCount, amount);
        } catch (ArithmeticException ignored) {
            refreshStatus();
            return;
        }
        if (lists.isEmpty()) TrackerConfig.INSTANCE.lists.add(list);
        TrackerHistory.record(list);
        if (existing == null) {
            list.items.add(new TrackerConfig.TrackedItem(itemId, target));
        } else {
            existing.setTargetCount(target);
            existing.manuallyCollectedTarget = 0;
        }
        TrackerConfig.saveList(list);
        ResourceTrackerClient.invalidateTargetItemCache();
        onClose();
    }

    @Override
    public void tick() {
        if (!validSession()) {
            onClose();
            return;
        }
        super.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft == null) return;
        returningToParent = validSession();
        VersionCompat.setScreen(minecraft, returningToParent ? parent : null);
    }

    @Override
    public void removed() {
        // If the server disconnects/closes the menu, run the cleanup deferred at opening.
        if (!returningToParent && !parentRemoved) {
            parentRemoved = true;
            parent.removed();
        }
        super.removed();
    }
}
