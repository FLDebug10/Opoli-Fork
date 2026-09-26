package dev.overgrown.apoli.mixin.modelparts;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.overgrown.apoli.client.render.AnimationPlayer;
import dev.overgrown.apoli.client.render.HandRenderPass;
import dev.overgrown.apoli.client.render.ModelPartAnimator;
import dev.overgrown.apoli.client.render.ModelPartEdits;
import dev.overgrown.apoli.data.ModelAnimation;
import dev.overgrown.apoli.power.builtin.ModifyPlayerModelPower;
import dev.overgrown.apoli.data.ModelPartTimeline;
import dev.overgrown.apoli.client.render.ModelPartLookup;
import dev.overgrown.apoli.data.BodyPart;
import dev.overgrown.apoli.data.HumanoidPose;
import dev.overgrown.apoli.data.ModelPartTransformation;
import dev.overgrown.apoli.data.PoseMath;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Mixin(HumanoidModel.class)
@Environment(EnvType.CLIENT)
public abstract class HumanoidModelModifyPartsMixin {

    private static final String SETUP_ANIM = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V";

    @Unique
    private final Map<ModelPart, float[]> apoli$originals = new IdentityHashMap<>();
    @Unique
    private final List<ModelPart> apoli$scratch = new ArrayList<>(2);
    @Unique
    private final float[] apoli$limbs = new float[HumanoidPose.PART_COUNT * PoseMath.STRIDE];
    @Unique
    private final float[] apoli$pose = new float[PoseMath.STRIDE];
    @Unique
    private final float[] apoli$pivot = new float[3];
    @Unique
    private final double[] apoli$vector = new double[3];
    @Unique
    private ModelAnimation apoli$modelAnimation;
    @Unique
    private boolean apoli$hadPower;
    @Unique
    private boolean apoli$poseOverridden;
    @Unique
    private float apoli$savedSwimAmount;
    @Unique
    private boolean apoli$savedCrouching;
    @Unique
    private boolean apoli$savedRiding;

    @Inject(method = SETUP_ANIM, at = @At("HEAD"))
    private void apoli$modifyPartsHead(LivingEntity entity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        this.apoli$modelAnimation = (Object) this instanceof PlayerModel
            ? ModifyPlayerModelPower.firstActiveAnimations(entity)
            : null;
        boolean has = !ModelPartAnimator.update(entity).isEmpty() || this.apoli$modelAnimation != null;
        if (has) {
            if (apoli$originals.isEmpty()) apoli$snapshot();
            apoli$restore();
        } else if (apoli$hadPower) {
            apoli$restore();
            apoli$originals.clear();
        }
        apoli$hadPower = has;

        apoli$poseOverridden = ModelPartAnimator.overridesPose(entity);
        if (apoli$poseOverridden) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            apoli$savedSwimAmount = model.swimAmount;
            apoli$savedCrouching = model.crouching;
            apoli$savedRiding = model.riding;
            model.swimAmount = 0.0F;
            model.crouching = false;
            model.riding = false;
        }
    }

    @WrapOperation(method = SETUP_ANIM, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;getFallFlyingTicks()I"))
    private int apoli$neutralizeFallFlying(LivingEntity entity, Operation<Integer> original) {
        return apoli$poseOverridden ? 0 : original.call(entity);
    }

    @Inject(method = SETUP_ANIM, at = @At("TAIL"))
    private void apoli$modifyPartsTail(LivingEntity entity, float f, float g, float h, float i, float j, CallbackInfo ci) {
        List<ModelPartTimeline.Slot> slots = ModelPartAnimator.update(entity);
        if (!slots.isEmpty()) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            boolean handPass = HandRenderPass.active();
            for (int s = 0; s < slots.size(); s++) {
                ModelPartTimeline.Slot slot = slots.get(s);
                if (slot.weight() <= 0.0F) continue;
                if (!slot.rendersIn(handPass)) continue;
                ModelPartTransformation transformation = slot.transformation();
                BodyPart part = transformation.bodyPart().sided(entity);
                apoli$scratch.clear();
                ModelPartLookup.resolveInto(model, part, apoli$scratch);
                if (part.isGroup() && PoseMath.isSpatial(transformation.type())) {
                    apoli$applyGroup(model, part, slot);
                    continue;
                }
                for (int p = 0; p < apoli$scratch.size(); p++) {
                    ModelPart member = apoli$scratch.get(p);
                    ModelPartEdits.apply(member, slot, apoli$originals.get(member));
                }
            }
            apoli$scratch.clear();
        }

        if (this.apoli$modelAnimation != null) {
            AnimationPlayer.apply((HumanoidModel<?>) (Object) this, entity, this.apoli$modelAnimation,
                ModifyPlayerModelPower.CANONICAL, Mth.clamp(h - entity.tickCount, 0.0F, 1.0F));
            this.apoli$modelAnimation = null;
        }

        if (apoli$poseOverridden) {
            HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
            model.swimAmount = apoli$savedSwimAmount;
            model.crouching = apoli$savedCrouching;
            model.riding = apoli$savedRiding;
            apoli$poseOverridden = false;
        }
    }

    @Unique
    private void apoli$applyGroup(HumanoidModel<?> model, BodyPart part, ModelPartTimeline.Slot slot) {
        ModelPartTransformation transformation = slot.transformation();
        ModelPartLookup.limbPosesInto(model, apoli$limbs);
        HumanoidPose.groupPivot(part, transformation, apoli$limbs, apoli$vector, apoli$pivot);
        for (int p = 0; p < apoli$scratch.size(); p++) {
            ModelPart member = apoli$scratch.get(p);
            ModelPartLookup.read(member, apoli$pose, 0);
            PoseMath.applyGroup(transformation.type(), apoli$pose, 0, slot.value(), slot.weight(),
                transformation.overrideAnimation(), apoli$pivot[0], apoli$pivot[1], apoli$pivot[2]);
            ModelPartLookup.write(apoli$pose, 0, member);
        }
        apoli$scratch.clear();
    }

    @Unique
    private void apoli$snapshot() {
        apoli$scratch.clear();
        ModelPartLookup.allPartsInto((HumanoidModel<?>) (Object) this, apoli$scratch);
        for (ModelPart part : apoli$scratch) {
            apoli$originals.put(part, ModelPartEdits.snapshot(part));
        }
        apoli$scratch.clear();
    }

    @Unique
    private void apoli$restore() {
        for (Map.Entry<ModelPart, float[]> entry : apoli$originals.entrySet()) {
            ModelPartEdits.restore(entry.getKey(), entry.getValue());
        }
    }
}
