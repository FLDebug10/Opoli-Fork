package dev.overgrown.apoli.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.overgrown.apoli.condition.context.EntityCtx;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerType;
import dev.overgrown.apoli.power.PowerTypeRegistry;
import dev.overgrown.apoli.client.render.AnimatedTextures;
import dev.overgrown.apoli.client.render.DynamicTextures;
import dev.overgrown.apoli.data.expr.ExprContext;
import dev.overgrown.apoli.data.TextureRef;
import dev.overgrown.apoli.power.builtin.OverlayPower;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class OverlayRenderer {
    private static final int SLOT_COUNT = ExprContext.slot("count");
    private static final int SLOT_INDEX = ExprContext.slot("index");

    private OverlayRenderer() {}

    public static void renderBelowHud(GuiGraphics graphics, float partialTick) {
        render(graphics, OverlayPower.DrawPhase.BELOW_HUD);
    }

    public static void renderAboveHud(GuiGraphics graphics, float partialTick) {
        render(graphics, OverlayPower.DrawPhase.ABOVE_HUD);
    }

    private static void render(GuiGraphics graphics, OverlayPower.DrawPhase phase) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean hudHidden = mc.options.hideGui;
        boolean thirdPerson = mc.options.getCameraType() != net.minecraft.client.CameraType.FIRST_PERSON;
        EntityCtx ctx = new EntityCtx(player, player.level());

        for (ResourceLocation powerId : ClientPowerState.localPowers()) {
            Power power = ApoliPowers.get(powerId);
            if (power == null) continue;
            PowerType<?> type = PowerTypeRegistry.get(power.typeId());
            if (!(type instanceof OverlayPower)) continue;
            if (!(power.config() instanceof OverlayPower.Config cfg)) continue;
            if (power.condition().isPresent() && !power.condition().get().test(ctx)) continue;

            List<OverlayPower.Entry> overlays = cfg.overlays();
            for (int i = 0; i < overlays.size(); i++) {
                OverlayPower.Entry entry = overlays.get(i);
                if (entry.drawPhase() != phase) continue;
                if (hudHidden && entry.hideWithHud()) continue;
                if (thirdPerson && !entry.visibleInThirdPerson()) continue;
                if (!entry.shouldRender(ctx)) continue;

                drawEntry(graphics, player, entry);
            }
        }
    }


    private static void drawEntry(GuiGraphics graphics, LocalPlayer player, OverlayPower.Entry entry) {
        ResourceLocation setId = entry.texture().set().orElse(null);
        if (setId == null) {
            draw(graphics, player, entry, DynamicTextures.subject(entry.texture(), player), null);
            return;
        }
        java.util.List<java.util.UUID> members = ClientEntitySets.members(setId);
        int count = members.size();
        if (count == 0) ClientEntitySets.warnIfUnknown(setId);
        double previousCount = ExprContext.push(SLOT_COUNT, count);
        double previousIndex = ExprContext.get(SLOT_INDEX);
        try {
            for (int i = 0; i < count; i++) {
                java.util.UUID uuid = members.get(i);
                ExprContext.push(SLOT_INDEX, i);
                draw(graphics, player, entry, memberEntity(player, uuid), uuid);
            }
        } finally {
            ExprContext.pop(SLOT_INDEX, previousIndex);
            ExprContext.pop(SLOT_COUNT, previousCount);
        }
    }

    private static net.minecraft.world.entity.Entity memberEntity(LocalPlayer player, java.util.UUID uuid) {
        if (player.getUUID().equals(uuid)) return player;
        java.util.List<? extends net.minecraft.world.entity.player.Player> players = player.level().players();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getUUID().equals(uuid)) return players.get(i);
        }
        return null;
    }

    private static void draw(GuiGraphics graphics, LocalPlayer player, OverlayPower.Entry entry,
                             net.minecraft.world.entity.Entity subject, java.util.UUID member) {
        switch (entry.drawMode()) {
            case TEXTURE -> drawTexture(graphics, player, entry, subject, member);
            case NAUSEA -> drawNausea(graphics, player, entry);
        }
    }

    private static void drawTexture(GuiGraphics graphics, LocalPlayer player, OverlayPower.Entry entry,
                                    net.minecraft.world.entity.Entity subject, java.util.UUID member) {
        int lock = entry.guiScaleLock();
        float poseScale = poseScale(lock);
        int screenW = screenWidth(graphics, lock);
        int screenH = screenHeight(graphics, lock);
        int w = entry.width().map(e -> e.evalInt(player)).orElse(screenW);
        int h = entry.height().map(e -> e.evalInt(player)).orElse(screenH);
        if (w <= 0 || h <= 0) return;
        int x = entry.anchor().originX(screenW, w) + entry.x().evalInt(player);
        int y = entry.anchor().originY(screenH, h) + entry.y().evalInt(player);

        TextureRef ref = entry.texture();
        TextureRef.Kind kind = ref.kind();
        if (kind != null && kind.isItem()) {
            if (poseScale != 1.0F) {
                graphics.pose().pushPose();
                graphics.pose().scale(poseScale, poseScale, 1.0F);
            }
            drawItem(graphics, DynamicTextures.stack(ref.texture(), subject), x, y, w, h);
            if (poseScale != 1.0F) graphics.pose().popPose();
            return;
        }
        ResourceLocation texture = kind == null
            ? AnimatedTextures.bindable(ref.texture())
            : DynamicTextures.resolve(ref.texture(), subject, member);
        boolean face = kind == TextureRef.Kind.PLAYER_FACE;
        int texW = entry.textureWidth().orElse(face ? 64 : w);
        int texH = entry.textureHeight().orElse(face ? 64 : h);
        int u = entry.u().evalInt(player);
        int v = entry.v().evalInt(player);
        if (face && entry.textureWidth().isEmpty() && u == 0 && v == 0) {
            u = 8;
            v = 8;
        }
        int regionW = entry.regionWidth().orElse(face ? 8 : w);
        int regionH = entry.regionHeight().orElse(face ? 8 : h);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((float) entry.red().eval(player), (float) entry.green().eval(player),
            (float) entry.blue().eval(player), (float) entry.strength().eval(player));
        if (poseScale != 1.0F) {
            graphics.pose().pushPose();
            graphics.pose().scale(poseScale, poseScale, 1.0F);
        }
        if (regionW == w && regionH == h) {
            graphics.blit(texture, x, y, -90, u, v, w, h, texW, texH);
        } else {
            graphics.blit(texture, x, y, w, h, u, v, regionW, regionH, texW, texH);
            if (face) {
                graphics.blit(texture, x, y, w, h, 40, 8, regionW, regionH, texW, texH);
            }
        }
        if (poseScale != 1.0F) graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void drawItem(GuiGraphics graphics, net.minecraft.world.item.ItemStack stack,
                                 int x, int y, int w, int h) {
        if (stack.isEmpty()) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(w / 16.0F, h / 16.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private static void drawNausea(GuiGraphics graphics, LocalPlayer player, OverlayPower.Entry entry) {
        int lock = entry.guiScaleLock();
        float poseScale = poseScale(lock);
        int screenW = screenWidth(graphics, lock);
        int screenH = screenHeight(graphics, lock);
        float strength = Mth.clamp((float) entry.strength().eval(player), 0.0F, 1.0F);
        float scale = Mth.lerp(strength, 2.0F, 1.0F);

        int baseW = entry.width().map(e -> e.evalInt(player)).orElse(screenW);
        int baseH = entry.height().map(e -> e.evalInt(player)).orElse(screenH);
        int quadW = Math.round(baseW * scale);
        int quadH = Math.round(baseH * scale);
        if (quadW <= 0 || quadH <= 0) return;
        int x = entry.anchor().originX(screenW, quadW) + entry.x().evalInt(player);
        int y = entry.anchor().originY(screenH, quadH) + entry.y().evalInt(player);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor((float) entry.red().eval(player) * strength,
            (float) entry.green().eval(player) * strength, (float) entry.blue().eval(player) * strength, 1.0F);
        if (poseScale != 1.0F) {
            graphics.pose().pushPose();
            graphics.pose().scale(poseScale, poseScale, 1.0F);
        }
        graphics.blit(nauseaTexture(entry.texture(), player),
            x, y, -90, entry.u().evalInt(player), entry.v().evalInt(player), quadW, quadH, quadW, quadH);
        if (poseScale != 1.0F) graphics.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static ResourceLocation nauseaTexture(TextureRef ref, LocalPlayer player) {
        if (ref.kind() == null) return AnimatedTextures.bindable(ref.texture());
        return DynamicTextures.resolve(ref.texture(), DynamicTextures.subject(ref, player));
    }

    private static float poseScale(int lock) {
        if (lock <= 0) return 1.0F;
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        return guiScale <= 0.0 ? 1.0F : (float) (lock / guiScale);
    }

    private static int screenWidth(GuiGraphics graphics, int lock) {
        if (lock <= 0) return graphics.guiWidth();
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, (int) Math.ceil(window.getWidth() / (double) lock));
    }

    private static int screenHeight(GuiGraphics graphics, int lock) {
        if (lock <= 0) return graphics.guiHeight();
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, (int) Math.ceil(window.getHeight() / (double) lock));
    }
}
