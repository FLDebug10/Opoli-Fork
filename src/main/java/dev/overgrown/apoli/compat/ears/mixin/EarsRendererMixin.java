package dev.overgrown.apoli.compat.ears.mixin;

import com.unascribed.ears.api.EarsFeatureType;
import com.unascribed.ears.api.features.EarsFeatures;
import com.unascribed.ears.common.render.EarsRenderDelegate;
import dev.overgrown.apoli.compat.ears.EarsDelegateState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.unascribed.ears.common.EarsRenderer", remap = false)
public abstract class EarsRendererMixin {

    private static final String RENDER = "render(Lcom/unascribed/ears/api/features/EarsFeatures;"
        + "Lcom/unascribed/ears/common/render/EarsRenderDelegate;)V";
    private static final String RENDER_INNER = "renderInner(Lcom/unascribed/ears/api/features/EarsFeatures;"
        + "Lcom/unascribed/ears/common/render/EarsRenderDelegate;IZ)V";
    private static final String IS_INHIBITED = "isInhibited(Lcom/unascribed/ears/common/render/EarsRenderDelegate;"
        + "Lcom/unascribed/ears/api/EarsFeatureType;)Z";

    @Inject(method = RENDER, at = @At("HEAD"), require = 0)
    private static void apoli$beginAttachments(EarsFeatures features, EarsRenderDelegate delegate, CallbackInfo ci) {
        if (delegate instanceof EarsDelegateState state) state.apoli$begin(features);
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 0)
    private static void apoli$endAttachments(EarsFeatures features, EarsRenderDelegate delegate, CallbackInfo ci) {
        if (delegate instanceof EarsDelegateState state) state.apoli$end();
    }

    @Inject(method = RENDER_INNER, at = @At("HEAD"), require = 0)
    private static void apoli$resetFeature(EarsFeatures features, EarsRenderDelegate delegate, int pass,
                                           boolean emissive, CallbackInfo ci) {
        if (delegate instanceof EarsDelegateState state) state.apoli$feature(null);
    }

    @Inject(method = IS_INHIBITED, at = @At("RETURN"), require = 0)
    private static void apoli$trackFeature(EarsRenderDelegate delegate, EarsFeatureType feature,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (delegate instanceof EarsDelegateState state) state.apoli$feature(cir.getReturnValue() ? null : feature);
    }
}
