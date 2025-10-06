package com.redeagle.chestlogger.mixin;

import com.redeagle.chestlogger.ChestEventHandler;
import com.redeagle.chestlogger.ChestLockManager;
import com.redeagle.chestlogger.Chestlogger;
import com.redeagle.chestlogger.Config;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Unique
    private BlockPos chestLogger$lastOpenedContainerPos;

    @Unique
    private ScreenHandler chestLogger$lastScreenHandler;

    @Unique
    private int chestLogger$tickCounter = 0;

    @Inject(method = "openHandledScreen", at = @At("TAIL"))
    private void onOpenHandledScreen(net.minecraft.screen.NamedScreenHandlerFactory factory, CallbackInfoReturnable<?> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Try to get the position from the factory if it's a block entity
        if (factory instanceof BlockEntity blockEntity) {
            chestLogger$lastOpenedContainerPos = blockEntity.getPos();
            chestLogger$lastScreenHandler = player.currentScreenHandler;

            if (player.currentScreenHandler != null) {
                String dimension = player.getWorld().getRegistryKey().getValue().toString();
                ChestEventHandler.onContainerOpen(player, player.currentScreenHandler, chestLogger$lastOpenedContainerPos, dimension);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        // Only check every 5 ticks (4 times per second) for performance
        if (chestLogger$lastOpenedContainerPos == null) {
            return; // Early exit if no container is open
        }

        chestLogger$tickCounter++;
        if (chestLogger$tickCounter < 5) {
            return;
        }
        chestLogger$tickCounter = 0;

        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;

        // Check if screen handler was closed (player is back to inventory)
        if (chestLogger$lastScreenHandler != null &&
            player.currentScreenHandler != chestLogger$lastScreenHandler) {
            ChestEventHandler.onContainerClose(player, chestLogger$lastScreenHandler);
            chestLogger$lastOpenedContainerPos = null;
            chestLogger$lastScreenHandler = null;
        }
    }
}
