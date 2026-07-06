package me.cedric.siegegame.view.display.shop;

import me.cedric.siegegame.SiegeGamePlugin;
import me.cedric.siegegame.model.teams.Team;
import me.cedric.siegegame.model.player.GamePlayer;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public interface Buyable {

    ItemStack getDisplayItem();

    Purchase getPurchase();

    int getPrice();

    default boolean handlePurchase(GamePlayer gamePlayer, ClickType clickType) {
        Player player = gamePlayer.getBukkitPlayer();
        SiegeGamePlugin plugin = gamePlayer.getPlugin(); 

        plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Called for item " + getDisplayItem().getType() + " by " + player.getName() + ". Price: " + getPrice());

        if (!gamePlayer.hasTeam()) {
            player.sendMessage(ChatColor.RED + "You need to be in a team to buy items");
            plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Condition failed - Player not in a team. Returning false.");
            return false;
        }
        plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Condition passed - Player has team: " + gamePlayer.getTeam().getName());


        Team team = gamePlayer.getTeam();
        boolean isInClaims = team.getTerritory().isInside(player.getLocation()); 
        plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Checking claims. Player location: " + player.getLocation().toString() + ". Is inside " + team.getName() + " claims: " + isInClaims);
        if (!isInClaims) {
            player.sendMessage(ChatColor.RED + "You need to be inside claims to do this");
            plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Condition failed - Player not in claims. Returning false.");
            return false;
        }
        plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Condition passed - Player is in claims.");


        if (gamePlayer.isDead()) {
            player.sendMessage(ChatColor.RED + "You cannot do this while dead");
            plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Condition failed - Player is dead. Returning false.");
            return false;
        }
        plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Condition passed - Player is not dead.");


        if (player.getLevel() < getPrice()) {
            player.sendMessage(ChatColor.RED + "You do not have enough levels to buy this. Price: " + getPrice() + ", Player Levels: " + player.getLevel());
            plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Condition failed - Not enough levels. Price=" + getPrice() + ", PlayerLevels=" + player.getLevel() + ". Returning false.");
            return false;
        }
        if (player.getLevel() >= getPrice() && (player.getInventory().firstEmpty() != -1 || player.getInventory().contains(getDisplayItem()) )) {
            int oldlevel = player.getLevel();
            player.setLevel(oldlevel - getPrice());
        }
        plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: Condition passed - Player has enough levels (or item is free). Price=" + getPrice() + ", PlayerLevels=" + player.getLevel());


        plugin.getLogger().info("[ShopDebug] Buyable.handlePurchase: All preliminary checks passed.");

        getPurchase().accept(gamePlayer, clickType);
        return true;
    }

}



