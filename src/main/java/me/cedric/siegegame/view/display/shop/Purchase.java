package me.cedric.siegegame.view.display.shop;

import me.cedric.siegegame.model.player.GamePlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.bukkit.event.inventory.ClickType;

public interface Purchase extends BiConsumer<GamePlayer, ClickType> {
}



