package dev.overgrown.apoli.compat.ears.mixin;

import com.unascribed.ears.api.EarsFeatureType;
import com.unascribed.ears.api.features.EarsFeatures;
import com.unascribed.ears.common.render.AbstractEarsRenderDelegate;
import com.unascribed.ears.common.render.EarsRenderDelegate;
import dev.overgrown.apoli.client.disguise.ClientDisguiseManager;
import dev.overgrown.apoli.client.render.AttachmentParts;
import dev.overgrown.apoli.client.render.SkinRenderCompat;
import dev.overgrown.apoli.compat.ears.EarsAttachments;
import dev.overgrown.apoli.compat.ears.EarsDelegateState;
import dev.overgrown.apoli.data.BodyAttachments;
import dev.overgrown.apoli.power.builtin.ModelColorPower;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = AbstractEarsRenderDelegate.class, remap = false)
@OnlyIn(Dist.CLIENT)
public abstract class AbstractEarsRenderDelegateMixin implements EarsDelegateState {

    private static final String ADD_VERTEX =
        "Lcom/unascribed/ears/common/render/AbstractEarsRenderDelegate;addVertex(FFIFFFFFFFFF)V";
    private static final String RENDER_FRONT =
        "renderFront(IIIILcom/unascribed/ears/common/render/EarsRenderDelegate$TexRotation;"
            + "Lcom/unascribed/ears/common/render/EarsRenderDelegate$TexFlip;"
            + "Lcom/unascribed/ears/common/render/EarsRenderDelegate$QuadGrow;)V";
    private static final String RENDER_BACK =
        "renderBack(IIIILcom/unascribed/ears/common/render/EarsRenderDelegate$TexRotation;"
            + "Lcom/unascribed/ears/common/render/EarsRenderDelegate$TexFlip;"
            + "Lcom/unascribed/ears/common/render/EarsRenderDelegate$QuadGrow;)V";

    @Shadow
    protected Object peer;

    @Unique
    private float[] apoli$colour = ModelColorPower.IDENTITY;
    @Unique
    private float[] apoli$whole = ModelColorPower.IDENTITY;
    @Unique
    private final float[] apoli$colours = AttachmentParts.newColours();
    @Unique
    private final float[] apoli$mixed = new float[4];
    @Unique
    private boolean apoli$prepared;
    @Unique
    private boolean apoli$tinted;
    @Unique
    private int apoli$hidden;
    @Unique
    @Nullable
    private EarsFeatures apoli$features;
    @Unique
    @Nullable
    private EarsFeatureType apoli$feature;
    @Unique
    private int apoli$quad;

    @Override
    public void apoli$begin(EarsFeatures features) {
        apoli$feature = null;
        apoli$quad = 0;
        if (!(this.peer instanceof LivingEntity entity)) {
            apoli$prepared = false;
            return;
        }
        apoli$features = features;
        apoli$whole = SkinRenderCompat.rgba(entity);
        apoli$hidden = AttachmentParts.hiddenMask(entity);
        apoli$tinted = AttachmentParts.colours(ClientDisguiseManager.powerSource(entity), apoli$colours);
        apoli$prepared = true;
    }

    @Override
    public void apoli$end() {
        apoli$prepared = false;
        apoli$feature = null;
        apoli$features = null;
    }

    @Override
    public void apoli$feature(@Nullable EarsFeatureType feature) {
        apoli$feature = feature;
        apoli$quad = 0;
    }

    @Inject(method = RENDER_FRONT, at = @At("HEAD"), cancellable = true)
    private void apoli$sampleFrontColour(int u, int v, int w, int h, EarsRenderDelegate.TexRotation rotation,
                                         EarsRenderDelegate.TexFlip flip, EarsRenderDelegate.QuadGrow grow,
                                         CallbackInfo ci) {
        if (apoli$beginQuad()) ci.cancel();
    }

    @Inject(method = RENDER_BACK, at = @At("HEAD"), cancellable = true)
    private void apoli$sampleBackColour(int u, int v, int w, int h, EarsRenderDelegate.TexRotation rotation,
                                        EarsRenderDelegate.TexFlip flip, EarsRenderDelegate.QuadGrow grow,
                                        CallbackInfo ci) {
        if (apoli$beginQuad()) ci.cancel();
    }

    @ModifyArgs(method = {RENDER_FRONT, RENDER_BACK}, at = @At(value = "INVOKE", target = ADD_VERTEX))
    private void apoli$tintVertex(Args args) {
        float[] colour = apoli$colour;
        if (colour == ModelColorPower.IDENTITY) return;
        args.set(3, args.<Float>get(3) * colour[0]);
        args.set(4, args.<Float>get(4) * colour[1]);
        args.set(5, args.<Float>get(5) * colour[2]);
        args.set(6, args.<Float>get(6) * colour[3]);
    }

    @Unique
    private boolean apoli$beginQuad() {
        if (!apoli$prepared) {
            apoli$colour = this.peer instanceof LivingEntity entity
                ? SkinRenderCompat.rgba(entity)
                : ModelColorPower.IDENTITY;
            return false;
        }
        int bit = EarsAttachments.bit(apoli$feature, apoli$features, apoli$quad++);
        if (bit != 0 && (apoli$hidden & bit) != 0) return true;
        if (bit == 0 || !apoli$tinted) {
            apoli$colour = apoli$whole;
            return false;
        }
        int at = BodyAttachments.index(bit) * AttachmentParts.STRIDE;
        float[] whole = apoli$whole;
        float[] colours = apoli$colours;
        float[] mixed = apoli$mixed;
        mixed[0] = whole[0] * colours[at];
        mixed[1] = whole[1] * colours[at + 1];
        mixed[2] = whole[2] * colours[at + 2];
        mixed[3] = whole[3] * colours[at + 3];
        apoli$colour = mixed;
        return false;
    }
}
