package me.cedric.siegegame.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.amplifier.AmplifierType;
import me.cedric.siegegame.enums.Permissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

public class AmplifierListCommand implements Command<CommandSourceStack> {

    private final SiegeGamePlugin plugin;

    public AmplifierListCommand(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        
        if (!sender.hasPermission(Permissions.LIST_AMPLIFIERS.getPermission())) {
            sender.sendMessage(Component.text("You don't have permission to use this command!", NamedTextColor.RED));
            return 0;
        }

        sendAmplifierList(sender);
        return 1;
    }

    private void sendAmplifierList(CommandSender sender) {
        sender.sendMessage(Component.text("\n=== Available Amplifiers ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        
        for (AmplifierType type : AmplifierType.values()) {
            Component line = Component.text("• ", NamedTextColor.GRAY)
                    .append(Component.text(type.name().toLowerCase().replace("_", " "), NamedTextColor.YELLOW))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(type.getSubtitle(), NamedTextColor.WHITE));
            sender.sendMessage(line);
        }
        
        sender.sendMessage(Component.text("===========================\n", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text("Use ", NamedTextColor.GRAY)
                .append(Component.text("/amplifierstart <name>", NamedTextColor.YELLOW))
                .append(Component.text(" to start an amplifier", NamedTextColor.GRAY)));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> createCommand(SiegeGamePlugin plugin) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("amplifierlist")
                .requires(source -> source.getSender().hasPermission(Permissions.LIST_AMPLIFIERS.getPermission()))
                .executes(new AmplifierListCommand(plugin));
    }
}



