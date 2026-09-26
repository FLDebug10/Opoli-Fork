package dev.overgrown.apoli.compat.gravestone.mixin;

import de.maxhenkel.gravestone.corelib.death.Death;
import de.maxhenkel.gravestone.tileentity.GraveStoneTileEntity;
import dev.overgrown.apoli.compat.grave.GraveInscriptions;
import dev.overgrown.apoli.compat.grave.InscribedGrave;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GraveStoneTileEntity.class)
public abstract class GraveStoneTileEntityMixin implements InscribedGrave {

    @Unique
    private static final String APOLI$INSCRIPTION = "ApoliInscription";

    @Unique
    private List<Component> apoli$inscription = List.of();

    @Inject(method = "setDeath", at = @At("TAIL"))
    private void apoli$inscribe(Death death, CallbackInfo ci) {
        if (!(((BlockEntity) (Object) this).getLevel() instanceof ServerLevel level)) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(death.getPlayerUUID());
        this.apoli$inscription = player == null ? List.of() : GraveInscriptions.collect(player);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void apoli$saveInscription(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        GraveInscriptions.write(tag, APOLI$INSCRIPTION, this.apoli$inscription, registries);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void apoli$loadInscription(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.apoli$inscription = GraveInscriptions.read(tag, APOLI$INSCRIPTION, registries);
    }

    @Override
    public List<Component> apoli$inscription() {
        return this.apoli$inscription;
    }
}
