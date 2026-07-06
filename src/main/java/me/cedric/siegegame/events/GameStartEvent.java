package me.cedric.siegegame.events;

import me.cedric.siegegame.model.SiegeGameMatch;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class GameStartEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final SiegeGameMatch match;

    public GameStartEvent(SiegeGameMatch match) {
        this.match = match;
    }

    public SiegeGameMatch getMatch() {
        return match;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}



