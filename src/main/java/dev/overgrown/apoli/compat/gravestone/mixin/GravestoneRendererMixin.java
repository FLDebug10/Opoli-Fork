package dev.overgrown.apoli.compat.gravestone.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import de.maxhenkel.gravestone.tileentity.render.GravestoneRenderer;
import dev.overgrown.apoli.compat.grave.InscribedGrave;
import dev.overgrown.apoli.compat.grave.client.GraveInscriptionRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(GravestoneRenderer.class)
@OnlyIn(Dist.CLIENT)
public abstract class GravestoneRendererMixin {

    @Unique
    private static final double APOLI$STONE_WIDTH = 0.8D;

    @WrapOperation(
        method = "render(Lde/maxhenkel/gravestone/tileentity/GraveStoneTileEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private int apoli$drawInscription(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f pose,
                                      MultiBufferSource buffers, Font.DisplayMode mode, int background, int light,
                                      Operation<Integer> original, @Local(argsOnly = true) GraveStoneTileEntity grave,
                                      @Local(argsOnly = true) PoseStack matrices, @Local double textScale) {
        int result = original.call(font, text, x, y, color, shadow, pose, buffers, mode, background, light);
        if (grave instanceof InscribedGrave inscribed) {
            List<Component> lines = inscribed.apoli$inscription();
            if (!lines.isEmpty()) {
                GraveInscriptionRenderer.drawBelow(font, lines, x + font.width(text) / 2.0F,
                    (float) (APOLI$STONE_WIDTH / textScale), matrices, buffers, color, light);
            }
        }
        return result;
    }
}
