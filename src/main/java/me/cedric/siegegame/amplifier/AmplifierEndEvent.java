package me.cedric.siegegame.amplifier;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AmplifierEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final AmplifierType amplifierType;

    
    public AmplifierEndEvent(AmplifierType amplifierType) {
        this.amplifierType = amplifierType;
    }

    
    public AmplifierType getAmplifierType() {
        return amplifierType;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}


