/*
  The class that actually decides what to do
  based on (the history of) the game state.
*/

class HigherBeing implements SConIntelligence {

    int playerNo;

    public HigherBeing(int p) {
	playerNo = p;
    }

    public void processGameStateUpdate(SConParsedGameState game) {
	
    }

    public SConPlayerControlParameters getNewControlParameters() {
	return null;
    }

}