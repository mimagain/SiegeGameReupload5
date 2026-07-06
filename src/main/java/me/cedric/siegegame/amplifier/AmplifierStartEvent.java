package me.cedric.siegegame.amplifier;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AmplifierStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final AmplifierType amplifierType;
    private final long durationTicks;

    
    public AmplifierStartEvent(AmplifierType amplifierType, long durationTicks) {
        this.amplifierType = amplifierType;
        this.durationTicks = durationTicks;
    }

    
    public AmplifierType getAmplifierType() {
        return amplifierType;
    }

    
    public long getDurationTicks() {
        return durationTicks;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}


