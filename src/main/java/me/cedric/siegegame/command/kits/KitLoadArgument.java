package me.cedric.siegegame.command.kits;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.enums.Permissions;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.player.kits.Kit;
import me.cedric.siegegame.model.teams.Team;
import me.cedric.siegegame.model.teams.territory.Territory;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

public class KitLoadArgument implements Command<CommandSourceStack> {

    private final SiegeGamePlugin plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    public KitLoadArgument(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int run(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
        CommandSender sender = commandContext.getSource().getSender();
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return 0;
        }

        Player player = (Player) sender;


        if (isOnCooldown(player.getUniqueId())) {
            long cooldown = getCooldown(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "You need to wait another " + cooldown + " seconds to do this.");
            return 0;
        }

        SiegeGameMatch match = plugin.getGameManager().getCurrentMatch();
        if (match == null) {
            player.sendMessage(ChatColor.RED + "You need to be in a match to do this");
            return 0;
        }

        WorldGame worldGame = match.getWorldGame();
        GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());
        if (gamePlayer == null) {
            return 0;
        }
        Team currentTeam = gamePlayer.getTeam();
        Territory territory = currentTeam.getTerritory();

        if (!territory.isInside(player.getLocation())) {
            player.sendMessage(ChatColor.RED + "You must be inside your team's claims to use /kit load.");
            return 0;
        }

        Kit kit = plugin.getGameManager().getKitController().getKit(player.getUniqueId(), worldGame.getMapIdentifier());

        if (kit == null) {
            player.sendMessage(ChatColor.RED + "You don't have a kit saved for this map or a default kit.");
            return 0;
        }

        player.getInventory().setContents(kit.getContents().toArray(new ItemStack[0]));
        player.updateInventory();
        player.sendMessage(ChatColor.GREEN + "Kit loaded successfully!");

        putOnCooldown(player.getUniqueId());
        return 1;
    }

    private boolean isOnCooldown(UUID uuid) {
        if (!cooldowns.containsKey(uuid))
            return false;

        long lastTime = cooldowns.get(uuid);
        long currentTime = System.currentTimeMillis();

        return currentTime - lastTime < 15 * 1000L; 
    }

    private void putOnCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    private long getCooldown(UUID uuid) {
        if (!cooldowns.containsKey(uuid))
            return 0L;

        long lastTime = cooldowns.get(uuid);
        long currentTime = System.currentTimeMillis();

        return ((15 * 1000L) - (currentTime - lastTime)) / 1000;
    }
}



