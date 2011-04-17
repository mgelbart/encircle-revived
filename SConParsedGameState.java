class SConParsedGameState {
    public int time;
    SConPlayerState[] states;

    public String toString() {
	StringBuilder s = new StringBuilder();
	
	s.append("time: ");
	s.append(time);

	for (int j=0; j<2; ++j) {
	    s.append("\nPlayer #");
	    s.append(j+1);
	    s.append("\n");

	    s.append(states[j].toString());
	}

	return s.toString();
    }
}
