package net.fabricmc.resourcetracker.model;

/** Persistent intent and transient observed stock; independent of Minecraft APIs. */
public class ResourceState {
    public String itemId;
    public int targetCount;
    public boolean pinned;
    public int manuallyCollectedTarget;
    public transient int cachedCount;

    public ResourceState(String itemId, int targetCount) {
        this.itemId = itemId;
        this.targetCount = targetCount;
    }

    public boolean isManuallyCollected() {
        return manuallyCollectedTarget > 0 && targetCount <= manuallyCollectedTarget;
    }

    public boolean isCompleted() {
        return isManuallyCollected() || cachedCount >= targetCount;
    }

    public void toggleCollected() {
        manuallyCollectedTarget = isManuallyCollected() ? 0 : targetCount;
    }

    public void setTargetCount(int target) {
        if (target < 1) throw new IllegalArgumentException("Target must be positive");
        targetCount = target;
        validateCollected();
    }

    public boolean validateCollected() {
        if (manuallyCollectedTarget > 0 && targetCount > manuallyCollectedTarget) {
            manuallyCollectedTarget = 0;
            return true;
        }
        if (manuallyCollectedTarget > targetCount) {
            // The checkbox acknowledges the current requirement, not a hidden partial stock.
            // A later increase must ask the user to acknowledge the resource again.
            manuallyCollectedTarget = targetCount;
            return true;
        }
        return false;
    }

    public int remaining() {
        return isManuallyCollected() ? 0 : Math.max(0, targetCount - cachedCount);
    }
}
