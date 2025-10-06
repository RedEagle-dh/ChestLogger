package com.redeagle.chestlogger;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Chestlogger implements DedicatedServerModInitializer {
    public static final String MOD_ID = "chestlogger";
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestLogger");

    private static ChestLogManager logManager;
    private static ChestLockManager lockManager;

    @Override
    public void onInitializeServer() {
        LOGGER.info("Chest Logger Mod wird initialisiert...");

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Register command handler will be done in ChestLogCommands
        ChestLogCommands.register();

        // Register lock command handler
        ChestLockCommands.register();

        // Register event handler will be done in ChestEventHandler
        ChestEventHandler.register();

        // Register protection handler
        ChestProtectionHandler.register();
    }

    private void onServerTick(MinecraftServer server) {
        if (logManager != null) {
            logManager.tick();
        }
    }

    private void onServerStarting(MinecraftServer server) {
        LOGGER.info("Chest Logger startet auf Server...");

        // Load config
        Path configPath = server.getRunDirectory().resolve("config");
        Config.load(configPath);

        // Initialize log manager
        logManager = new ChestLogManager(server);

        // Initialize lock manager
        lockManager = new ChestLockManager(server);

        LOGGER.info("Chest Logger erfolgreich gestartet!");
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("Chest Logger wird heruntergefahren...");
        if (logManager != null) {
            logManager.flush(); // Save all unsaved logs before shutdown
        }
        if (lockManager != null) {
            lockManager.flush(); // Save all unsaved locks before shutdown
        }
        logManager = null;
        lockManager = null;
    }

    public static ChestLogManager getLogManager() {
        return logManager;
    }

    public static ChestLockManager getLockManager() {
        return lockManager;
    }
}
