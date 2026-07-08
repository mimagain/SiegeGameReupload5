package me.cedric.siegegame.command.kits;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Function;
import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.SiegeGameMatch;
import me.cedric.siegegame.model.game.WorldGame;
import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.player.kits.Kit;
import me.cedric.siegegame.model.teams.Team;
import me.cedric.siegegame.model.teams.territory.Territory;
import me.cedric.siegegame.view.display.shop.ShopItem;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class KitLoadArgument implements Command<CommandSourceStack> {
    private final SiegeGamePlugin plugin;

    private final HashMap<UUID, Long> cooldowns = new HashMap<>();

    private final boolean loadDefault;

    public KitLoadArgument(SiegeGamePlugin plugin) {
        this(plugin, false);
    }

    public KitLoadArgument(SiegeGamePlugin plugin, boolean loadDefault) {
        this.plugin = plugin;
        this.loadDefault = loadDefault;
    }

    public int run(CommandContext<CommandSourceStack> commandContext) throws CommandSyntaxException {
        CommandSender sender = ((CommandSourceStack)commandContext.getSource()).getSender();
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players!");
            return 0;
        }
        Player player = (Player)sender;
        if (isOnCooldown(player.getUniqueId())) {
            long cooldown = getCooldown(player.getUniqueId());
            player.sendMessage(String.valueOf(ChatColor.RED) + "You need to wait another " + String.valueOf(ChatColor.RED) + " seconds to do this.");
            return 0;
        }
        SiegeGameMatch match = this.plugin.getGameManager().getCurrentMatch();
        if (match == null) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You need to be in a match to do this");
            return 0;
        }
        WorldGame worldGame = match.getWorldGame();
        GamePlayer gamePlayer = worldGame.getPlayer(player.getUniqueId());
        if (gamePlayer == null)
            return 0;
        Team currentTeam = gamePlayer.getTeam();
        Territory territory = currentTeam.getTerritory();
        if (!territory.isInside(player.getLocation())) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You must be inside your team's claims to use /kit load.");
            return 0;
        }
        if (this.loadDefault) {
            player.getInventory().setContents(getDefaultPvpKit(worldGame));
            player.updateInventory();
            player.sendMessage(String.valueOf(ChatColor.GREEN) + "Default PvP kit loaded successfully!");
            putOnCooldown(player.getUniqueId());
            return 1;
        }
        Kit kit = this.plugin.getGameManager().getKitController().getKit(player.getUniqueId(), worldGame.getMapIdentifier());
        if (kit == null) {
            player.sendMessage(String.valueOf(ChatColor.RED) + "You don't have a kit saved for this map. Try /kit load default to load the PvP kit.");
            return 0;
        }
        player.getInventory().setContents((ItemStack[])kit.getContents().toArray((Object[])new ItemStack[0]));
        player.updateInventory();
        player.sendMessage(String.valueOf(ChatColor.GREEN) + "Kit loaded successfully!");
        putOnCooldown(player.getUniqueId());
        return 1;
    }

    private boolean isOnCooldown(UUID uuid) {
        if (!this.cooldowns.containsKey(uuid))
            return false;
        long lastTime = ((Long)this.cooldowns.get(uuid)).longValue();
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastTime < 15000L);
    }

    private void putOnCooldown(UUID uuid) {
        this.cooldowns.put(uuid, Long.valueOf(System.currentTimeMillis()));
    }

    private long getCooldown(UUID uuid) {
        if (!this.cooldowns.containsKey(uuid))
            return 0L;
        long lastTime = ((Long)this.cooldowns.get(uuid)).longValue();
        long currentTime = System.currentTimeMillis();
        return (15000L - currentTime - lastTime) / 1000L;
    }

    private ItemStack[] getDefaultPvpKit(WorldGame worldGame) {
        ItemStack[] contents = new ItemStack[41];
        Function<String, ItemStack> shop = id -> {
            ShopItem si = worldGame.getShopGUI().getItem(id);
            return (si != null) ? si.getDisplayItem() : null;
        };
        contents[36] = shop.apply("diamond_boots");
        contents[37] = shop.apply("diamond_leggings");
        contents[38] = shop.apply("diamond_chestplate");
        contents[39] = shop.apply("diamond_helmet");
        contents[0] = shop.apply("diamond_sword");
        contents[1] = (shop.apply("golden_carrot") == null) ? new ItemStack(Material.GOLDEN_CARROT, 64) : shop.apply("golden_carrot");
        contents[2] = shop.apply("ender_pearl");
        contents[3] = shop.apply("diamond_pickaxe");
        contents[4] = shop.apply("speed_potion");
        contents[5] = shop.apply("fire_res");
        int idx = 9;
        for (int i = 0; i < 4; i++)
            contents[idx++] = shop.apply("speed_potion");
        contents[idx++] = shop.apply("fire_res");
        ItemStack heal = shop.apply("health_potion");
        for (int j = 0; j < contents.length; j++) {
            if (contents[j] == null)
                contents[j] = (heal != null) ? heal.clone() : null;
        }
        return contents;
    }
}
