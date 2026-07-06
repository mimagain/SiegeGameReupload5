package me.cedric.siegegame.events;

import me.cedric.siegegame.model.player.GamePlayer;
import me.cedric.siegegame.model.teams.Team;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PlayerTeamChangeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final GamePlayer player;
    private final Team oldTeam;
    private final Team newTeam;

    public PlayerTeamChangeEvent(@NotNull GamePlayer player, @Nullable Team oldTeam, @Nullable Team newTeam) {
        this.player = player;
        this.oldTeam = oldTeam;
        this.newTeam = newTeam;
    }

    @NotNull
    public GamePlayer getPlayer() {
        return player;
    }

    @Nullable
    public Team getOldTeam() {
        return oldTeam;
    }

    @Nullable
    public Team getNewTeam() {
        return newTeam;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}



