package aoi;

public interface SConIntelligence {
    public void processGameStateUpdate(SConGameState game);
    public SConPlayerControlParameters getNewControlParameters();
}