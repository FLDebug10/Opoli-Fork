package dev.overgrown.apoli.compat.gravestones;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.power.builtin.InventoryPower;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.pneumono.gravestones.api.GravestoneDataType;

import java.util.List;
import java.util.Optional;

final class PowerInventoriesDataType extends GravestoneDataType {
    private static final String STACKS = "stacks";
    private static final Codec<List<InventoryPower.DeathDrop>> CODEC = InventoryPower.DeathDrop.CODEC.listOf();

    @Override
    public void writeData(CompoundTag tag, DynamicOps<Tag> ops, Player player) {
        List<InventoryPower.DeathDrop> drops = InventoryPower.deathDrops(player, true);
        if (drops.isEmpty()) return;
        Optional<Tag> encoded = CODEC.encodeStart(ops, drops).resultOrPartial(err ->
            Apoli.LOGGER.error("[Apoli] Could not store power inventories in a gravestone: {}", err));
        if (encoded.isPresent()) {
            tag.put(STACKS, encoded.get());
            return;
        }
        for (InventoryPower.DeathDrop drop : drops) {
            ItemStack leftover = InventoryPower.restore(player, drop.power(), drop.slot(), drop.stack());
            if (!leftover.isEmpty()) player.drop(leftover, true, false);
        }
    }

    @Override
    public void onBreak(CompoundTag tag, DynamicOps<Tag> ops, Level level, BlockPos pos, int decay) {
        for (InventoryPower.DeathDrop drop : read(tag, ops)) dropStack(level, pos, drop.stack());
    }

    @Override
    public void onCollect(CompoundTag tag, DynamicOps<Tag> ops, Level level, BlockPos pos, Player player, int decay) {
        for (InventoryPower.DeathDrop drop : read(tag, ops)) {
            ItemStack leftover = InventoryPower.restore(player, drop.power(), drop.slot(), drop.stack());
            if (!leftover.isEmpty()) dropStack(player, leftover);
        }
    }

    private static List<InventoryPower.DeathDrop> read(CompoundTag tag, DynamicOps<Tag> ops) {
        Tag encoded = tag.get(STACKS);
        if (encoded == null) return List.of();
        return CODEC.parse(ops, encoded).resultOrPartial(err ->
            Apoli.LOGGER.error("[Apoli] Could not read power inventories from a gravestone: {}", err)).orElse(List.of());
    }
}
