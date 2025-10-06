package com.redeagle.chestlogger;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Chestlogger implements DedicatedServerModInitializer {
    public static final String MOD_ID = "chestlogger";
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestLogger");

    private static ChestLogManager logManager;

    @Override
    public void onInitializeServer() {
        LOGGER.info("Chest Logger Mod wird initialisiert...");

        // Register server lifecycle events
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        // Register command handler will be done in ChestLogCommands
        ChestLogCommands.register();

        // Register event handler will be done in ChestEventHandler
        ChestEventHandler.register();
    }

    private void onServerStarting(MinecraftServer server) {
        LOGGER.info("Chest Logger startet auf Server...");

        // Load config
        Path configPath = server.getRunDirectory().resolve("config");
        Config.load(configPath);

        // Initialize log manager
        logManager = new ChestLogManager(server);

        LOGGER.info("Chest Logger erfolgreich gestartet!");
    }

    private void onServerStopping(MinecraftServer server) {
        LOGGER.info("Chest Logger wird heruntergefahren...");
        logManager = null;
    }

    public static ChestLogManager getLogManager() {
        return logManager;
    }
}
