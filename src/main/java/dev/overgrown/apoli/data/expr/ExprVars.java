package dev.overgrown.apoli.data.expr;

import dev.overgrown.apoli.compat.voicechat.VoiceState;
import dev.overgrown.apoli.data.EnchantmentLevels;
import dev.overgrown.apoli.data.ItemSlot;
import dev.overgrown.apoli.data.NbtPathValue;
import dev.overgrown.apoli.data.NbtSources;
import dev.overgrown.apoli.data.SlotStacks;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerResources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

public final class ExprVars {
    private ExprVars() {}

    public record ResolvedVar(ExprVar accessor, boolean needsContainer, boolean needsPeer) {
        public ResolvedVar(ExprVar accessor, boolean needsContainer) {
            this(accessor, needsContainer, false);
        }
    }

    private static final String ACTOR_PREFIX = "actor_";
    private static final String TARGET_PREFIX = "target_";

    private static final Map<String, ResolvedVar> VARS = new HashMap<>();

    public static void load() {
    }

    public static void register(String name, ExprVar accessor) {
        VARS.put(name, new ResolvedVar(accessor, false));
    }

    public static void registerContext(String name) {
        int slot = ExprContext.slot(name);
        register(name, (e, c, l, v) -> ExprContext.get(slot));
    }

    public static @Nullable ResolvedVar resolve(String name) {
        ResolvedVar known = VARS.get(name);
        if (known != null) return known;
        ResolvedVar prefixed = resolvePrefixed(name);
        if (prefixed != null) return prefixed;
        if (name.indexOf(':') < 0) return null;
        ResourceLocation literal = ResourceLocation.tryParse(name);
        ResolvedVar bound = resolveBound(name, "_max", true, literal);
        if (bound != null) return bound;
        bound = resolveBound(name, "_min", false, literal);
        if (bound != null) return bound;
        bound = resolveSize(name, literal);
        if (bound != null) return bound;
        if (literal == null) return null;
        return new ResolvedVar((e, c, l, v) -> readResource(c, literal), true);
    }

    public static ResolvedVar resolveIndexed(ResourceLocation id, ExprNode index) {
        return new ResolvedVar((e, c, l, v) -> {
            int slot = (int) Math.round(index.eval(e, c, l, v));
            return readResourceAt(c, id, slot);
        }, true);
    }

    private static @Nullable ResolvedVar resolvePrefixed(String name) {
        boolean target = name.startsWith(TARGET_PREFIX);
        if (!target && !name.startsWith(ACTOR_PREFIX)) return null;
        String base = name.substring(target ? TARGET_PREFIX.length() : ACTOR_PREFIX.length());
        if (base.indexOf(':') >= 0) return null;
        ResolvedVar delegate = VARS.get(base);
        if (delegate == null) return null;
        ExprVar accessor = delegate.accessor();
        int slot = target ? ExprPeer.TARGET : ExprPeer.ACTOR;
        return new ResolvedVar((e, c, l, v) -> {
            Entity bound = ExprPeer.frame()[slot];
            return accessor.get(bound != null ? bound : e, c, l, v);
        }, delegate.needsContainer(), true);
    }

    private static @Nullable ResolvedVar resolveBound(String name, String suffix, boolean max,
                                                      @Nullable ResourceLocation literal) {
        if (!name.endsWith(suffix)) return null;
        ResourceLocation base = ResourceLocation.tryParse(name.substring(0, name.length() - suffix.length()));
        if (base == null) return null;
        return new ResolvedVar((e, c, l, v) -> readBound(c, base, literal, max), true);
    }

    private static @Nullable ResolvedVar resolveSize(String name, @Nullable ResourceLocation literal) {
        if (!name.endsWith("_size")) return null;
        ResourceLocation base = ResourceLocation.tryParse(name.substring(0, name.length() - "_size".length()));
        if (base == null) return null;
        return new ResolvedVar((e, c, l, v) -> {
            int size = PowerResources.size(c, base);
            if (size > 0) return size;
            return literal == null ? 0 : readResource(c, literal);
        }, true);
    }

    private static final ThreadLocal<int[]> BOUND_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private static double readBound(@Nullable PowerContainer container, ResourceLocation base,
                                    @Nullable ResourceLocation literal, boolean max) {
        int[] depth = BOUND_DEPTH.get();
        if (depth[0] >= 8) return 0;
        depth[0]++;
        try {
            OptionalInt bound = PowerResources.bound(container, base, max);
            if (bound.isPresent()) return bound.getAsInt();
        } finally {
            depth[0]--;
        }
        return literal == null ? 0 : readResource(container, literal);
    }

    public static double readResource(@Nullable PowerContainer container, ResourceLocation id) {
        if (container == null) return 0;
        OptionalInt value = PowerResources.read(container, id);
        return value.isPresent() ? value.getAsInt() : container.getAuxIntOr(id, 0);
    }

    public static double readResourceAt(@Nullable PowerContainer container, ResourceLocation id, int slot) {
        if (container == null) return 0;
        OptionalInt value = PowerResources.readAt(container, id, slot);
        return value.isPresent() ? value.getAsInt() : 0;
    }

    private static void registerIdFunctions() {
        ExprParser.registerIdFunction("resource", (id, args, name, at) -> {
            if (args.isEmpty()) return new ResolvedVar((e, c, l, v) -> readResource(c, id), true);
            ExprNode index = args.get(0);
            return new ResolvedVar((e, c, l, v) ->
                readResourceAt(c, id, (int) Math.round(index.eval(e, c, l, v))), true);
        });
        ExprParser.registerIdFunction("target_resource", (id, args, name, at) -> {
            ExprNode index = args.isEmpty() ? null : args.get(0);
            return new ResolvedVar((e, c, l, v) -> {
                PowerContainer peer = peerContainer(e, c);
                if (index == null) return readResource(peer, id);
                return readResourceAt(peer, id, (int) Math.round(index.eval(e, c, l, v)));
            }, true, true);
        });
        ExprParser.registerIdFunction("actor_resource", (id, args, name, at) -> {
            ExprNode index = args.isEmpty() ? null : args.get(0);
            return new ResolvedVar((e, c, l, v) -> {
                PowerContainer peer = actorContainer(e, c);
                if (index == null) return readResource(peer, id);
                return readResourceAt(peer, id, (int) Math.round(index.eval(e, c, l, v)));
            }, true, true);
        });
        ExprParser.registerIdFunction("actor_has_power", (id, args, name, at) ->
            new ResolvedVar((e, c, l, v) -> {
                PowerContainer peer = actorContainer(e, c);
                return peer != null && peer.hasPower(id) ? 1 : 0;
            }, true, true));
        ExprParser.registerIdFunction("has_power", (id, args, name, at) ->
            new ResolvedVar((e, c, l, v) -> c != null && c.hasPower(id) ? 1 : 0, true));
        ExprParser.registerIdFunction("target_has_power", (id, args, name, at) ->
            new ResolvedVar((e, c, l, v) -> {
                PowerContainer peer = peerContainer(e, c);
                return peer != null && peer.hasPower(id) ? 1 : 0;
            }, true, true));
        ExprParser.registerIdFunction("has_resource", (id, args, name, at) ->
            new ResolvedVar((e, c, l, v) -> PowerResources.read(c, id).isPresent() ? 1 : 0, true));
        ExprParser.registerIdFunction("resource_size", (id, args, name, at) ->
            new ResolvedVar((e, c, l, v) -> PowerResources.size(c, id), true));
        ExprParser.registerIdFunction("resource_contains", (id, args, name, at) -> {
            if (args.isEmpty()) {
                throw new ExprParseException("resource_contains(id, value) expects a value to look for", at);
            }
            ExprNode wanted = args.get(0);
            return new ResolvedVar((e, c, l, v) ->
                PowerResources.indexOf(c, id, (int) Math.round(wanted.eval(e, c, l, v))) >= 0 ? 1 : 0, true);
        });
        ExprParser.registerIdFunction("resource_index_of", (id, args, name, at) -> {
            if (args.isEmpty()) {
                throw new ExprParseException("resource_index_of(id, value) expects a value to look for", at);
            }
            ExprNode wanted = args.get(0);
            return new ResolvedVar((e, c, l, v) ->
                PowerResources.indexOf(c, id, (int) Math.round(wanted.eval(e, c, l, v))), true);
        });
    }

    private static void registerBracketFunctions() {
        ExprParser.registerBracketFunction("enchantment", ExprVars::enchantment);
        ExprParser.registerBracketFunction("target_enchantment", (args, name, at) ->
            onPeer(enchantment(args, name, at), ExprPeer.TARGET));
        ExprParser.registerBracketFunction("actor_enchantment", (args, name, at) ->
            onPeer(enchantment(args, name, at), ExprPeer.ACTOR));
        ExprParser.registerBracketFunction("nbt", ExprVars::nbt);
        ExprParser.registerBracketFunction("data", ExprVars::nbt);
    }

    private static ResolvedVar onPeer(ResolvedVar delegate, int slot) {
        ExprVar accessor = delegate.accessor();
        return new ResolvedVar((e, c, l, v) -> {
            Entity bound = ExprPeer.frame()[slot];
            return accessor.get(bound != null ? bound : e, c, l, v);
        }, delegate.needsContainer(), true);
    }

    private static ResolvedVar enchantment(java.util.List<String> args, String name, int at) throws ExprParseException {
        if (args.isEmpty() || args.get(0).isEmpty()) {
            throw new ExprParseException("enchantment[id, slot, calculation] expects an enchantment id", at);
        }
        ResourceLocation id = ResourceLocation.tryParse(args.get(0));
        if (id == null) throw new ExprParseException("'" + args.get(0) + "' is not a valid enchantment id", at);

        int scope = SlotStacks.SCOPE_EQUIPMENT;
        ItemSlot slot = null;
        boolean max = false;
        for (int i = 1; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals("sum")) continue;
            if (arg.equals("max")) {
                max = true;
                continue;
            }
            int named = SlotStacks.scopeOf(arg);
            if (named >= 0) {
                scope = named;
                slot = null;
                continue;
            }
            ItemSlot parsed = ItemSlot.parse(arg).result().orElse(null);
            if (parsed == null) {
                throw new ExprParseException("'" + arg + "' is not an item slot or a calculation (sum, max)", at);
            }
            slot = parsed;
            scope = SlotStacks.SCOPE_SLOT;
        }

        EnchantmentLevels levels = EnchantmentLevels.of(id);
        int finalScope = scope;
        ItemSlot finalSlot = slot;
        boolean useMax = max;
        return new ResolvedVar((e, c, l, v) -> {
            Level level = levelOf(e, l);
            if (level == null || e == null) return 0;
            if (finalScope == SlotStacks.SCOPE_SLOT) {
                return levels.levelIn(level, SlotStacks.get(e, finalSlot));
            }
            if (!(e instanceof LivingEntity living)) return 0;
            net.minecraft.world.entity.EquipmentSlot[] slots = SlotStacks.slotsOf(finalScope);
            int total = 0;
            int best = 0;
            for (int i = 0; i < slots.length; i++) {
                int value = levels.levelIn(level, living.getItemBySlot(slots[i]));
                total += value;
                if (value > best) best = value;
            }
            return useMax ? best : total;
        }, false);
    }

    private static final int NBT_SELF = 0;
    private static final int NBT_TARGET = 1;
    private static final int NBT_ACTOR = 2;
    private static final int NBT_SLOT = 3;
    private static final int NBT_BLOCK = 4;
    private static final int NBT_STORAGE = 5;

    private static ResolvedVar nbt(java.util.List<String> args, String name, int at) throws ExprParseException {
        if (args.isEmpty() || args.get(0).isEmpty()) {
            throw new ExprParseException(name + "[source, path] expects an NBT path", at);
        }
        int source = NBT_SELF;
        ItemSlot slot = null;
        ResourceLocation storage = null;
        ExprNode blockX = null;
        ExprNode blockY = null;
        ExprNode blockZ = null;
        boolean needsPeer = false;
        boolean needsContainer = false;
        int index = 0;

        if (args.size() > 1) {
            String head = args.get(0);
            switch (head) {
                case "self", "entity", "holder" -> index = 1;
                case "target" -> {
                    source = NBT_TARGET;
                    needsPeer = true;
                    index = 1;
                }
                case "actor" -> {
                    source = NBT_ACTOR;
                    needsPeer = true;
                    index = 1;
                }
                case "block" -> {
                    if (args.size() < 5) {
                        throw new ExprParseException(name + "[block, x, y, z, path] expects three coordinates", at);
                    }
                    source = NBT_BLOCK;
                    ExprParser.Nested x = ExprParser.compileNested(args.get(1), name, at);
                    ExprParser.Nested y = ExprParser.compileNested(args.get(2), name, at);
                    ExprParser.Nested z = ExprParser.compileNested(args.get(3), name, at);
                    blockX = x.node();
                    blockY = y.node();
                    blockZ = z.node();
                    needsContainer = x.needsContainer() || y.needsContainer() || z.needsContainer();
                    needsPeer = x.needsPeer() || y.needsPeer() || z.needsPeer();
                    index = 4;
                }
                case "storage" -> {
                    if (args.size() < 3) {
                        throw new ExprParseException(name + "[storage, id, path] expects a storage id", at);
                    }
                    source = NBT_STORAGE;
                    storage = ResourceLocation.tryParse(args.get(1));
                    if (storage == null) {
                        throw new ExprParseException("'" + args.get(1) + "' is not a valid storage id", at);
                    }
                    index = 2;
                }
                default -> {
                    ItemSlot parsed = ItemSlot.parse(head).result().orElse(null);
                    if (parsed != null) {
                        slot = parsed;
                        source = NBT_SLOT;
                        index = 1;
                    } else if (args.size() != 2 || NbtPathValue.aggregatorOf(args.get(1)) < 0) {
                        throw new ExprParseException("'" + head + "' is not an NBT source "
                            + "(self, target, actor, block, storage or an item slot)", at);
                    }
                }
            }
        }

        if (index >= args.size()) {
            throw new ExprParseException(name + "[source, path] expects an NBT path", at);
        }
        if (args.size() - index > 2) {
            throw new ExprParseException(name + "[...] takes a source, a path and an aggregator, "
                + "but got " + args.size() + " arguments", at);
        }
        String pathSource = args.get(index);
        int aggregator = NbtPathValue.FIRST;
        if (index + 1 < args.size()) {
            aggregator = NbtPathValue.aggregatorOf(args.get(index + 1));
            if (aggregator < 0) {
                throw new ExprParseException("'" + args.get(index + 1)
                    + "' is not an NBT aggregator (first, sum, max, min, count)", at);
            }
        }
        net.minecraft.commands.arguments.NbtPathArgument.NbtPath path = NbtPathValue.parse(pathSource);
        if (path == null) throw new ExprParseException("'" + pathSource + "' is not a valid NBT path", at);

        int finalSource = source;
        ItemSlot finalSlot = slot;
        ResourceLocation finalStorage = storage;
        ExprNode x = blockX;
        ExprNode y = blockY;
        ExprNode z = blockZ;
        int finalAggregator = aggregator;
        return new ResolvedVar((entity, container, level, value) -> {
            Level world = levelOf(entity, level);
            net.minecraft.nbt.Tag root = switch (finalSource) {
                case NBT_TARGET -> NbtSources.ofEntity(peerOr(ExprPeer.TARGET, entity));
                case NBT_ACTOR -> NbtSources.ofEntity(peerOr(ExprPeer.ACTOR, entity));
                case NBT_SLOT -> NbtSources.ofStack(SlotStacks.get(entity, finalSlot), world);
                case NBT_BLOCK -> NbtSources.ofBlock(world, net.minecraft.core.BlockPos.containing(
                    x.eval(entity, container, level, value),
                    y.eval(entity, container, level, value),
                    z.eval(entity, container, level, value)));
                case NBT_STORAGE -> NbtSources.ofStorage(world, finalStorage);
                default -> NbtSources.ofEntity(entity);
            };
            return NbtPathValue.read(root, path, finalAggregator);
        }, needsContainer, needsPeer);
    }

    private static @Nullable Entity peerOr(int slot, @Nullable Entity fallback) {
        Entity bound = ExprPeer.frame()[slot];
        return bound != null ? bound : fallback;
    }

    private static @Nullable PowerContainer actorContainer(@Nullable Entity primary, @Nullable PowerContainer own) {
        Entity actor = ExprPeer.actor();
        if (actor == null || actor == primary) return own;
        return PowerContainer.of(actor);
    }

    private static @Nullable PowerContainer peerContainer(@Nullable Entity primary, @Nullable PowerContainer own) {
        Entity peer = ExprPeer.target();
        if (peer == null || peer == primary) return own;
        return PowerContainer.of(peer);
    }

    private static Level levelOf(@Nullable Entity entity, @Nullable Level level) {
        if (level != null) return level;
        return entity != null ? entity.level() : null;
    }

    static {
        register("value", (e, c, l, v) -> v);

        registerContext("damage");
        registerContext("distance");
        registerContext("hit_x");
        registerContext("hit_y");
        registerContext("hit_z");
        registerContext("count");
        registerContext("index");
        registerContext("mouse_x");
        registerContext("mouse_y");

        register("health", (e, c, l, v) -> e instanceof LivingEntity le ? le.getHealth() : 0);
        register("max_health", (e, c, l, v) -> e instanceof LivingEntity le ? le.getMaxHealth() : 0);
        register("absorption", (e, c, l, v) -> e instanceof LivingEntity le ? le.getAbsorptionAmount() : 0);
        register("armor", (e, c, l, v) -> e instanceof LivingEntity le ? le.getArmorValue() : 0);
        register("air", (e, c, l, v) -> e != null ? e.getAirSupply() : 0);
        register("max_air", (e, c, l, v) -> e != null ? e.getMaxAirSupply() : 0);
        register("fall_distance", (e, c, l, v) -> e != null ? e.fallDistance : 0);
        register("attack_charge", (e, c, l, v) -> (e instanceof ServerPlayer) ? ((ServerPlayer) e).getAttackStrengthScale(0.0f) : 0.0f);

        register("x", (e, c, l, v) -> e != null ? e.getX() : 0);
        register("y", (e, c, l, v) -> e != null ? e.getY() : 0);
        register("z", (e, c, l, v) -> e != null ? e.getZ() : 0);
        register("yaw", (e, c, l, v) -> e != null ? e.getYRot() : 0);
        register("pitch", (e, c, l, v) -> e != null ? e.getXRot() : 0);
        register("velocity_x", (e, c, l, v) -> e != null ? e.getDeltaMovement().x : 0);
        register("velocity_y", (e, c, l, v) -> e != null ? e.getDeltaMovement().y : 0);
        register("velocity_z", (e, c, l, v) -> e != null ? e.getDeltaMovement().z : 0);

        register("food", (e, c, l, v) -> e instanceof Player p ? p.getFoodData().getFoodLevel() : 0);
        register("saturation", (e, c, l, v) -> e instanceof Player p ? p.getFoodData().getSaturationLevel() : 0);
        register("xp_level", (e, c, l, v) -> e instanceof Player p ? p.experienceLevel : 0);
        register("xp_progress", (e, c, l, v) -> e instanceof Player p ? p.experienceProgress : 0);

        register("power_count", (e, c, l, v) -> c == null ? 0 : c.allPowers().size());

        register("voice_loudness", (e, c, l, v) ->
            e == null ? 0 : VoiceState.loudness(e.getUUID()));
        register("voice_loudness_normalized", (e, c, l, v) ->
            e == null ? 0 : VoiceState.loudness(e.getUUID()) / 100.0);
        register("voice_speaking", (e, c, l, v) ->
            e != null && VoiceState.isSpeaking(e.getUUID()) ? 1 : 0);
        register("voice_whispering", (e, c, l, v) ->
            e != null && VoiceState.isWhispering(e.getUUID()) ? 1 : 0);
        register("voice_disabled", (e, c, l, v) ->
            e != null && (VoiceState.isDisabled(e.getUUID()) || VoiceState.isDisconnected(e.getUUID())) ? 1 : 0);

        registerIdFunctions();
        registerBracketFunctions();

        register("world_time", (e, c, l, v) -> {
            Level level = levelOf(e, l);
            return level != null ? level.getGameTime() : 0;
        });
        register("day_time", (e, c, l, v) -> {
            Level level = levelOf(e, l);
            return level != null ? level.getDayTime() : 0;
        });
        register("difficulty", (e, c, l, v) -> {
            Level level = levelOf(e, l);
            return level != null ? level.getDifficulty().getId() : 0;
        });
        register("moon_phase", (e, c, l, v) -> {
            Level level = levelOf(e, l);
            return level != null ? level.getMoonPhase() : 0;
        });
    }
}
