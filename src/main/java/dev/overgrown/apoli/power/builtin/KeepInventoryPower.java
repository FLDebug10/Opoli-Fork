package dev.overgrown.apoli.power.builtin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.Apoli;
import dev.overgrown.apoli.condition.ItemCondition;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.power.ApoliIds;
import dev.overgrown.apoli.power.PowerLookup;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class KeepInventoryPower extends PowerType<KeepInventoryPower.Config> {
    public static final ResourceLocation CANONICAL = Apoli.id("keep_inventory");

    private static final int[] DEFAULT_SLOTS = defaultSlots();

    public record Config(
        Optional<ItemCondition> itemCondition,
        Optional<List<Integer>> slots
    ) {}

    public record Kept(int slot, ItemStack stack) {}

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            dev.overgrown.apoli.codec.LoggedOptionalField.strict("item_condition", ItemCondition.CODEC).forGetter(Config::itemCondition),
            Codec.list(Codec.INT).optionalFieldOf("slots").forGetter(Config::slots)
        ).apply(i, Config::new));
    }

    private static int[] defaultSlots() {
        int[] slots = new int[40];
        for (int i = 0; i < 36; i++) slots[i] = i;
        for (int i = 0; i < 4; i++) slots[36 + i] = 100 + i;
        return slots;
    }

    public static List<Kept> takeKept(Player player) {
        List<Kept> kept = new ArrayList<>();
        PowerLookup.forEach(player, ApoliIds.KEEP_INVENTORY, Config.class, cfg -> {
            List<Integer> configured = cfg.slots.orElse(null);
            int count = configured == null ? DEFAULT_SLOTS.length : configured.size();
            for (int i = 0; i < count; i++) {
                int slot = configured == null ? DEFAULT_SLOTS[i] : configured.get(i);
                SlotAccess access = player.getSlot(slot);
                ItemStack stack = access.get();
                if (stack.isEmpty() || !accepts(cfg, stack, player)) continue;
                kept.add(new Kept(slot, stack.copy()));
                access.set(ItemStack.EMPTY);
            }
        });
        return kept;
    }

    public static boolean keepsInventorySlot(Player player, int inventoryIndex, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int slot = slotId(inventoryIndex);
        if (slot < 0) return false;
        return PowerLookup.anyActive(player, ApoliIds.KEEP_INVENTORY, Config.class,
            cfg -> covers(cfg, slot) && accepts(cfg, stack, player));
    }

    private static int slotId(int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex < 36) return inventoryIndex;
        if (inventoryIndex >= 36 && inventoryIndex < 40) return 100 + inventoryIndex - 36;
        return inventoryIndex == 40 ? 99 : -1;
    }

    private static boolean covers(Config cfg, int slot) {
        if (cfg.slots.isPresent()) return cfg.slots.get().contains(slot);
        for (int i = 0; i < DEFAULT_SLOTS.length; i++) {
            if (DEFAULT_SLOTS[i] == slot) return true;
        }
        return false;
    }

    private static boolean accepts(Config cfg, ItemStack stack, Player player) {
        return cfg.itemCondition.isEmpty() || cfg.itemCondition.get().test(new ItemCtx(stack, player.level(), player));
    }

    public static void putBack(Player player, List<Kept> kept) {
        for (int i = 0; i < kept.size(); i++) {
            Kept entry = kept.get(i);
            player.getSlot(entry.slot()).set(entry.stack());
        }
    }

    public static boolean isHeldBy(Player player) {
        return PowerLookup.hasActive(player, ApoliIds.KEEP_INVENTORY);
    }
}
