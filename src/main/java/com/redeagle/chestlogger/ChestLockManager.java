package com.redeagle.chestlogger;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ChestLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestLogger");
    private static final String LOCK_FILE_NAME = "chest_locks.dat";

    private final File lockFile;
    private final Map<String, ChestLock> locks; // Key: "dimension:x:y:z"

    public ChestLockManager(MinecraftServer server) {
        Path worldDir = server.getRunDirectory();
        Path lockPath = worldDir.resolve(LOCK_FILE_NAME);
        this.lockFile = lockPath.toFile();

        LOGGER.info("ChestLockManager initialized with lock file: {}", lockFile.getAbsolutePath());

        this.locks = new HashMap<>();
        loadLocks();
    }

    public boolean lockChest(ServerPlayerEntity player, BlockPos pos, String dimension) {
        String key = getKey(pos, dimension);

        // Check if already locked
        if (locks.containsKey(key)) {
            return false;
        }

        // Check max locks per player (if configured)
        if (Config.maxLocksPerPlayer > 0) {
            long playerLockCount = locks.values().stream()
                    .filter(lock -> lock.isOwner(player.getUuid()))
                    .count();

            if (playerLockCount >= Config.maxLocksPerPlayer) {
                return false;
            }
        }

        ChestLock lock = new ChestLock(
                player.getUuid(),
                player.getName().getString(),
                pos,
                dimension,
                System.currentTimeMillis()
        );

        locks.put(key, lock);
        saveLocks();
        LOGGER.info("Player {} locked chest at {}", player.getName().getString(), pos);
        return true;
    }

    public boolean unlockChest(BlockPos pos, String dimension) {
        String key = getKey(pos, dimension);
        ChestLock removed = locks.remove(key);

        if (removed != null) {
            saveLocks();
            LOGGER.info("Unlocked chest at {}", pos);
            return true;
        }

        return false;
    }

    public boolean isLocked(BlockPos pos, String dimension) {
        return locks.containsKey(getKey(pos, dimension));
    }

    public ChestLock getLock(BlockPos pos, String dimension) {
        return locks.get(getKey(pos, dimension));
    }

    public boolean canAccess(ServerPlayerEntity player, BlockPos pos, String dimension) {
        // Admins can always access
        if (player.hasPermissionLevel(2)) {
            return true;
        }

        ChestLock lock = getLock(pos, dimension);
        if (lock == null) {
            return true; // Not locked
        }

        return lock.isOwner(player.getUuid());
    }

    public List<ChestLock> getPlayerLocks(UUID playerUUID) {
        return locks.values().stream()
                .filter(lock -> lock.isOwner(playerUUID))
                .collect(Collectors.toList());
    }

    public List<ChestLock> getAllLocks() {
        return new ArrayList<>(locks.values());
    }

    public int getLockCount() {
        return locks.size();
    }

    private String getKey(BlockPos pos, String dimension) {
        return String.format("%s:%d:%d:%d", dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    private void loadLocks() {
        if (!lockFile.exists()) {
            LOGGER.info("No existing lock file found. Starting fresh.");
            return;
        }

        try {
            NbtCompound rootTag = NbtIo.readCompressed(lockFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
            if (rootTag.contains("Locks")) {
                rootTag.getList("Locks").ifPresent(locksList -> {
                    for (int i = 0; i < locksList.size(); i++) {
                        locksList.getCompound(i).ifPresent(lockTag -> {
                            ChestLock lock = ChestLock.fromNBT(lockTag);
                            String key = getKey(lock.getPosition(), lock.getDimension());
                            locks.put(key, lock);
                        });
                    }
                });
            }

            LOGGER.info("Loaded {} chest locks", locks.size());
        } catch (IOException e) {
            LOGGER.error("Failed to load chest locks", e);
        }
    }

    private void saveLocks() {
        try {
            NbtCompound rootTag = new NbtCompound();
            NbtList locksList = new NbtList();

            for (ChestLock lock : locks.values()) {
                locksList.add(lock.toNBT());
            }

            rootTag.put("Locks", locksList);

            // Ensure parent directory exists
            File parentDir = lockFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            NbtIo.writeCompressed(rootTag, lockFile.toPath());
        } catch (IOException e) {
            LOGGER.error("Failed to save chest locks", e);
        }
    }

    public void flush() {
        saveLocks();
    }
}
