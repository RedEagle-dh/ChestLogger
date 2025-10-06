package com.redeagle.chestlogger;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ChestEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestLogger");
    private static final Map<UUID, ContainerSnapshot> openContainers = new HashMap<>();

    public static void register() {
        // Fabric uses a different event system - we need to track screen opening/closing
        // This is done via ScreenHandler events
        LOGGER.info("ChestEventHandler registered");
    }

    public static void onContainerOpen(ServerPlayerEntity player, ScreenHandler handler, BlockPos pos, String dimension) {
        LOGGER.info("Container opened event fired!");

        if (pos != null && isTrackedContainer(player.getWorld(), pos)) {
            // Take snapshot of container contents
            Map<String, Integer> contents = captureContainerContents(handler);
            LOGGER.info("Captured {} unique items in container", contents.size());
            LOGGER.info("Container contents: {}", contents);

            ContainerSnapshot snapshot = new ContainerSnapshot(
                    pos,
                    dimension,
                    contents
            );
            openContainers.put(player.getUuid(), snapshot);
            LOGGER.info("Player {} opened container at {}, snapshot saved", player.getName().getString(), pos);
        } else {
            LOGGER.info("Container not tracked or position is null");
        }
    }

    public static void onContainerClose(ServerPlayerEntity player, ScreenHandler handler) {
        LOGGER.info("Container closed event fired!");

        UUID playerUUID = player.getUuid();
        ContainerSnapshot snapshot = openContainers.remove(playerUUID);

        if (snapshot == null) {
            LOGGER.info("No snapshot found for player {}", player.getName().getString());
            return;
        }

        // Compare old and new contents
        Map<String, Integer> newContents = captureContainerContents(handler);
        LOGGER.info("Old contents: {}", snapshot.contents);
        LOGGER.info("New contents: {}", newContents);

        List<String> itemsAdded = new ArrayList<>();
        List<String> itemsRemoved = new ArrayList<>();

        // Find items that were added
        for (Map.Entry<String, Integer> entry : newContents.entrySet()) {
            String itemName = entry.getKey();
            int newCount = entry.getValue();
            int oldCount = snapshot.contents.getOrDefault(itemName, 0);

            if (newCount > oldCount) {
                itemsAdded.add(itemName + " x" + (newCount - oldCount));
                LOGGER.info("Item added: {} x{}", itemName, (newCount - oldCount));
            }
        }

        // Find items that were removed
        for (Map.Entry<String, Integer> entry : snapshot.contents.entrySet()) {
            String itemName = entry.getKey();
            int oldCount = entry.getValue();
            int newCount = newContents.getOrDefault(itemName, 0);

            if (oldCount > newCount) {
                itemsRemoved.add(itemName + " x" + (oldCount - newCount));
                LOGGER.info("Item removed: {} x{}", itemName, (oldCount - newCount));
            }
        }

        LOGGER.info("Items added: {}, Items removed: {}", itemsAdded.size(), itemsRemoved.size());

        // Only log if there were changes
        if (!itemsAdded.isEmpty() || !itemsRemoved.isEmpty()) {
            ChestAccessLog log = new ChestAccessLog(
                    player.getName().getString(),
                    snapshot.position,
                    snapshot.dimension,
                    System.currentTimeMillis(),
                    itemsAdded,
                    itemsRemoved
            );

            ChestLogManager manager = Chestlogger.getLogManager();
            if (manager != null) {
                manager.addLog(log);
                LOGGER.info("Logged chest access by {} at {}", player.getName().getString(), snapshot.position);
            } else {
                LOGGER.warn("ChestLogManager is null, cannot save log!");
            }
        } else {
            LOGGER.info("No changes detected, not logging");
        }
    }

    private static boolean isTrackedContainer(World world, BlockPos pos) {
        if (pos == null || world == null) {
            return false;
        }

        Block block = world.getBlockState(pos).getBlock();

        // Check vanilla containers
        boolean isVanillaContainer =
            (Config.trackChests && block instanceof ChestBlock) ||
            (Config.trackBarrels && block instanceof BarrelBlock) ||
            (Config.trackShulkerBoxes && block instanceof ShulkerBoxBlock) ||
            (Config.trackEnderChests && block instanceof EnderChestBlock) ||
            (Config.trackHoppers && block instanceof HopperBlock);

        if (isVanillaContainer) {
            return true;
        }

        // Check for any modded containers by checking if there's a BlockEntity with inventory
        // This catches Reinforced Chests, Barrels, Shulkers, and any other modded containers
        String blockName = block.getTranslationKey().toLowerCase();
        return blockName.contains("chest") ||
               blockName.contains("barrel") ||
               blockName.contains("shulker") ||
               blockName.contains("storage") ||
               blockName.contains("container");
    }

    private static Map<String, Integer> captureContainerContents(ScreenHandler handler) {
        Map<String, Integer> contents = new HashMap<>();

        // For GenericContainerScreenHandler, only capture the container inventory slots (not player inventory)
        if (handler instanceof GenericContainerScreenHandler containerHandler) {
            int containerSize = containerHandler.getInventory().size();
            for (int i = 0; i < containerSize; i++) {
                ItemStack stack = containerHandler.getSlot(i).getStack();
                if (!stack.isEmpty()) {
                    String itemName = stack.getItem().toString();
                    contents.merge(itemName, stack.getCount(), Integer::sum);
                }
            }
        } else {
            // Fallback for other container types - capture all slots
            for (int i = 0; i < handler.slots.size(); i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (!stack.isEmpty()) {
                    String itemName = stack.getItem().toString();
                    contents.merge(itemName, stack.getCount(), Integer::sum);
                }
            }
        }

        return contents;
    }

    private static class ContainerSnapshot {
        final BlockPos position;
        final String dimension;
        final Map<String, Integer> contents;

        ContainerSnapshot(BlockPos position, String dimension, Map<String, Integer> contents) {
            this.position = position;
            this.dimension = dimension;
            this.contents = contents;
        }
    }
}
