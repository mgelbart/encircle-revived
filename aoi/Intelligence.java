package aoi;

public interface Intelligence extends Runnable {
    public void processGameStateUpdate(GameState game);
    public PlayerControlParameters getNewControlParameters();
}
