package ru.night.util.player;

public class MovementManager {
    private static final MovementManager instance = new MovementManager();

    public static MovementManager getInstance() {
        return instance;
    }

    public void unlockMovement(String reason) {}
    public void lockMovement(String reason) {}
}
