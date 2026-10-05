package vice.sol_valheim;

import net.minecraft.ChatFormatting;

import java.util.Locale;

public enum DietCategory {
    COMBAT("combat", "Warrior", "Warriors Diet", ChatFormatting.RED, "cmobs.diet.Warrior"),
    BUILDING("building", "Builder", "Builders Diet", ChatFormatting.AQUA, "cmobs.diet.Builder"),
    MINING("mining", "Miner", "Miners Diet", ChatFormatting.GOLD, "cmobs.diet.Miner"),
    ADVENTURING("adventuring", "Wayfarer", "Wayfarers Diet", ChatFormatting.GREEN, "cmobs.diet.Wayfarer");

    public final String id;
    public final String singularName;
    public final String tooltipName;
    public final ChatFormatting color;
    public final String legacyScoreboard;

    DietCategory(String id, String singularName, String tooltipName, ChatFormatting color, String legacyScoreboard) {
        this.id = id;
        this.singularName = singularName;
        this.tooltipName = tooltipName;
        this.color = color;
        this.legacyScoreboard = legacyScoreboard;
    }

    public static DietCategory fromId(String id) {
        if (id == null)
            return null;

        String normalized = id.toLowerCase(Locale.ROOT);
        for (DietCategory value : values()) {
            if (value.id.equals(normalized))
                return value;
        }
        return null;
    }
}
