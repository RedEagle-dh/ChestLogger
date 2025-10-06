package com.redeagle.chestlogger;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

public class ChestLock {
    private final UUID ownerUUID;
    private final String ownerName;
    private final BlockPos position;
    private final String dimension;
    private final long lockTimestamp;

    public ChestLock(UUID ownerUUID, String ownerName, BlockPos position, String dimension, long lockTimestamp) {
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.position = position;
        this.dimension = dimension;
        this.lockTimestamp = lockTimestamp;
    }

    public static ChestLock fromNBT(NbtCompound tag) {
        String uuidString = tag.getString("OwnerUUID").orElse("");
        UUID ownerUUID = uuidString.isEmpty() ? UUID.randomUUID() : UUID.fromString(uuidString);
        String ownerName = tag.getString("OwnerName").orElse("Unknown");
        BlockPos position = new BlockPos(
                tag.getInt("X").orElse(0),
                tag.getInt("Y").orElse(0),
                tag.getInt("Z").orElse(0)
        );
        String dimension = tag.getString("Dimension").orElse("minecraft:overworld");
        long lockTimestamp = tag.getLong("LockTimestamp").orElse(System.currentTimeMillis());

        return new ChestLock(ownerUUID, ownerName, position, dimension, lockTimestamp);
    }

    public NbtCompound toNBT() {
        NbtCompound tag = new NbtCompound();
        tag.putString("OwnerUUID", ownerUUID.toString());
        tag.putString("OwnerName", ownerName);
        tag.putInt("X", position.getX());
        tag.putInt("Y", position.getY());
        tag.putInt("Z", position.getZ());
        tag.putString("Dimension", dimension);
        tag.putLong("LockTimestamp", lockTimestamp);
        return tag;
    }

    public boolean isOwner(UUID playerUUID) {
        return ownerUUID.equals(playerUUID);
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public BlockPos getPosition() {
        return position;
    }

    public String getDimension() {
        return dimension;
    }

    public long getLockTimestamp() {
        return lockTimestamp;
    }

    @Override
    public String toString() {
        return String.format("ChestLock[owner=%s, pos=%s, dimension=%s]",
                ownerName, position, dimension);
    }
}
