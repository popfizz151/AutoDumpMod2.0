package com.example.autodump;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class AutoDump implements ClientModInitializer {

    private enum State {
        MINING,
        GOING_TO_CHEST,
        WAITING_AT_CHEST,
        DUMPING,
        RETURNING
    }

    private State state = State.MINING;

    private final BlockPos minePos = new BlockPos(120, 12, 120); // CHANGE THIS
    private final List<BlockPos> chests = List.of(
            new BlockPos(100, 64, 100) // CHANGE THIS
    );

    private final String mineCommand = "mine diamond_ore"; // CHANGE BLOCK
    private int chestIndex = 0;
    private int waitTicks = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            switch (state) {

                case MINING -> {
                    if (isInventoryFull(client)) {
                        stopBaritone();
                        goToChest();
                        state = State.GOING_TO_CHEST;
                    }
                }

                case GOING_TO_CHEST -> {
                    if (atChest(client)) {
                        waitTicks = 20;
                        state = State.WAITING_AT_CHEST;
                    }
                }

                case WAITING_AT_CHEST -> {
                    if (--waitTicks <= 0) {
                        openChest(client);
                        state = State.DUMPING;
                    }
                }

                case DUMPING -> {
                    if (dumpInventory(client)) {
                        closeScreen(client);
                        returnToMine();
                        state = State.RETURNING;
                    } else {
                        state = State.RETURNING;
                    }
                }

                case RETURNING -> {
                    if (client.player.getBlockPos().isWithinDistance(minePos, 3)) {
                        resumeMining();
                        state = State.MINING;
                    }
                }
            }
        });
    }

    private boolean isInventoryFull(MinecraftClient client) {
        return client.player.getInventory().getEmptySlot() == -1;
    }

    private void stopBaritone() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
    }

    private void resumeMining() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(mineCommand);
    }

    private void goToChest() {
        BaritoneAPI.getProvider().getPrimaryBaritone()
                .getCustomGoalProcess()
                .setGoalAndPath(new GoalBlock(chests.get(chestIndex)));
    }

    private void returnToMine() {
        BaritoneAPI.getProvider().getPrimaryBaritone()
                .getCustomGoalProcess()
                .setGoalAndPath(new GoalBlock(minePos));
    }

    private boolean atChest(MinecraftClient client) {
        return client.player.getBlockPos().isWithinDistance(chests.get(chestIndex), 3);
    }

    private void openChest(MinecraftClient client) {
        client.interactionManager.interactBlock(
                client.player,
                client.player.getMainHandStack(),
                null
        );
    }

    private boolean dumpInventory(MinecraftClient client) {
        if (!(client.player.currentScreenHandler instanceof GenericContainerScreenHandler handler))
            return false;

        int containerSlots = handler.getRows() * 9;
        boolean movedAny = false;

        for (int i = containerSlots; i < containerSlots + 36; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                client.interactionManager.clickSlot(
                        handler.syncId,
                        i,
                        0,
                        SlotActionType.QUICK_MOVE,
                        client.player
                );
                movedAny = true;
            }
        }

        return movedAny;
    }

    private void closeScreen(MinecraftClient client) {
        client.player.closeHandledScreen();
    }
}
