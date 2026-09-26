package dev.overgrown.apoli.compat.gravestones.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.overgrown.apoli.compat.grave.GraveInscriptions;
import dev.overgrown.apoli.compat.grave.InscribedGrave;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignText;
import net.pneumono.gravestones.block.TechnicalGravestoneBlockEntity;
import net.pneumono.gravestones.content.TechnicalGravestoneBlockEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(TechnicalGravestoneBlockEntityRenderer.class)
@Environment(EnvType.CLIENT)
public abstract class TechnicalGravestoneBlockEntityRendererMixin {

    @ModifyReturnValue(
        method = "getSignText(Lnet/pneumono/gravestones/block/TechnicalGravestoneBlockEntity;)Lnet/minecraft/world/level/block/entity/SignText;",
        at = @At("RETURN"))
    private SignText apoli$inscribe(SignText text, @Local(argsOnly = true) TechnicalGravestoneBlockEntity gravestone) {
        if (!(gravestone instanceof InscribedGrave inscribed)) return text;
        List<Component> lines = inscribed.apoli$inscription();
        if (lines.isEmpty()) return text;
        Component inscription = GraveInscriptions.join(lines);
        return new SignText(apoli$shifted(text, false, inscription), apoli$shifted(text, true, inscription),
            text.getColor(), text.hasGlowingText());
    }

    @Unique
    private static Component[] apoli$shifted(SignText text, boolean filtered, Component inscription) {
        return new Component[]{text.getMessage(0, filtered), inscription, text.getMessage(1, filtered), text.getMessage(2, filtered)};
    }
}
