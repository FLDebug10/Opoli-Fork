package dev.overgrown.apoli.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.overgrown.apoli.power.ApoliPowers;
import dev.overgrown.apoli.power.Power;
import dev.overgrown.apoli.power.PowerContainer;
import dev.overgrown.apoli.power.PowerResources;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.OperationArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class ApoliResourceCommand {

    private ApoliResourceCommand() {}

    public static final SuggestionProvider<CommandSourceStack> RESOURCE_POWERS = (ctx, builder) -> {
        List<ResourceLocation> held = heldResourcePowers(ctx);
        return SharedSuggestionProvider.suggestResource(held.isEmpty() ? loadedResourcePowers() : held, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("apoli:resource")
            .requires(ApoliPermissions.require("apoli.command.resource", 2));

        root.then(Commands.literal("get")
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("power", ResourceLocationArgument.id())
                    .suggests(RESOURCE_POWERS)
                    .executes(ApoliResourceCommand::get)
                    .then(Commands.argument("position", IntegerArgumentType.integer(0))
                        .executes(ApoliResourceCommand::get)))));

        root.then(Commands.literal("list")
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("power", ResourceLocationArgument.id())
                    .suggests(RESOURCE_POWERS)
                    .executes(ApoliResourceCommand::list))));

        root.then(Commands.literal("set")
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("power", ResourceLocationArgument.id())
                    .suggests(RESOURCE_POWERS)
                    .then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(ApoliResourceCommand::set)
                        .then(Commands.argument("position", IntegerArgumentType.integer(0))
                            .executes(ApoliResourceCommand::set))))));

        root.then(Commands.literal("change")
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("power", ResourceLocationArgument.id())
                    .suggests(RESOURCE_POWERS)
                    .then(Commands.argument("value", IntegerArgumentType.integer())
                        .executes(ApoliResourceCommand::change)
                        .then(Commands.argument("position", IntegerArgumentType.integer(0))
                            .executes(ApoliResourceCommand::change))))));

        root.then(Commands.literal("has")
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("power", ResourceLocationArgument.id())
                    .suggests(RESOURCE_POWERS)
                    .executes(ApoliResourceCommand::has))));

        root.then(Commands.literal("operation")
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("power", ResourceLocationArgument.id())
                    .suggests(RESOURCE_POWERS)
                    .then(operationTail())
                    .then(Commands.argument("position", IntegerArgumentType.integer(0))
                        .then(operationTail())))));

        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);

        dispatcher.register(Commands.literal("resource")
            .requires(ApoliPermissions.require("apoli.command.resource", 2))
            .redirect(node));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, OperationArgument.Operation> operationTail() {
        return Commands.argument("operation", OperationArgument.operation())
            .then(Commands.argument("source", ScoreHolderArgument.scoreHolder())
                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                .then(Commands.argument("objective", ObjectiveArgument.objective())
                    .executes(ApoliResourceCommand::operation)));
    }

    private static int operation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        int position = position(ctx);
        OperationArgument.Operation operation = OperationArgument.getOperation(ctx, "operation");
        Scoreboard scoreboard = ctx.getSource().getServer().getScoreboard();
        Score source = scoreboard.getOrCreatePlayerScore(
            ScoreHolderArgument.getName(ctx, "source"), ObjectiveArgument.getObjective(ctx, "objective"));
        int affected = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!target.isAlive()) continue;
            PowerContainer c = PowerContainer.of(target);
            if (c == null) continue;
            OptionalInt current = position < 0
                ? PowerResources.read(c, power)
                : PowerResources.readAt(c, power, position);
            if (current.isEmpty()) continue;
            ResourceValue value = new ResourceValue(scoreboard, current.getAsInt());
            operation.apply(value, source);
            OptionalInt written = position < 0
                ? writeAll(c, power, value.getScore())
                : PowerResources.writeAt(c, power, position, value.getScore());
            if (written.isEmpty()) continue;
            int before = current.getAsInt();
            int after = written.getAsInt();
            ctx.getSource().sendSuccess(() -> Component.literal(
                target.getName().getString() + " — " + power + " changed from " + before + " to " + after), true);
            affected++;
        }
        if (affected == 0) {
            ctx.getSource().sendFailure(Component.literal("No target holds the resource power " + power));
        }
        return affected;
    }

    private static int position(CommandContext<CommandSourceStack> ctx) {
        try {
            return IntegerArgumentType.getInteger(ctx, "position");
        } catch (IllegalArgumentException absent) {
            return -1;
        }
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        int reported = 0;
        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
            PowerContainer c = PowerContainer.of(e);
            if (c == null) continue;
            int size = Math.max(1, PowerResources.size(c, power));
            StringBuilder line = new StringBuilder();
            boolean any = false;
            for (int slot = 0; slot < size; slot++) {
                OptionalInt val = PowerResources.readAt(c, power, slot);
                if (val.isEmpty()) continue;
                if (any) line.append(", ");
                line.append(slot).append(": ").append(val.getAsInt());
                any = true;
            }
            if (!any) continue;
            String body = line.toString();
            ctx.getSource().sendSuccess(() -> Component.literal(
                e.getName().getString() + " — " + power + " [" + body + "]"), false);
            reported++;
        }
        if (reported == 0) {
            ctx.getSource().sendFailure(Component.literal("No target has a resource value for " + power));
        }
        return reported;
    }

    private static int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        int position = position(ctx);
        OptionalInt first = OptionalInt.empty();
        int reported = 0;
        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
            PowerContainer c = PowerContainer.of(e);
            if (c == null) continue;
            OptionalInt val = position < 0
                ? PowerResources.read(c, power)
                : PowerResources.readAt(c, power, position);
            if (val.isEmpty()) continue;
            int v = val.getAsInt();
            String at = position < 0 ? "" : "[" + position + "]";
            ctx.getSource().sendSuccess(() -> Component.literal(
                e.getName().getString() + " — " + power + at + ": " + v), false);
            if (first.isEmpty()) first = OptionalInt.of(v);
            reported++;
        }
        if (reported == 0) {
            ctx.getSource().sendFailure(Component.literal("No target has a resource value for " + power));
            return 0;
        }
        return first.orElse(0);
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        int value = IntegerArgumentType.getInteger(ctx, "value");
        int position = position(ctx);
        int affected = 0;
        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
            PowerContainer c = PowerContainer.of(e);
            if (c == null) continue;
            OptionalInt written = position < 0
                ? writeAll(c, power, value)
                : PowerResources.writeAt(c, power, position, value);
            if (written.isEmpty()) continue;
            int w = written.getAsInt();
            ctx.getSource().sendSuccess(() -> Component.literal(
                e.getName().getString() + " — " + power + " set to " + w), true);
            affected++;
        }
        if (affected == 0) {
            ctx.getSource().sendFailure(Component.literal("No target holds the resource power " + power));
        }
        return affected;
    }

    private static int change(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        int delta = IntegerArgumentType.getInteger(ctx, "value");
        int position = position(ctx);
        int affected = 0;
        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
            PowerContainer c = PowerContainer.of(e);
            if (c == null) continue;
            OptionalInt cur = position < 0
                ? PowerResources.read(c, power)
                : PowerResources.readAt(c, power, position);
            if (cur.isEmpty()) continue;
            OptionalInt written = position < 0
                ? PowerResources.write(c, power, cur.getAsInt() + delta)
                : PowerResources.writeAt(c, power, position, cur.getAsInt() + delta);
            if (written.isEmpty()) continue;
            int w = written.getAsInt();
            ctx.getSource().sendSuccess(() -> Component.literal(
                e.getName().getString() + " — " + power + " now " + w), true);
            affected++;
        }
        if (affected == 0) {
            ctx.getSource().sendFailure(Component.literal("No target holds the resource power " + power));
        }
        return affected;
    }

    private static int has(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation power = ResourceLocationArgument.getId(ctx, "power");
        int count = 0;
        for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
            PowerContainer c = PowerContainer.of(e);
            if (c == null) continue;
            if (PowerResources.read(c, power).isPresent()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                    e.getName().getString() + " has resource " + power), false);
                count++;
            }
        }
        if (count == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("No target holds the resource power " + power), false);
        }
        return count;
    }

    public static OptionalInt writeAll(PowerContainer container, ResourceLocation power, int value) {
        int size = PowerResources.size(container, power);
        if (size <= 1) return PowerResources.write(container, power, value);
        OptionalInt last = OptionalInt.empty();
        for (int slot = 0; slot < size; slot++) {
            OptionalInt written = PowerResources.writeAt(container, power, slot, value);
            if (written.isPresent()) last = written;
        }
        return last;
    }

    private static List<ResourceLocation> heldResourcePowers(CommandContext<CommandSourceStack> ctx) {
        List<ResourceLocation> out = new ArrayList<>();
        try {
            for (Entity e : EntityArgument.getEntities(ctx, "targets")) {
                PowerContainer c = PowerContainer.of(e);
                if (c == null) continue;
                for (ResourceLocation id : c.allPowers()) {
                    Power power = ApoliPowers.get(id);
                    if (power != null && PowerResources.isResource(id) && !out.contains(id)) out.add(id);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static List<ResourceLocation> loadedResourcePowers() {
        List<ResourceLocation> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Power> e : ApoliPowers.view().entrySet()) {
            if (PowerResources.isResource(e.getKey())) out.add(e.getKey());
        }
        return out;
    }

    private static final class ResourceValue extends Score {
        private int value;

        private ResourceValue(Scoreboard scoreboard, int value) {
            super(scoreboard, null, "");
            this.value = value;
        }

        @Override
        public int getScore() {
            return value;
        }

        @Override
        public void setScore(int value) {
            this.value = value;
        }
    }
}
