/*
  The class that actually decides what to do
  based on (the history of) the game state.
*/

class HigherBeing implements SConIntelligence {

    int playerNo;

    SConParsedGameState[] games;

    public HigherBeing(int p) {
	playerNo = p;
	games = new SConParsedGameState[1];
    }

    public void processGameStateUpdate(SConParsedGameState game) {
	games[0] = game;
	System.out.println(game.toString());
    }

    public SConPlayerControlParameters getNewControlParameters() {
	return new SConPlayerControlParameters(SConSimulation.INTMAX,
					       SConSimulation.INTMAX/2,
					       games[0].states[playerNo-1].direction + SConSimulation.INTMAX/2,
					       (int) (((double) SConSimulation.INTMAX) * (4.0/5.0)),
					       0, 0, 0);
    }

}