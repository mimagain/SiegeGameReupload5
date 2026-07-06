package me.cedric.siegegame.util;

import me.cedric.siegegame.SiegeGamePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VoteSkipManager {

    private final SiegeGamePlugin plugin;
    private boolean isVoteActive = false;
    private int yesVotes;
    private int noVotes;
    private final Set<UUID> votedPlayers = new HashSet<>();
    private BukkitTask voteEndTask;
    private BukkitTask actionBarTask;

    // Vote settings
    private static final int VOTE_DURATION_SECONDS = 30;
    private static final int MAX_TIME_AFTER_GAME_START_SECONDS = 60;

    public VoteSkipManager(SiegeGamePlugin plugin) {
        this.plugin = plugin;
    }

    public void startVote(Player initiator) {
        if (isVoteActive) {
            initiator.sendMessage(Component.text("A voteskip is already in progress.", NamedTextColor.RED));
            return;
        }

        if (plugin.getGameManager().getCurrentMatch() == null) {
            initiator.sendMessage(Component.text("A game is not currently running.", NamedTextColor.RED));
            return;
        }

        long gameStartTime = plugin.getGameManager().getCurrentMatch().getMatchStartTime();
        long timeSinceStart = (System.currentTimeMillis() - gameStartTime) / 1000;

        if (timeSinceStart > MAX_TIME_AFTER_GAME_START_SECONDS) {
            initiator.sendMessage(Component.text("You can only start a voteskip within the first " + MAX_TIME_AFTER_GAME_START_SECONDS + " seconds of the game.", NamedTextColor.RED));
            return;
        }

        resetState();
        isVoteActive = true;

        Bukkit.broadcast(Component.text(initiator.getName() + " has initiated a vote to skip the map!", NamedTextColor.GOLD));

        final Title voteTitle = Title.title(
                Component.text("Voteskip Started!", NamedTextColor.AQUA),
                Component.text("Use /voteyes or /voteno", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        );
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.showTitle(voteTitle);
        }

        // The initiator automatically votes yes
        addYesVote(initiator);

        // Schedule the end of the vote
        voteEndTask = Bukkit.getScheduler().runTaskLater(plugin, this::endVote, VOTE_DURATION_SECONDS * 20L);

        // Schedule the action bar updater
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateActionBar, 0L, 20L); // Update every second
    }

    public void addYesVote(Player player) {
        if (!isVoteActive) {
            player.sendMessage(Component.text("There is no active voteskip.", NamedTextColor.RED));
            return;
        }
        if (votedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("You have already voted.", NamedTextColor.RED));
            return;
        }
        votedPlayers.add(player.getUniqueId());
        yesVotes++;
        player.sendMessage(Component.text("You voted YES.", NamedTextColor.GREEN));
    }

    public void addNoVote(Player player) {
        if (!isVoteActive) {
            player.sendMessage(Component.text("There is no active voteskip.", NamedTextColor.RED));
            return;
        }
        if (votedPlayers.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("You have already voted.", NamedTextColor.RED));
            return;
        }
        votedPlayers.add(player.getUniqueId());
        noVotes++;
        player.sendMessage(Component.text("You voted NO.", NamedTextColor.RED));
    }

    private void endVote() {
        if (!isVoteActive) {
            return;
        }

        if (yesVotes > noVotes) {
            Bukkit.broadcast(Component.text("» Voteskip Succeeded! (" + yesVotes + " to " + noVotes + ") Starting next map...", NamedTextColor.GREEN));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "siegegame start");
        } else {
            Bukkit.broadcast(Component.text("» Voteskip Failed! (" + yesVotes + " to " + noVotes + ") The current map will continue.", NamedTextColor.RED));
        }

        resetState();
    }

    private void updateActionBar() {
        if (!isVoteActive) return;

        Component actionBarComponent = Component.text()
                .append(Component.text("Voteskip: ", NamedTextColor.GOLD))
                .append(Component.text("Yes: " + yesVotes, NamedTextColor.GREEN))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("No: " + noVotes, NamedTextColor.RED))
                .build();

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendActionBar(actionBarComponent);
        }
    }


    public void resetState() {
        isVoteActive = false;
        yesVotes = 0;
        noVotes = 0;
        votedPlayers.clear();

        if (voteEndTask != null) {
            voteEndTask.cancel();
            voteEndTask = null;
        }
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendActionBar(Component.empty());
        }
    }
}