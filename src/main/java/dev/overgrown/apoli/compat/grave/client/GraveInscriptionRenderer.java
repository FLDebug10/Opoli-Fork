package dev.overgrown.apoli.compat.grave.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class GraveInscriptionRenderer {
    private static final float MAX_SCALE = 0.7F;

    private GraveInscriptionRenderer() {}

    public static void drawBelow(Font font, List<Component> lines, float centerX, float maxWidth, PoseStack pose,
                                 MultiBufferSource buffers, int color, int light) {
        float y = font.lineHeight + 1.0F;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            int width = font.width(line);
            if (width <= 0) continue;
            float scale = Math.min(MAX_SCALE, maxWidth / width);
            pose.pushPose();
            pose.translate(centerX, y, 0.0F);
            pose.scale(scale, scale, 1.0F);
            font.drawInBatch(line, -width / 2.0F, 0.0F, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, light);
            pose.popPose();
            y += font.lineHeight * scale + 1.0F;
        }
    }
}
