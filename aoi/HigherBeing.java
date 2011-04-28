package aoi;

/*
  The class that actually decides what to do
  based on (the history of) the game state.
*/

public class HigherBeing implements Intelligence {

    int playerNo;

    GameState[] games;

    public HigherBeing(int p) {
	playerNo = p;
	games = new GameState[1];

	assert(playerNo < 2);
	assert(playerNo >= 0);
    }

    public void processGameStateUpdate(GameState game) {
	games[0] = game;
	System.out.println(game.toString());
    }

    public PlayerControlParameters getNewControlParameters() {
	return new PlayerControlParameters(Simulation.INTMAX/2,
					       games[0].states[playerNo].direction + Simulation.INTMAX/2,
					       0, 0);
    }

}
