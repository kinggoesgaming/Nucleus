/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.jail.commands;

import com.google.inject.Inject;
import io.github.nucleuspowered.nucleus.Util;
import io.github.nucleuspowered.nucleus.api.nucleusdata.NamedLocation;
import io.github.nucleuspowered.nucleus.internal.annotations.NoCooldown;
import io.github.nucleuspowered.nucleus.internal.annotations.NoCost;
import io.github.nucleuspowered.nucleus.internal.annotations.NoWarmup;
import io.github.nucleuspowered.nucleus.internal.annotations.Permissions;
import io.github.nucleuspowered.nucleus.internal.annotations.RegisterCommand;
import io.github.nucleuspowered.nucleus.internal.annotations.RunAsync;
import io.github.nucleuspowered.nucleus.internal.command.AbstractCommand;
import io.github.nucleuspowered.nucleus.internal.permissions.SuggestedLevel;
import io.github.nucleuspowered.nucleus.modules.jail.handlers.JailHandler;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.service.pagination.PaginationService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@NoCooldown
@NoCost
@NoWarmup
@RunAsync
@RegisterCommand(value = "jails")
@Permissions(prefix = "jail", mainOverride = "list", suggestedLevel = SuggestedLevel.MOD)
public class JailsCommand extends AbstractCommand<CommandSource> {

    @Inject private JailHandler handler;

    @Override
    public CommandResult executeCommand(CommandSource src, CommandContext args) throws Exception {
        PaginationService ps = Sponge.getServiceManager().provideUnchecked(PaginationService.class);

        Map<String, NamedLocation> mjs = handler.getJails();
        if (mjs.isEmpty()) {
            src.sendMessage(plugin.getMessageProvider().getTextMessageWithFormat("command.jails.nojails"));
            return CommandResult.empty();
        }

        List<Text> lt = mjs.entrySet().stream()
                .map(x -> createJail(x.getValue(), x.getKey()))
                .collect(Collectors.toList());

        Util.getPaginationBuilder(src)
            .title(plugin.getMessageProvider().getTextMessageWithFormat("command.jails.list.header")).padding(Text.of(TextColors.GREEN, "-"))
            .contents(lt).sendTo(src);
        return CommandResult.success();
    }

    private Text createJail(NamedLocation data, String name) {
        if (data == null || !data.getLocation().isPresent()) {
            return Text.builder(name).color(TextColors.RED).onHover(TextActions.showText(plugin.getMessageProvider().getTextMessageWithFormat
                    ("command.jails.unavailable"))).build();
        }

        Location<World> world = data.getLocation().get();
        Text.Builder inner = Text.builder(name).color(TextColors.GREEN).style(TextStyles.ITALIC)
                .onClick(TextActions.runCommand("/jails tp " + name))
                .onHover(TextActions.showText(plugin.getMessageProvider().getTextMessageWithFormat("command.jails.warpprompt", name)));

        return Text.builder().append(inner.build())
                .append(plugin.getMessageProvider().getTextMessageWithFormat("command.warps.warploc",
                        world.getExtent().getName(), world.getBlockPosition().toString()
                )).build();
    }
}
