package dev.overgrown.apoli.compat.grave;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class GraveCompatMixinPlugin implements IMixinConfigPlugin {

    private static final Map<String, String> REQUIRED_CLASS = Map.of(
        "dev.overgrown.apoli.compat.yigd.mixin", "com/b1n_ry/yigd/compat/InvModCompat.class",
        "dev.overgrown.apoli.compat.gravestone.mixin", "de/maxhenkel/gravestone/tileentity/GraveStoneTileEntity.class");

    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        String required = REQUIRED_CLASS.get(mixinPackage);
        this.present = required != null && GraveCompatMixinPlugin.class.getClassLoader().getResource(required) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.present;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
