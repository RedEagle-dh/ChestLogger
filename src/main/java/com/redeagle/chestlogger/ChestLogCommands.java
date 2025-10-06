package com.redeagle.chestlogger;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ChestLogCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("chestlog")
                            .requires(source -> source.hasPermissionLevel(2)) // Nur für OPs (Permission Level 2+)
                            .then(literal("query")
                                    .then(argument("player", StringArgumentType.string())
                                            .executes(ChestLogCommands::queryPlayer)))
                            .then(literal("recent")
                                    .executes(ctx -> recentLogs(ctx, 10))
                                    .then(argument("count", IntegerArgumentType.integer(1, 100))
                                            .executes(ctx -> recentLogs(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                            .then(literal("at")
                                    .then(argument("pos", BlockPosArgumentType.blockPos())
                                            .executes(ChestLogCommands::logsAtPosition)))
                            .then(literal("here")
                                    .executes(ChestLogCommands::logsHere))
                            .then(literal("clear")
                                    .executes(ChestLogCommands::clearAllLogs))
                            .then(literal("clearold")
                                    .then(argument("days", IntegerArgumentType.integer(1, 365))
                                            .executes(ChestLogCommands::clearOldLogs)))
                            .then(literal("stats")
                                    .executes(ChestLogCommands::showStats))
            );
        });
    }

    private static int queryPlayer(CommandContext<ServerCommandSource> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        ChestLogManager manager = Chestlogger.getLogManager();

        if (manager == null) {
            ctx.getSource().sendError(Text.literal("Log-Manager nicht verfügbar!"));
            return 0;
        }

        List<ChestAccessLog> logs = manager.getLogsByPlayer(playerName);

        if (logs.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Keine Logs für Spieler: " + playerName), false);
            return 0;
        }

        ctx.getSource().sendFeedback(() -> Text.literal("=== Chest-Logs für " + playerName + " (" + logs.size() + " Einträge) ==="), false);
        for (ChestAccessLog log : logs) {
            ctx.getSource().sendFeedback(() -> Text.literal(log.toString()), false);
        }

        return logs.size();
    }

    private static int recentLogs(CommandContext<ServerCommandSource> ctx, int count) {
        ChestLogManager manager = Chestlogger.getLogManager();

        if (manager == null) {
            ctx.getSource().sendError(Text.literal("Log-Manager nicht verfügbar!"));
            return 0;
        }

        List<ChestAccessLog> logs = manager.getRecentLogs(count);

        if (logs.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("Keine Logs vorhanden."), false);
            return 0;
        }

        ctx.getSource().sendFeedback(() -> Text.literal("=== Letzte " + logs.size() + " Chest-Zugriffe ==="), false);
        for (ChestAccessLog log : logs) {
            ctx.getSource().sendFeedback(() -> Text.literal(log.toString()), false);
        }

        return logs.size();
    }

    private static int logsAtPosition(CommandContext<ServerCommandSource> ctx) {
        try {
            BlockPos pos = BlockPosArgumentType.getBlockPos(ctx, "pos");
            String dimension = ctx.getSource().getWorld().getRegistryKey().getValue().toString();
            ChestLogManager manager = Chestlogger.getLogManager();

            if (manager == null) {
                ctx.getSource().sendError(Text.literal("Log-Manager nicht verfügbar!"));
                return 0;
            }

            List<ChestAccessLog> logs = manager.getLogsByPosition(pos, dimension);

            if (logs.isEmpty()) {
                ctx.getSource().sendFeedback(() -> Text.literal("Keine Logs für Position: " + pos), false);
                return 0;
            }

            ctx.getSource().sendFeedback(() -> Text.literal("=== Chest-Logs bei " + pos + " (" + logs.size() + " Einträge) ==="), false);
            for (ChestAccessLog log : logs) {
                ctx.getSource().sendFeedback(() -> Text.literal(log.toString()), false);
            }

            return logs.size();
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Fehler beim Abrufen der Position: " + e.getMessage()));
            return 0;
        }
    }

    private static int logsHere(CommandContext<ServerCommandSource> ctx) {
        try {
            if (ctx.getSource().getEntity() == null) {
                ctx.getSource().sendError(Text.literal("Dieser Befehl kann nur von einem Spieler ausgeführt werden!"));
                return 0;
            }

            // Get player position and check the block they're standing on or looking at
            BlockPos playerPos = ctx.getSource().getEntity().getBlockPos();
            String dimension = ctx.getSource().getWorld().getRegistryKey().getValue().toString();

            // Check block below player (if standing on container)
            BlockPos checkPos = playerPos.down();

            ChestLogManager manager = Chestlogger.getLogManager();
            if (manager == null) {
                ctx.getSource().sendError(Text.literal("Log-Manager nicht verfügbar!"));
                return 0;
            }

            List<ChestAccessLog> logs = manager.getLogsByPosition(checkPos, dimension);

            // If no logs at feet, check surrounding blocks
            if (logs.isEmpty()) {
                for (BlockPos offset : new BlockPos[]{
                        playerPos,
                        playerPos.north(),
                        playerPos.south(),
                        playerPos.east(),
                        playerPos.west()
                }) {
                    logs = manager.getLogsByPosition(offset, dimension);
                    if (!logs.isEmpty()) {
                        checkPos = offset;
                        break;
                    }
                }
            }

            if (logs.isEmpty()) {
                ctx.getSource().sendFeedback(() -> Text.literal("Keine Container in deiner Nähe gefunden oder keine Logs vorhanden."), false);
                return 0;
            }

            BlockPos finalCheckPos = checkPos;
            int finalLogCount = logs.size();
            ctx.getSource().sendFeedback(() -> Text.literal("=== Chest-Logs bei " + finalCheckPos + " (" + finalLogCount + " Einträge) ==="), false);
            for (ChestAccessLog log : logs) {
                ctx.getSource().sendFeedback(() -> Text.literal(log.toString()), false);
            }

            return logs.size();
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Fehler: " + e.getMessage()));
            return 0;
        }
    }

    private static int clearAllLogs(CommandContext<ServerCommandSource> ctx) {
        ChestLogManager manager = Chestlogger.getLogManager();

        if (manager == null) {
            ctx.getSource().sendError(Text.literal("Log-Manager nicht verfügbar!"));
            return 0;
        }

        int count = manager.getLogCount();
        manager.clearAllLogs();
        ctx.getSource().sendFeedback(() -> Text.literal("Alle " + count + " Chest-Logs wurden gelöscht."), true);

        return 1;
    }

    private static int clearOldLogs(CommandContext<ServerCommandSource> ctx) {
        int days = IntegerArgumentType.getInteger(ctx, "days");
        ChestLogManager manager = Chestlogger.getLogManager();

        if (manager == null) {
            ctx.getSource().sendError(Text.literal("Log-Manager nicht verfügbar!"));
            return 0;
        }

        long cutoffTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
        int beforeCount = manager.getLogCount();
        manager.clearOldLogs(cutoffTimestamp);
        int afterCount = manager.getLogCount();
        int deletedCount = beforeCount - afterCount;

        ctx.getSource().sendFeedback(() -> Text.literal(deletedCount + " Logs älter als " + days + " Tage wurden gelöscht."), true);

        return deletedCount;
    }

    private static int showStats(CommandContext<ServerCommandSource> ctx) {
        ChestLogManager manager = Chestlogger.getLogManager();

        if (manager == null) {
            ctx.getSource().sendError(Text.literal("Log-Manager nicht verfügbar!"));
            return 0;
        }

        int totalLogs = manager.getLogCount();
        ctx.getSource().sendFeedback(() -> Text.literal("=== Chest Logger Statistiken ==="), false);
        ctx.getSource().sendFeedback(() -> Text.literal("Gesamt Logs: " + totalLogs), false);

        return 1;
    }
}
