package io.github.icohedron.safeconnect;

import com.google.common.reflect.TypeToken;
import com.google.inject.Inject;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.cause.entity.damage.DamageType;
import org.spongepowered.api.event.cause.entity.damage.DamageTypes;
import org.spongepowered.api.event.cause.entity.damage.source.DamageSource;
import org.spongepowered.api.event.cause.entity.damage.source.EntityDamageSource;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.filter.cause.First;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Plugin(id = "safeconnect", name = "SafeConnect", version = "1.0.0-S5.1-SNAPSHOT-1",
        description = "Ensures a safe (re)connection to the server")
public class SafeConnect {

    private final Text prefix = Text.of(TextColors.GRAY, "[", TextColors.GOLD, "SafeConnect", TextColors.GRAY, "] ");
    private final String configFileName = "safeconnect.conf";

    @Inject @ConfigDir(sharedRoot = false) private Path configurationPath;

    private int durationInTicks;
    private String command;

    private Set<UUID> justConnected;

    @Inject private Logger logger;

    @Listener
    public void onInitialization(GameInitializationEvent event) {
        justConnected = new HashSet<>();
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(configurationPath.toFile(), configFileName);
        ConfigurationLoader<CommentedConfigurationNode> configurationLoader = HoconConfigurationLoader.builder().setFile(configFile).build();
        ConfigurationNode rootNode;

        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            logger.info("Configuration file '" + configFile.getPath() + "' not found. Creating default configuration");
            try {
                Sponge.getAssetManager().getAsset(this, configFileName).get().copyToFile(configFile.toPath());
            } catch (IOException e) {
                logger.error("Failed to load default configuration file!");
                e.printStackTrace();
            }
        }

        try {
            rootNode = configurationLoader.load();

            durationInTicks = rootNode.getNode("duration").getInt(40);
            command = rootNode.getNode("useCommand").getString();

        } catch (IOException e) {
            logger.error("An error has occurred while reading the configuration file: ");
            e.printStackTrace();
        }
    }


    @Listener
    public void onClientConnection(ClientConnectionEvent.Join event, @First Player player) {
        justConnected.add(player.getUniqueId());
        Optional<Location<World>> safeLoc = Sponge.getGame().getTeleportHelper().getSafeLocation(player.getLocation());
        if (!safeLoc.isPresent()) {
            logger.info("'" + player.getName() + "' connected to the server in a dangerous location");
            return;
        }

        if (!player.getLocation().equals(safeLoc.get())) {
            logger.info("'" + player.getName() + "' connected to the server in a dangerous location");
        }
    }

    @Listener
    public void onDamageEntity(DamageEntityEvent event) {
        if (event.getTargetEntity() instanceof Player) {

            Player player = (Player) event.getTargetEntity();
            Optional<DamageSource> damageSource = event.getCause().first(DamageSource.class);
            if (damageSource.isPresent()) {
                DamageType damageType = damageSource.get().getType();

                if (damageType.equals(DamageTypes.FIRE) || damageType.equals(DamageTypes.SUFFOCATE)) {
                    if (justConnected.contains(player.getUniqueId())) {

                        if (command != null || command.equals("")) {
                            CommandResult spawnCommandResult = Sponge.getCommandManager().process(player, "spawn");
                            if (spawnCommandResult.equals(CommandResult.empty())) {
                                Text errorText = Text.of(prefix, TextColors.RED, "Error: The command '" + command + "' failed to execute for player '" + player.getName() + "'");
                                player.sendMessage(errorText);
                                logger.error(errorText.toPlain());
                            }
                        } else {
                            Location<World> spawn = player.getWorld().getSpawnLocation();
                            Location<World> teleportLoc = Sponge.getGame().getTeleportHelper().getSafeLocation(spawn).get();
                            player.setLocation(teleportLoc);
                            player.sendMessage(Text.of(prefix, TextColors.YELLOW, "You've been teleported to the world spawn"));
                        }

                        justConnected.remove(player.getUniqueId());

                    }
                }

            }

        }
    }

    private void teleportToSpawn(Player player) {

    }
}
