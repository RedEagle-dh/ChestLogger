package com.redeagle.chestlogger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class ChestLockCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChestLogger");

    public static void register() {
        CommandRegistrationCallback.EVENT.register(ChestLockCommands::registerCommands);
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher,
                                        CommandRegistryAccess registryAccess,
                                        CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("chestlock")
                .requires(source -> source.hasPermissionLevel(0))
                .then(literal("lock").executes(ChestLockCommands::lockChest))
                .then(literal("unlock").executes(ChestLockCommands::unlockChest))
                .then(literal("info").executes(ChestLockCommands::lockInfo))
                .then(literal("list").executes(ChestLockCommands::listLocks))
        );
    }

    private static int lockChest(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can lock chests"));
            return 0;
        }

        if (!Config.enableChestLocking) {
            source.sendError(Text.literal("Chest locking is disabled"));
            return 0;
        }

        BlockPos targetPos = getTargetBlock(player);
        if (targetPos == null) {
            source.sendError(Text.literal("You must be looking at a container"));
            return 0;
        }

        World world = player.getWorld();
        BlockState state = world.getBlockState(targetPos);

        if (!isLockableContainer(state.getBlock())) {
            source.sendError(Text.literal("This block cannot be locked"));
            return 0;
        }

        ChestLockManager lockManager = Chestlogger.getLockManager();
        String dimension = world.getRegistryKey().getValue().toString();

        if (lockManager.isLocked(targetPos, dimension)) {
            source.sendError(Text.literal("This container is already locked"));
            return 0;
        }

        // Check max locks per player
        if (Config.maxLocksPerPlayer > 0) {
            int currentLocks = lockManager.getPlayerLocks(player.getUuid()).size();
            if (currentLocks >= Config.maxLocksPerPlayer) {
                source.sendError(Text.literal("You have reached the maximum number of locks (" + Config.maxLocksPerPlayer + ")"));
                return 0;
            }
        }

        lockManager.lockChest(player, targetPos, dimension);
        source.sendFeedback(() -> Text.literal("§aContainer locked successfully"), false);
        LOGGER.info("Player {} locked container at {} in {}", player.getName().getString(), targetPos, dimension);

        return 1;
    }

    private static int unlockChest(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can unlock chests"));
            return 0;
        }

        BlockPos targetPos = getTargetBlock(player);
        if (targetPos == null) {
            source.sendError(Text.literal("You must be looking at a container"));
            return 0;
        }

        World world = player.getWorld();
        String dimension = world.getRegistryKey().getValue().toString();

        ChestLockManager lockManager = Chestlogger.getLockManager();

        if (!lockManager.isLocked(targetPos, dimension)) {
            source.sendError(Text.literal("This container is not locked"));
            return 0;
        }

        ChestLock lock = lockManager.getLock(targetPos, dimension);
        boolean isAdmin = source.hasPermissionLevel(2);

        if (!lock.isOwner(player.getUuid()) && !isAdmin) {
            source.sendError(Text.literal("You don't own this lock"));
            return 0;
        }

        lockManager.unlockChest(targetPos, dimension);
        source.sendFeedback(() -> Text.literal("§aContainer unlocked successfully"), false);
        LOGGER.info("Player {} unlocked container at {} in {}", player.getName().getString(), targetPos, dimension);

        return 1;
    }

    private static int lockInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can check lock info"));
            return 0;
        }

        BlockPos targetPos = getTargetBlock(player);
        if (targetPos == null) {
            source.sendError(Text.literal("You must be looking at a container"));
            return 0;
        }

        World world = player.getWorld();
        String dimension = world.getRegistryKey().getValue().toString();

        ChestLockManager lockManager = Chestlogger.getLockManager();

        if (!lockManager.isLocked(targetPos, dimension)) {
            source.sendFeedback(() -> Text.literal("§7This container is not locked"), false);
            return 1;
        }

        ChestLock lock = lockManager.getLock(targetPos, dimension);
        source.sendFeedback(() -> Text.literal("§6=== Lock Info ==="), false);
        source.sendFeedback(() -> Text.literal("§7Owner: §f" + lock.getOwnerName()), false);
        source.sendFeedback(() -> Text.literal("§7Position: §f" + lock.getPosition()), false);
        source.sendFeedback(() -> Text.literal("§7Dimension: §f" + lock.getDimension()), false);
        source.sendFeedback(() -> Text.literal("§7Locked since: §f" + new java.util.Date(lock.getLockTimestamp())), false);

        return 1;
    }

    private static int listLocks(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("Only players can list locks"));
            return 0;
        }

        ChestLockManager lockManager = Chestlogger.getLockManager();
        var locks = lockManager.getPlayerLocks(player.getUuid());

        if (locks.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§7You don't have any locked containers"), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("§6=== Your Locks (" + locks.size() + ") ==="), false);
        for (ChestLock lock : locks) {
            String posText = String.format("[%d, %d, %d]",
                lock.getPosition().getX(),
                lock.getPosition().getY(),
                lock.getPosition().getZ());
            source.sendFeedback(() -> Text.literal("§7" + posText + " §8in §7" + lock.getDimension()), false);
        }

        if (Config.maxLocksPerPlayer > 0) {
            source.sendFeedback(() -> Text.literal("§8(" + locks.size() + "/" + Config.maxLocksPerPlayer + " locks used)"), false);
        }

        return 1;
    }

    private static BlockPos getTargetBlock(ServerPlayerEntity player) {
        World world = player.getWorld();
        double reachDistance = 5.0;

        BlockHitResult hitResult = (BlockHitResult) world.raycast(new RaycastContext(
                player.getCameraPosVec(1.0F),
                player.getCameraPosVec(1.0F).add(player.getRotationVec(1.0F).multiply(reachDistance)),
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return hitResult.getBlockPos();
        }

        return null;
    }

    private static boolean isLockableContainer(Block block) {
        String blockName = block.getTranslationKey().toLowerCase();
        return blockName.contains("chest") ||
               blockName.contains("barrel") ||
               blockName.contains("shulker") ||
               blockName.contains("storage") ||
               blockName.contains("container");
    }
}
