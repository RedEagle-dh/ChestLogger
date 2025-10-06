package com.redeagle.chestlogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestLogger");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Config values
    public static int logRetentionDays = 30;
    public static boolean trackChests = true;
    public static boolean trackBarrels = true;
    public static boolean trackShulkerBoxes = true;
    public static boolean trackEnderChests = false;
    public static boolean trackHoppers = false;

    // Chest locking config
    public static boolean enableChestLocking = true;
    public static int maxLocksPerPlayer = -1; // -1 = unlimited
    public static boolean lockProtectFromFire = true;
    public static boolean lockProtectFromTNT = true;
    public static boolean lockProtectFromMobs = true;

    public static void load(Path configPath) {
        Path configFile = configPath.resolve("chestlogger.json");

        if (Files.exists(configFile)) {
            try {
                String json = Files.readString(configFile);
                ConfigData data = GSON.fromJson(json, ConfigData.class);

                logRetentionDays = data.logRetentionDays;
                trackChests = data.trackChests;
                trackBarrels = data.trackBarrels;
                trackShulkerBoxes = data.trackShulkerBoxes;
                trackEnderChests = data.trackEnderChests;
                trackHoppers = data.trackHoppers;

                enableChestLocking = data.enableChestLocking;
                maxLocksPerPlayer = data.maxLocksPerPlayer;
                lockProtectFromFire = data.lockProtectFromFire;
                lockProtectFromTNT = data.lockProtectFromTNT;
                lockProtectFromMobs = data.lockProtectFromMobs;

                LOGGER.info("Config loaded successfully");
            } catch (IOException e) {
                LOGGER.error("Failed to load config, using defaults", e);
                save(configPath);
            }
        } else {
            LOGGER.info("Config file not found, creating with defaults");
            save(configPath);
        }
    }

    public static void save(Path configPath) {
        Path configFile = configPath.resolve("chestlogger.json");

        try {
            Files.createDirectories(configPath);

            ConfigData data = new ConfigData();
            data.logRetentionDays = logRetentionDays;
            data.trackChests = trackChests;
            data.trackBarrels = trackBarrels;
            data.trackShulkerBoxes = trackShulkerBoxes;
            data.trackEnderChests = trackEnderChests;
            data.trackHoppers = trackHoppers;

            data.enableChestLocking = enableChestLocking;
            data.maxLocksPerPlayer = maxLocksPerPlayer;
            data.lockProtectFromFire = lockProtectFromFire;
            data.lockProtectFromTNT = lockProtectFromTNT;
            data.lockProtectFromMobs = lockProtectFromMobs;

            String json = GSON.toJson(data);
            Files.writeString(configFile, json);

            LOGGER.info("Config saved successfully");
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    private static class ConfigData {
        int logRetentionDays = 30;
        boolean trackChests = true;
        boolean trackBarrels = true;
        boolean trackShulkerBoxes = true;
        boolean trackEnderChests = false;
        boolean trackHoppers = false;

        boolean enableChestLocking = true;
        int maxLocksPerPlayer = -1;
        boolean lockProtectFromFire = true;
        boolean lockProtectFromTNT = true;
        boolean lockProtectFromMobs = true;
    }
}
