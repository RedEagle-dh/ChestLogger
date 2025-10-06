package com.redeagle.chestlogger;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestProtectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestLogger");

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register(ChestProtectionHandler::onBlockBreak);
        UseBlockCallback.EVENT.register(ChestProtectionHandler::onUseBlock);
        LOGGER.info("ChestProtectionHandler registered");
    }

    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }

        if (!Config.enableChestLocking) {
            return ActionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();

        // Check if it's a container block with a BlockEntity
        if (world.getBlockEntity(pos) == null) {
            return ActionResult.PASS; // Not a block entity
        }

        // Check if this container is locked
        ChestLockManager lockManager = Chestlogger.getLockManager();
        if (lockManager == null) {
            return ActionResult.PASS;
        }

        String dimension = world.getRegistryKey().getValue().toString();

        if (lockManager.isLocked(pos, dimension)) {
            if (!lockManager.canAccess(serverPlayer, pos, dimension)) {
                serverPlayer.sendMessage(Text.literal("§cThis container is locked!"), false);
                return ActionResult.FAIL; // Block the interaction
            }
        }

        return ActionResult.PASS; // Allow the interaction
    }

    private static boolean onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return true;
        }

        if (!Config.enableChestLocking) {
            return true;
        }

        ChestLockManager lockManager = Chestlogger.getLockManager();
        if (lockManager == null) {
            return true;
        }

        String dimension = world.getRegistryKey().getValue().toString();

        if (lockManager.isLocked(pos, dimension)) {
            // Check if player is owner or admin
            if (!lockManager.canAccess(serverPlayer, pos, dimension)) {
                player.sendMessage(Text.literal("§cThis container is locked and you cannot break it!"), false);
                return false; // Cancel the break
            } else if (!serverPlayer.hasPermissionLevel(2)) {
                // Owner can break their own lock
                player.sendMessage(Text.literal("§eYou broke your locked container. The lock has been removed."), false);
                lockManager.unlockChest(pos, dimension);
                return true; // Allow break
            } else {
                // Admin breaking - warn but allow
                player.sendMessage(Text.literal("§6Admin: Broke locked container owned by " +
                    lockManager.getLock(pos, dimension).getOwnerName()), false);
                lockManager.unlockChest(pos, dimension);
                return true; // Allow break
            }
        }

        return true; // Not locked, allow break
    }
}
