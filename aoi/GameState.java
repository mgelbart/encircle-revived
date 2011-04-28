package aoi;

/*
  A class used for communicating the game state
  from the server to the client.
 */

import java.io.*;

public class GameState implements Cloneable {
    public final int time;
    public final PlayerState[] states;

    public GameState(int time, PlayerState[] states) {
	assert(states.length == 2);

	this.time = time;
	this.states = states;
    }

    public GameState clone() {
	PlayerState[] clonedStates = new PlayerState[2];

	for (int j=0; j<2; ++j)
	    clonedStates[j] = states[j].clone();

	return new GameState(time, clonedStates);
    }

    public String toString() {
	StringBuilder s = new StringBuilder();
	
	s.append("time: ");
	s.append(time);
	s.append("\n");

	for (int j=0; j<2; ++j)
	    s.append(states[j].toString());

	return s.toString();
    }

    public void serialize(DataOutputStream o) {

	try {

	    o.writeBytes("t");
	    o.writeInt(time);

	    for (int j=0; j<2; ++j)
		states[j].serialize(o);

	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	}

    }

    public static GameState parse(DataInputStream i) {

	Byte cmd;
	int t;
	PlayerState states[] = new PlayerState[2];

	try {

	    cmd = i.readByte();
	    assert(cmd == 't');
	    t = i.readInt();

	    for (int j=0; j<2; ++j)
		states[j] = PlayerState.parse(i);
	    
	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	    return null;
	}

	return new GameState(t, states);
    }
}
