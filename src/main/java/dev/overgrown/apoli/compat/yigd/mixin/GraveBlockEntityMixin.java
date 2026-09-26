package dev.overgrown.apoli.compat.yigd.mixin;

import com.b1n_ry.yigd.block.entity.GraveBlockEntity;
import com.b1n_ry.yigd.components.GraveComponent;
import dev.overgrown.apoli.compat.grave.GraveInscriptions;
import dev.overgrown.apoli.compat.grave.InscribedGrave;
import dev.overgrown.apoli.compat.yigd.ApoliYigdCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(GraveBlockEntity.class)
public abstract class GraveBlockEntityMixin implements InscribedGrave {

    @Unique
    private static final String APOLI$INSCRIPTION = "apoli:inscription";

    @Shadow(remap = false)
    private GraveComponent component;

    @Unique
    private List<Component> apoli$inscription = List.of();

    @Inject(method = "getUpdateTag()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void apoli$sendInscription(CallbackInfoReturnable<CompoundTag> cir) {
        GraveInscriptions.write(cir.getReturnValue(), APOLI$INSCRIPTION, ApoliYigdCompat.inscriptionOf(this.component));
    }

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void apoli$readInscription(CompoundTag tag, CallbackInfo ci) {
        this.apoli$inscription = GraveInscriptions.read(tag, APOLI$INSCRIPTION);
    }

    @Override
    public List<Component> apoli$inscription() {
        return this.apoli$inscription;
    }
}
