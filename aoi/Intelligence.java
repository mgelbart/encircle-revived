package aoi;

public interface Intelligence {
    public void processGameStateUpdate(GameState game);
    public PlayerControlParameters getNewControlParameters();
}
