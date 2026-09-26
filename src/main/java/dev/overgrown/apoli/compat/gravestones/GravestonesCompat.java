package dev.overgrown.apoli.compat.gravestones;

import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.compat.grave.GraveInscriptions;
import dev.overgrown.apoli.power.builtin.KeepInventoryPower;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.pneumono.gravestones.api.GravestonesApi;
import net.pneumono.gravestones.api.event.GravestoneContentsEvents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class GravestonesCompat {
    static final String LINES = "lines";

    private static final String INSCRIPTION = Apoli.id("inscription").toString();
    private static final String VANILLA_SLOTS = "minecraft";

    private GravestonesCompat() {}

    public static void init() {
        GravestonesApi.registerDataType(Apoli.id("power_inventories"), new PowerInventoriesDataType());
        GravestonesApi.registerDataType(Apoli.id("inscription"), new InscriptionDataType());
        GravestoneContentsEvents.registerSkipItem(Apoli.id("keep_inventory"), GravestonesCompat::keptByPower);
    }

    public static List<Component> inscription(CompoundTag contents) {
        if (!contents.contains(INSCRIPTION)) return List.of();
        return GraveInscriptions.read(contents.getCompound(INSCRIPTION), LINES);
    }

    private static boolean keptByPower(Player player, ItemStack stack, @Nullable ResourceLocation slot) {
        if (slot == null || !VANILLA_SLOTS.equals(slot.getNamespace()) || !KeepInventoryPower.isHeldBy(player)) return false;
        try {
            return KeepInventoryPower.keepsInventorySlot(player, Integer.parseInt(slot.getPath()), stack);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
