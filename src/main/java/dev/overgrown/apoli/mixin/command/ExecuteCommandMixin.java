package dev.overgrown.apoli.mixin.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.overgrown.apoli.command.ApoliResourceCommand;
import dev.overgrown.apoli.power.PowerContainer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(ExecuteCommand.class)
public class ExecuteCommandMixin {
    @Inject(method = "register", at = @At(value = "TAIL"))
    private static void apoli$registerExecuteStoreResource(CommandDispatcher<CommandSourceStack> commandDispatcher, CommandBuildContext commandBuildContext, CallbackInfo ci) {
        LiteralCommandNode<CommandSourceStack> executeNode = (LiteralCommandNode<CommandSourceStack>) commandDispatcher.getRoot().getChild("execute");
        CommandNode<CommandSourceStack> storeNode = executeNode.getChild("store");
        storeNode.getChild("result").addChild(apoli$storeResource(executeNode, false));
        storeNode.getChild("success").addChild(apoli$storeResource(executeNode, true));
    }

    @Unique
    private static CommandNode<CommandSourceStack> apoli$storeResource(LiteralCommandNode<CommandSourceStack> executeNode, boolean successOnly) {
        return Commands.literal("resource")
            .then(Commands.argument("targets", EntityArgument.entities())
                .then(Commands.argument("power", ResourceLocationArgument.id())
                    .suggests(ApoliResourceCommand.RESOURCE_POWERS)
                    .redirect(executeNode, ctx -> apoli$storeValue(
                        ctx.getSource(),
                        EntityArgument.getEntities(ctx, "targets"),
                        ResourceLocationArgument.getId(ctx, "power"),
                        successOnly))))
            .build();
    }

    @Unique
    private static CommandSourceStack apoli$storeValue(CommandSourceStack source, Collection<? extends Entity> targets, ResourceLocation power, boolean successOnly) {
        return source.withCallback((success, result) -> {
            int value = successOnly ? (success ? 1 : 0) : result;
            int affected = 0;
            for (Entity target : targets) {
                if (!target.isAlive()) continue;
                PowerContainer container = PowerContainer.of(target);
                if (container == null) continue;
                if (ApoliResourceCommand.writeAll(container, power, value).isPresent()) affected++;
            }
            if (affected == 0) {
                source.sendFailure(Component.literal("No target holds the resource power " + power));
            }
        }, CommandResultCallback::chain);
    }
}
