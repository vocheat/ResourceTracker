package net.fabricmc.resourcetracker.bootstrap;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Select mixin names without linking any version-specific Minecraft classes. */
public final class QuickAddMixinPlugin implements IMixinConfigPlugin {
    private String activePackage;
    private boolean selected;

    @Override
    public void onLoad(String mixinPackage) {
        activePackage = "net.fabricmc.resourcetracker.platform."
                + VersionMatrix.platformId(ResourceTrackerBootstrap.minecraftVersion()) + ".mixin";
        selected = activePackage.equals(mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        // Loom's direct Mixin remapper embeds intermediary names in each 1.21
        // payload; Minecraft 26 payloads already use production names.
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return selected && mixinClassName.startsWith(activePackage + ".");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }

    @Override
    public List<String> getMixins() {
        return selected ? List.of("ContainerScreenAccessor", "QuickAddNavigationMixin") : List.of();
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo) { }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo) { }
}
