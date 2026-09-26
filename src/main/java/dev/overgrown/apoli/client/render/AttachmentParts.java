package dev.overgrown.apoli.client.render;

import dev.overgrown.apoli.data.BodyAttachments;
import dev.overgrown.apoli.data.ModelPartTimeline;
import dev.overgrown.apoli.data.ModelPartTransformation;
import dev.overgrown.apoli.power.builtin.ModelColorPower;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class AttachmentParts {
    public static final int STRIDE = 5;

    private static final int PAIRED_EARS = BodyAttachments.RIGHT_EAR | BodyAttachments.LEFT_EAR;
    private static final int PAIRED_WINGS = BodyAttachments.RIGHT_WING | BodyAttachments.LEFT_WING;

    private static final ThreadLocal<List<ModelColorPower.PartColor>> SCRATCH =
        ThreadLocal.withInitial(() -> new ArrayList<>(4));

    private AttachmentParts() {}

    public static float[] newColours() {
        float[] colours = new float[BodyAttachments.COUNT * STRIDE];
        reset(colours);
        return colours;
    }

    public static int hiddenMask(@Nullable LivingEntity entity) {
        if (entity == null) return 0;
        List<ModelPartTimeline.Slot> slots = ModelPartAnimator.update(entity);
        if (slots.isEmpty()) return 0;
        boolean handPass = HandRenderPass.active();
        int hidden = 0;
        for (int i = 0; i < slots.size(); i++) {
            ModelPartTimeline.Slot slot = slots.get(i);
            if (slot.weight() < 0.5F || !slot.rendersIn(handPass)) continue;
            ModelPartTransformation transformation = slot.transformation();
            int bits = transformation.bodyPart().attachments();
            if (bits == 0) continue;
            boolean on = slot.value() != 0.0F;
            switch (transformation.type()) {
                case VISIBLE -> hidden = on ? hidden & ~bits : hidden | bits;
                case HIDDEN -> hidden = on ? hidden | bits : hidden & ~bits;
                default -> {
                }
            }
        }
        if ((hidden & PAIRED_EARS) == PAIRED_EARS) hidden |= BodyAttachments.EAR_PAIR;
        if ((hidden & PAIRED_WINGS) == PAIRED_WINGS) hidden |= BodyAttachments.WING_PAIR;
        return hidden;
    }

    public static boolean colours(@Nullable Entity source, float[] out) {
        if (!ModelColorPower.hasPartColors(source)) return false;
        List<ModelColorPower.PartColor> parts = SCRATCH.get();
        parts.clear();
        ModelColorPower.collectPartColors(source, parts);
        boolean tinted = false;
        for (int i = 0; i < parts.size(); i++) {
            ModelColorPower.PartColor colour = parts.get(i);
            int bits = colour.part().attachments();
            if (bits == 0) continue;
            if (!tinted) {
                reset(out);
                tinted = true;
            }
            while (bits != 0) {
                int at = Integer.numberOfTrailingZeros(bits) * STRIDE;
                bits &= bits - 1;
                out[at] *= colour.red();
                out[at + 1] *= colour.green();
                out[at + 2] *= colour.blue();
                out[at + 3] *= colour.alpha();
                if (colour.whiten()) out[at + 4] = 1.0F;
            }
        }
        parts.clear();
        return tinted;
    }

    public static boolean tinted(float[] colours, int at) {
        return colours[at] != 1.0F || colours[at + 1] != 1.0F || colours[at + 2] != 1.0F
            || colours[at + 3] != 1.0F || colours[at + 4] != 0.0F;
    }

    private static void reset(float[] colours) {
        for (int at = 0; at < colours.length; at += STRIDE) {
            colours[at] = 1.0F;
            colours[at + 1] = 1.0F;
            colours[at + 2] = 1.0F;
            colours[at + 3] = 1.0F;
            colours[at + 4] = 0.0F;
        }
    }
}
