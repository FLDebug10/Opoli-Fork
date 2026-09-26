package dev.overgrown.apoli.compat.gravestones;

import com.mojang.serialization.DynamicOps;
import dev.overgrown.apoli.compat.grave.GraveInscriptions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.pneumono.gravestones.api.GravestoneDataType;

final class InscriptionDataType extends GravestoneDataType {
    @Override
    public void writeData(CompoundTag tag, DynamicOps<Tag> ops, Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            GraveInscriptions.write(tag, GravestonesCompat.LINES, GraveInscriptions.collect(serverPlayer), ops);
        }
    }

    @Override
    public void onBreak(CompoundTag tag, DynamicOps<Tag> ops, Level level, BlockPos pos, int decay) {}

    @Override
    public void onCollect(CompoundTag tag, DynamicOps<Tag> ops, Level level, BlockPos pos, Player player, int decay) {}
}
