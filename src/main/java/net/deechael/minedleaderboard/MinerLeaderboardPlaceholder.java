package net.deechael.minedleaderboard;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MinerLeaderboardPlaceholder extends PlaceholderExpansion {

    @Override
    @Nullable
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        return switch (identifier) {
            case "score" -> String.valueOf(MinedLeaderboard.getInstance().getScore(player.getUniqueId()));
            default -> null;
        };
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "dynamichat";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "DeeChael";
    }

    @Override
    @NotNull
    public String getVersion() {
        return "1.00.0";
    }

    @Override
    public @Nullable String getRequiredPlugin() {
        return MinedLeaderboard.getInstance().getName();
    }

}
