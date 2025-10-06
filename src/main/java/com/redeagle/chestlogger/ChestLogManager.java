package com.redeagle.chestlogger;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.level.storage.LevelStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ChestLogManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestLogger");
    private static final String LOG_FILE_NAME = "chest_logs.dat";

    private final File logFile;
    private final List<ChestAccessLog> logs;

    public ChestLogManager(MinecraftServer server) {
        Path worldDir = server.getRunDirectory();
        Path logPath = worldDir.resolve(LOG_FILE_NAME);
        this.logFile = logPath.toFile();

        LOGGER.info("ChestLogManager initialized with log file: {}", logFile.getAbsolutePath());

        this.logs = new ArrayList<>();
        loadLogs();
    }

    public void addLog(ChestAccessLog log) {
        logs.add(log);
        saveLogs();
    }

    public List<ChestAccessLog> getAllLogs() {
        return new ArrayList<>(logs);
    }

    public List<ChestAccessLog> getLogsByPlayer(String playerName) {
        return logs.stream()
                .filter(log -> log.getPlayerName().equalsIgnoreCase(playerName))
                .collect(Collectors.toList());
    }

    public List<ChestAccessLog> getLogsByPosition(BlockPos pos, String dimension) {
        return logs.stream()
                .filter(log -> log.getPosition().equals(pos) && log.getDimension().equals(dimension))
                .collect(Collectors.toList());
    }

    public List<ChestAccessLog> getRecentLogs(int count) {
        int size = logs.size();
        int startIndex = Math.max(0, size - count);
        return new ArrayList<>(logs.subList(startIndex, size));
    }

    public void clearOldLogs(long olderThanTimestamp) {
        logs.removeIf(log -> log.getTimestamp() < olderThanTimestamp);
        saveLogs();
        LOGGER.info("Cleared old chest logs older than timestamp: {}", olderThanTimestamp);
    }

    public void clearAllLogs() {
        logs.clear();
        saveLogs();
        LOGGER.info("Cleared all chest logs");
    }

    private void loadLogs() {
        if (!logFile.exists()) {
            LOGGER.info("No existing chest log file found. Starting fresh.");
            return;
        }

        try {
            NbtCompound rootTag = NbtIo.readCompressed(logFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
            if (rootTag.contains("Logs")) {
                rootTag.getList("Logs").ifPresent(logsList -> {
                    for (int i = 0; i < logsList.size(); i++) {
                        logsList.getCompound(i).ifPresent(logTag ->
                            logs.add(ChestAccessLog.fromNBT(logTag))
                        );
                    }
                });
            }

            LOGGER.info("Loaded {} chest access logs", logs.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load chest logs", e);
        }
    }

    private void saveLogs() {
        try {
            NbtCompound rootTag = new NbtCompound();
            NbtList logsList = new NbtList();

            for (ChestAccessLog log : logs) {
                logsList.add(log.toNBT());
            }

            rootTag.put("Logs", logsList);

            // Ensure parent directory exists
            File parentDir = logFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            NbtIo.writeCompressed(rootTag, logFile.toPath());
        } catch (IOException e) {
            LOGGER.error("Failed to save chest logs", e);
        }
    }

    public int getLogCount() {
        return logs.size();
    }
}
