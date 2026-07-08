package me.cedric.siegegame;

import com.comphenix.protocol.ProtocolLibrary;
import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.cedric.siegegame.command.SpawnCommand;
import me.cedric.siegegame.command.SpectateCommand;
import me.cedric.siegegame.command.StatsCommandExecutor;
import me.cedric.siegegame.command.SwitchCommand;
import me.cedric.siegegame.command.TeamChatCommand;
import me.cedric.siegegame.command.TopStatsCommandExecutor;
import me.cedric.siegegame.command.StatsHideCommand;
import me.cedric.siegegame.command.StatsModifyCommandExecutor;
import me.cedric.siegegame.command.args.ReloadArg;
import me.cedric.siegegame.command.args.StartGameArg;
import me.cedric.siegegame.command.kits.DeleteKitArgument;
import me.cedric.siegegame.command.kits.KitLoadArgument;
import me.cedric.siegegame.command.kits.KitSetArgument;
import me.cedric.siegegame.amplifier.AmplifierType;

import me.cedric.siegegame.command.RallyCommand;
import me.cedric.siegegame.command.AmplifierListCommand;
import me.cedric.siegegame.enums.Permissions;
import me.cedric.siegegame.model.player.border.blockers.BlockChangePacketAdapter;
import me.cedric.siegegame.listeners.TeamChatListener;
import me.cedric.siegegame.model.player.border.PlayerBorderListener;
import me.cedric.siegegame.command.ResourcesCommand;
import me.cedric.siegegame.config.ConfigLoader;
import me.cedric.siegegame.config.GameConfig;
import me.cedric.siegegame.controller.stats.StatsController;
import me.cedric.siegegame.model.player.kits.db.PersistenceManager;
import me.cedric.siegegame.model.player.stats.PlayerStats;
import me.cedric.siegegame.util.VoteSkipManager;
import me.cedric.siegegame.view.display.placeholderapi.SiegeGameExpansion;
import net.kyori.adventure.text.Component;
import me.cedric.siegegame.model.player.PlayerListener;
import me.cedric.siegegame.command.AmplifierStopCommand;
import me.cedric.siegegame.command.MatchStatsCommandExecutor;
import me.cedric.siegegame.model.GameManager;
import me.cedric.siegegame.DatabaseManager;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.bukkit.Bukkit;
import me.cedric.siegegame.command.AmplifierStartCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class SiegeGamePlugin extends JavaPlugin {

    private ConfigLoader configLoader;
    private GameManager gameManager;
    private MatchStatsCommandExecutor matchStatsCommandExecutor;
    private PlayerStats playerStats;
    private DatabaseManager databaseManager;
    private StatsController statsController;
    private VoteSkipManager voteSkipManager;

    @Override
    public void onEnable() {
        PersistenceManager persistenceManager = new PersistenceManager(this);
        persistenceManager.initialise();
        this.voteSkipManager = new VoteSkipManager(this);

this.databaseManager = new DatabaseManager(this, new File(this.getDataFolder(), "playerstats.db"));
 this.databaseManager.establishConnection();
        this.databaseManager.createTable();
        this.gameManager = new GameManager(this, persistenceManager.getKitController());
        this.configLoader = new ConfigLoader(this);
        this.matchStatsCommandExecutor = new MatchStatsCommandExecutor(this);

        this.getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        this.getServer().getPluginManager().registerEvents(new PlayerBorderListener(this), this);
        // Register TeamChatListener after a game actually starts so we have a valid WorldGame reference
        this.getServer().getPluginManager().registerEvents(new TeamChatListener(this), this);
        // Register amplifier visual effects listener
        this.getServer().getPluginManager().registerEvents(new me.cedric.siegegame.amplifier.Axaxa(this), this);

        ProtocolLibrary.getProtocolManager().addPacketListener(new BlockChangePacketAdapter(this));

        registerCommands();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)
            new SiegeGameExpansion(this).register();

        configLoader.initialiseAndLoad();

        if (getGameConfig().getStartGameOnServerStartup())
            Bukkit.getScheduler().runTaskLater(this, () -> getGameManager().startNextGame(), 1L);
    }

    @Override
    public void onDisable() {
        gameManager.endGame(true, false);
        if (gameManager != null && gameManager.getCurrentMatch() != null) {
            gameManager.getCurrentMatch().getWorldGame().getStatsController().shutdown();
        }
        super.onDisable();
    }
    public VoteSkipManager getVoteSkipManager() {
        return voteSkipManager;
    }
    public GameManager getGameManager() {
        return gameManager;
    }

    public GameConfig getGameConfig() {
        return configLoader;
    }
    public static String adventureToLegacy(Component component) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
    }
    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(
                    Commands.literal("siegegame")
                            .then(Commands.literal("start")
                                    .executes(new StartGameArg(this))
                                    .then(Commands.argument("map", StringArgumentType.word())
                                            .suggests((context, builder) -> {
                                                for (String id : getGameConfig().getMapIDs()) {
                                                    if (id.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                                        builder.suggest(id);
                                                    }
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(new StartGameArg(this))
                                    )
                            )
                            .then(Commands.literal("reload")
                                    .executes(new ReloadArg(this))
                            ).build()
            );
            commands.registrar().register(Commands.literal("kit")
                    .requires(source -> source.getSender() instanceof Player &&
                            source.getSender().hasPermission(Permissions.KITS.getPermission()))
                    .then(Commands.literal("set")
                            .then(Commands.argument("map", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        builder.suggest("allmaps");
                                        getGameConfig().getMapIDs().forEach(builder::suggest);
                                        return builder.buildFuture();
                                    })
                                    .executes(new KitSetArgument(this))
                            )
                    )
                    .then(Commands.literal("delete")
                            .then(Commands.argument("identifier", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        builder.suggest("allmaps");
                                        getGameConfig().getMapIDs().forEach(builder::suggest);
                                        return builder.buildFuture();
                                    })
                                    .executes(new DeleteKitArgument(this))
                            )
                    )
                    .then(Commands.literal("load").executes(new KitLoadArgument(this)).then(Commands.literal("default").executes((Command)new KitLoadArgument(this, true)))).build());
            commands.registrar().register(Commands.literal("amplifier")
                    .then(Commands.literal("stop").executes(new AmplifierStopCommand(this)))
                    .then(AmplifierStartCommand.createCommand(this))
                    .then(AmplifierListCommand.createCommand(this))
                    .build());
            commands.registrar().register(Commands.literal("resources").executes(new ResourcesCommand(this)).build());
            commands.registrar().register(Commands.literal("rally").executes(new RallyCommand(this)).build());
            commands.registrar().register(Commands.literal("switch")
                    .requires(source -> source.getSender() instanceof Player)
                    .executes(new SwitchCommand(this))
                    .build());
            commands.registrar().register(Commands.literal("t")
                    .then(Commands.literal("spawn").executes(new SpawnCommand(this)))
                    .build());

            commands.registrar().register(Commands.literal("tc")
                    .requires(source -> source.getSender() instanceof Player)
                    .executes(new TeamChatCommand(this))
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(new TeamChatCommand(this)))
                    .build());
            commands.registrar().register(Commands.literal("voteskip")
                    .requires(source -> source.getSender() instanceof Player)
                    .executes(context -> {
                        Player player = (Player) context.getSource().getSender();
                        getVoteSkipManager().startVote(player);
                        return 1;
                    })
                    .build());

            commands.registrar().register(Commands.literal("voteyes")
                    .requires(source -> source.getSender() instanceof Player)
                    .executes(context -> {
                        Player player = (Player) context.getSource().getSender();
                        getVoteSkipManager().addYesVote(player);
                        return 1;
                    })
                    .build());

            commands.registrar().register(Commands.literal("voteno")
                    .requires(source -> source.getSender() instanceof Player)
                    .executes(context -> {
                        Player player = (Player) context.getSource().getSender();
                        getVoteSkipManager().addNoVote(player);
                        return 1;
                    })
                    .build());
            commands.registrar().register(Commands.literal("spectate")
                    .requires(source -> source.getSender() instanceof Player)
                    .executes(ctx -> {
                        new SpectateCommand(this).onCommand(
                                ctx.getSource().getSender(),
                                null,
                                "spectate",
                                new String[0]
                        );
                        return 1;
                    })
                    .build());
         commands.registrar().register(Commands.literal("stats")

                    .requires(source -> source.getSender().hasPermission("siegegame.stats"))
                    .executes(new StatsCommandExecutor(this))
                    .then(Commands.argument("player", StringArgumentType.word())
                            .executes(new StatsCommandExecutor(this)))
                    .then(Commands.literal("hide")
                            .requires(source -> source.getSender().hasPermission("siegegame.stats.hide"))
                            .executes(new StatsHideCommand(this)))
                    .then(Commands.literal("modify")
                            .requires(source -> source.getSender().hasPermission("siegegame.stats.modify"))
                            .then(Commands.argument("action", StringArgumentType.word())
                                    .suggests((c,b)->{b.suggest("add");b.suggest("set");return b.buildFuture();})
                                    .then(Commands.argument("player", StringArgumentType.word())
                                            .then(Commands.argument("stat", StringArgumentType.word())
                                                    .suggests((ctx,builder)->{
                                                        builder.suggest("kills");
                                                        builder.suggest("deaths");
                                                        builder.suggest("damage");
                                                        builder.suggest("damagetaken");
                                                        builder.suggest("gamesplayed");
                                                        builder.suggest("gameswon");
                                                        builder.suggest("kdr");
                                                        return builder.buildFuture();
                                                    })
                                                    .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                            .executes(new StatsModifyCommandExecutor(this)))))))
                    .then(Commands.literal("top")
                            .requires(source -> source.getSender().hasPermission("siegegame.stats.top"))
                            .executes(new TopStatsCommandExecutor(this))
                            .then(Commands.argument("stat", StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        builder.suggest("kills");
                                        builder.suggest("deaths");
                                        builder.suggest("damage");
                                        builder.suggest("games");
                                        builder.suggest("wins");
                                        builder.suggest("kdr");
                                        return builder.buildFuture();
                                    })
                                    .executes(new TopStatsCommandExecutor(this))))
                    .build());
                    commands.registrar().register(Commands.literal("matchstats")
                    .requires(source -> source.getSender().hasPermission("siegegame.matchstats"))
                    .executes(matchStatsCommandExecutor)
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(matchStatsCommandExecutor))
                    .build());
        });
    }
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    public StatsController getStatsController() {
        if (gameManager != null && gameManager.getCurrentMatch() != null) {
            return gameManager.getCurrentMatch().getWorldGame().getStatsController();
        }
        return null;
    }
    public ICombatLogX getCombatLogX() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin plugin = pluginManager.getPlugin("CombatLogX");
        return (ICombatLogX) plugin;
    }
}



