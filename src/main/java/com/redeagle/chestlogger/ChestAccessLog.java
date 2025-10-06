package com.redeagle.chestlogger;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChestAccessLog {
    private final String playerName;
    private final BlockPos position;
    private final String dimension;
    private final long timestamp;
    private final List<String> itemsAdded;
    private final List<String> itemsRemoved;

    public ChestAccessLog(String playerName, BlockPos position, String dimension, long timestamp,
                          List<String> itemsAdded, List<String> itemsRemoved) {
        this.playerName = playerName;
        this.position = position;
        this.dimension = dimension;
        this.timestamp = timestamp;
        this.itemsAdded = new ArrayList<>(itemsAdded);
        this.itemsRemoved = new ArrayList<>(itemsRemoved);
    }

    public static ChestAccessLog fromNBT(NbtCompound tag) {
        String playerName = tag.getString("PlayerName").orElse("");
        BlockPos position = new BlockPos(
                tag.getInt("X").orElse(0),
                tag.getInt("Y").orElse(0),
                tag.getInt("Z").orElse(0)
        );
        String dimension = tag.getString("Dimension").orElse("minecraft:overworld");
        long timestamp = tag.getLong("Timestamp").orElse(0L);

        List<String> itemsAdded = new ArrayList<>();
        if (tag.contains("ItemsAdded")) {
            tag.getList("ItemsAdded").ifPresent(addedList -> {
                for (int i = 0; i < addedList.size(); i++) {
                    addedList.getString(i).ifPresent(itemsAdded::add);
                }
            });
        }

        List<String> itemsRemoved = new ArrayList<>();
        if (tag.contains("ItemsRemoved")) {
            tag.getList("ItemsRemoved").ifPresent(removedList -> {
                for (int i = 0; i < removedList.size(); i++) {
                    removedList.getString(i).ifPresent(itemsRemoved::add);
                }
            });
        }

        return new ChestAccessLog(playerName, position, dimension, timestamp, itemsAdded, itemsRemoved);
    }

    public NbtCompound toNBT() {
        NbtCompound tag = new NbtCompound();
        tag.putString("PlayerName", playerName);
        tag.putInt("X", position.getX());
        tag.putInt("Y", position.getY());
        tag.putInt("Z", position.getZ());
        tag.putString("Dimension", dimension);
        tag.putLong("Timestamp", timestamp);

        NbtList addedList = new NbtList();
        for (String item : itemsAdded) {
            addedList.add(NbtString.of(item));
        }
        tag.put("ItemsAdded", addedList);

        NbtList removedList = new NbtList();
        for (String item : itemsRemoved) {
            removedList.add(NbtString.of(item));
        }
        tag.put("ItemsRemoved", removedList);

        return tag;
    }

    public String getPlayerName() {
        return playerName;
    }

    public BlockPos getPosition() {
        return position;
    }

    public String getDimension() {
        return dimension;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<String> getItemsAdded() {
        return new ArrayList<>(itemsAdded);
    }

    public List<String> getItemsRemoved() {
        return new ArrayList<>(itemsRemoved);
    }

    public String getFormattedTimestamp() {
        Instant instant = Instant.ofEpochMilli(timestamp);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        return formatter.format(instant);
    }

    public String getFormattedPosition() {
        return String.format("X: %d, Y: %d, Z: %d", position.getX(), position.getY(), position.getZ());
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getFormattedTimestamp()).append(" - ");
        sb.append(playerName).append(" @ ");
        sb.append(getFormattedPosition()).append(" (").append(dimension).append(")");

        if (!itemsAdded.isEmpty()) {
            sb.append("\n  +: ").append(String.join(", ", itemsAdded));
        }
        if (!itemsRemoved.isEmpty()) {
            sb.append("\n  -: ").append(String.join(", ", itemsRemoved));
        }

        return sb.toString();
    }
}
