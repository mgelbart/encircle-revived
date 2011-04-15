interface SConIntelligence {
    public void processGameStateUpdate(SConParsedGameState game);
    public SConPlayerControlParameters getNewControlParameters();
}