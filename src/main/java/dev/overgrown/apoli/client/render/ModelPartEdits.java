package dev.overgrown.apoli.client.render;

import dev.overgrown.apoli.data.ModelPartTimeline;
import dev.overgrown.apoli.data.ModelPartTransformation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class ModelPartEdits {
    private ModelPartEdits() {}

    public static float[] snapshot(ModelPart part) {
        return new float[]{
            part.x, part.y, part.z,
            part.xRot, part.yRot, part.zRot,
            part.xScale, part.yScale, part.zScale,
            part.visible ? 1f : 0f, part.skipDraw ? 1f : 0f
        };
    }

    public static void restore(ModelPart part, float[] o) {
        part.x = o[0]; part.y = o[1]; part.z = o[2];
        part.xRot = o[3]; part.yRot = o[4]; part.zRot = o[5];
        part.xScale = o[6]; part.yScale = o[7]; part.zScale = o[8];
        part.visible = o[9] != 0f;
        part.skipDraw = o[10] != 0f;
    }

    public static void apply(ModelPart part, ModelPartTimeline.Slot slot, @Nullable float[] o) {
        ModelPartTransformation t = slot.transformation();
        float value = slot.value();
        float weight = slot.weight();
        boolean override = t.overrideAnimation();
        switch (t.type()) {
            case PITCH -> part.xRot = override ? part.xRot + (value - part.xRot) * weight : part.xRot + value * weight;
            case YAW -> part.yRot = override ? part.yRot + (value - part.yRot) * weight : part.yRot + value * weight;
            case ROLL -> part.zRot = override ? part.zRot + (value - part.zRot) * weight : part.zRot + value * weight;
            case VISIBLE -> { if (weight >= 0.5f) part.visible = value != 0f; }
            case HIDDEN -> { if (weight >= 0.5f) part.skipDraw = value != 0f; }
            case X_SCALE -> part.xScale = (o != null ? o[6] : 1f) + value * weight;
            case Y_SCALE -> part.yScale = (o != null ? o[7] : 1f) + value * weight;
            case Z_SCALE -> part.zScale = (o != null ? o[8] : 1f) + value * weight;
            case PIVOT_X -> part.x += value * weight;
            case PIVOT_Y -> part.y += value * weight;
            case PIVOT_Z -> part.z += value * weight;
        }
    }
}
