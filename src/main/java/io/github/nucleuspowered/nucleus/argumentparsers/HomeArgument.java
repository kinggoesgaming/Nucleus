/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.argumentparsers;

import com.google.common.collect.Lists;
import io.github.nucleuspowered.nucleus.Nucleus;
import io.github.nucleuspowered.nucleus.NucleusPlugin;
import io.github.nucleuspowered.nucleus.api.nucleusdata.Home;
import io.github.nucleuspowered.nucleus.api.nucleusdata.NamedLocation;
import io.github.nucleuspowered.nucleus.modules.core.config.CoreConfigAdapter;
import io.github.nucleuspowered.nucleus.modules.home.datamodules.HomeUserDataModule;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.ArgumentParseException;
import org.spongepowered.api.command.args.CommandArgs;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.annotation.NonnullByDefault;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Returns a {@link NamedLocation}
 */
@NonnullByDefault
public class HomeArgument extends CommandElement {

    private final Nucleus plugin;
    private final CoreConfigAdapter cca;

    public HomeArgument(@Nullable Text key, Nucleus plugin, CoreConfigAdapter cca) {
        super(key);
        this.plugin = plugin;
        this.cca = cca;
    }

    @Nullable
    @Override
    protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
        if (!(source instanceof User)) {
            throw args.createError(NucleusPlugin.getNucleus().getMessageProvider().getTextMessageWithFormat("command.playeronly"));
        }

        return getHome((User) source, args.next(), args);
    }

    Home getHome(User user, String home, CommandArgs args) throws ArgumentParseException {
        try {
            Optional<Home> owl = plugin.getUserDataManager().get(user).get().quickGet(HomeUserDataModule.class, x -> x.getHome(home));
            if (owl.isPresent()) {
                return owl.get();
            }
        } catch (Exception e) {
            if (cca.getNodeOrDefault().isDebugmode()) {
                e.printStackTrace();
            }

            throw args.createError(Text.of(TextColors.RED, "An unspecified error occurred"));
        }

        throw args.createError(NucleusPlugin.getNucleus().getMessageProvider().getTextMessageWithFormat("args.home.nohome", home));
    }

    @Override
    public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
        if (!(src instanceof User)) {
            return Lists.newArrayList();
        }

        User u = (User) src;
        try {
            return complete(u, args.peek());
        } catch (ArgumentParseException e) {
            return complete(u, "");
        }
    }

    protected List<String> complete(User src, String homeName) {
        Set<String> s;
        try {
            s = plugin.getUserDataManager().get(src).get().quickGet(HomeUserDataModule.class, HomeUserDataModule::getHomes).keySet();
        } catch (Exception e) {
            return Lists.newArrayList();
        }

        String name = homeName.toLowerCase();
        return s.stream().filter(x -> x.toLowerCase().startsWith(name)).collect(Collectors.toList());
    }
}
