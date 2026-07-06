package me.cedric.siegegame.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.enums.Permissions;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.game.WorldGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

public class AmplifierStopCommand implements Command<CommandSourceStack> {

    private final SiegeGamePlugin plugin;

    public AmplifierStopCommand(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
        CommandSender sender = commandContext.getSource().getSender();
        if (!sender.hasPermission(Permissions.STOP_AMPLIFIER.getPermission()))
            return 0;
        Player player = (Player) sender;
        SiegeGameMatch gameMatch = plugin.getGameManager().getCurrentMatch();

        if (gameMatch == null) {
            player.sendMessage(Component.text("No game running", NamedTextColor.RED));
            return 0;
        }

        WorldGame worldGame = gameMatch.getWorldGame();
        if (worldGame.getAmplifierManager().getCurrent() == null)
        {
            player.sendMessage(Component.text("No amplifier running", NamedTextColor.RED));
            return 0;
        }
        worldGame.getAmplifierManager().endAmplifier();
      

        player.sendMessage(Component.text("Amplifier Stopped", NamedTextColor.GREEN));
        return 0;
    }
}



