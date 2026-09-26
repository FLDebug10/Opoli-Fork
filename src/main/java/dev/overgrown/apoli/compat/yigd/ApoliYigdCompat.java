package dev.overgrown.apoli.compat.yigd;

import com.b1n_ry.yigd.compat.CompatComponent;
import com.b1n_ry.yigd.compat.InvModCompat;
import com.b1n_ry.yigd.components.GraveComponent;
import com.b1n_ry.yigd.components.InventoryComponent;
import com.b1n_ry.yigd.data.DeathContext;
import com.b1n_ry.yigd.data.GraveItem;
import com.b1n_ry.yigd.events.DropRuleEvent;
import com.b1n_ry.yigd.util.DropRule;
import dev.overgrown.apoli.compat.grave.GraveInscriptions;
import dev.overgrown.apoli.compat.yigd.mixin.InventoryComponentAccessor;
import dev.overgrown.apoli.power.builtin.InventoryPower;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ApoliYigdCompat implements InvModCompat<ApoliYigdCompat.Contents> {
    public static final String NAME = "apoli";

    private static final String INVENTORIES = "inventories";
    private static final String INSCRIPTION = "inscription";
    private static final String DROP_RULE = "dropRule";

    public record Contents(Map<ResourceLocation, NonNullList<GraveItem>> inventories, List<Component> inscription) {}

    @Override
    public String getModName() {
        return NAME;
    }

    @Override
    public void clear(ServerPlayer player) {
        InventoryPower.deathDrops(player, true);
    }

    @Override
    public CompatComponent<Contents> readNbt(CompoundTag nbt) {
        Map<ResourceLocation, NonNullList<GraveItem>> inventories = new LinkedHashMap<>();
        CompoundTag stored = nbt.getCompound(INVENTORIES);
        for (String key : stored.getAllKeys()) {
            ResourceLocation power = ResourceLocation.tryParse(key);
            if (power == null) continue;
            NonNullList<GraveItem> items = NonNullList.create();
            items.addAll(InventoryComponent.listFromNbt(stored.getCompound(key), itemNbt -> new GraveItem(
                ItemStack.of(itemNbt), dropRule(itemNbt.getString(DROP_RULE))),
                InventoryComponent.EMPTY_GRAVE_ITEM));
            inventories.put(power, items);
        }
        return new PowerGraveComponent(new Contents(inventories, GraveInscriptions.read(nbt, INSCRIPTION)));
    }

    @Override
    public CompatComponent<Contents> getNewComponent(ServerPlayer player) {
        return new PowerGraveComponent(player);
    }

    public static List<Component> inscriptionOf(@Nullable GraveComponent grave) {
        if (grave == null || !(grave.getInventoryComponent() instanceof InventoryComponentAccessor accessor)) return List.of();
        return accessor.apoli$modInventories().get(NAME) instanceof PowerGraveComponent component
            ? component.inscription()
            : List.of();
    }

    private static DropRule dropRule(String name) {
        try {
            return name.isEmpty() ? DropRule.PUT_IN_GRAVE : DropRule.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DropRule.PUT_IN_GRAVE;
        }
    }

    private static void place(NonNullList<GraveItem> items, int slot, GraveItem item) {
        while (items.size() <= slot) items.add(InventoryComponent.EMPTY_GRAVE_ITEM);
        items.set(slot, item);
    }

    private static final class PowerGraveComponent extends CompatComponent<Contents> {
        private PowerGraveComponent(ServerPlayer player) {
            super(player);
        }

        private PowerGraveComponent(Contents contents) {
            super(contents);
        }

        private List<Component> inscription() {
            return this.inventory.inscription();
        }

        @Override
        public Contents getInventory(ServerPlayer player) {
            Map<ResourceLocation, NonNullList<GraveItem>> inventories = new LinkedHashMap<>();
            List<InventoryPower.DeathDrop> drops = InventoryPower.deathDrops(player, false);
            for (int i = 0; i < drops.size(); i++) {
                InventoryPower.DeathDrop drop = drops.get(i);
                place(inventories.computeIfAbsent(drop.power(), id -> NonNullList.create()), drop.slot(),
                    new GraveItem(drop.stack(), DropRule.PUT_IN_GRAVE));
            }
            return new Contents(inventories, GraveInscriptions.collect(player));
        }

        @Override
        public NonNullList<GraveItem> merge(CompatComponent<?> merging, ServerPlayer merger) {
            NonNullList<GraveItem> extra = NonNullList.create();
            if (!(merging instanceof PowerGraveComponent other)) return extra;
            for (Map.Entry<ResourceLocation, NonNullList<GraveItem>> entry : other.inventory.inventories().entrySet()) {
                NonNullList<GraveItem> current = this.inventory.inventories().computeIfAbsent(entry.getKey(), id -> NonNullList.create());
                NonNullList<GraveItem> incoming = entry.getValue();
                for (int i = 0; i < incoming.size(); i++) {
                    if (incoming.get(i).stack.isEmpty()) continue;
                    GraveItem item = incoming.get(i).copy();
                    if (i < current.size() && !current.get(i).stack.isEmpty()) {
                        extra.add(item);
                    } else {
                        place(current, i, item);
                    }
                }
            }
            return extra;
        }

        @Override
        public NonNullList<ItemStack> storeToPlayer(ServerPlayer player) {
            NonNullList<ItemStack> extra = NonNullList.create();
            for (Map.Entry<ResourceLocation, NonNullList<GraveItem>> entry : this.inventory.inventories().entrySet()) {
                NonNullList<GraveItem> items = entry.getValue();
                for (int i = 0; i < items.size(); i++) {
                    ItemStack stack = items.get(i).stack;
                    if (stack.isEmpty()) continue;
                    ItemStack leftover = InventoryPower.restore(player, entry.getKey(), i, stack.copy());
                    if (!leftover.isEmpty()) extra.add(leftover);
                }
            }
            return extra;
        }

        @Override
        public void handleDropRules(DeathContext context) {
            for (NonNullList<GraveItem> items : this.inventory.inventories().values()) {
                for (GraveItem item : items) {
                    if (item.stack.isEmpty()) continue;
                    item.dropRule = DropRuleEvent.EVENT.invoker().getDropRule(item.stack, -1, context, true);
                }
            }
        }

        @Override
        public NonNullList<GraveItem> getAsGraveItemList() {
            NonNullList<GraveItem> all = NonNullList.create();
            for (NonNullList<GraveItem> items : this.inventory.inventories().values()) all.addAll(items);
            return all;
        }

        @Override
        public CompatComponent<Contents> filterInv(Predicate<DropRule> predicate) {
            Map<ResourceLocation, NonNullList<GraveItem>> filtered = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, NonNullList<GraveItem>> entry : this.inventory.inventories().entrySet()) {
                NonNullList<GraveItem> items = NonNullList.create();
                for (GraveItem item : entry.getValue()) {
                    items.add(predicate.test(item.dropRule) ? item : InventoryComponent.EMPTY_GRAVE_ITEM);
                }
                filtered.put(entry.getKey(), items);
            }
            return new PowerGraveComponent(new Contents(filtered, this.inventory.inscription()));
        }

        @Override
        public boolean removeItem(Predicate<ItemStack> predicate, int itemCount) {
            for (NonNullList<GraveItem> items : this.inventory.inventories().values()) {
                for (GraveItem item : items) {
                    if (item.stack.isEmpty() || !predicate.test(item.stack)) continue;
                    item.stack.shrink(itemCount);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void clear() {
            for (NonNullList<GraveItem> items : this.inventory.inventories().values()) {
                for (int i = 0; i < items.size(); i++) items.set(i, InventoryComponent.EMPTY_GRAVE_ITEM);
            }
        }

        @Override
        public CompoundTag writeNbt() {
            CompoundTag nbt = new CompoundTag();
            CompoundTag inventories = new CompoundTag();
            for (Map.Entry<ResourceLocation, NonNullList<GraveItem>> entry : this.inventory.inventories().entrySet()) {
                inventories.put(entry.getKey().toString(), InventoryComponent.listToNbt(entry.getValue(), item -> {
                    CompoundTag itemNbt = item.stack.save(new CompoundTag());
                    itemNbt.putString(DROP_RULE, item.dropRule.name());
                    return itemNbt;
                }, item -> item.stack.isEmpty()));
            }
            nbt.put(INVENTORIES, inventories);
            GraveInscriptions.write(nbt, INSCRIPTION, this.inventory.inscription());
            return nbt;
        }
    }
}
