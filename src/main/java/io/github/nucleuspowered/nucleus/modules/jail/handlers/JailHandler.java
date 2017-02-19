/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.modules.jail.handlers;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import io.github.nucleuspowered.nucleus.Nucleus;
import io.github.nucleuspowered.nucleus.NucleusPlugin;
import io.github.nucleuspowered.nucleus.api.nucleusdata.NamedLocation;
import io.github.nucleuspowered.nucleus.dataservices.modular.ModularGeneralService;
import io.github.nucleuspowered.nucleus.dataservices.modular.ModularUserService;
import io.github.nucleuspowered.nucleus.iapi.data.JailData;
import io.github.nucleuspowered.nucleus.iapi.service.NucleusJailService;
import io.github.nucleuspowered.nucleus.internal.teleport.NucleusTeleportHandler;
import io.github.nucleuspowered.nucleus.modules.core.datamodules.CoreUserDataModule;
import io.github.nucleuspowered.nucleus.modules.fly.datamodules.FlyUserDataModule;
import io.github.nucleuspowered.nucleus.modules.jail.datamodules.JailGeneralDataModule;
import io.github.nucleuspowered.nucleus.modules.jail.datamodules.JailUserDataModule;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.service.context.ContextCalculator;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class JailHandler implements NucleusJailService, ContextCalculator<Subject> {

    @Inject private ModularGeneralService store;

    private final NucleusPlugin plugin;

    // Used for the context canclulator
    private final Map<UUID, Context> jailDataCache = Maps.newHashMap();
    private final static Context jailContext = new Context("nucleus_jailed", "true");

    public JailHandler(NucleusPlugin plugin) {
        this.plugin = plugin;
    }

    private JailGeneralDataModule getModule() {
        return store.get(JailGeneralDataModule.class);
    }

    @Override
    public Optional<NamedLocation> getJail(String warpName) {
        return getModule().getJailLocation(warpName);
    }

    @Override
    public boolean removeJail(String warpName) {
        return getModule().removeJail(warpName);
    }

    @Override
    public boolean setJail(String warpName, Location<World> location, Vector3d rotation) {
        return getModule().addJail(warpName, location, rotation);
    }

    @Override
    public Map<String, NamedLocation> getJails() {
        return getModule().getJails();
    }

    @Override
    public boolean isPlayerJailed(User user) {
        return getPlayerJailData(user).isPresent();
    }

    @Override
    public Optional<JailData> getPlayerJailData(User user) {
        try {
            Optional<JailData> data = plugin.getUserDataManager().get(user).get().get(JailUserDataModule.class).getJailData();
            if (data.isPresent()) {
                jailDataCache.put(user.getUniqueId(), new Context("nucleus_jail", data.get().getJailName()));
            } else {
                jailDataCache.put(user.getUniqueId(), null);
            }

            return data;
        } catch (Exception e) {
            if (Nucleus.getNucleus().isDebugMode()) {
                e.printStackTrace();
            }

            return Optional.empty();
        }
    }

    @Override
    public boolean jailPlayer(User user, JailData data) {
        ModularUserService modularUserService = plugin.getUserDataManager().get(user).get();
        JailUserDataModule jailUserDataModule = modularUserService.get(JailUserDataModule.class);

        if (jailUserDataModule.getJailData().isPresent()) {
            return false;
        }

        // Get the jail.
        Optional<NamedLocation> owl = getJail(data.getJailName());
        NamedLocation wl = owl.orElseGet(() -> {
            if (!getJails().isEmpty()) {
                return null;
            }

            return getJails().entrySet().stream().findFirst().get().getValue();
        });

        if (wl == null) {
            return false;
        }

        jailUserDataModule.setJailData(data);
        jailDataCache.put(user.getUniqueId(), new Context("nucleus_jail", data.getJailName()));
        if (user.isOnline()) {
            Sponge.getScheduler().createSyncExecutor(plugin).execute(() -> {
                Player player = user.getPlayer().get();
                plugin.getTeleportHandler().teleportPlayer(player, owl.get().getLocation().get(), owl.get().getRotation(),
                    NucleusTeleportHandler.TeleportMode.NO_CHECK, Cause.of(NamedCause.owner(plugin)));
                modularUserService.get(FlyUserDataModule.class).setFlying(false);
            });
        } else {
            jailUserDataModule.setJailOnNextLogin(true);
        }

        return true;
    }

    @Override
    public boolean unjailPlayer(User user) {
        final ModularUserService modularUserService = plugin.getUserDataManager().get(user).get();
        final JailUserDataModule jailUserDataModule = modularUserService.get(JailUserDataModule.class);
        Optional<JailData> ojd = jailUserDataModule.getJailData();
        if (!ojd.isPresent()) {
            return false;
        }

        Optional<Location<World>> ow = ojd.get().getPreviousLocation();
        jailDataCache.put(user.getUniqueId(), null);
        if (user.isOnline()) {
            Player player = user.getPlayer().get();
            Sponge.getScheduler().createSyncExecutor(plugin).execute(() -> {
                player.setLocation(ow.isPresent() ? ow.get() : player.getWorld().getSpawnLocation());
                player.sendMessage(NucleusPlugin.getNucleus().getMessageProvider().getTextMessageWithFormat("jail.elapsed"));

                // Remove after the teleport for the back data.
                jailUserDataModule.removeJailData();
            });
        } else {
            modularUserService.get(CoreUserDataModule.class).sendToLocationOnLogin(ow.isPresent() ? ow.get()
                    : new Location<>(Sponge.getServer().getWorld(Sponge.getServer().getDefaultWorld().get().getUniqueId()).get(),
                            Sponge.getServer().getDefaultWorld().get().getSpawnPosition()));
            jailUserDataModule.removeJailData();
        }

        return true;
    }

    public Optional<NamedLocation> getWarpLocation(User user) {
        if (!isPlayerJailed(user)) {
            return Optional.empty();
        }

        Optional<NamedLocation> owl = getJail(getPlayerJailData(user).get().getJailName());
        if (!owl.isPresent()) {
            Collection<NamedLocation> wl = getJails().values();
            if (wl.isEmpty()) {
                return Optional.empty();
            }

            owl = wl.stream().findFirst();
        }

        return owl;
    }

    @Override public void accumulateContexts(Subject calculable, Set<Context> accumulator) {
        if (calculable instanceof User) {
            UUID c = ((User) calculable).getUniqueId();
            if (!jailDataCache.containsKey(c)) {
                getPlayerJailData((User) calculable);
            }

            Context co = jailDataCache.get(c);
            if (co != null) {
                accumulator.add(co);
                accumulator.add(jailContext);
            }
        }
    }

    @Override public boolean matches(Context context, Subject subject) {
        if (context.getKey().equals("nucleus_jail")) {
            if (subject instanceof User) {
                UUID u = ((User) subject).getUniqueId();
                return context.equals(jailDataCache.get(u));
            }
        } else if (context.getKey().equals("nucleus_jailed")) {
            if (subject instanceof User) {
                UUID u = ((User) subject).getUniqueId();
                return jailDataCache.get(u) != null;
            }
        }

        return false;
    }
}
