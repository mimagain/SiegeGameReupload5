package me.cedric.siegegame.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.amplifier.AmplifierManager;
import me.cedric.siegegame.amplifier.AmplifierType;
import me.cedric.siegegame.enums.Permissions;
import me.cedric.siegegame.model.game.WorldGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AmplifierStartCommand implements Command<CommandSourceStack> {

    private final SiegeGamePlugin plugin;

    public AmplifierStartCommand(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player) && !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
            return 0;
        }

        if (!sender.hasPermission(Permissions.START_AMPLIFIER.getPermission())) {
            sender.sendMessage(Component.text("You don't have permission to use this command!", NamedTextColor.RED));
            return 0;
        }

        try {
            String amplifierName = context.getArgument("amplifier", String.class);
            startAmplifier(sender, amplifierName);
        } catch (IllegalArgumentException e) {

            showUsage(sender);
        }

        return 1;
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /amplifierstart <amplifier>", NamedTextColor.RED));
        sender.sendMessage(Component.text("Available amplifiers: ", NamedTextColor.YELLOW)
                .append(Component.text(String.join(", ", getAmplifierNames()), NamedTextColor.WHITE)));
    }

    private void startAmplifier(CommandSender sender, String amplifierName) {
        try {
            AmplifierType type = AmplifierType.valueOf(amplifierName.toUpperCase().replace(" ", "_"));

            if (plugin.getGameManager() == null || plugin.getGameManager().getCurrentMatch() == null) {
                sender.sendMessage(Component.text("No active game match!", NamedTextColor.RED));
                return;
            }
            
            WorldGame worldGame = plugin.getGameManager().getCurrentMatch().getWorldGame();
            if (worldGame == null) {
                sender.sendMessage(Component.text("WorldGame is not initialized!", NamedTextColor.RED));
                return;
            }

            AmplifierManager manager = worldGame.getModules().stream()
                    .filter(module -> module instanceof AmplifierManager)
                    .map(module -> (AmplifierManager) module)
                    .findFirst()
                    .orElse(null);
            
            if (manager == null) {
                sender.sendMessage(Component.text("Amplifier manager is not initialized!", NamedTextColor.RED));
                return;
            }

            if (manager.triggerManual(type)) {
                sender.sendMessage(Component.text("Started amplifier: " + type.getTitle(), NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Failed to start amplifier. Another amplifier might be active.", NamedTextColor.RED));
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Invalid amplifier type!", NamedTextColor.RED));
            sender.sendMessage(Component.text("Available amplifiers: ", NamedTextColor.YELLOW)
                    .append(Component.text(String.join(", ", getAmplifierNames()), NamedTextColor.WHITE)));
        }
    }

    private String[] getAmplifierNames() {
        AmplifierType[] types = AmplifierType.values();
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = types[i].name().toLowerCase().replace("_", " ");
        }
        return names;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createCommand(SiegeGamePlugin plugin) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("amplifierstart")
                .requires(source -> source.getSender().hasPermission(Permissions.START_AMPLIFIER.getPermission()))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("amplifier", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (AmplifierType type : AmplifierType.values()) {
                                builder.suggest(type.name().toLowerCase().replace("_", " "));
                            }
                            return builder.buildFuture();
                        })
                        .executes(new AmplifierStartCommand(plugin)));
    }
}



