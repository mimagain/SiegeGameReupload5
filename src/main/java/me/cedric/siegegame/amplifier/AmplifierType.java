package me.cedric.siegegame.amplifier;

import org.bukkit.Sound;

public enum AmplifierType {
    SUDDEN_DEATH("SUDDEN DEATH", "Kills worth 1000 points!", Sound.ENTITY_ENDER_DRAGON_GROWL),
    NOBODY_SAFE("NOBODY SAFE", "Claims disabled! Build anywhere!", Sound.ENTITY_WITHER_SPAWN),
    LAST_MAN_STANDING("LAST MAN STANDING", "No respawns for 120 seconds, last man standing earns his team points.", Sound.ENTITY_WARDEN_ROAR),
    WHO_IS_WHO("WHO IS WHO", "invisibility for everyone for 30 seconds!CAREFUL, FRIENDLY FIRE IS ON!", Sound.ENTITY_BAT_TAKEOFF),
    WE_SHARPER_NOW("WE SHARPER NOW!", "strength 1 for everyone!", Sound.BLOCK_BEACON_ACTIVATE),
    MOON("MOON", "Everyone gets a glass head!", Sound.BLOCK_AMETHYST_BLOCK_RESONATE),
    KING_OF_THE_HILL("KING OF THE HILL", "STAY IN THE ZONE TO WIN!", Sound.ITEM_GOAT_HORN_SOUND_1),
    WE_FASTER_NOW("WE FASTER NOW", "speed 1 for everyone", Sound.ENTITY_GHAST_SCREAM);
    private final String title;
    private final String subtitle;
    private final Sound sound;

    AmplifierType(String title, String subtitle, Sound sound) {
        this.title = title;
        this.subtitle = subtitle;
        this.sound = sound;
    }

    AmplifierType(String title, String subtitle) {
        this(title, subtitle, Sound.BLOCK_AMETHYST_BLOCK_RESONATE);
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public Sound getSound() {
        return sound;
    }
}
