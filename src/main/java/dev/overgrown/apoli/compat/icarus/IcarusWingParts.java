package dev.overgrown.apoli.compat.icarus;

import dev.overgrown.apoli.client.disguise.ClientDisguiseManager;
import dev.overgrown.apoli.client.render.AttachmentParts;
import dev.overgrown.apoli.client.render.HandRenderPass;
import dev.overgrown.apoli.client.render.ModelPartAnimator;
import dev.overgrown.apoli.client.render.ModelPartEdits;
import dev.overgrown.apoli.client.render.PartColorMap;
import dev.overgrown.apoli.data.BodyAttachments;
import dev.overgrown.apoli.data.ModelPartTimeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class IcarusWingParts {
    private static final float[] COLOURS = AttachmentParts.newColours();
    private static final int RIGHT = BodyAttachments.index(BodyAttachments.RIGHT_WING) * AttachmentParts.STRIDE;
    private static final int LEFT = BodyAttachments.index(BodyAttachments.LEFT_WING) * AttachmentParts.STRIDE;

    private IcarusWingParts() {}

    public static boolean edit(LivingEntity entity, ModelPart rightWing, ModelPart leftWing,
                               float[] rightOriginal, float[] leftOriginal) {
        boolean edited = false;
        List<ModelPartTimeline.Slot> slots = ModelPartAnimator.update(entity);
        if (!slots.isEmpty()) {
            boolean handPass = HandRenderPass.active();
            for (int i = 0; i < slots.size(); i++) {
                ModelPartTimeline.Slot slot = slots.get(i);
                if (slot.weight() <= 0.0F || !slot.rendersIn(handPass)) continue;
                int bits = slot.transformation().bodyPart().attachments();
                if ((bits & BodyAttachments.RIGHT_WING) != 0) {
                    ModelPartEdits.apply(rightWing, slot, rightOriginal);
                    edited = true;
                }
                if ((bits & BodyAttachments.LEFT_WING) != 0) {
                    ModelPartEdits.apply(leftWing, slot, leftOriginal);
                    edited = true;
                }
            }
        }
        if (AttachmentParts.colours(ClientDisguiseManager.powerSource(entity), COLOURS)) {
            if (AttachmentParts.tinted(COLOURS, RIGHT)) PartColorMap.attach(rightWing, COLOURS, RIGHT);
            if (AttachmentParts.tinted(COLOURS, LEFT)) PartColorMap.attach(leftWing, COLOURS, LEFT);
        }
        return edited;
    }
}
