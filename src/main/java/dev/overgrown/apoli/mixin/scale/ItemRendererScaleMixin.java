package dev.overgrown.apoli.mixin.scale;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.overgrown.apoli.scale.ScaleType;
import dev.overgrown.apoli.scale.ScaleTypes;
import dev.overgrown.apoli.scale.Scales;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

@Mixin(ItemRenderer.class)
@Environment(EnvType.CLIENT)
public abstract class ItemRendererScaleMixin {

    @Unique
    private static boolean apoli$scalable(@Nullable LivingEntity entity, ItemStack stack, ItemDisplayContext context) {
        if (entity == null || stack.isEmpty()) return false;
        if (context == ItemDisplayContext.HEAD
            && entity.getMainHandItem() != stack && entity.getOffhandItem() != stack) {
            return false;
        }
        return !Scales.untouched(entity);
    }

    @Unique
    private static ScaleType apoli$handScale(LivingEntity entity, ItemStack stack, boolean leftHand) {
        if (stack == entity.getOffhandItem()) return ScaleTypes.HELD_ITEM_OFFHAND;
        if (stack == entity.getMainHandItem()) return ScaleTypes.HELD_ITEM_MAINHAND;
        return leftHand == (entity.getMainArm() == HumanoidArm.LEFT)
            ? ScaleTypes.HELD_ITEM_MAINHAND
            : ScaleTypes.HELD_ITEM_OFFHAND;
    }

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("HEAD"))
    private void apoli$scaleHeldItemPush(@Nullable LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                         boolean leftHand, PoseStack poseStack, MultiBufferSource buffer,
                                         @Nullable Level level, int light, int overlay, int seed, CallbackInfo ci) {
        if (!apoli$scalable(entity, stack, context)) return;
        poseStack.pushPose();
        float scale = Scales.applied(entity, apoli$handScale(entity, stack, leftHand), Minecraft.getInstance().getFrameTime());
        if (scale != 1.0F) poseStack.scale(scale, scale, scale);
    }

    @Inject(method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
            at = @At("RETURN"))
    private void apoli$scaleHeldItemPop(@Nullable LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                        boolean leftHand, PoseStack poseStack, MultiBufferSource buffer,
                                        @Nullable Level level, int light, int overlay, int seed, CallbackInfo ci) {
        if (!apoli$scalable(entity, stack, context)) return;
        poseStack.popPose();
    }
}
