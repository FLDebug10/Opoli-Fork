package dev.overgrown.apoli.compat.yigd.mixin;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.client.render.GraveBlockEntityRenderer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.overgrown.apoli.compat.grave.InscribedGrave;
import dev.overgrown.apoli.compat.grave.client.GraveInscriptionRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(GraveBlockEntityRenderer.class)
@Environment(EnvType.CLIENT)
public abstract class GraveBlockEntityRendererMixin {

    @WrapOperation(
        method = "renderGraveText(Lcom/b1n_ry/yigd/block/entity/GraveBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private int apoli$drawInscription(Font font, Component text, float x, float y, int color, boolean shadow, Matrix4f pose,
                                      MultiBufferSource buffers, Font.DisplayMode mode, int background, int light,
                                      Operation<Integer> original, @Local(argsOnly = true) GraveBlockEntity grave,
                                      @Local(argsOnly = true) PoseStack matrices) {
        int result = original.call(font, text, x, y, color, shadow, pose, buffers, mode, background, light);
        if (grave instanceof InscribedGrave inscribed) {
            List<Component> lines = inscribed.apoli$inscription();
            if (!lines.isEmpty()) {
                int width = font.width(text);
                GraveInscriptionRenderer.drawBelow(font, lines, x + width / 2.0F, width, matrices, buffers, color, light);
            }
        }
        return result;
    }
}
