package net.deechael.minedleaderboard;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class MinedLeaderboard extends JavaPlugin implements Listener {

    private static CommandMap BUKKIT_COMMAND_MAP;
    private Sqlite mined;
    private Scoreboard vanillaScoreboard;
    private Scoreboard scoreboard;
    private Objective objective;

    public static MinedLeaderboard getInstance() {
        return JavaPlugin.getPlugin(MinedLeaderboard.class);
    }

    private static CommandSender getBukkitSender(Object commandListenerWrapper) {
        return ((CommandSender) Ref.invoke(commandListenerWrapper, Ref.getMethod(Ref.getNmsOrOld("commands.CommandListenerWrapper", "CommandListenerWrapper"), "getBukkitSender")));
    }

    private static void setDispatcher(Command vanillaListenerWrapper, Object nmsCommandDispatcher) {
        try {
            Field field = Ref.getObcClass("command.VanillaCommandWrapper").getDeclaredField("dispatcher");
            field.setAccessible(true);
            field.set(vanillaListenerWrapper, nmsCommandDispatcher);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static <T> LiteralCommandNode<?> clone(String prefix, CommandNode commandNode, Class<T> clazz) {
        return new LiteralCommandNode<T>(prefix.toLowerCase() + ":" + commandNode.getName().toLowerCase(), commandNode.getCommand(), commandNode.getRequirement(), commandNode.getRedirect(), commandNode.getRedirectModifier(), commandNode.isFork());
    }

    private static <T> LiteralCommandNode<?> clone(CommandNode commandNode, Class<T> clazz) {
        return new LiteralCommandNode<T>(commandNode.getName().toLowerCase(), commandNode.getCommand(), commandNode.getRequirement(), commandNode.getRedirect(), commandNode.getRedirectModifier(), commandNode.isFork());
    }

    private static Class<?> CommandListenerWrapper() {
        if (Ref.getVersion() <= 15 && Ref.getVersion() >= 9) {
            return Ref.getNmsClass("CommandListenerWrapper");
        } else {
            return Ref.getClass("net.minecraft.commands.CommandListenerWrapper");
        }
    }

    private static void registerToCommandPatcher(Object nmsCommandDispatcher, CommandNode<Object> commandNode) {
        CommandDispatcher<Object> commandDispatcher = getMojangCommandDispatcher(nmsCommandDispatcher);
        commandDispatcher.getRoot().addChild(commandNode);
    }

    private static Map<String, Command> getKnownCommands() {
        if (BUKKIT_COMMAND_MAP == null) {
            try {
                BUKKIT_COMMAND_MAP = (CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
                return null;
            }
        }
        try {
            Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
            field.setAccessible(true);
            return (Map<String, Command>) field.get(BUKKIT_COMMAND_MAP);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void setKnownCommands(Map<String, Command> knownCommands) {
        try {
            Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
            field.setAccessible(true);
            field.set(BUKKIT_COMMAND_MAP, knownCommands);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static void removeFromRoot(RootCommandNode<Object> rootCommandNode, CommandNode<Object> commandNode) {
        try {
            Field field = CommandNode.class.getDeclaredField("children");
            field.setAccessible(true);
            Map<String, CommandNode<Object>> map = (Map<String, CommandNode<Object>>) field.get(rootCommandNode);
            for (String key : map.keySet()) {
                if (map.get(key).equals(commandNode)) {
                    map.remove(key);
                }
            }
            field.set(rootCommandNode, map);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static Command createVanillaCommandWrapper(Object nmsCommandDispatcher, CommandNode<Object> commandNode) {
        try {
            return (Command) Ref.getObcClass("command.VanillaCommandWrapper").getConstructor(nmsCommandDispatcher(), CommandNode.class).newInstance(nmsCommandDispatcher, commandNode);
        } catch (InstantiationException | InvocationTargetException | NoSuchMethodException |
                 IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static CommandDispatcher<Object> getMojangCommandDispatcher(Object nmsCommandDispatcher) {
        try {
            return (CommandDispatcher<Object>) nmsCommandDispatcher().getMethod("a").invoke(nmsCommandDispatcher);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Class<?> nmsCommandDispatcher() {
        if (Ref.getVersion() <= 15 && Ref.getVersion() >= 9) {
            return Ref.getNmsClass("CommandDispatcher");
        } else {
            return Ref.getClass("net.minecraft.commands.CommandDispatcher");
        }
    }

    private static Object getVanillaCommandDispatcher() {
        Object minecraftServer = getMinecraftServer();
        try {
            return MinecraftServer().getField("vanillaCommandDispatcher").get(minecraftServer);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Object getBukkitCommandDispatcher() {
        Object minecraftServer = getMinecraftServer();
        try {
            return MinecraftServer().getMethod("aC").invoke(minecraftServer);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Class<?> MinecraftServer() {
        return Ref.getNmsOrOld("server.MinecraftServer", "MinecraftServer");
    }

    private static Object getMinecraftServer() {
        try {
            return Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    @EventHandler
    public void onBreakingBlock(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (mined(world, x, y, z))
            return;
        addMined(world, x, y, z);
        if (!block.getType().isSolid())
            return;
        this.addScore(event.getPlayer().getUniqueId(), 1);
        this.objective.getScore(event.getPlayer().getName()).setScore(this.getScore(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onPlacingBlock(BlockPlaceEvent event) {
        Location location = event.getBlock().getLocation();
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (!mined(world, x, y, z))
            addMined(world, x, y, z);
    }

    @EventHandler
    public void onJoining(PlayerJoinEvent event) {
        if (shouldShowScoreboard(event.getPlayer().getUniqueId()))
            event.getPlayer().setScoreboard(this.scoreboard);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.getPlayer().setScoreboard(this.vanillaScoreboard);
    }

    // Command reflection

    @Override
    public void onEnable() {
        File dataFolder = this.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        sqliteCheck(mined);
        mined = new Sqlite(new File(this.getDataFolder(), "mined.db"));
        mined.executeUpdate("CREATE TABLE IF NOT EXISTS `player` ( `uuid` TEXT, `amount` BIGINT);");
        mined.executeUpdate("CREATE TABLE IF NOT EXISTS `scoreboard` ( `uuid` TEXT, `if_or_not` INT);");
        mined.executeUpdate("CREATE TABLE IF NOT EXISTS `mined` ( `world` TEXT, `loc_x` BIGINT , `loc_y` BIGINT , `loc_z` BIGINT);");
        ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
        if (scoreboardManager != null) {
            this.vanillaScoreboard = scoreboardManager.getNewScoreboard();
            this.vanillaScoreboard.registerNewObjective("minedleaderboard", "dummy", "Null").setDisplaySlot(DisplaySlot.SIDEBAR);
            this.scoreboard = scoreboardManager.getNewScoreboard();
            this.objective = this.scoreboard.getObjective("minedleaderboard");
            if (this.objective == null)
                this.objective = this.scoreboard.registerNewObjective("minedleaderboard", "dummy", "§b§l挖掘方块");
            for (Map.Entry<UUID, Integer> entry : getScoreAll().entrySet()) {
                this.objective.getScore(Objects.requireNonNull(Bukkit.getOfflinePlayer(entry.getKey()).getName())).setScore(entry.getValue());
            }
            this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldShowScoreboard(player.getUniqueId()))
                continue;
            player.setScoreboard(this.scoreboard);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        if (BUKKIT_COMMAND_MAP == null) {
            try {
                BUKKIT_COMMAND_MAP = (CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MinerLeaderboardPlaceholder().register();
        }
        // This is the part to get the scores might be stored by CoreProtect but not be stored by MinerLeaderboard
        /*
        if (Bukkit.getPluginManager().isPluginEnabled("CoreProtect")) {
            CoreProtectAPI cp = getCoreProtect();
            if (cp != null) {
                cp.testAPI();
                List<String> users = Bukkit.getWhitelistedPlayers().stream().map(OfflinePlayer::getName).toList();
                List<String[]> _________ = cp.performLookup(365 * 24 * 60 * 60, users, null, null, null, null, 0, null);
                List<CoreProtectAPI.ParseResult> resultList = _________.stream().map(cp::parseResult).toList();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Map<Vec3D, List<CoreProtectAPI.ParseResult>> checks = new HashMap<>();
                        for (CoreProtectAPI.ParseResult result : resultList) {
                            Vec3D vec = new Vec3D(result.worldName(), result.getX(), result.getY(), result.getZ());
                            if (mined(vec))
                                continue;
                            if (!checks.containsKey(vec))
                                checks.put(vec, new ArrayList<>());
                            checks.get(vec).add(result);
                        }
                        Map<UUID, Integer> scores = new HashMap<>();
                        for (Map.Entry<Vec3D, List<CoreProtectAPI.ParseResult>> entry : checks.entrySet()) {
                            Vec3D location = entry.getKey();
                            List<CoreProtectAPI.ParseResult> results = entry.getValue();
                            results.sort(Comparator.comparingLong(CoreProtectAPI.ParseResult::getTimestamp));
                            for (CoreProtectAPI.ParseResult result : results) {
                                if (Objects.equals(result.getActionString(), "place")) {
                                    addMined(location);
                                    break;
                                }
                                if (Objects.equals(result.getActionString(), "break")) {
                                    addMined(location);
                                    UUID uuid = Bukkit.getOfflinePlayer(result.getPlayer()).getUniqueId();
                                    if (scores.containsKey(uuid)) {
                                        scores.put(uuid, scores.get(uuid) + 1);
                                    } else {
                                        scores.put(uuid, 1);
                                    }
                                    break;
                                }
                            }
                        }
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                for (Map.Entry<UUID, Integer> entry : scores.entrySet()) {
                                    MinedLeaderboard.this.addScore(entry.getKey(), entry.getValue());
                                }
                            }
                        }.runTask(MinedLeaderboard.this);
                    }
                }.runTaskAsynchronously(this);
            }
        }
        */

        Bukkit.getPluginManager().addPermission(new Permission("minedleaderboard.command.mined", PermissionDefault.TRUE));
        Bukkit.getPluginManager().addPermission(new Permission("minedleaderboard.command.leaderboard", PermissionDefault.TRUE));

        LiteralCommandNode<?> minedCommandNode = LiteralArgumentBuilder.literal("mined")
                .requires(o -> {
                    CommandSender sender = getBukkitSender(o);
                    return sender.hasPermission("minedleaderboard.command.mined");
                })
                .executes(context -> {
                    CommandSender sender = getBukkitSender(context.getSource());
                    if (sender instanceof Player player) {
                        player.sendMessage("§a§l(!) §r§a你的挖掘分数为 §r§e" + getScore(player.getUniqueId()));
                    } else {
                        sender.sendMessage("§c§l(!) §r§c你必须是一名玩家！");
                    }
                    return 1;
                }).then(LiteralArgumentBuilder.literal("scoreboard").executes(context -> {
                    CommandSender sender = getBukkitSender(context.getSource());
                    if (sender instanceof Player player) {
                        if (player.getScoreboard() == this.scoreboard) {
                            player.setScoreboard(this.vanillaScoreboard);
                            setScoreboard(player.getUniqueId(), false);
                            player.sendMessage("§a§l(!) §r§a计分板 §r§e已关闭");
                        } else {
                            player.setScoreboard(this.scoreboard);
                            setScoreboard(player.getUniqueId(), true);
                            player.sendMessage("§a§l(!) §r§a计分板 §r§e已开启");
                        }
                    } else {
                        sender.sendMessage("§c§l(!) §r§c你必须是一名玩家！");
                    }
                    return 1;
                })).build();

        LiteralCommandNode<?> leaderboardCommandNode = LiteralArgumentBuilder.literal("leaderboard")
                .requires(o -> {
                    CommandSender sender = getBukkitSender(o);
                    return sender.hasPermission("minedleaderboard.command.leaderboard");
                })
                .executes(context -> {
                    CommandSender sender = getBukkitSender(context.getSource());
                    List<Map.Entry<UUID, Integer>> all = getScoreAll().entrySet().stream().sorted((p2, p1) -> p1.getValue().compareTo(p2.getValue())).toList();
                    int pages = all.size() % 15 == 0 ? all.size() / 15 : all.size() / 15 + 1;
                    StringBuilder builder = new StringBuilder();
                    builder.append("§6§l========== [§r§e").append(1).append("§6§l/§r§e").append(pages).append("§6§l] ==========\n");
                    for (int i = 0; i < Math.min(15, all.size()); i++) {
                        Map.Entry<UUID, Integer> entry = all.get(i);
                        builder.append("§e").append(i).append(".").append(Bukkit.getOfflinePlayer(entry.getKey()).getName()).append(" §r§f- §r§e").append(entry.getValue()).append("\n");
                    }
                    builder.append("§6§l==========================");
                    if (pages > 10)
                        builder.append("=");
                    sender.sendMessage(builder.toString());
                    return 1;
                }).then(RequiredArgumentBuilder.argument("page", IntegerArgumentType.integer(1)).executes(context -> {
                    CommandSender sender = getBukkitSender(context.getSource());
                    int page = IntegerArgumentType.getInteger(context, "page");
                    List<Map.Entry<UUID, Integer>> all = getScoreAll().entrySet().stream().sorted((p2, p1) -> p1.getValue().compareTo(p2.getValue())).toList();
                    int pages = all.size() % 15 == 0 ? all.size() / 15 : all.size() / 15 + 1;
                    if (page > pages)
                        page = pages;
                    StringBuilder builder = new StringBuilder();
                    builder.append("§6§l========== [§r§e").append(page).append("§6§l/§r§e").append(pages).append("§6§l] ==========\n");
                    for (int i = 15 * (page - 1); i < Math.min(15 * page, all.size()); i++) {
                        Map.Entry<UUID, Integer> entry = all.get(i);
                        builder.append("§e").append(i).append(".").append(Bukkit.getOfflinePlayer(entry.getKey()).getName()).append(" §r§f- §r§e").append(entry.getValue()).append("\n");
                    }
                    builder.append("§6§l==========================");
                    if (pages > 10)
                        builder.append("=");
                    sender.sendMessage(builder.toString());
                    return 1;
                })).build();

        BUKKIT_COMMAND_MAP.register("minedleaderboard", createVanillaCommandWrapper(null, (CommandNode<Object>) minedCommandNode));
        BUKKIT_COMMAND_MAP.register("minedleaderboard", createVanillaCommandWrapper(null, (CommandNode<Object>) leaderboardCommandNode));

        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            Object commandDispatcher = getBukkitCommandDispatcher();
            Map<String, Command> knownCommands = getKnownCommands();
            if (knownCommands != null) {
                // Mined command
                Command minedCommand = knownCommands.get("mined");
                minedCommand.setPermission("minedleaderboard.command.mined");
                registerToCommandPatcher(commandDispatcher, (CommandNode<Object>) minedCommandNode);
                setDispatcher(minedCommand, commandDispatcher);
                knownCommands.put("mined", minedCommand);
                Command minedCommandPrefixed = knownCommands.get("minedleaderboard:mined");
                minedCommandPrefixed.setPermission("minedleaderboard.command.mined");
                LiteralCommandNode<?> literalCommandNode = clone("minedleaderboard", minedCommandNode, CommandListenerWrapper());
                registerToCommandPatcher(commandDispatcher, (CommandNode<Object>) literalCommandNode);
                setDispatcher(minedCommandPrefixed, commandDispatcher);
                knownCommands.put("minedleaderboard:mined", minedCommandPrefixed);

                // Leaderboard command
                Command leaderboardCommand = knownCommands.get("leaderboard");
                leaderboardCommand.setPermission("minedleaderboard.command.leaderboard");
                registerToCommandPatcher(commandDispatcher, (CommandNode<Object>) leaderboardCommandNode);
                setDispatcher(leaderboardCommand, commandDispatcher);
                knownCommands.put("leaderboard", leaderboardCommand);
                Command leaderboardCommandPrefixed = knownCommands.get("minedleaderboard:leaderboard");
                leaderboardCommandPrefixed.setPermission("minedleaderboard.command.leaderboard");
                LiteralCommandNode<?> literalCommandNode2 = clone("minedleaderboard", leaderboardCommandNode, CommandListenerWrapper());
                registerToCommandPatcher(commandDispatcher, (CommandNode<Object>) literalCommandNode2);
                setDispatcher(leaderboardCommandPrefixed, commandDispatcher);
                knownCommands.put("minedleaderboard:leaderboard", leaderboardCommandPrefixed);
            }
        });
    }

    @Override
    public void onDisable() {
        this.mined = sqliteCheck(mined);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(this.vanillaScoreboard);
        }
    }

    private CoreProtectAPI getCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");

        if (!(plugin instanceof CoreProtect)) {
            return null;
        }

        CoreProtectAPI CoreProtect = ((CoreProtect) plugin).getAPI();
        if (!CoreProtect.isEnabled()) {
            return null;
        }

        if (CoreProtect.APIVersion() < 9) {
            return null;
        }

        return CoreProtect;
    }

    private boolean mined(Vec3D vec) {
        return this.mined(vec.world(), vec.x(), vec.y(), vec.z());
    }

    private boolean mined(String world, int x, int y, int z) {
        PreparedStatement statement = this.mined.preparedStatement("SELECT * FROM `mined` WHERE world=? and loc_x=? and loc_y=? and loc_z=?;");
        try {
            statement.setString(1, world);
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            ResultSet resultSet = statement.executeQuery();
            boolean result = resultSet.next();
            resultSet.close();
            statement.close();
            return result;
        } catch (SQLException e) {
            return false;
        }
    }

    private void addMined(Vec3D vec) {
        this.addMined(vec.world(), vec.x(), vec.y(), vec.z());
    }

    private void addMined(String world, int x, int y, int z) {
        PreparedStatement statement = this.mined.preparedStatement("INSERT INTO `mined` (`world`, `loc_x`, `loc_y`, `loc_z`) VALUES (?, ?, ?, ?);");
        try {
            statement.setString(1, world);
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addScore(UUID player, int amount) {
        if (!hasScore(player)) {
            PreparedStatement statement = this.mined.preparedStatement("INSERT INTO `player` (`uuid`, `amount`) VALUES (?, ?);");
            try {
                statement.setString(1, player.toString());
                statement.setInt(2, amount);
                statement.executeUpdate();
                statement.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            PreparedStatement statement = this.mined.preparedStatement("UPDATE `player` SET amount=amount+? WHERE uuid=?;");
            try {
                statement.setInt(1, amount);
                statement.setString(2, player.toString());
                statement.executeUpdate();
                statement.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void setScore(UUID player, int amount) {
        if (!hasScore(player)) {
            PreparedStatement statement = this.mined.preparedStatement("INSERT INTO `player` (`uuid`, `amount`) VALUES (?, ?);");
            try {
                statement.setString(1, player.toString());
                statement.setInt(2, amount);
                statement.executeUpdate();
                statement.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            PreparedStatement statement = this.mined.preparedStatement("UPDATE `player` SET amount=? WHERE uuid=?;");
            try {
                statement.setInt(1, amount);
                statement.setString(2, player.toString());
                statement.executeUpdate();
                statement.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean hasScore(UUID player) {
        PreparedStatement statement = this.mined.preparedStatement("SELECT * FROM `player` WHERE uuid=?;");
        try {
            statement.setString(1, player.toString());
            ResultSet resultSet = statement.executeQuery();
            boolean result = resultSet.next();
            resultSet.close();
            statement.close();
            return result;
        } catch (SQLException e) {
            return false;
        }
    }

    public int getScore(UUID player) {
        if (hasScore(player)) {
            PreparedStatement statement = this.mined.preparedStatement("SELECT * FROM `player` WHERE uuid=?;");
            try {
                statement.setString(1, player.toString());
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    int result = resultSet.getInt("amount");
                    resultSet.close();
                    statement.close();
                    return result;
                }
                resultSet.close();
                statement.close();
            } catch (SQLException e) {
                return 0;
            }
        }
        return 0;
    }

    private Map<UUID, Integer> getScoreAll() {
        Map<UUID, Integer> map = new HashMap<>();
        try {
            ResultSet resultSet = this.mined.executeQuery("SELECT * FROM `player`;");
            while (resultSet.next()) {
                map.put(UUID.fromString(resultSet.getString("uuid")), resultSet.getInt("amount"));
            }
            resultSet.close();
        } catch (SQLException ignored) {
        }
        return map;
    }

    private void setScoreboard(UUID player, boolean show) {
        if (!hasScoreboard(player)) {
            PreparedStatement statement = this.mined.preparedStatement("INSERT INTO `scoreboard` (`uuid`, `if_or_not`) VALUES (?, ?);");
            try {
                statement.setString(1, player.toString());
                statement.setInt(2, show ? 1 : 0);
                statement.executeUpdate();
                statement.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else {
            PreparedStatement statement = this.mined.preparedStatement("UPDATE `scoreboard` SET if_or_not=? WHERE uuid=?;");
            try {
                statement.setInt(1, show ? 1 : 0);
                statement.setString(2, player.toString());
                statement.executeUpdate();
                statement.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean shouldShowScoreboard(UUID player) {
        if (hasScore(player)) {
            PreparedStatement statement = this.mined.preparedStatement("SELECT * FROM `scoreboard` WHERE uuid=?;");
            try {
                statement.setString(1, player.toString());
                ResultSet resultSet = statement.executeQuery();
                if (resultSet.next()) {
                    boolean result = resultSet.getInt("if_or_not") == 1;
                    resultSet.close();
                    statement.close();
                    return result;
                }
                resultSet.close();
                statement.close();
            } catch (SQLException e) {
                return false;
            }
        }
        return false;
    }

    private boolean hasScoreboard(UUID player) {
        if (hasScore(player)) {
            PreparedStatement statement = this.mined.preparedStatement("SELECT * FROM `scoreboard` WHERE uuid=?;");
            try {
                statement.setString(1, player.toString());
                ResultSet resultSet = statement.executeQuery();
                boolean result = resultSet.next();
                resultSet.close();
                statement.close();
                return result;
            } catch (SQLException e) {
                return false;
            }
        }
        return false;
    }

    private Sqlite sqliteCheck(@Nullable Sqlite sqlite) {
        if (sqlite != null && !sqlite.isClosed()) {
            sqlite.close();
        }
        return null;
    }

}
