package dev.overgrown.apoli.compat.yigd.mixin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

public class YigdCompatMixinPlugin implements IMixinConfigPlugin {

    private static final boolean UPSTREAM_ORIGINS_HOOK = registersEventInConstructor(
        "com/b1n_ry/yigd/compat/OriginsCompat.class");

    private static final boolean YIGD_PRESENT =
        YigdCompatMixinPlugin.class.getClassLoader().getResource("com/b1n_ry/yigd/compat/InvModCompat.class") != null;

    private static boolean registersEventInConstructor(String path) {
        try (InputStream stream = YigdCompatMixinPlugin.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) return false;
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : node.methods) {
                if (!method.name.equals("<init>")) continue;
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call
                        && call.owner.equals("net/fabricmc/fabric/api/event/Event")
                        && call.name.equals("register")
                        && call.desc.equals("(Ljava/lang/Object;)V")) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return mixinClassName.endsWith(".OriginsCompatMixin") ? UPSTREAM_ORIGINS_HOOK : YIGD_PRESENT;
    }

    @Override
    public void onLoad(String mixinPackage) {}

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
