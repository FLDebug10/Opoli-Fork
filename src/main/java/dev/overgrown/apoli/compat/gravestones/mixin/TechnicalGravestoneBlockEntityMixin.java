package dev.overgrown.apoli.compat.gravestones.mixin;

import dev.overgrown.apoli.compat.grave.InscribedGrave;
import dev.overgrown.apoli.compat.gravestones.GravestonesCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.pneumono.gravestones.block.TechnicalGravestoneBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(TechnicalGravestoneBlockEntity.class)
@Environment(EnvType.CLIENT)
public abstract class TechnicalGravestoneBlockEntityMixin implements InscribedGrave {

    @Shadow(remap = false)
    public abstract CompoundTag getContents();

    @Unique
    private List<Component> apoli$inscription = List.of();

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void apoli$readInscription(CompoundTag tag, CallbackInfo ci) {
        this.apoli$inscription = GravestonesCompat.inscription(this.getContents());
    }

    @Override
    public List<Component> apoli$inscription() {
        return this.apoli$inscription;
    }
}
