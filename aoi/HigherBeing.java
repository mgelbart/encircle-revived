package aoi;

/*
  The class that actually decides what to do
  based on (the history of) the game state.
*/

public class HigherBeing implements SConIntelligence {

    int playerNo;

    SConGameState[] games;

    public HigherBeing(int p) {
	playerNo = p;
	games = new SConGameState[1];

	assert(playerNo < 2);
	assert(playerNo >= 0);
    }

    public void processGameStateUpdate(SConGameState game) {
	games[0] = game;
	System.out.println(game.toString());
    }

    public SConPlayerControlParameters getNewControlParameters() {
	return new SConPlayerControlParameters(SConSimulation.INTMAX/2,
					       games[0].states[playerNo].direction + SConSimulation.INTMAX/2,
					       0, 0);
    }

}