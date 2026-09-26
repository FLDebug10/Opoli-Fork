package dev.overgrown.apoli.power.builtin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.overgrown.apoli.condition.ItemCondition;
import dev.overgrown.apoli.condition.context.ItemCtx;
import dev.overgrown.apoli.data.ContainerType;
import dev.overgrown.apoli.data.Key;
import dev.overgrown.apoli.data.TextComponent;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerContainerImpl;
import dev.overgrown.apoli.power.PowerType;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class InventoryPower extends PowerType<InventoryPower.Config> {
    private static final Component DEFAULT_TITLE = Component.translatable("container.inventory");

    public record Config(
        Component title,
        ContainerType containerType,
        boolean dropOnDeath,
        Optional<ItemCondition> dropOnDeathFilter,
        boolean recoverable,
        Key key
    ) {}

    public record DeathDrop(ResourceLocation power, int slot, ItemStack stack) {
        public static final Codec<DeathDrop> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("power").forGetter(DeathDrop::power),
            Codec.INT.fieldOf("slot").forGetter(DeathDrop::slot),
            ItemStack.CODEC.fieldOf("stack").forGetter(DeathDrop::stack)
        ).apply(i, DeathDrop::new));
    }

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(i -> i.group(
            TextComponent.CODEC.optionalFieldOf("title", DEFAULT_TITLE).forGetter(Config::title),
            ContainerType.CODEC.optionalFieldOf("container_type", ContainerType.DROPPER).forGetter(Config::containerType),
            Codec.BOOL.optionalFieldOf("drop_on_death", false).forGetter(Config::dropOnDeath),
            dev.overgrown.apoli.codec.LoggedOptionalField.strict("drop_on_death_filter", ItemCondition.CODEC).forGetter(Config::dropOnDeathFilter),
            Codec.BOOL.optionalFieldOf("recoverable", true).forGetter(Config::recoverable),
            Key.CODEC.optionalFieldOf("key", Key.DEFAULT_PRIMARY).forGetter(Config::key)
        ).apply(i, Config::new));
    }

    private static final java.util.Map<java.util.UUID, java.util.Map<ResourceLocation, SimpleContainer>> OPEN_CONTAINERS =
        new java.util.HashMap<>();

    private static SimpleContainer liveContainer(java.util.UUID owner, ResourceLocation powerId) {
        java.util.Map<ResourceLocation, SimpleContainer> byPower = OPEN_CONTAINERS.get(owner);
        return byPower == null ? null : byPower.get(powerId);
    }

    private static void releaseContainer(java.util.UUID owner, ResourceLocation powerId) {
        java.util.Map<ResourceLocation, SimpleContainer> byPower = OPEN_CONTAINERS.get(owner);
        if (byPower == null) return;
        byPower.remove(powerId);
        if (byPower.isEmpty()) OPEN_CONTAINERS.remove(owner);
    }

    public static void onPlayerLeave(java.util.UUID owner) {
        OPEN_CONTAINERS.remove(owner);
    }

    public void open(ResourceLocation powerId, Config cfg, ServerPlayer player, PowerContainerImpl container) {
        java.util.UUID owner = player.getUUID();
        SimpleContainer inv = new SimpleContainer(cfg.containerType().slots()) {
            @Override
            public void stopOpen(net.minecraft.world.entity.player.Player closing) {
                releaseContainer(owner, powerId);
                super.stopOpen(closing);
            }
        };
        fill(inv, container.getAuxNbt(powerId));
        inv.addListener(changed -> container.setAuxNbt(powerId, save(inv)));
        OPEN_CONTAINERS.computeIfAbsent(owner, k -> new java.util.HashMap<>(2)).put(powerId, inv);
        player.openMenu(cfg.containerType().menuProvider(cfg.title(), inv));
    }

    @Override
    public void onRemoved(ResourceLocation powerId, Config cfg, PowerContainer holder, ResourceLocation source) {
        if (holder.allPowers().contains(powerId)) return;
        if (!(holder instanceof PowerContainerImpl impl)) return;

        CompoundTag stored = impl.getAuxNbt(powerId);
        if (stored != null && cfg.recoverable() && holder.owner() instanceof ServerPlayer player) {
            SimpleContainer inv = load(stored, cfg.containerType().slots());
            for (int slot = 0; slot < inv.getContainerSize(); slot++) {
                ItemStack stack = inv.getItem(slot);
                if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
            }
        }
        impl.removeAux(powerId);
    }

    public void dropOnDeath(ResourceLocation powerId, Config cfg, LivingEntity dead, PowerContainerImpl container, ServerLevel level) {
        List<DeathDrop> drops = new ArrayList<>();
        collectDeathDrops(powerId, cfg, dead, container, level, true, drops);
        for (int i = 0; i < drops.size(); i++) dead.spawnAtLocation(drops.get(i).stack());
    }

    public static List<DeathDrop> deathDrops(LivingEntity dead, boolean take) {
        if (!(dead.level() instanceof ServerLevel level)) return List.of();
        if (!(PowerContainer.of(dead) instanceof PowerContainerImpl impl) || impl.isEmpty()) return List.of();
        List<DeathDrop> drops = null;
        for (ResourceLocation powerId : impl.allPowers()) {
            if (impl.isSuppressed(powerId)) continue;
            Power loaded = ApoliPowers.get(powerId);
            if (loaded == null || !(loaded.config() instanceof Config cfg) || !cfg.dropOnDeath()) continue;
            if (drops == null) drops = new ArrayList<>(4);
            collectDeathDrops(powerId, cfg, dead, impl, level, take, drops);
        }
        return drops == null ? List.of() : drops;
    }

    private static void collectDeathDrops(ResourceLocation powerId, Config cfg, LivingEntity dead, PowerContainerImpl container,
                                          ServerLevel level, boolean take, List<DeathDrop> out) {
        CompoundTag stored = container.getAuxNbt(powerId);
        if (stored == null) return;
        SimpleContainer live = liveContainer(dead.getUUID(), powerId);
        SimpleContainer inv = live != null ? live : load(stored, cfg.containerType().slots());
        boolean changed = false;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) continue;
            if (cfg.dropOnDeathFilter().isPresent()
                && !cfg.dropOnDeathFilter().get().test(new ItemCtx(stack, level, dead))) continue;
            if (!take) {
                out.add(new DeathDrop(powerId, slot, stack.copy()));
                continue;
            }
            out.add(new DeathDrop(powerId, slot, stack));
            inv.setItem(slot, ItemStack.EMPTY);
            changed = true;
        }
        if (changed && live == null) container.setAuxNbt(powerId, save(inv));
    }

    public static ItemStack restore(LivingEntity holder, ResourceLocation powerId, int slot, ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!(holder.level() instanceof ServerLevel level)
            || !(PowerContainer.of(holder) instanceof PowerContainerImpl impl)
            || !impl.allPowers().contains(powerId)) return stack;
        Power loaded = ApoliPowers.get(powerId);
        if (loaded == null || !(loaded.config() instanceof Config cfg)) return stack;
        SimpleContainer live = liveContainer(holder.getUUID(), powerId);
        SimpleContainer inv = live != null ? live : load(impl.getAuxNbt(powerId), cfg.containerType().slots());
        if (slot < 0 || slot >= inv.getContainerSize() || !inv.getItem(slot).isEmpty()) return stack;
        inv.setItem(slot, stack);
        if (live == null) impl.setAuxNbt(powerId, save(inv));
        return ItemStack.EMPTY;
    }

    public static void syncAll(ServerPlayer player) {
        if (!(PowerContainer.of(player) instanceof PowerContainerImpl impl)) return;
        for (ResourceLocation powerId : impl.allPowers()) {
            CompoundTag stored = impl.getAuxNbt(powerId);
            if (stored == null) continue;
            Power loaded = ApoliPowers.get(powerId);
            if (loaded == null || !(loaded.config() instanceof Config)) continue;
            dev.overgrown.apoli.ApoliNetwork.sendPowerInventory(player,
                new dev.overgrown.apoli.network.payload.PowerInventoryS2C(powerId, stored));
        }
    }

    private static java.util.function.BiFunction<LivingEntity, ResourceLocation, CompoundTag> CLIENT_LOOKUP =
        (holder, powerId) -> null;

    public static void setClientLookup(java.util.function.BiFunction<LivingEntity, ResourceLocation, CompoundTag> lookup) {
        CLIENT_LOOKUP = lookup;
    }

    private static SimpleContainer clientContainer(LivingEntity holder, ResourceLocation powerId) {
        if (!holder.level().isClientSide()) return null;
        Power loaded = ApoliPowers.get(powerId);
        if (loaded == null || !(loaded.config() instanceof Config cfg)) return null;
        CompoundTag stored = CLIENT_LOOKUP.apply(holder, powerId);
        if (stored == null) return null;
        return load(stored, cfg.containerType().slots());
    }

    public static SimpleContainer openContainer(LivingEntity holder, ResourceLocation powerId) {
        if (holder.level().isClientSide()) return clientContainer(holder, powerId);
        if (!(PowerContainer.of(holder) instanceof PowerContainerImpl impl)) return null;
        Power loaded = ApoliPowers.get(powerId);
        if (loaded == null || !(loaded.config() instanceof Config cfg)) return null;
        SimpleContainer live = liveContainer(holder.getUUID(), powerId);
        if (live != null) return live;
        return load(impl.getAuxNbt(powerId), cfg.containerType().slots());
    }

    public static void saveContainer(LivingEntity holder, ResourceLocation powerId, SimpleContainer container) {
        if (holder.level().isClientSide()
            || !(PowerContainer.of(holder) instanceof PowerContainerImpl impl)) return;
        impl.setAuxNbt(powerId, save(container));
        if (container == liveContainer(holder.getUUID(), powerId)) container.setChanged();
    }

    private static SimpleContainer load(CompoundTag stored, int slots) {
        SimpleContainer inv = new SimpleContainer(slots);
        fill(inv, stored);
        return inv;
    }

    private static void fill(SimpleContainer inv, CompoundTag stored) {
        if (stored == null) return;
        int slots = inv.getContainerSize();
        NonNullList<ItemStack> items = NonNullList.withSize(slots, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(stored, items);
        for (int i = 0; i < slots; i++) inv.setItem(i, items.get(i));
    }

    private static CompoundTag save(SimpleContainer inv) {
        NonNullList<ItemStack> items = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < inv.getContainerSize(); i++) items.set(i, inv.getItem(i));
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items);
        return tag;
    }
}
