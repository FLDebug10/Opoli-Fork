package dev.overgrown.apoli.client.render;

import dev.overgrown.apoli.power.builtin.ModelColorPower;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class PartColorMap {

    private static final ThreadLocal<PartColorMap> SCRATCH = ThreadLocal.withInitial(PartColorMap::new);

    private final Map<ModelPart, float[]> colors = new IdentityHashMap<>();
    private final List<float[]> pool = new ArrayList<>();
    private final List<ModelColorPower.PartColor> partColors = new ArrayList<>(4);
    private final List<ModelPart> parts = new ArrayList<>(8);
    private int used;

    private PartColorMap() {}

    @Nullable
    public static Map<ModelPart, float[]> build(Entity entity, HumanoidModel<?> model) {
        return SCRATCH.get().fill(entity, model, null, null);
    }

    @Nullable
    public static Map<ModelPart, float[]> buildHand(Entity entity, HumanoidModel<?> model, ModelPart arm,
                                                    ModelPart sleeve, float[] whole) {
        PartColorMap map = SCRATCH.get();
        boolean tinted = whole != ModelColorPower.IDENTITY;
        if (!tinted && !ModelColorPower.hasPartColors(entity)) return null;
        map.fill(entity, model, arm, sleeve);
        if (!tinted) return map.colors.isEmpty() ? null : map.colors;
        map.multiply(arm, whole);
        map.multiply(sleeve, whole);
        return map.colors;
    }

    public static void attach(ModelPart part, float[] colours, int at) {
        PartColorMap map = SCRATCH.get();
        float[] color = map.colors.get(part);
        if (color == null) {
            color = map.next();
            map.colors.put(part, color);
        }
        System.arraycopy(colours, at, color, 0, 5);
        ModelColorState.set(map.colors);
    }

    private void multiply(ModelPart part, float[] whole) {
        float[] color = colors.get(part);
        if (color == null) {
            color = next();
            colors.put(part, color);
        }
        color[0] *= whole[0];
        color[1] *= whole[1];
        color[2] *= whole[2];
        color[3] *= whole[3];
        color[4] = Math.max(color[4], whole[4]);
    }

    @Nullable
    private Map<ModelPart, float[]> fill(Entity entity, HumanoidModel<?> model, @Nullable ModelPart onlyA,
                                         @Nullable ModelPart onlyB) {
        colors.clear();
        used = 0;
        partColors.clear();
        ModelColorPower.collectPartColors(entity, partColors);
        for (int i = 0; i < partColors.size(); i++) {
            ModelColorPower.PartColor partColor = partColors.get(i);
            parts.clear();
            ModelPartLookup.resolveInto(model, partColor.part().sided(entity), parts);
            for (int p = 0; p < parts.size(); p++) {
                ModelPart part = parts.get(p);
                if (onlyA != null && part != onlyA && part != onlyB) continue;
                float[] color = colors.get(part);
                if (color == null) {
                    color = next();
                    colors.put(part, color);
                }
                color[0] *= partColor.red();
                color[1] *= partColor.green();
                color[2] *= partColor.blue();
                color[3] *= partColor.alpha();
                if (partColor.whiten()) color[4] = 1f;
            }
        }
        parts.clear();
        partColors.clear();
        return colors.isEmpty() ? null : colors;
    }

    private float[] next() {
        float[] color;
        if (used < pool.size()) {
            color = pool.get(used);
        } else {
            color = new float[5];
            pool.add(color);
        }
        used++;
        color[0] = 1f;
        color[1] = 1f;
        color[2] = 1f;
        color[3] = 1f;
        color[4] = 0f;
        return color;
    }
}
